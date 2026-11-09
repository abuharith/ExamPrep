package com.examprep.ai.client;

import com.examprep.common.exception.AiNotConfiguredException;
import com.examprep.common.exception.AiServiceException;
import com.examprep.config.AppProperties;
import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.Part;
import jakarta.annotation.PreDestroy;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Gemini Developer API client built on the official Google Gen AI Java SDK.
 *
 * <p>The API key is read from configuration only. Requests always ask for JSON output,
 * use a bounded response size and a request timeout, and are retried a limited number of
 * times for transient or rate-limited failures.</p>
 */
@Component
public class GoogleGenAiGeminiGateway implements GeminiGateway {

    private static final Logger log = LoggerFactory.getLogger(GoogleGenAiGeminiGateway.class);
    private static final Set<Integer> RETRYABLE_CODES = Set.of(408, 429, 500, 502, 503, 504);

    private final AppProperties.Ai configuration;
    private volatile Client client;

    public GoogleGenAiGeminiGateway(AppProperties properties) {
        this.configuration = properties.ai();
    }

    @Override
    public boolean isConfigured() {
        return configuration.configured();
    }

    @Override
    public String generateJson(GeminiPrompt prompt) {
        if (!isConfigured()) {
            throw new AiNotConfiguredException();
        }
        int attempts = Math.max(1, configuration.maxAttempts());
        AiServiceException lastFailure = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return callModel(prompt);
            } catch (AiServiceException ex) {
                lastFailure = ex;
                if (!ex.isRetryable() || attempt == attempts) {
                    break;
                }
                long delay = configuration.retryBackoffMs() * (1L << (attempt - 1));
                log.info("Gemini request failed (attempt {}/{}), retrying in {} ms", attempt, attempts, delay);
                sleep(delay);
            }
        }
        throw lastFailure == null ? new AiServiceException("The AI service could not complete the request.")
                : lastFailure;
    }

    private String callModel(GeminiPrompt prompt) {
        try {
            GenerateContentConfig requestConfig = GenerateContentConfig.builder()
                    .systemInstruction(Content.fromParts(Part.fromText(prompt.systemInstruction())))
                    .responseMimeType("application/json")
                    .temperature(0.2f)
                    .maxOutputTokens(prompt.maxOutputTokens())
                    .build();
            GenerateContentResponse response = client().models.generateContent(
                    configuration.model(),
                    Content.fromParts(Part.fromText(prompt.userPrompt())),
                    requestConfig);
            String text = response.text();
            if (text == null || text.isBlank()) {
                throw new AiServiceException(
                        "The AI service returned an empty response. Try again in a moment.");
            }
            return text;
        } catch (ApiException ex) {
            boolean retryable = RETRYABLE_CODES.contains(ex.code());
            String message = switch (ex.code()) {
                case 400 -> "The AI service rejected the request. The document may be too large or malformed.";
                case 401, 403 -> "The Gemini API key was rejected. Check GOOGLE_API_KEY on the server.";
                case 404 -> "The configured Gemini model is not available. Check GEMINI_MODEL on the server.";
                case 429 -> "The Gemini rate limit was reached. Try again in a minute.";
                default -> "The AI service is temporarily unavailable. Try again in a moment.";
            };
            throw new AiServiceException(message, ex).retryable(retryable);
        } catch (GenAiIOException | IllegalStateException ex) {
            throw new AiServiceException(
                    "The AI service could not be reached. Check the server's network connection and try again.", ex)
                    .retryable(true);
        }
    }

    private Client client() {
        Client current = client;
        if (current == null) {
            synchronized (this) {
                current = client;
                if (current == null) {
                    current = Client.builder()
                            .apiKey(configuration.apiKey())
                            .httpOptions(HttpOptions.builder()
                                    .timeout(Math.max(5, configuration.timeoutSeconds()) * 1000)
                                    .build())
                            .build();
                    client = current;
                }
            }
        }
        return current;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiServiceException("The AI request was interrupted.", ex);
        }
    }

    @PreDestroy
    void shutdown() {
        Client current = client;
        if (current != null) {
            current.close();
        }
    }
}

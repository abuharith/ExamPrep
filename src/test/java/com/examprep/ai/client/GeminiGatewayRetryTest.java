package com.examprep.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.examprep.common.exception.AiServiceException;
import com.examprep.config.AppProperties;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** Exercises the retry policy without any network access or real API key. */
class GeminiGatewayRetryTest {

    private static final GeminiPrompt PROMPT = new GeminiPrompt("system", "user", 100);

    /** Gateway whose "model call" replays scripted outcomes and counts invocations. */
    private static final class ScriptedGateway extends GoogleGenAiGeminiGateway {
        private final Deque<Supplier<String>> outcomes = new ArrayDeque<>();
        int calls;

        ScriptedGateway(int maxAttempts) {
            super(new AppProperties(
                    new AppProperties.Storage("/tmp/examprep-test"),
                    new AppProperties.Upload(25),
                    new AppProperties.Async(1, 1, 1),
                    new AppProperties.Ai("test-key", "gemini-3.8-flash", 30, maxAttempts, 0, 4096),
                    new AppProperties.Generation(60000, 60000)));
        }

        ScriptedGateway then(Supplier<String> outcome) {
            outcomes.add(outcome);
            return this;
        }

        @Override
        String callModel(GeminiPrompt prompt) {
            calls++;
            return outcomes.poll().get();
        }
    }

    private static Supplier<String> failure(String message, boolean retryable) {
        return () -> {
            throw new AiServiceException(message).retryable(retryable);
        };
    }

    @Test
    void retriesRateLimitsAndTransientOutagesThenSucceeds() {
        ScriptedGateway gateway = new ScriptedGateway(3)
                .then(failure("The Gemini rate limit was reached.", true))
                .then(failure("The AI service is temporarily unavailable.", true))
                .then(() -> "{\"ok\":true}");

        assertThat(gateway.generateJson(PROMPT)).isEqualTo("{\"ok\":true}");
        assertThat(gateway.calls).isEqualTo(3);
    }

    @Test
    void givesUpAfterTheConfiguredAttemptsAndReportsTheLastFailure() {
        ScriptedGateway gateway = new ScriptedGateway(2)
                .then(failure("first", true))
                .then(failure("The Gemini rate limit was reached.", true));

        assertThatThrownBy(() -> gateway.generateJson(PROMPT))
                .isInstanceOf(AiServiceException.class)
                .hasMessageContaining("rate limit");
        assertThat(gateway.calls).isEqualTo(2);
    }

    @Test
    void doesNotRetryFailuresThatCannotSucceed() {
        ScriptedGateway gateway = new ScriptedGateway(3)
                .then(failure("The Gemini API key was rejected.", false));

        assertThatThrownBy(() -> gateway.generateJson(PROMPT))
                .isInstanceOf(AiServiceException.class)
                .hasMessageContaining("API key");
        assertThat(gateway.calls).isEqualTo(1);
    }

    @Test
    void treatsAnUnreachableServiceAsRetryableThenFails() {
        ScriptedGateway gateway = new ScriptedGateway(2)
                .then(failure("The AI service could not be reached.", true))
                .then(failure("The AI service could not be reached.", true));

        assertThatThrownBy(() -> gateway.generateJson(PROMPT))
                .isInstanceOf(AiServiceException.class)
                .hasMessageContaining("could not be reached");
        assertThat(gateway.calls).isEqualTo(2);
    }
}

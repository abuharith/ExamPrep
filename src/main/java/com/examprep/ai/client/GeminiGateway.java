package com.examprep.ai.client;

/**
 * All Gemini access goes through this interface so that business logic never depends on
 * SDK types and tests can supply deterministic responses.
 */
public interface GeminiGateway {

    /** @return the raw JSON text produced by the model. */
    String generateJson(GeminiPrompt prompt);

    boolean isConfigured();
}

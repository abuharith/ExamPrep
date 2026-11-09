package com.examprep.common.exception;

/**
 * Raised when an AI action is requested but GOOGLE_API_KEY is not set. The application
 * still starts in this state and reports the problem instead of faking a response.
 */
public class AiNotConfiguredException extends RuntimeException {

    public AiNotConfiguredException() {
        super("Gemini is not configured on this server. Set GOOGLE_API_KEY and restart the application.");
    }
}

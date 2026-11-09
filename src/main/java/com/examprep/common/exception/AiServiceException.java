package com.examprep.common.exception;

/** A Gemini request failed or returned output that could not be used. */
public class AiServiceException extends RuntimeException {

    private final boolean retryable;

    public AiServiceException(String message) {
        super(message);
        this.retryable = false;
    }

    public AiServiceException(String message, Throwable cause) {
        super(message, cause);
        this.retryable = false;
    }

    private AiServiceException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.retryable = retryable;
    }

    public AiServiceException retryable(boolean retryable) {
        return new AiServiceException(getMessage(), getCause(), retryable);
    }

    public boolean isRetryable() {
        return retryable;
    }
}

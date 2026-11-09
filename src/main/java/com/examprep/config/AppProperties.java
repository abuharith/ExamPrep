package com.examprep.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed application configuration. All Gemini settings come from the environment; the
 * API key is never rendered into HTML, JavaScript or the logs.
 */
@ConfigurationProperties("examprep")
public record AppProperties(Storage storage, Upload upload, Async async, Ai ai, Generation generation) {

    public record Storage(String location) {
    }

    public record Upload(int maxSizeMb) {
        public long maxSizeBytes() {
            return (long) maxSizeMb * 1024L * 1024L;
        }
    }

    public record Async(int corePoolSize, int maxPoolSize, int queueCapacity) {
    }

    public record Ai(String apiKey, String model, int timeoutSeconds, int maxAttempts,
                     long retryBackoffMs, int maxOutputTokens) {

        public boolean configured() {
            return apiKey != null && !apiKey.isBlank();
        }

        /** Deliberately omits the key so that nothing can leak it into a log line. */
        @Override
        public String toString() {
            return "Ai[apiKey=" + (configured() ? "***" : "<not set>") + ", model=" + model + "]";
        }
    }

    public record Generation(int maxSourceChars, int summariseThresholdChars) {
    }
}

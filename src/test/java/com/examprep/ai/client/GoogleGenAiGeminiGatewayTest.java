package com.examprep.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.examprep.common.exception.AiNotConfiguredException;
import com.examprep.config.AppProperties;
import org.junit.jupiter.api.Test;

class GoogleGenAiGeminiGatewayTest {

    @Test
    void reportsMissingConfigurationInsteadOfFakingAResponse() {
        AppProperties properties = new AppProperties(
                new AppProperties.Storage("/tmp/examprep-test"),
                new AppProperties.Upload(25),
                new AppProperties.Async(1, 1, 1),
                new AppProperties.Ai("", "gemini-3.8-flash", 30, 3, 1000, 4096),
                new AppProperties.Generation(60000, 60000));
        GoogleGenAiGeminiGateway gateway = new GoogleGenAiGeminiGateway(properties);

        assertThat(gateway.isConfigured()).isFalse();
        assertThatThrownBy(() -> gateway.generateJson(new GeminiPrompt("system", "user", 100)))
                .isInstanceOf(AiNotConfiguredException.class)
                .hasMessageContaining("GOOGLE_API_KEY");
    }

    @Test
    void neverPrintsTheApiKeyWhenConfigurationIsLogged() {
        AppProperties.Ai ai = new AppProperties.Ai("super-secret-key", "gemini-3.8-flash", 30, 3, 1000, 4096);

        assertThat(ai.toString()).doesNotContain("super-secret-key");
        assertThat(ai.configured()).isTrue();
    }
}

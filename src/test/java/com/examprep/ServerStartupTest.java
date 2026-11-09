package com.examprep;

import static org.assertj.core.api.Assertions.assertThat;

import com.examprep.support.TestPostgres;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Starts the real embedded Tomcat with the production configuration, proving that the
 * application boots against a migrated PostgreSQL schema and serves Thymeleaf pages over HTTP.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ServerStartupTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestPostgres.register(registry);
    }

    @LocalServerPort
    private int port;

    @Test
    void startsAndServesTheLandingPage() throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/"))
                        .timeout(Duration.ofSeconds(15)).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("ExamPrep").contains("mock examination");

        HttpResponse<String> stylesheet = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/css/app.css"))
                        .timeout(Duration.ofSeconds(15)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(stylesheet.statusCode()).isEqualTo(200);
        assertThat(stylesheet.headers().firstValue("content-type").orElse("")).startsWith("text/css");
    }
}

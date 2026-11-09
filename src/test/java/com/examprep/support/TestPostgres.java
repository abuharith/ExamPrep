package com.examprep.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * One real PostgreSQL server for the test JVM, started from the embedded distribution so no
 * Docker daemon is required. The production Flyway migrations create the schema.
 */
public final class TestPostgres {

    private static final EmbeddedPostgres POSTGRES = startPostgres();

    public static final String JDBC_URL = POSTGRES.getJdbcUrl("postgres", "postgres");
    public static final Path STORAGE = createStorage();

    private TestPostgres() {
    }

    public static void register(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> JDBC_URL);
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "");
        registry.add("examprep.storage.location", () -> STORAGE.toString());
        registry.add("examprep.async.core-pool-size", () -> 2);
        registry.add("examprep.async.max-pool-size", () -> 4);
        registry.add("examprep.async.queue-capacity", () -> 100);
    }

    private static EmbeddedPostgres startPostgres() {
        try {
            EmbeddedPostgres postgres = EmbeddedPostgres.builder().start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    postgres.close();
                } catch (IOException ignored) {
                    // The JVM is exiting; nothing useful can be done.
                }
            }));
            return postgres;
        } catch (IOException ex) {
            throw new IllegalStateException("Embedded PostgreSQL could not be started", ex);
        }
    }

    private static Path createStorage() {
        try {
            return Files.createTempDirectory("examprep-test-uploads");
        } catch (IOException ex) {
            throw new IllegalStateException("Test storage directory could not be created", ex);
        }
    }
}

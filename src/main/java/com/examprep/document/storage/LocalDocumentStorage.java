package com.examprep.document.storage;

import com.examprep.common.exception.StorageException;
import com.examprep.config.AppProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LocalDocumentStorage implements DocumentStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalDocumentStorage.class);

    private final Path baseDirectory;
    private final Path temporaryDirectory;

    public LocalDocumentStorage(AppProperties properties) {
        this.baseDirectory = Path.of(properties.storage().location()).toAbsolutePath().normalize();
        this.temporaryDirectory = baseDirectory.resolve(".incoming");
        try {
            Files.createDirectories(baseDirectory);
            Files.createDirectories(temporaryDirectory);
        } catch (IOException ex) {
            throw new StorageException("The document storage directory could not be created: " + baseDirectory, ex);
        }
        if (!Files.isWritable(baseDirectory)) {
            throw new StorageException("The document storage directory is not writable: " + baseDirectory);
        }
        log.info("Document storage ready at {}", baseDirectory);
    }

    @Override
    public Path store(InputStream content, String extension) throws IOException {
        Path target = resolveKey(newKey(extension));
        Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    @Override
    public Path resolve(String storageKey) {
        return resolveKey(storageKey);
    }

    private Path resolveKey(String storageKey) {
        Path candidate = baseDirectory.resolve(storageKey).normalize();
        if (!candidate.startsWith(baseDirectory)) {
            // Defensive: storage keys are generated internally, so this must never match.
            throw new StorageException("Invalid storage reference.");
        }
        return candidate;
    }

    @Override
    public void delete(String storageKey) throws IOException {
        Files.deleteIfExists(resolveKey(storageKey));
    }

    @Override
    public void deleteQuietly(String storageKey) {
        try {
            delete(storageKey);
        } catch (IOException | StorageException ex) {
            log.warn("Could not remove stored document {}", storageKey, ex);
        }
    }

    @Override
    public Path createTempFile(String extension) throws IOException {
        String suffix = extension == null || extension.isBlank() ? ".tmp" : "." + sanitiseExtension(extension);
        return Files.createTempFile(temporaryDirectory, "upload-", suffix);
    }

    private static String newKey(String extension) {
        return UUID.randomUUID().toString().replace("-", "") + "." + sanitiseExtension(extension);
    }

    private static String sanitiseExtension(String extension) {
        String cleaned = extension == null ? "" : extension.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return cleaned.isEmpty() ? "bin" : cleaned;
    }

    static {
        // Fail fast in development if the JVM cannot write temporary files.
        try {
            Files.createDirectories(Path.of(System.getProperty("java.io.tmpdir")));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}

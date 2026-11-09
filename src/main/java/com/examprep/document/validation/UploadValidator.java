package com.examprep.document.validation;

import com.examprep.common.exception.InvalidUploadException;
import com.examprep.config.AppProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Upload validation. The browser-supplied name and content type are never trusted: the
 * extension is checked against a whitelist, the size against configuration, and the real
 * content type is detected from the bytes.
 */
@Component
public class UploadValidator {

    private static final Map<String, Set<String>> ALLOWED_TYPES = Map.ofEntries(
            Map.entry("pdf", Set.of("application/pdf")),
            Map.entry("docx", Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document")),
            Map.entry("doc", Set.of("application/msword", "application/x-tika-msoffice")),
            Map.entry("pptx", Set.of("application/vnd.openxmlformats-officedocument.presentationml.presentation")),
            Map.entry("ppt", Set.of("application/vnd.ms-powerpoint", "application/x-tika-msoffice")),
            Map.entry("txt", Set.of("text/plain")),
            Map.entry("md", Set.of("text/plain", "text/markdown", "text/x-markdown", "text/x-web-markdown")),
            Map.entry("markdown", Set.of("text/plain", "text/markdown", "text/x-markdown", "text/x-web-markdown")),
            Map.entry("rtf", Set.of("application/rtf", "text/rtf")),
            Map.entry("odt", Set.of("application/vnd.oasis.opendocument.text")),
            Map.entry("odp", Set.of("application/vnd.oasis.opendocument.presentation")));

    private static final long MIN_SIZE_BYTES = 1;
    private final AppProperties properties;
    private final Tika tika = new Tika();

    public UploadValidator(AppProperties properties) {
        this.properties = properties;
    }

    public Set<String> supportedExtensions() {
        return ALLOWED_TYPES.keySet();
    }

    public long maxSizeBytes() {
        return properties.upload().maxSizeBytes();
    }

    /** Validates the file name, extension and size, returning a safe extension. */
    public String inspect(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidUploadException("Choose a non-empty document to upload.");
        }
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            throw new InvalidUploadException("The upload did not include a file name.");
        }
        String normalisedName = name.replace('\\', '/');
        normalisedName = normalisedName.substring(normalisedName.lastIndexOf('/') + 1).trim();
        if (normalisedName.isBlank() || normalisedName.equals(".") || normalisedName.equals("..")) {
            throw new InvalidUploadException("The upload has an invalid file name.");
        }
        if (normalisedName.length() > 255) {
            throw new InvalidUploadException("The file name is too long. Rename the file and try again.");
        }
        int dot = normalisedName.lastIndexOf('.');
        String extension = dot < 0 ? "" : normalisedName.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_TYPES.containsKey(extension)) {
            throw new InvalidUploadException(
                    "That file type is not supported. Upload PDF, DOCX, DOC, PPTX, PPT, TXT, Markdown, RTF, ODT or ODP.");
        }
        if (file.getSize() < MIN_SIZE_BYTES) {
            throw new InvalidUploadException("The uploaded file is empty.");
        }
        if (file.getSize() > maxSizeBytes()) {
            throw new InvalidUploadException("The file is larger than the "
                    + properties.upload().maxSizeMb() + MB + " MB upload limit.");
        }
        return extension;
    }

    private static final String MB = " MB";

    /** Detects the real media type from the stored bytes and rejects mismatches. */
    public String detectAndVerify(Path storedFile, String extension) throws IOException {
        String detected;
        try (InputStream in = Files.newInputStream(storedFile)) {
            detected = tika.detect(in, "upload." + extension);
        }
        String normalised = detected == null ? "" : detected.toLowerCase(Locale.ROOT);
        Set<String> accepted = ALLOWED_TYPES.get(extension);
        if (accepted == null || !accepted.contains(normalised)) {
            throw new InvalidUploadException(
                    "The file content does not match its ." + extension + " extension. "
                            + "Upload the document in its original format.");
        }
        return normalised;
    }
}

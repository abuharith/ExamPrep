package com.examprep.document.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.examprep.common.exception.InvalidUploadException;
import com.examprep.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class UploadValidatorTest {

    private static final AppProperties PROPERTIES = new AppProperties(
            new AppProperties.Storage("/tmp/examprep-test"),
            new AppProperties.Upload(1),
            new AppProperties.Async(1, 1, 1),
            new AppProperties.Ai("", "gemini-3.8-flash", 30, 1, 1, 4096),
            new AppProperties.Generation(60000, 60000));

    private final UploadValidator validator = new UploadValidator(PROPERTIES);

    @Test
    void acceptsSupportedExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "notes.PDF", "application/pdf", "%PDF-1.4".getBytes());

        assertThat(validator.inspect(file)).isEqualTo("pdf");
    }

    @Test
    void rejectsUnsupportedExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "script.exe", "application/octet-stream",
                new byte[]{1, 2, 3});

        assertThatThrownBy(() -> validator.inspect(file))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessageContaining("not supported");
    }

    @Test
    void rejectsEmptyFiles() {
        MockMultipartFile file = new MockMultipartFile("file", "notes.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> validator.inspect(file))
                .isInstanceOf(InvalidUploadException.class);
    }

    @Test
    void rejectsOversizedFiles() {
        byte[] oversized = new byte[1024 * 1024 + 10];
        MockMultipartFile file = new MockMultipartFile("file", "notes.pdf", "application/pdf", oversized);

        assertThatThrownBy(() -> validator.inspect(file))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessageContaining("larger than");
    }

    @Test
    void rejectsContentThatDoesNotMatchTheExtension(@TempDir Path directory) throws Exception {
        Path disguised = directory.resolve("upload.pdf");
        Files.writeString(disguised, "This is plainly a text file, not a PDF.", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> validator.detectAndVerify(disguised, "pdf"))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void acceptsRealTextContent(@TempDir Path directory) throws Exception {
        Path plain = directory.resolve("upload.txt");
        Files.writeString(plain, "Lecture notes about cell biology.", StandardCharsets.UTF_8);

        assertThat(validator.detectAndVerify(plain, "txt")).isEqualTo("text/plain");
    }
}

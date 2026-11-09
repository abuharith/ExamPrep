package com.examprep.document.parser;

import com.examprep.common.exception.ProcessingException;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;

/** Selects the parser for a stored document, based on the validated extension. */
@Component
public class DocumentExtractionRouter {

    private final List<DocumentParser> parsers;

    public DocumentExtractionRouter(List<DocumentParser> parsers) {
        this.parsers = List.copyOf(parsers);
    }

    public ExtractionResult extract(Path file, String originalFilename, String extension) {
        return parsers.stream()
                .filter(parser -> parser.supports(extension))
                .findFirst()
                .map(parser -> {
                    try {
                        return parser.extract(file, originalFilename);
                    } catch (ProcessingException ex) {
                        throw ex;
                    } catch (Exception ex) {
                        throw new ProcessingException(
                                "The document could not be processed. Check that it is not corrupted.", ex);
                    }
                })
                .orElseThrow(() -> new ProcessingException("This file type is not supported."));
    }
}

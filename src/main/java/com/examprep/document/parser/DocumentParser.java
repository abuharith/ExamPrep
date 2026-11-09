package com.examprep.document.parser;

import java.nio.file.Path;

public interface DocumentParser {

    boolean supports(String extension);

    ExtractionResult extract(Path file, String originalFilename) throws Exception;
}

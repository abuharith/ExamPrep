package com.examprep.document.parser;

import java.util.List;

/**
 * @param chunks    citable text chunks in document order
 * @param pageCount number of pages/slides when the format exposes them, otherwise 0
 * @param title     a title taken from document metadata, or null when none is available
 */
public record ExtractionResult(List<ExtractedChunk> chunks, int pageCount, String title) {

    public ExtractionResult {
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
    }

    public int characterCount() {
        return chunks.stream().mapToInt(chunk -> chunk.text().length()).sum();
    }
}

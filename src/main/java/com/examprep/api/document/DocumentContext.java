package com.examprep.api.document;

import java.util.List;
import java.util.UUID;

/** The extracted source material handed to the study-generation engine and the tutor. */
public record DocumentContext(UUID documentId, String filename, String title, List<DocumentChunkDto> chunks) {

    public DocumentContext {
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
    }
}

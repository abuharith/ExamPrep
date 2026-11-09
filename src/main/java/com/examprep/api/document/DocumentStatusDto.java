package com.examprep.api.document;

import java.time.Instant;
import java.util.UUID;

public record DocumentStatusDto(
        UUID documentId,
        String filename,
        ProcessingStatus status,
        String message,
        String failureReason,
        int chunkCount,
        int pageCount,
        Instant updatedAt) {
}

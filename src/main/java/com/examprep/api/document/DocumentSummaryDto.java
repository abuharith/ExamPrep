package com.examprep.api.document;

import java.time.Instant;
import java.util.UUID;

public record DocumentSummaryDto(
        UUID id,
        String originalFilename,
        String contentType,
        String extension,
        long sizeBytes,
        ProcessingStatus status,
        String failureReason,
        int chunkCount,
        int pageCount,
        Instant createdAt) {
}

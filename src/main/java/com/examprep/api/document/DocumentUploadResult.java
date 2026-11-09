package com.examprep.api.document;

import java.util.UUID;

public record DocumentUploadResult(UUID documentId, String originalFilename, long sizeBytes, ProcessingStatus status) {
}

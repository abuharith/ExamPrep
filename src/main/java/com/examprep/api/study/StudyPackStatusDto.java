package com.examprep.api.study;

import java.time.Instant;
import java.util.UUID;

public record StudyPackStatusDto(
        UUID studyPackId,
        UUID documentId,
        StudyPackStatus status,
        int progressPercent,
        String message,
        String failureReason,
        Instant updatedAt) {
}

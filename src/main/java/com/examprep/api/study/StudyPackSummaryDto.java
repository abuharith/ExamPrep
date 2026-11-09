package com.examprep.api.study;

import java.time.Instant;
import java.util.UUID;

public record StudyPackSummaryDto(
        UUID id,
        UUID documentId,
        String documentFilename,
        String title,
        StudyPackStatus status,
        int progressPercent,
        int flashcardCount,
        int practiceQuestionCount,
        Instant createdAt,
        Instant completedAt) {
}

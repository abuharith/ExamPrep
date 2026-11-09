package com.examprep.api.study;

import java.time.Instant;
import java.util.UUID;

public record QuizAttemptSummaryDto(
        UUID id,
        UUID studyPackId,
        String mode,
        int score,
        int totalQuestions,
        int percentage,
        int durationSeconds,
        Instant submittedAt) {
}

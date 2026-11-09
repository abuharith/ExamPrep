package com.examprep.api.study;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record QuizResultDto(
        UUID attemptId,
        String mode,
        int score,
        int totalQuestions,
        int percentage,
        int durationSeconds,
        Instant submittedAt,
        List<QuizQuestionReviewDto> reviews,
        List<String> revisionSuggestions) {

    public QuizResultDto {
        reviews = reviews == null ? List.of() : List.copyOf(reviews);
        revisionSuggestions = revisionSuggestions == null ? List.of() : List.copyOf(revisionSuggestions);
    }
}

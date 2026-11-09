package com.examprep.api.study;

import java.util.List;
import java.util.UUID;

/** Full answer feedback, returned only after a submission has been scored. */
public record QuizQuestionReviewDto(
        UUID questionId,
        QuestionType type,
        String prompt,
        List<String> options,
        String submittedAnswer,
        String correctAnswer,
        String explanation,
        boolean correct,
        Difficulty difficulty,
        String sourceReference) {

    public QuizQuestionReviewDto {
        options = options == null ? List.of() : List.copyOf(options);
    }
}

package com.examprep.api.study;

import java.util.Map;
import java.util.UUID;

/**
 * @param answers         question id to the student's answer (option text, "true"/"false" or free text)
 * @param durationSeconds elapsed time in seconds, used for study progress
 */
public record QuizSubmissionDto(Map<UUID, String> answers, int durationSeconds) {

    public QuizSubmissionDto {
        answers = answers == null ? Map.of() : Map.copyOf(answers);
    }
}

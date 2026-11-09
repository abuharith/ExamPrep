package com.examprep.api.study;

import java.util.List;
import java.util.UUID;

/**
 * A question as shown to the browser before submission. It deliberately carries no
 * correct answer and no explanation.
 */
public record QuestionDto(
        UUID id,
        QuestionType type,
        String prompt,
        List<String> options,
        Difficulty difficulty,
        String sourceReference) {

    public QuestionDto {
        options = options == null ? List.of() : List.copyOf(options);
    }
}

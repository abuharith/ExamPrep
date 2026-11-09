package com.examprep.api.study;

import java.time.Instant;
import java.util.List;

public record TutorAnswerDto(
        String answer,
        List<String> sourceReferences,
        boolean insufficientInformation,
        Instant answeredAt) {

    public TutorAnswerDto {
        sourceReferences = sourceReferences == null ? List.of() : List.copyOf(sourceReferences);
    }
}

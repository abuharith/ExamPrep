package com.examprep.api.study;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TutorMessageDto(UUID id, String role, String content, List<String> sourceReferences, Instant createdAt) {

    public TutorMessageDto {
        sourceReferences = sourceReferences == null ? List.of() : List.copyOf(sourceReferences);
    }
}

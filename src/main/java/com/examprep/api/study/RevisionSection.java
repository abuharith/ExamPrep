package com.examprep.api.study;

import java.util.List;

public record RevisionSection(String heading, String body, List<String> sourceReferences) {

    public RevisionSection {
        sourceReferences = sourceReferences == null ? List.of() : List.copyOf(sourceReferences);
    }
}

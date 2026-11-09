package com.examprep.api.study;

import java.util.List;

public record StudyTopic(String name, String explanation, List<String> keyConcepts, List<String> sourceReferences) {

    public StudyTopic {
        keyConcepts = keyConcepts == null ? List.of() : List.copyOf(keyConcepts);
        sourceReferences = sourceReferences == null ? List.of() : List.copyOf(sourceReferences);
    }
}

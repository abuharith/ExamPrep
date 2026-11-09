package com.examprep.api.study;

import java.util.List;

public record StudyOverview(
        String title,
        String introduction,
        String summary,
        List<String> learningObjectives,
        List<String> revisionFocus) {

    public StudyOverview {
        learningObjectives = learningObjectives == null ? List.of() : List.copyOf(learningObjectives);
        revisionFocus = revisionFocus == null ? List.of() : List.copyOf(revisionFocus);
    }
}

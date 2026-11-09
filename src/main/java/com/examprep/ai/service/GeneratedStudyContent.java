package com.examprep.ai.service;

import com.examprep.api.study.DefinitionDto;
import com.examprep.api.study.Difficulty;
import com.examprep.api.study.KeyFactDto;
import com.examprep.api.study.QuestionType;
import com.examprep.api.study.RevisionSection;
import com.examprep.api.study.StudyOverview;
import com.examprep.api.study.StudyTopic;
import java.util.List;

/** Validated AI output, ready to be persisted. Contains no fabricated citations. */
public record GeneratedStudyContent(
        StudyOverview overview,
        List<StudyTopic> topics,
        List<RevisionSection> revisionNotes,
        List<DefinitionDto> definitions,
        List<KeyFactDto> keyFacts,
        List<GeneratedFlashcard> flashcards,
        List<GeneratedQuestion> practiceQuestions,
        List<GeneratedQuestion> mockQuestions) {

    public record GeneratedFlashcard(String front, String back, String sourceReference) {
    }

    public record GeneratedQuestion(QuestionType type, String prompt, List<String> options, String correctAnswer,
                                    String explanation, Difficulty difficulty, String sourceReference) {
    }
}

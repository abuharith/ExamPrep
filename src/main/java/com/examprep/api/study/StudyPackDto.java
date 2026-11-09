package com.examprep.api.study;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Everything a student can read from one generated study package. */
public record StudyPackDto(
        UUID id,
        UUID documentId,
        String documentFilename,
        String title,
        StudyOverview overview,
        List<StudyTopic> topics,
        List<RevisionSection> revisionNotes,
        List<DefinitionDto> definitions,
        List<KeyFactDto> keyFacts,
        List<FlashcardDto> flashcards,
        List<QuestionDto> practiceQuestions,
        int mockQuestionCount,
        int mockDurationMinutes,
        StudyPreferences preferences,
        StudyPackStatus status,
        Instant createdAt,
        Instant completedAt) {

    public StudyPackDto {
        topics = topics == null ? List.of() : List.copyOf(topics);
        revisionNotes = revisionNotes == null ? List.of() : List.copyOf(revisionNotes);
        definitions = definitions == null ? List.of() : List.copyOf(definitions);
        keyFacts = keyFacts == null ? List.of() : List.copyOf(keyFacts);
        flashcards = flashcards == null ? List.of() : List.copyOf(flashcards);
        practiceQuestions = practiceQuestions == null ? List.of() : List.copyOf(practiceQuestions);
    }
}

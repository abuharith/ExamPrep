package com.examprep.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Typed Gemini responses. Every shape is deserialised with Jackson and then validated;
 * these types never reach the browser directly.
 */
public final class AiDtos {

    private AiDtos() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OverviewAndTopics(
            String title,
            String introduction,
            String summary,
            List<String> learningObjectives,
            List<String> revisionFocus,
            List<Topic> topics) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Topic(String name, String explanation, List<String> keyConcepts,
                        List<String> sourceReferences) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RevisionNotes(List<RevisionNote> sections) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RevisionNote(String heading, String body, List<String> sourceReferences) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Glossary(List<Definition> definitions, List<KeyFact> keyFacts) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Definition(String term, String definition, String sourceReference) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KeyFact(String label, String detail, String sourceReference, Boolean formula) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FlashcardSet(List<Flashcard> flashcards) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Flashcard(String front, String back, String sourceReference) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QuestionSet(List<GeneratedQuestion> practice, List<GeneratedQuestion> mock) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GeneratedQuestion(String type, String prompt, List<String> options, String correctAnswer,
                                    String explanation, String difficulty, String sourceReference) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TutorReply(String answer, List<String> sourceReferences, Boolean insufficientInformation) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SectionDigest(String summary, List<String> keyPoints) {
    }
}

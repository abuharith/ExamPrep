package com.examprep.ai.service;

import com.examprep.ai.client.GeminiGateway;
import com.examprep.ai.client.GeminiPrompt;
import com.examprep.ai.dto.AiDtos;
import com.examprep.ai.prompt.StudyPrompts;
import com.examprep.ai.retrieval.ChunkRetriever;
import com.examprep.api.document.DocumentChunkDto;
import com.examprep.api.document.DocumentContext;
import com.examprep.api.study.DefinitionDto;
import com.examprep.api.study.Difficulty;
import com.examprep.api.study.KeyFactDto;
import com.examprep.api.study.QuestionType;
import com.examprep.api.study.RevisionSection;
import com.examprep.api.study.StudyOverview;
import com.examprep.api.study.StudyPreferences;
import com.examprep.api.study.StudyTopic;
import com.examprep.common.exception.AiServiceException;
import com.examprep.config.AppProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates structured Gemini generation. Large documents are digested section by
 * section first so that no single request carries the whole document. Every returned
 * citation is checked against the real chunk list and dropped when it is not valid.
 */
@Service
public class StudyContentGenerator {

    private static final Logger log = LoggerFactory.getLogger(StudyContentGenerator.class);
    private static final int MAX_TOPICS = 10;
    private static final int MAX_SECTIONS = 12;
    private static final int MAX_DEFINITIONS = 30;
    private static final int MAX_FACTS = 20;

    private final GeminiGateway gateway;
    private final ObjectMapper objectMapper;
    private final ChunkRetriever retriever;
    private final AppProperties.Generation settings;
    private final AppProperties.Ai ai;

    public StudyContentGenerator(GeminiGateway gateway, ObjectMapper objectMapper, ChunkRetriever retriever,
                                 AppProperties properties) {
        this.gateway = gateway;
        this.objectMapper = objectMapper;
        this.retriever = retriever;
        this.settings = properties.generation();
        this.ai = properties.ai();
    }

    public GeneratedStudyContent generate(DocumentContext document, StudyPreferences preferences,
                                           ProgressListener progress) {
        List<DocumentChunkDto> chunks = document.chunks();
        if (chunks.isEmpty()) {
            throw new AiServiceException("This document has no extracted text to generate from.");
        }
        Map<String, String> references = new LinkedHashMap<>();
        for (DocumentChunkDto chunk : chunks) {
            references.put(chunk.referenceId(), chunk.location());
        }

        progress.update(8, "Preparing the study material");
        String source = prepareSource(document, progress);

        int titleBudget = Math.max(2048, ai.maxOutputTokens());

        progress.update(16, "Analysing the overview and topics");
        AiDtos.OverviewAndTopics overviewAndTopics = callJson(
                StudyPrompts.SYSTEM, StudyPrompts.overviewTask(preferences, source),
                AiDtos.OverviewAndTopics.class, titleBudget);
        if (overviewAndTopics == null || isBlank(overviewAndTopics.summary())) {
            throw new AiServiceException("The AI service returned an incomplete overview. Please try again.");
        }

        progress.update(34, "Writing revision notes");
        AiDtos.RevisionNotes revisionNotes = callJson(
                StudyPrompts.SYSTEM, StudyPrompts.revisionTask(preferences, source),
                AiDtos.RevisionNotes.class, ai.maxOutputTokens());

        progress.update(52, "Extracting definitions, formulas and key facts");
        AiDtos.Glossary glossary = callJson(
                StudyPrompts.SYSTEM, StudyPrompts.glossaryTask(preferences, source),
                AiDtos.Glossary.class, titleBudget);

        progress.update(68, "Writing flashcards");
        AiDtos.FlashcardSet flashcardSet = callJson(
                StudyPrompts.SYSTEM, StudyPrompts.flashcardsTask(preferences, preferences.flashcardCount(), source),
                AiDtos.FlashcardSet.class, ai.maxOutputTokens());

        progress.update(82, "Writing practice questions and the mock examination");
        AiDtos.QuestionSet questionSet = callJson(
                StudyPrompts.SYSTEM,
                StudyPrompts.questionsTask(preferences, preferences.practiceQuestionCount(),
                        preferences.mockQuestionCount(), source),
                AiDtos.QuestionSet.class, Math.max(ai.maxOutputTokens(), 8192));

        StudyOverview overview = new StudyOverview(
                limit(clean(overviewAndTopics.title()), 200),
                limit(clean(overviewAndTopics.introduction()), 2000),
                limit(clean(overviewAndTopics.summary()), 6000),
                strings(overviewAndTopics.learningObjectives(), 8, 300),
                strings(overviewAndTopics.revisionFocus(), 8, 300));
        List<StudyTopic> topics = topics(overviewAndTopics.topics(), references);
        List<RevisionSection> notes = revisionSections(revisionNotes, references);
        List<DefinitionDto> definitions = definitions(glossary, references);
        List<KeyFactDto> keyFacts = keyFacts(glossary, references);
        List<GeneratedStudyContent.GeneratedFlashcard> flashcards = flashcards(flashcardSet, references);
        List<GeneratedStudyContent.GeneratedQuestion> practice = questions(
                questionSet == null ? null : questionSet.practice(), references, false);
        List<GeneratedStudyContent.GeneratedQuestion> mock = questions(
                questionSet == null ? null : questionSet.mock(), references, true);

        if (topics.isEmpty()) {
            throw new AiServiceException(
                    "The AI response did not contain usable topics. Please generate the package again.");
        }
        if (notes.isEmpty()) {
            throw new AiServiceException(
                    "The AI response did not contain usable revision notes. Please generate the package again.");
        }
        if (flashcards.isEmpty()) {
            throw new AiServiceException(
                    "The AI response did not contain usable flashcards. Please generate the package again.");
        }
        if (practice.isEmpty() || mock.isEmpty()) {
            throw new AiServiceException(
                    "The AI response did not contain usable questions. Please generate the package again.");
        }
        progress.update(92, "Saving the study package");
        log.info("Generated study content for {}: {} topics, {} notes, {} cards, {} practice, {} mock",
                document.documentId(), topics.size(), notes.size(), flashcards.size(), practice.size(), mock.size());
        return new GeneratedStudyContent(overview, topics, notes, definitions, keyFacts, flashcards, practice, mock);
    }

    /**
     * Builds the source block. Documents that fit the budget are sent with their citation
     * ids; larger ones are summarised in groups and the digests are combined.
     */
    private String prepareSource(DocumentContext document, ProgressListener progress) {
        List<DocumentChunkDto> chunks = document.chunks();
        int totalCharacters = chunks.stream().mapToInt(chunk -> chunk.text().length()).sum();
        if (totalCharacters <= settings.summariseThresholdChars()) {
            List<DocumentChunkDto> selected = retriever.sample(chunks, settings.maxSourceChars());
            return renderChunks(selected);
        }

        int budgetPerGroup = Math.max(6000, Math.min(18000, settings.maxSourceChars() / 4));
        List<List<DocumentChunkDto>> groups = new ArrayList<>();
        List<DocumentChunkDto> current = new ArrayList<>();
        int currentSize = 0;
        for (DocumentChunkDto chunk : chunks) {
            if (currentSize + chunk.text().length() > budgetPerGroup && !current.isEmpty()) {
                groups.add(current);
                current = new ArrayList<>();
                currentSize = 0;
            }
            current.add(chunk);
            currentSize += chunk.text().length();
        }
        if (!current.isEmpty()) {
            groups.add(current);
        }
        if (groups.size() > 8) {
            // Keep the first and last groups and sample the middle evenly.
            List<List<DocumentChunkDto>> sampled = new ArrayList<>();
            int step = (int) Math.ceil(groups.size() / 8.0);
            for (int i = 0; i < groups.size(); i += step) {
                sampled.add(groups.get(i));
            }
            groups = sampled;
        }

        StringBuilder digests = new StringBuilder();
        for (int i = 0; i < groups.size(); i++) {
            List<DocumentChunkDto> group = groups.get(i);
            progress.update(9 + (int) (6.0 * i / Math.max(1, groups.size())),
                    "Summarising section " + (i + 1) + " of " + groups.size());
            String groupSource = renderChunks(group);
            try {
                AiDtos.SectionDigest digest = callJson(StudyPrompts.SYSTEM,
                        StudyPrompts.digestTask(groupSource, i + 1, groups.size()),
                        AiDtos.SectionDigest.class, 2048);
                if (digest != null && !isBlank(digest.summary())) {
                    digests.append("SECTION ").append(i + 1).append(" SUMMARY: ")
                            .append(limit(clean(digest.summary()), 3000)).append('\n');
                    for (String point : strings(digest.keyPoints(), 12, 400)) {
                        digests.append("- ").append(point).append('\n');
                    }
                    digests.append("Sources: ").append(String.join(", ",
                            group.stream().map(DocumentChunkDto::referenceId).toList())).append("\n\n");
                }
            } catch (AiServiceException ex) {
                log.warn("Section digest {} of {} failed, falling back to raw text", i + 1, groups.size());
                digests.append(renderChunks(group)).append("\n\n");
            }
        }
        String combined = digests.toString();
        if (combined.length() > settings.maxSourceChars()) {
            combined = combined.substring(0, settings.maxSourceChars());
        }
        return combined;
    }

    private static String renderChunks(List<DocumentChunkDto> chunks) {
        StringBuilder source = new StringBuilder();
        for (DocumentChunkDto chunk : chunks) {
            source.append('[').append(chunk.referenceId()).append(" | ").append(chunk.location()).append("]\n")
                    .append(chunk.text()).append("\n\n");
        }
        return source.toString();
    }

    private <T> T callJson(String systemInstruction, String userPrompt, Class<T> type, int maxTokens) {
        GeminiPrompt prompt = new GeminiPrompt(systemInstruction, userPrompt, maxTokens);
        AiServiceException lastFailure = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            String raw = gateway.generateJson(prompt);
            try {
                return objectMapper.readValue(stripFence(raw), type);
            } catch (JsonProcessingException ex) {
                lastFailure = new AiServiceException(
                        "The AI service returned a response that could not be read. Please try again.", ex);
                log.warn("Gemini returned unparseable JSON (attempt {})", attempt);
            }
        }
        throw lastFailure;
    }

    private static String stripFence(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.startsWith("```")) {
            int firstNewline = value.indexOf('\n');
            if (firstNewline > 0) {
                value = value.substring(firstNewline + 1);
            }
            if (value.endsWith("```")) {
                value = value.substring(0, value.length() - 3);
            }
        }
        return value.trim();
    }

    private List<StudyTopic> topics(List<AiDtos.Topic> generated, Map<String, String> references) {
        List<StudyTopic> topics = new ArrayList<>();
        if (generated == null) {
            return topics;
        }
        for (AiDtos.Topic topic : generated) {
            if (topic == null || isBlank(topic.name()) || isBlank(topic.explanation())) {
                continue;
            }
            topics.add(new StudyTopic(limit(clean(topic.name()), 200), limit(clean(topic.explanation()), 6000),
                    strings(topic.keyConcepts(), 10, 200), labels(topic.sourceReferences(), references)));
            if (topics.size() >= MAX_TOPICS) {
                break;
            }
        }
        return topics;
    }

    private List<RevisionSection> revisionSections(AiDtos.RevisionNotes generated, Map<String, String> references) {
        List<RevisionSection> sections = new ArrayList<>();
        if (generated == null || generated.sections() == null) {
            return sections;
        }
        for (AiDtos.RevisionNote section : generated.sections()) {
            if (section == null || isBlank(section.body())) {
                continue;
            }
            String heading = isBlank(section.heading()) ? "Revision notes" : clean(section.heading());
            sections.add(new RevisionSection(limit(heading, 200), limit(clean(section.body()), 12000),
                    labels(section.sourceReferences(), references)));
            if (sections.size() >= MAX_SECTIONS) {
                break;
            }
        }
        return sections;
    }

    private List<DefinitionDto> definitions(AiDtos.Glossary glossary, Map<String, String> references) {
        List<DefinitionDto> definitions = new ArrayList<>();
        if (glossary == null || glossary.definitions() == null) {
            return definitions;
        }
        for (AiDtos.Definition definition : glossary.definitions()) {
            if (definition == null || isBlank(definition.term()) || isBlank(definition.definition())) {
                continue;
            }
            definitions.add(new DefinitionDto(limit(clean(definition.term()), 160),
                    limit(clean(definition.definition()), 2000), label(definition.sourceReference(), references)));
            if (definitions.size() >= MAX_DEFINITIONS) {
                break;
            }
        }
        return definitions;
    }

    private List<KeyFactDto> keyFacts(AiDtos.Glossary glossary, Map<String, String> references) {
        List<KeyFactDto> facts = new ArrayList<>();
        if (glossary == null || glossary.keyFacts() == null) {
            return facts;
        }
        for (AiDtos.KeyFact fact : glossary.keyFacts()) {
            if (fact == null || isBlank(fact.label()) || isBlank(fact.detail())) {
                continue;
            }
            facts.add(new KeyFactDto(limit(clean(fact.label()), 200), limit(clean(fact.detail()), 2000),
                    label(fact.sourceReference(), references), Boolean.TRUE.equals(fact.formula())));
            if (facts.size() >= MAX_FACTS) {
                break;
            }
        }
        return facts;
    }

    private List<GeneratedStudyContent.GeneratedFlashcard> flashcards(AiDtos.FlashcardSet generated,
                                                                     Map<String, String> references) {
        List<GeneratedStudyContent.GeneratedFlashcard> cards = new ArrayList<>();
        if (generated == null || generated.flashcards() == null) {
            return cards;
        }
        for (AiDtos.Flashcard card : generated.flashcards()) {
            if (card == null || isBlank(card.front()) || isBlank(card.back())) {
                continue;
            }
            cards.add(new GeneratedStudyContent.GeneratedFlashcard(limit(clean(card.front()), 600),
                    limit(clean(card.back()), 2000), label(card.sourceReference(), references)));
            if (cards.size() >= 40) {
                break;
            }
        }
        return cards;
    }

    private List<GeneratedStudyContent.GeneratedQuestion> questions(List<AiDtos.GeneratedQuestion> generated,
                                                                    Map<String, String> references,
                                                                    boolean mockExam) {
        List<GeneratedStudyContent.GeneratedQuestion> questions = new ArrayList<>();
        if (generated == null) {
            return questions;
        }
        for (AiDtos.GeneratedQuestion question : generated) {
            if (question == null || isBlank(question.prompt())) {
                continue;
            }
            QuestionType type = parseType(question.type());
            if (mockExam && type == QuestionType.SHORT_ANSWER) {
                continue; // mock examinations are scored automatically
            }
            List<String> options = question.options() == null ? List.of()
                    : question.options().stream().filter(option -> option != null && !option.isBlank())
                    .map(option -> limit(clean(option), 400)).toList();
            String correctAnswer;
            if (type == QuestionType.TRUE_FALSE) {
                String parsed = normaliseBoolean(question.correctAnswer());
                if (parsed == null) {
                    continue;
                }
                correctAnswer = parsed;
                options = List.of("True", "False");
            } else if (type == QuestionType.MULTIPLE_CHOICE) {
                if (options.size() < 2) {
                    continue;
                }
                correctAnswer = matchOption(question.correctAnswer(), options);
                if (correctAnswer == null) {
                    continue;
                }
            } else {
                if (isBlank(question.correctAnswer())) {
                    continue;
                }
                correctAnswer = limit(clean(question.correctAnswer()), 2000);
                options = List.of();
            }
            questions.add(new GeneratedStudyContent.GeneratedQuestion(type,
                    limit(clean(question.prompt()), 2000), options, correctAnswer,
                    isBlank(question.explanation()) ? "See the referenced section of the document."
                            : limit(clean(question.explanation()), 3000),
                    parseDifficulty(question.difficulty()),
                    label(question.sourceReference(), references)));
            if (questions.size() >= 30) {
                break;
            }
        }
        return questions;
    }

    private static QuestionType parseType(String raw) {
        if (raw == null) {
            return QuestionType.MULTIPLE_CHOICE;
        }
        String value = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        return switch (value) {
            case "TRUE_FALSE", "TRUEFALSE", "BOOLEAN", "TRUE/FALSE" -> QuestionType.TRUE_FALSE;
            case "SHORT_ANSWER", "SHORTANSWER", "OPEN", "FREE_TEXT" -> QuestionType.SHORT_ANSWER;
            default -> QuestionType.MULTIPLE_CHOICE;
        };
    }

    private static Difficulty parseDifficulty(String raw) {
        if (raw == null) {
            return Difficulty.MEDIUM;
        }
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "EASY", "LOW", "SIMPLE" -> Difficulty.EASY;
            case "HARD", "HIGH", "DIFFICULT" -> Difficulty.HARD;
            default -> Difficulty.MEDIUM;
        };
    }

    private static String normaliseBoolean(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "true", "t", "yes", "y", "correct" -> "True";
            case "false", "f", "no", "n", "incorrect" -> "False";
            default -> null;
        };
    }

    private static String matchOption(String answer, List<String> options) {
        if (answer == null) {
            return null;
        }
        String trimmed = answer.trim();
        for (String option : options) {
            if (option.trim().equalsIgnoreCase(trimmed)) {
                return option;
            }
        }
        // Tolerate answers given as a letter (A-D) or one-based index.
        if (trimmed.length() == 1) {
            int index = Character.toUpperCase(trimmed.charAt(0)) - 'A';
            if (index >= 0 && index < options.size()) {
                return options.get(index);
            }
        }
        try {
            int index = Integer.parseInt(trimmed) - 1;
            if (index >= 0 && index < options.size()) {
                return options.get(index);
            }
        } catch (NumberFormatException ignored) {
            // fall through to the loose contains check
        }
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).contains(trimmed.toLowerCase(Locale.ROOT))) {
                return option;
            }
        }
        return null;
    }

    private static String label(String referenceId, Map<String, String> references) {
        if (referenceId == null) {
            return null;
        }
        return references.get(referenceId.trim());
    }

    private static List<String> labels(List<String> referenceIds, Map<String, String> references) {
        if (referenceIds == null) {
            return List.of();
        }
        Set<String> labels = new LinkedHashSet<>();
        for (String referenceId : referenceIds) {
            String label = label(referenceId, references);
            if (label != null) {
                labels.add(label);
            }
        }
        return List.copyOf(labels);
    }

    private static List<String> strings(List<String> values, int maxItems, int maxLength) {
        if (values == null) {
            return List.of();
        }
        List<String> cleaned = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            cleaned.add(limit(clean(value), maxLength));
            if (cleaned.size() >= maxItems) {
                break;
            }
        }
        return cleaned;
    }

    static String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\r\\n?", "\n").trim();
    }

    private static String limit(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "\u2026";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

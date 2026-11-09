package com.examprep.study.service;

import com.examprep.api.document.DocumentApplicationService;
import com.examprep.api.document.DocumentContext;
import com.examprep.api.document.DocumentStatusDto;
import com.examprep.api.document.ProcessingStatus;
import com.examprep.api.study.DefinitionDto;
import com.examprep.api.study.FlashcardDto;
import com.examprep.api.study.KeyFactDto;
import com.examprep.api.study.QuestionDto;
import com.examprep.api.study.QuizAttemptSummaryDto;
import com.examprep.api.study.QuizQuestionReviewDto;
import com.examprep.api.study.QuizQuestionReviewDto;
import com.examprep.api.study.QuizResultDto;
import com.examprep.api.study.QuizSubmissionDto;
import com.examprep.api.study.RevisionSection;
import com.examprep.api.study.StudyOverview;
import com.examprep.api.study.StudyPackApplicationService;
import com.examprep.api.study.StudyPackCreationResult;
import com.examprep.api.study.StudyPackDto;
import com.examprep.api.study.StudyPackStatus;
import com.examprep.api.study.StudyPackStatusDto;
import com.examprep.api.study.StudyPackSummaryDto;
import com.examprep.api.study.StudyPreferences;
import com.examprep.api.study.StudyTopic;
import com.examprep.api.study.TutorAnswerDto;
import com.examprep.api.study.TutorMessageDto;
import com.examprep.api.study.TutorQuestionDto;
import com.examprep.ai.service.TutorGenerationService;
import com.examprep.common.exception.InvalidRequestException;
import com.examprep.common.exception.ResourceNotFoundException;
import com.examprep.config.AppProperties;
import com.examprep.study.domain.Flashcard;
import com.examprep.study.domain.FlashcardReview;
import com.examprep.study.domain.Question;
import com.examprep.study.domain.QuizAnswer;
import com.examprep.study.domain.QuizAttempt;
import com.examprep.study.domain.StudyPack;
import com.examprep.study.domain.TutorMessage;
import com.examprep.study.quiz.QuizGrader;
import com.examprep.study.repository.FlashcardRepository;
import com.examprep.study.repository.FlashcardReviewRepository;
import com.examprep.study.repository.QuestionRepository;
import com.examprep.study.repository.QuizAnswerRepository;
import com.examprep.study.repository.QuizAttemptRepository;
import com.examprep.study.repository.StudyPackRepository;
import com.examprep.study.repository.TutorMessageRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class StudyPackServiceImpl implements StudyPackApplicationService {

    private static final Logger log = LoggerFactory.getLogger(StudyPackServiceImpl.class);
    private static final TypeReference<List<StudyTopic>> TOPIC_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<RevisionSection>> SECTION_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<DefinitionDto>> DEFINITION_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<KeyFactDto>> FACT_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final StudyPackRepository studyPacks;
    private final FlashcardRepository flashcards;
    private final QuestionRepository questions;
    private final QuizAttemptRepository attempts;
    private final QuizAnswerRepository answers;
    private final TutorMessageRepository tutorMessages;
    private final FlashcardReviewRepository flashcardReviews;
    private final DocumentApplicationService documents;
    private final StudyGenerationService generation;
    private final TutorGenerationService tutor;
    private final JsonCodec json;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final AppProperties properties;

    public StudyPackServiceImpl(StudyPackRepository studyPacks, FlashcardRepository flashcards,
                                QuestionRepository questions, QuizAttemptRepository attempts,
                                QuizAnswerRepository answers, TutorMessageRepository tutorMessages,
                                FlashcardReviewRepository flashcardReviews, DocumentApplicationService documents,
                                StudyGenerationService generation, TutorGenerationService tutor, JsonCodec json,
                                TransactionTemplate transactions, Clock clock, AppProperties properties) {
        this.studyPacks = studyPacks;
        this.flashcards = flashcards;
        this.questions = questions;
        this.attempts = attempts;
        this.answers = answers;
        this.tutorMessages = tutorMessages;
        this.flashcardReviews = flashcardReviews;
        this.documents = documents;
        this.generation = generation;
        this.tutor = tutor;
        this.json = json;
        this.transactions = transactions;
        this.clock = clock;
        this.properties = properties;
    }

    @Override
    public StudyPackCreationResult create(UUID documentId, UUID ownerId, StudyPreferences requested) {
        DocumentStatusDto document = documents.getStatus(documentId, ownerId);
        if (document.status() == ProcessingStatus.FAILED) {
            throw new InvalidRequestException("This document failed text extraction: "
                    + (document.failureReason() == null ? "upload it again." : document.failureReason()));
        }
        if (document.status() != ProcessingStatus.READY) {
            throw new InvalidRequestException("Wait for text extraction to finish before generating a study package.");
        }
        StudyPreferences preferences = normalise(requested);
        UUID packId = UUID.randomUUID();
        Instant now = clock.instant();
        String title = truncate(document.filename(), 300);
        transactions.executeWithoutResult(status -> studyPacks.save(new StudyPack(packId, ownerId, documentId,
                title, document.filename(), preferences.language(), preferences.academicLevel(),
                preferences.examDate(), preferences.mockDurationMinutes(), now)));
        try {
            generation.generateAsync(packId);
        } catch (RejectedExecutionException ex) {
            generation.fail(packId, "The server is busy. Generate the study package again in a moment.");
            log.warn("Generation queue rejected study package {}", packId);
        }
        return new StudyPackCreationResult(packId, StudyPackStatus.QUEUED);
    }

    @Override
    public StudyPackStatusDto getStatus(UUID studyPackId, UUID ownerId) {
        StudyPack pack = requireOwned(studyPackId, ownerId);
        return new StudyPackStatusDto(pack.getId(), pack.getDocumentId(), pack.getStatus(),
                pack.getProgressPercent(), pack.getProgressMessage(), pack.getFailureReason(),
                pack.getUpdatedAt());
    }

    @Override
    public StudyPackDto getStudyPack(UUID studyPackId, UUID ownerId) {
        StudyPack pack = requireReady(studyPackId, ownerId);
        List<FlashcardDto> cards = flashcards.findByStudyPackIdOrderByPositionAsc(studyPackId).stream()
                .map(card -> new FlashcardDto(card.getId(), card.getFront(), card.getBack(), card.getSourceReference()))
                .toList();
        List<Question> practice = questions.findByStudyPackIdAndMockExamOrderByPositionAsc(studyPackId, false);
        int mockCount = questions.findByStudyPackIdAndMockExamOrderByPositionAsc(studyPackId, true).size();
        StudyOverview overview = json.read(pack.getOverview(), StudyOverview.class);
        if (overview == null) {
            overview = new StudyOverview(pack.getTitle(), null, pack.getSummary(), List.of(), List.of());
        }
        StudyPreferences preferences = new StudyPreferences(pack.getLanguage(), pack.getAcademicLevel(),
                pack.getExamDate(), practice.size(), mockCount, cards.size(), pack.getMockDurationMinutes());
        return new StudyPackDto(pack.getId(), pack.getDocumentId(), pack.getDocumentFilename(), pack.getTitle(),
                overview,
                json.readList(pack.getTopics(), TOPIC_LIST),
                json.readList(pack.getRevisionNotes(), SECTION_LIST),
                json.readList(pack.getDefinitions(), DEFINITION_LIST),
                json.readList(pack.getKeyFacts(), FACT_LIST),
                cards,
                practice.stream().map(StudyPackServiceImpl::toQuestionDto).toList(),
                mockCount,
                pack.getMockDurationMinutes(),
                preferences,
                pack.getStatus(),
                pack.getCreatedAt(),
                pack.getCompletedAt());
    }

    @Override
    public QuizResultDto submitQuiz(UUID studyPackId, UUID ownerId, QuizSubmissionDto submission) {
        StudyPack pack = requireReady(studyPackId, ownerId);
        Map<UUID, String> submitted = sanitiseAnswers(submission);
        List<Question> packQuestions = questions.findByStudyPackIdOrderByMockExamAscPositionAsc(studyPackId);
        Map<UUID, Question> byId = new LinkedHashMap<>();
        for (Question question : packQuestions) {
            byId.put(question.getId(), question);
        }
        List<Question> gradedQuestions = new ArrayList<>();
        for (UUID questionId : submitted.keySet()) {
            Question question = byId.get(questionId);
            if (question != null) {
                gradedQuestions.add(question);
            }
        }
        if (gradedQuestions.isEmpty()) {
            throw new InvalidRequestException("Answer at least one question before submitting.");
        }
        boolean mock = gradedQuestions.stream().allMatch(Question::isMockExam);
        QuizGrader.Grade grade = QuizGrader.grade(gradedQuestions, submitted);
        int duration = Math.max(0, Math.min(submission.durationSeconds(), 6 * 60 * 60));
        Instant now = clock.instant();
        UUID attemptId = UUID.randomUUID();
        String mode = mock ? "MOCK" : "PRACTICE";
        transactions.executeWithoutResult(status -> {
            attempts.save(new QuizAttempt(attemptId, ownerId, studyPackId, mode, grade.score(),
                    grade.totalQuestions(), duration, now));
            List<QuizAnswer> entities = grade.outcomes().stream()
                    .map(outcome -> new QuizAnswer(UUID.randomUUID(), attemptId, outcome.question().getId(),
                            truncate(outcome.submittedAnswer(), 4000), outcome.correct(),
                            outcome.correct() ? 1 : 0))
                    .toList();
            answers.saveAll(entities);
        });

        int percentage = grade.totalQuestions() == 0 ? 0
                : (int) Math.round(grade.score() * 100.0 / grade.totalQuestions());
        List<QuizQuestionReviewDto> reviews = grade.outcomes().stream()
                .map(outcome -> new QuizQuestionReviewDto(outcome.question().getId(),
                        outcome.question().getQuestionType(), outcome.question().getPrompt(),
                        QuizGrader.optionList(outcome.question()), outcome.submittedAnswer(),
                        outcome.question().getCorrectAnswer(), outcome.question().getExplanation(),
                        outcome.correct(), outcome.question().getDifficulty(),
                        outcome.question().getSourceReference()))
                .toList();
        List<String> suggestions = revisionSuggestions(pack, grade);
        return new QuizResultDto(attemptId, mode, grade.score(), grade.totalQuestions(), percentage, duration,
                now, reviews, suggestions);
    }

    private static Map<UUID, String> sanitiseAnswers(QuizSubmissionDto submission) {
        Map<UUID, String> answers = new LinkedHashMap<>();
        if (submission == null || submission.answers() == null) {
            return answers;
        }
        int count = 0;
        for (Map.Entry<UUID, String> entry : submission.answers().entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isBlank()) {
                continue;
            }
            answers.put(entry.getKey(), truncate(entry.getValue().trim(), 4000));
            if (++count >= 200) {
                break;
            }
        }
        return answers;
    }

    private List<String> revisionSuggestions(StudyPack pack, QuizGrader.Grade grade) {
        Set<String> incorrectReferences = new LinkedHashSet<>();
        for (QuizGrader.Outcome outcome : grade.outcomes()) {
            if (!outcome.correct() && outcome.question().getSourceReference() != null) {
                incorrectReferences.add(outcome.question().getSourceReference());
            }
        }
        return suggestionsFor(pack, incorrectReferences);
    }

    private List<String> suggestionsFor(StudyPack pack, Set<String> references) {
        if (references.isEmpty()) {
            return List.of();
        }
        List<String> suggestions = new ArrayList<>();
        for (StudyTopic topic : json.readList(pack.getTopics(), TOPIC_LIST)) {
            if (topic.sourceReferences() != null
                    && topic.sourceReferences().stream().anyMatch(references::contains)) {
                suggestions.add(topic.name());
            }
            if (suggestions.size() >= 5) {
                break;
            }
        }
        return List.copyOf(suggestions);
    }

    @Override
    public TutorAnswerDto askTutor(UUID studyPackId, UUID ownerId, TutorQuestionDto question) {
        StudyPack pack = requireReady(studyPackId, ownerId);
        String text = question == null || question.question() == null ? "" : question.question().trim();
        if (text.isEmpty()) {
            throw new InvalidRequestException("Type a question to ask the tutor.");
        }
        List<TutorMessageDto> history = getTutorConversation(studyPackId, ownerId);
        Instant now = clock.instant();
        transactions.executeWithoutResult(status -> tutorMessages.save(new TutorMessage(UUID.randomUUID(), ownerId,
                studyPackId, "STUDENT", text, json.write(List.of()), now)));
        DocumentContext context = documents.getContext(pack.getDocumentId(), ownerId);
        TutorAnswerDto answer = tutor.answer(context, history, text, clock.instant());
        transactions.executeWithoutResult(status -> tutorMessages.save(new TutorMessage(UUID.randomUUID(), ownerId,
                studyPackId, "ASSISTANT", answer.answer(), json.write(answer.sourceReferences()),
                answer.answeredAt())));
        return answer;
    }

    @Override
    public void delete(UUID studyPackId, UUID ownerId) {
        requireOwned(studyPackId, ownerId);
        transactions.executeWithoutResult(status -> studyPacks.deleteById(studyPackId));
    }

    @Override
    public List<StudyPackSummaryDto> listStudyPacks(UUID ownerId) {
        return studyPacks.findByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .map(this::toSummary)
                .toList();
    }

    private StudyPackSummaryDto toSummary(StudyPack pack) {
        return new StudyPackSummaryDto(pack.getId(), pack.getDocumentId(), pack.getDocumentFilename(),
                pack.getTitle(), pack.getStatus(), pack.getProgressPercent(),
                (int) flashcards.countByStudyPackId(pack.getId()),
                questions.findByStudyPackIdAndMockExamOrderByPositionAsc(pack.getId(), false).size(),
                pack.getCreatedAt(), pack.getCompletedAt());
    }

    @Override
    public List<QuestionDto> getExamQuestions(UUID studyPackId, UUID ownerId) {
        requireReady(studyPackId, ownerId);
        return questions.findByStudyPackIdAndMockExamOrderByPositionAsc(studyPackId, true).stream()
                .map(StudyPackServiceImpl::toQuestionDto)
                .toList();
    }

    @Override
    public List<QuizAttemptSummaryDto> listAttempts(UUID studyPackId, UUID ownerId) {
        requireOwned(studyPackId, ownerId);
        return attempts.findByStudyPackIdAndOwnerIdOrderBySubmittedAtDesc(studyPackId, ownerId).stream()
                .map(StudyPackServiceImpl::toAttemptSummary)
                .toList();
    }

    @Override
    public List<QuizAttemptSummaryDto> listRecentAttempts(UUID ownerId, int limit) {
        int bounded = Math.max(1, Math.min(limit, 50));
        return attempts.findByOwnerIdOrderBySubmittedAtDesc(ownerId, PageRequest.of(0, bounded)).stream()
                .map(StudyPackServiceImpl::toAttemptSummary)
                .toList();
    }

    private static QuizAttemptSummaryDto toAttemptSummary(QuizAttempt attempt) {
        int percentage = attempt.getTotalQuestions() == 0 ? 0
                : (int) Math.round(attempt.getScore() * 100.0 / attempt.getTotalQuestions());
        return new QuizAttemptSummaryDto(attempt.getId(), attempt.getStudyPackId(), attempt.getMode(),
                attempt.getScore(), attempt.getTotalQuestions(), percentage, attempt.getDurationSeconds(),
                attempt.getSubmittedAt());
    }

    @Override
    public List<TutorMessageDto> getTutorConversation(UUID studyPackId, UUID ownerId) {
        requireOwned(studyPackId, ownerId);
        return tutorMessages.findByStudyPackIdAndOwnerIdOrderByCreatedAtAsc(studyPackId, ownerId).stream()
                .map(message -> new TutorMessageDto(message.getId(), message.getRole(), message.getContent(),
                        json.readStrings(message.getSourceReferences()), message.getCreatedAt()))
                .toList();
    }

    @Override
    public void recordFlashcardReview(UUID studyPackId, UUID ownerId, int cardsReviewed) {
        requireOwned(studyPackId, ownerId);
        int count = Math.max(1, Math.min(cardsReviewed, 200));
        transactions.executeWithoutResult(status -> flashcardReviews.save(new FlashcardReview(UUID.randomUUID(),
                ownerId, studyPackId, count, clock.instant())));
    }

    @Override
    public QuizResultDto getAttemptResult(UUID studyPackId, UUID ownerId, UUID attemptId) {
        StudyPack pack = requireOwned(studyPackId, ownerId);
        QuizAttempt attempt = attempts.findById(attemptId)
                .filter(candidate -> ownerId.equals(candidate.getOwnerId())
                        && studyPackId.equals(candidate.getStudyPackId()))
                .orElseThrow(() -> new ResourceNotFoundException("Examination attempt not found."));
        Map<UUID, Question> byId = new LinkedHashMap<>();
        for (Question question : questions.findByStudyPackIdOrderByMockExamAscPositionAsc(studyPackId)) {
            byId.put(question.getId(), question);
        }
        List<QuizQuestionReviewDto> reviews = new ArrayList<>();
        Set<String> incorrectReferences = new LinkedHashSet<>();
        for (QuizAnswer answer : answers.findByAttemptId(attemptId)) {
            Question question = byId.get(answer.getQuestionId());
            if (question == null) {
                continue;
            }
            reviews.add(new QuizQuestionReviewDto(question.getId(), question.getQuestionType(),
                    question.getPrompt(), QuizGrader.optionList(question), answer.getSubmittedAnswer(),
                    question.getCorrectAnswer(), question.getExplanation(), answer.isCorrect(),
                    question.getDifficulty(), question.getSourceReference()));
            if (!answer.isCorrect() && question.getSourceReference() != null) {
                incorrectReferences.add(question.getSourceReference());
            }
        }
        int percentage = attempt.getTotalQuestions() == 0 ? 0
                : (int) Math.round(attempt.getScore() * 100.0 / attempt.getTotalQuestions());
        return new QuizResultDto(attempt.getId(), attempt.getMode(), attempt.getScore(),
                attempt.getTotalQuestions(), percentage, attempt.getDurationSeconds(), attempt.getSubmittedAt(),
                reviews, suggestionsFor(pack, incorrectReferences));
    }

    @Override
    public QuizQuestionReviewDto revealPracticeQuestion(UUID studyPackId, UUID ownerId, UUID questionId) {
        requireReady(studyPackId, ownerId);
        Question question = questions.findAllById(List.of(questionId)).stream()
                .filter(candidate -> studyPackId.equals(candidate.getStudyPackId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Question not found."));
        if (question.isMockExam()) {
            throw new InvalidRequestException(
                    "Examination answers are only available after you submit the examination.");
        }
        return new QuizQuestionReviewDto(question.getId(), question.getQuestionType(), question.getPrompt(),
                QuizGrader.optionList(question), null, question.getCorrectAnswer(), question.getExplanation(),
                false, question.getDifficulty(), question.getSourceReference());
    }

    @Override
    public long flashcardReviewCount(UUID ownerId) {
        return flashcardReviews.countByOwnerId(ownerId);
    }

    @Override
    public long attemptCount(UUID ownerId) {
        return attempts.countByOwnerId(ownerId);
    }

    @Override
    public boolean aiConfigured() {
        return properties.ai().configured();
    }

    private StudyPack requireOwned(UUID studyPackId, UUID ownerId) {
        if (studyPackId == null || ownerId == null) {
            throw new ResourceNotFoundException("Study package not found.");
        }
        return studyPacks.findByIdAndOwnerId(studyPackId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Study package not found."));
    }

    private StudyPack requireReady(UUID studyPackId, UUID ownerId) {
        StudyPack pack = requireOwned(studyPackId, ownerId);
        if (pack.getStatus() == StudyPackStatus.FAILED) {
            throw new InvalidRequestException("This study package failed: "
                    + (pack.getFailureReason() == null ? "generate it again." : pack.getFailureReason()));
        }
        if (pack.getStatus() != StudyPackStatus.READY) {
            throw new InvalidRequestException("This study package is still being generated.");
        }
        return pack;
    }

    private static QuestionDto toQuestionDto(Question question) {
        return new QuestionDto(question.getId(), question.getQuestionType(), question.getPrompt(),
                QuizGrader.optionList(question), question.getDifficulty(), question.getSourceReference());
    }

    private StudyPreferences normalise(StudyPreferences requested) {
        StudyPreferences base = requested == null ? StudyPreferences.defaults() : requested;
        String language = base.language() == null || base.language().isBlank() ? "English"
                : truncate(base.language().trim(), 32);
        String level = base.academicLevel() == null || base.academicLevel().isBlank() ? "Undergraduate"
                : truncate(base.academicLevel().trim(), 40);
        return new StudyPreferences(language, level, base.examDate(),
                clamp(base.practiceQuestionCount(), 4, 20, 8),
                clamp(base.mockQuestionCount(), 5, 25, 10),
                clamp(base.flashcardCount(), 4, 30, 12),
                clamp(base.mockDurationMinutes(), 5, 180, 30));
    }

    private static int clamp(int value, int min, int max, int fallback) {
        if (value <= 0) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}

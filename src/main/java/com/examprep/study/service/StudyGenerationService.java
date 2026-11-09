package com.examprep.study.service;

import com.examprep.ai.service.GeneratedStudyContent;
import com.examprep.ai.service.ProgressListener;
import com.examprep.ai.service.StudyContentGenerator;
import com.examprep.api.document.DocumentContext;
import com.examprep.api.document.DocumentApplicationService;
import com.examprep.api.study.Difficulty;
import com.examprep.api.study.QuestionType;
import com.examprep.api.study.StudyPreferences;
import com.examprep.api.study.StudyPackStatus;
import com.examprep.api.study.StudyTopic;
import com.examprep.common.exception.AiNotConfiguredException;
import com.examprep.common.exception.AiServiceException;
import com.examprep.common.exception.InvalidRequestException;
import com.examprep.common.exception.ResourceNotFoundException;
import com.examprep.config.AsyncConfig;
import com.examprep.study.domain.Flashcard;
import com.examprep.study.domain.Question;
import com.examprep.study.domain.StudyPack;
import com.examprep.study.repository.FlashcardRepository;
import com.examprep.study.repository.QuestionRepository;
import com.examprep.study.repository.StudyPackRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Generates study packages off the request thread and records progress in PostgreSQL.
 * Failures are stored with a student-safe reason; no placeholder content is ever saved.
 */
@Service
public class StudyGenerationService {

    private static final Logger log = LoggerFactory.getLogger(StudyGenerationService.class);

    private final StudyPackRepository studyPacks;
    private final FlashcardRepository flashcards;
    private final QuestionRepository questions;
    private final StudyContentGenerator generator;
    private final DocumentApplicationService documents;
    private final JsonCodec json;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public StudyGenerationService(StudyPackRepository studyPacks, FlashcardRepository flashcards,
                                  QuestionRepository questions, StudyContentGenerator generator,
                                  DocumentApplicationService documents, JsonCodec json,
                                  TransactionTemplate transactions, Clock clock) {
        this.studyPacks = studyPacks;
        this.flashcards = flashcards;
        this.questions = questions;
        this.generator = generator;
        this.documents = documents;
        this.json = json;
        this.transactions = transactions;
        this.clock = clock;
    }

    /** Invoked from {@link StudyPackServiceImpl} so that Spring's @Async proxy applies. */
    @Async(AsyncConfig.PROCESSING_EXECUTOR)
    public void generateAsync(UUID studyPackId) {
        generate(studyPackId);
    }

    public void generate(UUID studyPackId) {
        StudyPack pack = transactions.execute(status -> studyPacks.findById(studyPackId).orElse(null));
        if (pack == null || pack.getStatus() == StudyPackStatus.READY) {
            return;
        }
        updateProgress(studyPackId, 3, "Starting generation");
        try {
            DocumentContext document = documents.getContext(pack.getDocumentId(), pack.getOwnerId());
            StudyPreferences preferences = new StudyPreferences(pack.getLanguage(), pack.getAcademicLevel(),
                    pack.getExamDate(), 8, 10, 12, pack.getMockDurationMinutes());
            ProgressListener listener = (percent, message) -> updateProgress(studyPackId, percent, message);
            GeneratedStudyContent content = generator.generate(document, preferences, listener);
            persist(studyPackId, content);
        } catch (AiNotConfiguredException ex) {
            fail(studyPackId, ex.getMessage());
        } catch (AiServiceException ex) {
            fail(studyPackId, ex.getMessage());
            log.warn("Study generation failed for {}: {}", studyPackId, ex.getMessage());
        } catch (ResourceNotFoundException | InvalidRequestException ex) {
            fail(studyPackId, ex.getMessage());
        } catch (Exception ex) {
            fail(studyPackId,
                    "Study generation failed unexpectedly. Please try again in a moment.");
            log.error("Unexpected study generation failure for {}", studyPackId, ex);
        }
    }

    private void persist(UUID studyPackId, GeneratedStudyContent content) {
        transactions.executeWithoutResult(status -> {
            StudyPack pack = studyPacks.findById(studyPackId).orElse(null);
            if (pack == null) {
                return;
            }
            flashcards.deleteByStudyPackId(studyPackId);
            questions.deleteByStudyPackId(studyPackId);

            List<Flashcard> cards = new ArrayList<>();
            int cardPosition = 0;
            for (GeneratedStudyContent.GeneratedFlashcard card : content.flashcards()) {
                cards.add(new Flashcard(UUID.randomUUID(), studyPackId, cardPosition++, card.front(), card.back(),
                        card.sourceReference()));
            }
            flashcards.saveAll(cards);

            List<Question> allQuestions = new ArrayList<>();
            int practicePosition = 0;
            for (GeneratedStudyContent.GeneratedQuestion question : content.practiceQuestions()) {
                allQuestions.add(toEntity(studyPackId, question, false, practicePosition++));
            }
            int mockPosition = 0;
            for (GeneratedStudyContent.GeneratedQuestion question : content.mockQuestions()) {
                allQuestions.add(toEntity(studyPackId, question, true, mockPosition++));
            }
            questions.saveAll(allQuestions);

            List<StudyTopic> topics = content.topics();
            pack.markReady(
                    titleOrNull(content.overview().title()),
                    content.overview().summary(),
                    json.write(content.overview()),
                    json.write(topics),
                    json.write(content.revisionNotes()),
                    json.write(content.definitions()),
                    json.write(content.keyFacts()),
                    clock.instant());
            studyPacks.save(pack);
        });
        log.info("Study package {} is ready", studyPackId);
    }

    private static Question toEntity(UUID studyPackId, GeneratedStudyContent.GeneratedQuestion generated,
                                     boolean mockExam, int position) {
        QuestionType type = generated.type() == null ? QuestionType.MULTIPLE_CHOICE : generated.type();
        Difficulty difficulty = generated.difficulty() == null ? Difficulty.MEDIUM : generated.difficulty();
        return new Question(UUID.randomUUID(), studyPackId, type, generated.prompt(),
                null, generated.correctAnswer(), generated.explanation(), difficulty,
                generated.sourceReference(), mockExam, position);
    }

    private static String titleOrNull(String title) {
        return title == null || title.isBlank() ? null : title;
    }

    private void updateProgress(UUID studyPackId, int percent, String message) {
        transactions.executeWithoutResult(status -> studyPacks.findById(studyPackId).ifPresent(pack -> {
            if (pack.getStatus() == StudyPackStatus.READY || pack.getStatus() == StudyPackStatus.FAILED) {
                return;
            }
            pack.updateProgress(percent, message, clock.instant());
            studyPacks.save(pack);
        }));
    }

    public void fail(UUID studyPackId, String reason) {
        transactions.executeWithoutResult(status -> studyPacks.findById(studyPackId).ifPresent(pack -> {
            pack.markFailed(reason, clock.instant());
            studyPacks.save(pack);
        }));
    }
}

package com.examprep.api.study;

import java.util.List;
import java.util.UUID;

public interface StudyPackApplicationService {

    StudyPackCreationResult create(UUID documentId, UUID ownerId, StudyPreferences preferences);

    StudyPackStatusDto getStatus(UUID studyPackId, UUID ownerId);

    StudyPackDto getStudyPack(UUID studyPackId, UUID ownerId);

    QuizResultDto submitQuiz(UUID studyPackId, UUID ownerId, QuizSubmissionDto submission);

    TutorAnswerDto askTutor(UUID studyPackId, UUID ownerId, TutorQuestionDto question);

    void delete(UUID studyPackId, UUID ownerId);

    List<StudyPackSummaryDto> listStudyPacks(UUID ownerId);

    List<QuestionDto> getExamQuestions(UUID studyPackId, UUID ownerId);

    List<QuizAttemptSummaryDto> listAttempts(UUID studyPackId, UUID ownerId);

    List<QuizAttemptSummaryDto> listRecentAttempts(UUID ownerId, int limit);

    List<TutorMessageDto> getTutorConversation(UUID studyPackId, UUID ownerId);

    void recordFlashcardReview(UUID studyPackId, UUID ownerId, int cardsReviewed);

    /** Loads a stored attempt so results survive a page reload and can be revisited. */
    QuizResultDto getAttemptResult(UUID studyPackId, UUID ownerId, UUID attemptId);

    /** Reveals the answer of a practice question. Mock examination answers stay hidden. */
    QuizQuestionReviewDto revealPracticeQuestion(UUID studyPackId, UUID ownerId, UUID questionId);

    long flashcardReviewCount(UUID ownerId);

    long attemptCount(UUID ownerId);

    /** True when a Gemini API key is configured; the UI shows a notice when it is not. */
    boolean aiConfigured();
}

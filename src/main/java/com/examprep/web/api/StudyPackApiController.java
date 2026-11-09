package com.examprep.web.api;

import com.examprep.api.study.QuizQuestionReviewDto;
import com.examprep.api.study.QuizResultDto;
import com.examprep.api.study.QuizSubmissionDto;
import com.examprep.api.study.StudyPackApplicationService;
import com.examprep.api.study.StudyPackCreationResult;
import com.examprep.api.study.StudyPackStatusDto;
import com.examprep.api.study.StudyPreferences;
import com.examprep.api.study.TutorAnswerDto;
import com.examprep.api.study.TutorQuestionDto;
import com.examprep.api.user.CurrentUserService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/study-packs")
public class StudyPackApiController {

    private final CurrentUserService currentUser;
    private final StudyPackApplicationService studyPacks;

    public StudyPackApiController(CurrentUserService currentUser, StudyPackApplicationService studyPacks) {
        this.currentUser = currentUser;
        this.studyPacks = studyPacks;
    }

    public record CreateStudyPackRequest(UUID documentId, String language, String academicLevel,
                                         LocalDate examDate, Integer practiceQuestionCount,
                                         Integer mockQuestionCount, Integer flashcardCount,
                                         Integer mockDurationMinutes) {

        StudyPreferences toPreferences() {
            StudyPreferences defaults = StudyPreferences.defaults();
            return new StudyPreferences(
                    language == null ? defaults.language() : language,
                    academicLevel == null ? defaults.academicLevel() : academicLevel,
                    examDate,
                    practiceQuestionCount == null ? defaults.practiceQuestionCount() : practiceQuestionCount,
                    mockQuestionCount == null ? defaults.mockQuestionCount() : mockQuestionCount,
                    flashcardCount == null ? defaults.flashcardCount() : flashcardCount,
                    mockDurationMinutes == null ? defaults.mockDurationMinutes() : mockDurationMinutes);
        }
    }

    @PostMapping
    public ResponseEntity<StudyPackCreationResult> create(@RequestBody CreateStudyPackRequest request) {
        UUID ownerId = currentUser.requireCurrentUserId();
        StudyPackCreationResult result = studyPacks.create(request.documentId(), ownerId,
                request.toPreferences());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
    }

    @GetMapping("/{id}/status")
    public StudyPackStatusDto status(@PathVariable UUID id) {
        return studyPacks.getStatus(id, currentUser.requireCurrentUserId());
    }

    @PostMapping("/{id}/quiz")
    public QuizResultDto submitQuiz(@PathVariable UUID id, @RequestBody @Valid QuizSubmissionDto submission) {
        return studyPacks.submitQuiz(id, currentUser.requireCurrentUserId(), submission);
    }

    @PostMapping("/{id}/tutor")
    public TutorAnswerDto askTutor(@PathVariable UUID id, @RequestBody @Valid TutorQuestionDto question) {
        return studyPacks.askTutor(id, currentUser.requireCurrentUserId(), question);
    }

    public record FlashcardReviewRequest(int cardsReviewed) {
    }

    @PostMapping("/{id}/flashcards/review")
    public ResponseEntity<Void> review(@PathVariable UUID id, @RequestBody FlashcardReviewRequest request) {
        studyPacks.recordFlashcardReview(id, currentUser.requireCurrentUserId(), request.cardsReviewed());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/questions/{questionId}/reveal")
    public QuizQuestionReviewDto reveal(@PathVariable UUID id, @PathVariable UUID questionId) {
        return studyPacks.revealPracticeQuestion(id, currentUser.requireCurrentUserId(), questionId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        studyPacks.delete(id, currentUser.requireCurrentUserId());
        return ResponseEntity.noContent().build();
    }
}

package com.examprep.study.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "quiz_answers")
public class QuizAnswer {

    @Id
    private UUID id;

    @Column(name = "attempt_id", nullable = false)
    private UUID attemptId;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(name = "submitted_answer")
    private String submittedAnswer;

    @Column(nullable = false)
    private boolean correct;

    @Column(name = "awarded_points", nullable = false)
    private int awardedPoints;

    protected QuizAnswer() {
    }

    public QuizAnswer(UUID id, UUID attemptId, UUID questionId, String submittedAnswer, boolean correct,
                      int awardedPoints) {
        this.id = id;
        this.attemptId = attemptId;
        this.questionId = questionId;
        this.submittedAnswer = submittedAnswer;
        this.correct = correct;
        this.awardedPoints = awardedPoints;
    }

    public UUID getAttemptId() {
        return attemptId;
    }

    public UUID getQuestionId() {
        return questionId;
    }

    public String getSubmittedAnswer() {
        return submittedAnswer;
    }

    public boolean isCorrect() {
        return correct;
    }

    public int getAwardedPoints() {
        return awardedPoints;
    }
}

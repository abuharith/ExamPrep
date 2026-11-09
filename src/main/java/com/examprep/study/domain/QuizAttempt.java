package com.examprep.study.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "quiz_attempts")
public class QuizAttempt {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "study_pack_id", nullable = false)
    private UUID studyPackId;

    @Column(nullable = false, length = 16)
    private String mode;

    @Column(nullable = false)
    private int score;

    @Column(name = "total_questions", nullable = false)
    private int totalQuestions;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    protected QuizAttempt() {
    }

    public QuizAttempt(UUID id, UUID ownerId, UUID studyPackId, String mode, int score, int totalQuestions,
                       int durationSeconds, Instant submittedAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.studyPackId = studyPackId;
        this.mode = mode;
        this.score = score;
        this.totalQuestions = totalQuestions;
        this.durationSeconds = durationSeconds;
        this.submittedAt = submittedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getStudyPackId() {
        return studyPackId;
    }

    public String getMode() {
        return mode;
    }

    public int getScore() {
        return score;
    }

    public int getTotalQuestions() {
        return totalQuestions;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }
}

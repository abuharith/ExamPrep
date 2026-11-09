package com.examprep.study.domain;

import com.examprep.api.study.StudyPackStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "study_packs")
public class StudyPack {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private StudyPackStatus status;

    @Column(name = "progress_percent", nullable = false)
    private int progressPercent;

    @Column(name = "progress_message", length = 300)
    private String progressMessage;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(name = "document_filename", nullable = false, length = 255)
    private String documentFilename;

    private String summary;

    private String overview;

    private String topics;

    @Column(name = "revision_notes")
    private String revisionNotes;

    private String definitions;

    @Column(name = "key_facts")
    private String keyFacts;

    @Column(nullable = false, length = 32)
    private String language;

    @Column(name = "academic_level", nullable = false, length = 40)
    private String academicLevel;

    @Column(name = "exam_date")
    private LocalDate examDate;

    @Column(name = "mock_duration_minutes", nullable = false)
    private int mockDurationMinutes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected StudyPack() {
    }

    public StudyPack(UUID id, UUID ownerId, UUID documentId, String title, String documentFilename,
                     String language, String academicLevel, LocalDate examDate, int mockDurationMinutes,
                     Instant now) {
        this.id = id;
        this.ownerId = ownerId;
        this.documentId = documentId;
        this.documentFilename = documentFilename;
        this.status = StudyPackStatus.QUEUED;
        this.progressPercent = 0;
        this.progressMessage = "Queued for generation.";
        this.title = title;
        this.language = language;
        this.academicLevel = academicLevel;
        this.examDate = examDate;
        this.mockDurationMinutes = mockDurationMinutes;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updateProgress(int percent, String message, Instant now) {
        this.status = StudyPackStatus.PROCESSING;
        this.progressPercent = Math.max(0, Math.min(100, percent));
        this.progressMessage = message == null ? null : message.substring(0, Math.min(300, message.length()));
        this.updatedAt = now;
    }

    public void markReady(String generatedTitle, String generatedSummary, String overviewJson, String topicsJson,
                          String revisionJson, String definitionsJson, String factsJson, Instant now) {
        this.status = StudyPackStatus.READY;
        this.progressPercent = 100;
        this.progressMessage = "Study package ready.";
        this.title = generatedTitle == null || generatedTitle.isBlank() ? title : generatedTitle;
        this.summary = generatedSummary;
        this.overview = overviewJson;
        this.topics = topicsJson;
        this.revisionNotes = revisionJson;
        this.definitions = definitionsJson;
        this.keyFacts = factsJson;
        this.failureReason = null;
        this.completedAt = now;
        this.updatedAt = now;
    }

    public void markFailed(String reason, Instant now) {
        this.status = StudyPackStatus.FAILED;
        this.failureReason = reason == null ? null : reason.substring(0, Math.min(1000, reason.length()));
        this.progressMessage = "Generation failed.";
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public StudyPackStatus getStatus() {
        return status;
    }

    public int getProgressPercent() {
        return progressPercent;
    }

    public String getProgressMessage() {
        return progressMessage;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getTitle() {
        return title;
    }

    public String getDocumentFilename() {
        return documentFilename;
    }

    public String getSummary() {
        return summary;
    }

    public String getOverview() {
        return overview;
    }

    public String getTopics() {
        return topics;
    }

    public String getRevisionNotes() {
        return revisionNotes;
    }

    public String getDefinitions() {
        return definitions;
    }

    public String getKeyFacts() {
        return keyFacts;
    }

    public String getLanguage() {
        return language;
    }

    public String getAcademicLevel() {
        return academicLevel;
    }

    public LocalDate getExamDate() {
        return examDate;
    }

    public int getMockDurationMinutes() {
        return mockDurationMinutes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}

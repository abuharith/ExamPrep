package com.examprep.study.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tutor_messages")
public class TutorMessage {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "study_pack_id", nullable = false)
    private UUID studyPackId;

    @Column(nullable = false, length = 16)
    private String role;

    @Column(nullable = false)
    private String content;

    @Column(name = "source_references")
    private String sourceReferences;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TutorMessage() {
    }

    public TutorMessage(UUID id, UUID ownerId, UUID studyPackId, String role, String content,
                        String sourceReferencesJson, Instant createdAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.studyPackId = studyPackId;
        this.role = role;
        this.content = content;
        this.sourceReferences = sourceReferencesJson;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public String getSourceReferences() {
        return sourceReferences;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

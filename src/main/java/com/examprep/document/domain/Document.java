package com.examprep.document.domain;

import com.examprep.api.document.ProcessingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "documents")
public class Document {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "detected_content_type", nullable = false, length = 160)
    private String detectedContentType;

    @Column(nullable = false, length = 16)
    private String extension;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "storage_key", nullable = false, length = 160)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ProcessingStatus status;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "char_count")
    private Integer charCount;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected Document() {
    }

    public Document(UUID id, UUID ownerId, String originalFilename, String detectedContentType, String extension,
                    long sizeBytes, String storageKey, Instant now) {
        this.id = id;
        this.ownerId = ownerId;
        this.originalFilename = originalFilename;
        this.detectedContentType = detectedContentType;
        this.extension = extension;
        this.sizeBytes = sizeBytes;
        this.storageKey = storageKey;
        this.status = ProcessingStatus.UPLOADED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void markProcessing(Instant now) {
        this.status = ProcessingStatus.PROCESSING;
        this.failureReason = null;
        this.updatedAt = now;
    }

    public void markReady(int pageCount, int charCount, int chunkCount, Instant now) {
        this.status = ProcessingStatus.READY;
        this.pageCount = pageCount;
        this.charCount = charCount;
        this.chunkCount = chunkCount;
        this.failureReason = null;
        this.processedAt = now;
        this.updatedAt = now;
    }

    public void markFailed(String reason, Instant now) {
        this.status = ProcessingStatus.FAILED;
        this.failureReason = truncate(reason, 1000);
        this.updatedAt = now;
        this.processedAt = now;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "\u2026";
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getDetectedContentType() {
        return detectedContentType;
    }

    public String getExtension() {
        return extension;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public ProcessingStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Integer getPageCount() {
        return pageCount;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

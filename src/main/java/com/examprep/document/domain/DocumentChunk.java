package com.examprep.document.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "document_chunks")
public class DocumentChunk {

    @Id
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "reference_id", nullable = false, length = 40)
    private String referenceId;

    @Column(name = "location_label", nullable = false, length = 160)
    private String locationLabel;

    @Column(nullable = false)
    private String content;

    @Column(name = "char_count", nullable = false)
    private int charCount;

    protected DocumentChunk() {
    }

    public DocumentChunk(UUID id, UUID documentId, int chunkIndex, String referenceId, String locationLabel,
                         String content) {
        this.id = id;
        this.documentId = documentId;
        this.chunkIndex = chunkIndex;
        this.referenceId = referenceId;
        this.locationLabel = locationLabel;
        this.content = content;
        this.charCount = content.length();
    }

    public UUID getId() {
        return id;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public String getLocationLabel() {
        return locationLabel;
    }

    public String getContent() {
        return content;
    }

    public int getCharCount() {
        return charCount;
    }
}

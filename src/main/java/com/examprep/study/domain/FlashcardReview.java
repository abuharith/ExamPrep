package com.examprep.study.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "flashcard_reviews")
public class FlashcardReview {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "study_pack_id", nullable = false)
    private UUID studyPackId;

    @Column(name = "cards_reviewed", nullable = false)
    private int cardsReviewed;

    @Column(name = "reviewed_at", nullable = false)
    private Instant reviewedAt;

    protected FlashcardReview() {
    }

    public FlashcardReview(UUID id, UUID ownerId, UUID studyPackId, int cardsReviewed, Instant reviewedAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.studyPackId = studyPackId;
        this.cardsReviewed = cardsReviewed;
        this.reviewedAt = reviewedAt;
    }
}

package com.examprep.study.repository;

import com.examprep.study.domain.FlashcardReview;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlashcardReviewRepository extends JpaRepository<FlashcardReview, UUID> {

    List<FlashcardReview> findByOwnerIdOrderByReviewedAtDesc(UUID ownerId);

    long countByOwnerId(UUID ownerId);
}

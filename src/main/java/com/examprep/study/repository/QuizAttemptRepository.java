package com.examprep.study.repository;

import com.examprep.study.domain.QuizAttempt;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, UUID> {

    List<QuizAttempt> findByStudyPackIdAndOwnerIdOrderBySubmittedAtDesc(UUID studyPackId, UUID ownerId);

    List<QuizAttempt> findByOwnerIdOrderBySubmittedAtDesc(UUID ownerId, Pageable pageable);

    long countByOwnerId(UUID ownerId);

    void deleteByStudyPackId(UUID studyPackId);
}

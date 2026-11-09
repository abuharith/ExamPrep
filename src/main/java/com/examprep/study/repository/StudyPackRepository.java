package com.examprep.study.repository;

import com.examprep.api.study.StudyPackStatus;
import com.examprep.study.domain.StudyPack;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyPackRepository extends JpaRepository<StudyPack, UUID> {

    Optional<StudyPack> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<StudyPack> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    List<StudyPack> findByStatusIn(Collection<StudyPackStatus> statuses);

    long countByOwnerId(UUID ownerId);

    long countByDocumentId(UUID documentId);
}

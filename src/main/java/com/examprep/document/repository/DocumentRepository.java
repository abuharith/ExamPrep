package com.examprep.document.repository;

import com.examprep.api.document.ProcessingStatus;
import com.examprep.document.domain.Document;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Optional<Document> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<Document> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    List<Document> findByStatus(ProcessingStatus status);

    long countByOwnerId(UUID ownerId);
}

package com.examprep.document.service;

import com.examprep.api.document.DocumentApplicationService;
import com.examprep.api.document.DocumentChunkDto;
import com.examprep.api.document.DocumentContext;
import com.examprep.api.document.DocumentStatusDto;
import com.examprep.api.document.DocumentSummaryDto;
import com.examprep.api.document.DocumentUploadResult;
import com.examprep.api.document.ProcessingStatus;
import com.examprep.common.exception.InvalidRequestException;
import com.examprep.common.exception.InvalidUploadException;
import com.examprep.common.exception.ResourceNotFoundException;
import com.examprep.document.domain.Document;
import com.examprep.document.domain.DocumentChunk;
import com.examprep.document.repository.DocumentChunkRepository;
import com.examprep.document.repository.DocumentRepository;
import com.examprep.document.storage.DocumentStorage;
import com.examprep.document.validation.UploadValidator;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentServiceImpl implements DocumentApplicationService {

    private static final Logger log = LoggerFactory.getLogger(DocumentServiceImpl.class);

    private final DocumentRepository documents;
    private final DocumentChunkRepository chunks;
    private final DocumentStorage storage;
    private final UploadValidator validator;
    private final DocumentIngestionService ingestion;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public DocumentServiceImpl(DocumentRepository documents, DocumentChunkRepository chunks,
                               DocumentStorage storage, UploadValidator validator,
                               DocumentIngestionService ingestion, TransactionTemplate transactions, Clock clock) {
        this.documents = documents;
        this.chunks = chunks;
        this.storage = storage;
        this.validator = validator;
        this.ingestion = ingestion;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public DocumentUploadResult upload(MultipartFile file, UUID ownerId) {
        String extension = validator.inspect(file);
        String originalFilename = sanitiseFilename(file.getOriginalFilename(), extension);
        Path temporary = null;
        String storageKey = null;
        try {
            temporary = storage.createTempFile(extension);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, temporary, StandardCopyOption.REPLACE_EXISTING);
            }
            String contentType = validator.detectAndVerify(temporary, extension);
            try (InputStream in = Files.newInputStream(temporary)) {
                storageKey = storage.store(in, extension).getFileName().toString();
            }
            UUID documentId = UUID.randomUUID();
            long size = file.getSize();
            String key = storageKey;
            transactions.executeWithoutResult(status -> documents.save(new Document(documentId, ownerId,
                    originalFilename, contentType, extension, size, key, clock.instant())));
            storageKey = null; // ownership transferred to the document record
            submit(documentId, ownerId);
            return new DocumentUploadResult(documentId, originalFilename, size, ProcessingStatus.UPLOADED);
        } catch (IOException ex) {
            throw new InvalidUploadException("The file could not be stored. Try uploading it again.");
        } finally {
            deleteTemporary(temporary);
            if (storageKey != null) {
                storage.deleteQuietly(storageKey);
            }
        }
    }

    private void submit(UUID documentId, UUID ownerId) {
        try {
            ingestion.processAsync(documentId);
        } catch (RejectedExecutionException ex) {
            ingestion.markFailed(documentId, "The server is busy. Delete this document and upload it again.");
            log.warn("Extraction queue rejected document {}", documentId);
        }
    }

    private static void deleteTemporary(Path temporary) {
        if (temporary == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException ignored) {
            // The temp directory is cleaned on restart when needed.
        }
    }

    @Override
    public DocumentStatusDto getStatus(UUID documentId, UUID ownerId) {
        Document document = requireOwned(documentId, ownerId);
        return new DocumentStatusDto(document.getId(), document.getOriginalFilename(), document.getStatus(),
                statusMessage(document), document.getFailureReason(), document.getChunkCount(),
                document.getPageCount() == null ? 0 : document.getPageCount(), document.getUpdatedAt());
    }

    private static String statusMessage(Document document) {
        return switch (document.getStatus()) {
            case UPLOADED -> "Queued for text extraction.";
            case PROCESSING -> "Extracting text from your document.";
            case READY -> "Text extracted. You can generate a study package.";
            case FAILED -> document.getFailureReason() == null
                    ? "Processing failed." : document.getFailureReason();
        };
    }

    @Override
    public List<DocumentSummaryDto> listDocuments(UUID ownerId) {
        return documents.findByOwnerIdOrderByCreatedAtDesc(ownerId).stream().map(DocumentServiceImpl::toSummary)
                .toList();
    }

    private static DocumentSummaryDto toSummary(Document document) {
        return new DocumentSummaryDto(document.getId(), document.getOriginalFilename(),
                document.getDetectedContentType(), document.getExtension(), document.getSizeBytes(),
                document.getStatus(), document.getFailureReason(), document.getChunkCount(),
                document.getPageCount() == null ? 0 : document.getPageCount(), document.getCreatedAt());
    }

    @Override
    public DocumentContext getContext(UUID documentId, UUID ownerId) {
        Document document = requireOwned(documentId, ownerId);
        if (document.getStatus() != ProcessingStatus.READY) {
            throw new InvalidRequestException("This document is not ready yet. Wait for text extraction to finish.");
        }
        List<DocumentChunk> extracted = chunks.findByDocumentIdOrderByChunkIndexAsc(documentId);
        if (extracted.isEmpty()) {
            throw new InvalidRequestException("This document has no extracted text.");
        }
        List<DocumentChunkDto> chunkDtos = extracted.stream()
                .map(chunk -> new DocumentChunkDto(chunk.getReferenceId(), chunk.getLocationLabel(),
                        chunk.getContent()))
                .toList();
        return new DocumentContext(document.getId(), document.getOriginalFilename(),
                document.getOriginalFilename(), chunkDtos);
    }

    @Override
    public void delete(UUID documentId, UUID ownerId) {
        Document document = requireOwned(documentId, ownerId);
        String storageKey = document.getStorageKey();
        transactions.executeWithoutResult(status -> documents.deleteById(documentId));
        storage.deleteQuietly(storageKey);
    }

    private Document requireOwned(UUID documentId, UUID ownerId) {
        if (documentId == null || ownerId == null) {
            throw new ResourceNotFoundException("Document not found.");
        }
        return documents.findByIdAndOwnerId(documentId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found."));
    }

    private static String sanitiseFilename(String supplied, String extension) {
        String name = supplied == null ? "" : supplied.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isBlank()) {
            return "document." + extension;
        }
        if (name.length() > 255) {
            int dot = name.lastIndexOf('.');
            String suffix = dot < 0 ? "" : name.substring(dot);
            name = name.substring(0, Math.min(dot < 0 ? 250 : dot, 250)) + suffix;
        }
        return name.toLowerCase(Locale.ROOT).equals(name) ? name : name;
    }
}

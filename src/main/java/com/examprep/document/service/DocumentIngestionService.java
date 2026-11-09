package com.examprep.document.service;

import com.examprep.api.document.ProcessingStatus;
import com.examprep.common.exception.InsufficientMaterialException;
import com.examprep.common.exception.ProcessingException;
import com.examprep.config.AsyncConfig;
import com.examprep.document.domain.Document;
import com.examprep.document.domain.DocumentChunk;
import com.examprep.document.parser.DocumentExtractionRouter;
import com.examprep.document.parser.ExtractedChunk;
import com.examprep.document.parser.ExtractionResult;
import com.examprep.document.repository.DocumentChunkRepository;
import com.examprep.document.repository.DocumentRepository;
import com.examprep.document.storage.DocumentStorage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs extraction away from the request thread and keeps the document state machine in
 * PostgreSQL so the browser can poll it.
 */
@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    private final DocumentRepository documents;
    private final DocumentChunkRepository chunks;
    private final DocumentStorage storage;
    private final DocumentExtractionRouter router;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public DocumentIngestionService(DocumentRepository documents, DocumentChunkRepository chunks,
                                    DocumentStorage storage, DocumentExtractionRouter router,
                                    TransactionTemplate transactions, Clock clock) {
        this.documents = documents;
        this.chunks = chunks;
        this.storage = storage;
        this.router = router;
        this.transactions = transactions;
        this.clock = clock;
    }

    /** Called by {@link DocumentServiceImpl}; the call must cross the bean boundary for @Async. */
    @Async(AsyncConfig.PROCESSING_EXECUTOR)
    public void processAsync(UUID documentId) {
        process(documentId);
    }

    public void process(UUID documentId) {
        Document document = find(documentId);
        if (document == null || document.getStatus() == ProcessingStatus.READY) {
            return;
        }
        transactions.executeWithoutResult(status -> documents.findById(documentId).ifPresent(current -> {
            current.markProcessing(clock.instant());
            documents.save(current);
        }));

        try {
            Path file = storage.resolve(document.getStorageKey());
            if (!Files.isReadable(file)) {
                throw new ProcessingException(
                        "The stored file could not be found. Delete this document and upload it again.");
            }
            ExtractionResult result = router.extract(file, document.getOriginalFilename(), document.getExtension());
            persistSuccess(documentId, result);
        } catch (InsufficientMaterialException ex) {
            markFailed(documentId, ex.getMessage());
            log.info("Document {} has no usable text: {}", documentId, ex.getMessage());
        } catch (ProcessingException ex) {
            markFailed(documentId, ex.getMessage());
            log.warn("Document {} could not be processed: {}", documentId, ex.getMessage());
        } catch (Exception ex) {
            markFailed(documentId,
                    "The document could not be processed. Try uploading it again or use a different format.");
            log.error("Unexpected extraction failure for document {}", documentId, ex);
        }
    }

    /** Public so callers can record a queue rejection without leaking internals. */
    public void markFailed(UUID documentId, String reason) {
        transactions.executeWithoutResult(status -> documents.findById(documentId).ifPresent(current -> {
            current.markFailed(reason, clock.instant());
            documents.save(current);
        }));
    }

    private void persistSuccess(UUID documentId, ExtractionResult result) {
        int characters = result.characterCount();
        int chunkCount = result.chunks().size();
        transactions.executeWithoutResult(status -> {
            Document current = documents.findById(documentId).orElse(null);
            if (current == null) {
                return;
            }
            chunks.deleteByDocumentId(documentId);
            List<DocumentChunk> entities = new ArrayList<>(chunkCount);
            int index = 0;
            for (ExtractedChunk chunk : result.chunks()) {
                entities.add(new DocumentChunk(UUID.randomUUID(), documentId, index++, chunk.referenceId(),
                        chunk.location(), chunk.text()));
            }
            chunks.saveAll(entities);
            current.markReady(result.pageCount(), characters, chunkCount, clock.instant());
            documents.save(current);
        });
        log.info("Document {} extracted: {} chunks, {} characters", documentId, chunkCount, characters);
    }

    private Document find(UUID documentId) {
        return transactions.execute(status -> documents.findById(documentId).orElse(null));
    }
}

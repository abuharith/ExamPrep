package com.examprep.document.service;

import com.examprep.api.document.ProcessingStatus;
import com.examprep.document.domain.Document;
import com.examprep.document.repository.DocumentRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * A restart can leave documents stuck in UPLOADED or PROCESSING. Extraction is
 * deterministic and free, so those jobs are queued again instead of being abandoned.
 */
@Component
public class DocumentRecovery implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DocumentRecovery.class);

    private final DocumentRepository documents;
    private final DocumentIngestionService ingestion;

    public DocumentRecovery(DocumentRepository documents, DocumentIngestionService ingestion) {
        this.documents = documents;
        this.ingestion = ingestion;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Document> interrupted = new ArrayList<>();
        interrupted.addAll(documents.findByStatus(ProcessingStatus.PROCESSING));
        interrupted.addAll(documents.findByStatus(ProcessingStatus.UPLOADED));
        for (Document document : interrupted) {
            try {
                ingestion.processAsync(document.getId());
            } catch (RejectedExecutionException ex) {
                ingestion.markFailed(document.getId(), "Processing was interrupted by a server restart. "
                        + "Delete this document and upload it again.");
            }
        }
        if (!interrupted.isEmpty()) {
            log.info("Re-queued {} document extraction job(s) after restart", interrupted.size());
        }
    }
}

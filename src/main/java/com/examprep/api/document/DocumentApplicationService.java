package com.examprep.api.document;

import java.util.List;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface DocumentApplicationService {

    DocumentUploadResult upload(MultipartFile file, UUID ownerId);

    DocumentStatusDto getStatus(UUID documentId, UUID ownerId);

    List<DocumentSummaryDto> listDocuments(UUID ownerId);

    DocumentContext getContext(UUID documentId, UUID ownerId);

    void delete(UUID documentId, UUID ownerId);
}

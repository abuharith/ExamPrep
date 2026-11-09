package com.examprep.web.api;

import com.examprep.api.document.DocumentApplicationService;
import com.examprep.api.document.DocumentStatusDto;
import com.examprep.api.document.DocumentUploadResult;
import com.examprep.api.user.CurrentUserService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
public class DocumentApiController {

    private final CurrentUserService currentUser;
    private final DocumentApplicationService documents;

    public DocumentApiController(CurrentUserService currentUser, DocumentApplicationService documents) {
        this.currentUser = currentUser;
        this.documents = documents;
    }

    @PostMapping
    public ResponseEntity<DocumentUploadResult> upload(@RequestParam("file") MultipartFile file) {
        UUID ownerId = currentUser.requireCurrentUserId();
        DocumentUploadResult result = documents.upload(file, ownerId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
    }

    @GetMapping("/{id}/status")
    public DocumentStatusDto status(@PathVariable UUID id) {
        UUID ownerId = currentUser.requireCurrentUserId();
        return documents.getStatus(id, ownerId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        UUID ownerId = currentUser.requireCurrentUserId();
        documents.delete(id, ownerId);
        return ResponseEntity.noContent().build();
    }
}

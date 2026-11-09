package com.examprep.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.examprep.api.document.DocumentApplicationService;
import com.examprep.api.document.DocumentChunkDto;
import com.examprep.api.document.DocumentContext;
import com.examprep.api.document.DocumentStatusDto;
import com.examprep.api.document.ProcessingStatus;
import com.examprep.document.repository.DocumentRepository;
import com.examprep.document.storage.DocumentStorage;
import com.examprep.support.Await;
import com.examprep.support.IntegrationTestBase;
import com.examprep.support.TestDocuments;
import com.examprep.support.WithMockStudent;
import com.examprep.user.repository.UserAccountRepository;
import com.jayway.jsonpath.JsonPath;
import java.nio.file.Files;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class DocumentIngestionIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentApplicationService documents;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentStorage storage;

    @Autowired
    private UserAccountRepository users;

    @Test
    @WithMockStudent(email = "pdf-owner@example.edu")
    void extractsPdfTextWithRealPageReferences() throws Exception {
        UUID documentId = upload("lecture.pdf", "application/pdf",
                TestDocuments.pdf(TestDocuments.PAGE_ONE, TestDocuments.PAGE_TWO));

        DocumentStatusDto status = awaitSettled(documentId, "pdf-owner@example.edu");

        assertThat(status.status()).isEqualTo(ProcessingStatus.READY);
        assertThat(status.pageCount()).isEqualTo(2);
        assertThat(status.chunkCount()).isGreaterThanOrEqualTo(2);

        DocumentContext context = documents.getContext(documentId, userId("pdf-owner@example.edu"));
        assertThat(context.chunks()).extracting(DocumentChunkDto::location).contains("Page 1", "Page 2");
        assertThat(context.chunks()).extracting(DocumentChunkDto::referenceId).contains("p1", "p2");
        assertThat(context.chunks().get(0).text()).contains("Cell theory");
    }

    @Test
    @WithMockStudent(email = "docx-owner@example.edu")
    void extractsDocxHeadingsAsSections() throws Exception {
        UUID documentId = upload("notes.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                TestDocuments.docx("Cell Structure", TestDocuments.PAGE_ONE));

        DocumentStatusDto status = awaitSettled(documentId, "docx-owner@example.edu");

        assertThat(status.status()).isEqualTo(ProcessingStatus.READY);
        DocumentContext context = documents.getContext(documentId, userId("docx-owner@example.edu"));
        assertThat(context.chunks().get(0).location()).contains("Cell Structure");
        assertThat(context.chunks().get(0).referenceId()).isEqualTo("s1");
    }

    @Test
    @WithMockStudent(email = "pptx-owner@example.edu")
    void extractsPptxSlidesWithSlideReferences() throws Exception {
        UUID documentId = upload("slides.pptx",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                TestDocuments.pptx(TestDocuments.PAGE_ONE));

        DocumentStatusDto status = awaitSettled(documentId, "pptx-owner@example.edu");

        assertThat(status.status()).isEqualTo(ProcessingStatus.READY);
        DocumentContext context = documents.getContext(documentId, userId("pptx-owner@example.edu"));
        assertThat(context.chunks().get(0).location()).isEqualTo("Slide 1");
        assertThat(context.chunks().get(0).referenceId()).isEqualTo("s1");
    }

    @Test
    @WithMockStudent(email = "txt-owner@example.edu")
    void extractsMarkdownSections() throws Exception {
        String markdown = """
                # Osmosis

                Osmosis is the movement of water across a semi-permeable membrane.

                # Diffusion

                Diffusion is the movement of particles from high to low concentration.
                """;
        UUID documentId = upload("revision.md", "text/markdown", markdown.getBytes());

        awaitSettled(documentId, "txt-owner@example.edu");
        DocumentContext context = documents.getContext(documentId, userId("txt-owner@example.edu"));

        assertThat(context.chunks()).extracting(DocumentChunkDto::location)
                .contains("Section: Osmosis", "Section: Diffusion");
    }

    @Test
    @WithMockStudent(email = "scan-owner@example.edu")
    void detectsScannedPdfWithoutSelectableText() throws Exception {
        UUID documentId = upload("scan.pdf", "application/pdf", TestDocuments.blankPdf());

        DocumentStatusDto status = awaitSettled(documentId, "scan-owner@example.edu");

        assertThat(status.status()).isEqualTo(ProcessingStatus.FAILED);
        assertThat(status.failureReason()).contains("scanned");
    }

    @Test
    @WithMockStudent(email = "corrupt-owner@example.edu")
    void reportsCorruptedPdfWithoutCrashing() throws Exception {
        UUID documentId = upload("broken.pdf", "application/pdf",
                "%PDF-1.7\nthis is not a valid pdf body".getBytes());

        DocumentStatusDto status = awaitSettled(documentId, "corrupt-owner@example.edu");

        assertThat(status.status()).isEqualTo(ProcessingStatus.FAILED);
        assertThat(status.failureReason()).isNotBlank();
    }

    @Test
    @WithMockStudent(email = "reject-owner@example.edu")
    void rejectsUnsupportedAndMismatchedFiles() throws Exception {
        mockMvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", "run.exe", "application/octet-stream", new byte[]{1, 2}))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("not supported")));

        mockMvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", "disguised.pdf", "application/pdf",
                                "just plain text".getBytes()))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("does not match")));
    }

    @Test
    @WithMockStudent(email = "delete-owner@example.edu")
    void deletesDocumentChunksAndStoredFile() throws Exception {
        UUID documentId = upload("delete-me.pdf", "application/pdf",
                TestDocuments.pdf(TestDocuments.PAGE_ONE));
        awaitSettled(documentId, "delete-owner@example.edu");
        UUID ownerId = userId("delete-owner@example.edu");
        String storageKey = documentRepository.findByIdAndOwnerId(documentId, ownerId).orElseThrow()
                .getStorageKey();
        assertThat(Files.exists(storage.resolve(storageKey))).isTrue();

        mockMvc.perform(get("/documents/" + documentId))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("delete-me.pdf")));

        mockMvc.perform(delete("/api/documents/" + documentId).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/documents/" + documentId + "/status").with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(Files.exists(storage.resolve(storageKey))).isFalse();
    }

    @Test
    @WithMockStudent(email = "intruder@example.edu")
    void anotherStudentCannotReadADocumentStatus() throws Exception {
        ensureUser("victim@example.edu");
        UUID documentId = uploadAs("victim@example.edu", "private.pdf", "application/pdf",
                TestDocuments.pdf(TestDocuments.PAGE_ONE));
        awaitSettled(documentId, "victim@example.edu");

        mockMvc.perform(get("/api/documents/" + documentId + "/status"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/documents/" + documentId))
                .andExpect(status().isNotFound());
    }

    private UUID upload(String filename, String contentType, byte[] content) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", filename, contentType, content))
                        .with(csrf()))
                .andExpect(status().isAccepted())
                .andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.documentId"));
    }

    private UUID uploadAs(String email, String filename, String contentType, byte[] content) {
        UUID ownerId = userId(email);
        return documents.upload(new MockMultipartFile("file", filename, contentType, content), ownerId)
                .documentId();
    }

    private UUID ensureUser(String email) {
        return users.findByEmailIgnoreCase(email)
                .map(account -> account.getId())
                .orElseGet(() -> users.saveAndFlush(new com.examprep.user.domain.UserAccount(
                        UUID.randomUUID(), "Seeded Student", email,
                        new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                                .encode("password123"), java.time.Instant.now())).getId());
    }

    private UUID userId(String email) {
        return users.findByEmailIgnoreCase(email).orElseThrow().getId();
    }

    private DocumentStatusDto awaitSettled(UUID documentId, String email) {
        UUID ownerId = userId(email);
        return Await.until(Duration.ofSeconds(25), () -> documents.getStatus(documentId, ownerId),
                status -> status.status() == ProcessingStatus.READY
                        || status.status() == ProcessingStatus.FAILED,
                "document extraction");
    }
}

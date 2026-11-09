package com.examprep.study;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.examprep.ai.client.GeminiGateway;
import com.examprep.ai.client.GeminiPrompt;
import com.examprep.api.document.DocumentApplicationService;
import com.examprep.api.document.DocumentStatusDto;
import com.examprep.api.document.ProcessingStatus;
import com.examprep.api.study.StudyPackApplicationService;
import com.examprep.api.study.StudyPackDto;
import com.examprep.api.study.StudyPackStatus;
import com.examprep.api.study.StudyPackStatusDto;
import com.examprep.api.study.StudyPreferences;
import com.examprep.common.exception.InvalidRequestException;
import com.examprep.common.exception.ResourceNotFoundException;
import com.examprep.study.domain.Question;
import com.examprep.study.repository.QuestionRepository;
import com.examprep.support.Await;
import com.examprep.support.IntegrationTestBase;
import com.examprep.support.TestDocuments;
import com.examprep.support.WithMockStudent;
import com.examprep.user.repository.UserAccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class StudyFlowIntegrationTest extends IntegrationTestBase {

    private static final String OVERVIEW = """
            {"title":"Cell Biology","introduction":"Cells are the basic units of life.",
             "summary":"This lecture covers cell structure and energy production.",
             "learningObjectives":["Describe organelles","Explain respiration"],
             "revisionFocus":["Membrane transport"],
             "topics":[{"name":"Organelles","explanation":"Specialised structures inside cells.",
             "keyConcepts":["Nucleus","Mitochondrion"],"sourceReferences":["p1"]}]}
            """;
    private static final String NOTES = """
            {"sections":[{"heading":"The cell","body":"All living organisms are made of cells. The nucleus stores DNA.",
             "sourceReferences":["p1"]}]}
            """;
    private static final String GLOSSARY = """
            {"definitions":[{"term":"Organelle","definition":"A specialised structure inside a cell.",
             "sourceReference":"p2"}],
             "keyFacts":[{"label":"ATP","detail":"The energy currency produced by mitochondria.",
             "sourceReference":"p2","formula":false}]}
            """;
    private static final String FLASHCARDS = """
            {"flashcards":[{"front":"What is the basic unit of life?","back":"The cell.","sourceReference":"p1"},
             {"front":"What produces ATP?","back":"Mitochondria.","sourceReference":"p2"}]}
            """;
    private static final String QUESTIONS = """
            {"practice":[{"type":"MULTIPLE_CHOICE","prompt":"Which organelle produces ATP?",
             "options":["Nucleus","Mitochondrion"],"correctAnswer":"Mitochondrion",
             "explanation":"Mitochondria carry out respiration.","difficulty":"EASY","sourceReference":"p2"},
             {"type":"SHORT_ANSWER","prompt":"Define a cell.","options":[],
             "correctAnswer":"The basic structural and functional unit of life.","explanation":"See the notes.",
             "difficulty":"MEDIUM","sourceReference":"p1"}],
             "mock":[{"type":"TRUE_FALSE","prompt":"The nucleus stores DNA.","options":["True","False"],
             "correctAnswer":"True","explanation":"DNA is found in the nucleus.","difficulty":"EASY",
             "sourceReference":"p1"}]}
            """;
    private static final String TUTOR = """
            {"answer":"Mitochondria produce ATP through cellular respiration.","sourceReferences":["p2"],
             "insufficientInformation":false}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentApplicationService documents;

    @Autowired
    private StudyPackApplicationService studyPacks;

    @Autowired
    private QuestionRepository questions;

    @Autowired
    private UserAccountRepository users;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GeminiGateway geminiGateway;

    @BeforeEach
    void stubGemini() {
        when(geminiGateway.isConfigured()).thenReturn(true);
        when(geminiGateway.generateJson(any())).thenAnswer(invocation -> {
            GeminiPrompt prompt = invocation.getArgument(0);
            String text = prompt.userPrompt();
            if (text.contains("STUDENT QUESTION")) {
                return TUTOR;
            }
            if (text.contains("overview and topic map")) {
                return OVERVIEW;
            }
            if (text.contains("revision notes")) {
                return NOTES;
            }
            if (text.contains("glossary")) {
                return GLOSSARY;
            }
            if (text.contains("flashcards")) {
                return FLASHCARDS;
            }
            if (text.contains("practice questions")) {
                return QUESTIONS;
            }
            if (text.contains("Summarise part")) {
                return "{\"summary\":\"Part summary\",\"keyPoints\":[\"A point\"]}";
            }
            throw new AssertionError("Unexpected Gemini prompt: " + text.substring(0, Math.min(80, text.length())));
        });
    }

    @Test
    @WithMockStudent(email = "study-owner@example.edu")
    void generatesPersistsAndScoresAStudyPackage() throws Exception {
        UUID documentId = uploadDocument("study-owner@example.edu");
        StudyPackCreationResultHolder created = createPack(documentId, "study-owner@example.edu", 6, 5, 8, 15);
        StudyPackStatusDto ready = awaitPack(created.packId(), "study-owner@example.edu");

        assertThat(ready.status()).isEqualTo(StudyPackStatus.READY);
        assertThat(ready.progressPercent()).isEqualTo(100);

        StudyPackDto pack = studyPacks.getStudyPack(created.packId(), userId("study-owner@example.edu"));
        assertThat(pack.title()).isEqualTo("Cell Biology");
        assertThat(pack.topics()).hasSize(1);
        assertThat(pack.revisionNotes()).hasSize(1);
        assertThat(pack.definitions()).hasSize(1);
        assertThat(pack.flashcards()).hasSize(2);
        assertThat(pack.practiceQuestions()).hasSize(2);
        assertThat(pack.mockQuestionCount()).isEqualTo(1);
        // Citations were mapped back to real source locations; nothing fabricated.
        assertThat(pack.topics().get(0).sourceReferences()).containsExactly("Page 1");
        assertThat(pack.practiceQuestions().get(0).sourceReference()).isEqualTo("Page 2");

        mockMvc.perform(get("/study-packs/" + created.packId()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Revision notes")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Cell Biology")));
        mockMvc.perform(get("/study-packs/" + created.packId() + "/flashcards"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("What produces ATP?")));
        mockMvc.perform(get("/study-packs/" + created.packId() + "/exam"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/study-packs/" + created.packId() + "/tutor"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/study-packs/" + created.packId() + "/status"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Generating your study package")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("100%")));

        List<Question> mockQuestions = questions
                .findByStudyPackIdAndMockExamOrderByPositionAsc(created.packId(), true);
        assertThat(mockQuestions).hasSize(1);
        Map<UUID, String> answers = new LinkedHashMap<>();
        mockQuestions.forEach(question -> answers.put(question.getId(), question.getCorrectAnswer()));

        MvcResult quizResult = mockMvc.perform(post("/api/study-packs/" + created.packId() + "/quiz")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "answers", answers, "durationSeconds", 95))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(1))
                .andExpect(jsonPath("$.totalQuestions").value(1))
                .andExpect(jsonPath("$.percentage").value(100))
                .andReturn();
        String attemptId = JsonPath.read(quizResult.getResponse().getContentAsString(), "$.attemptId");

        mockMvc.perform(get("/study-packs/" + created.packId() + "/attempts/" + attemptId))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Answer review")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("100%")));

        Question practice = questions.findByStudyPackIdAndMockExamOrderByPositionAsc(created.packId(), false)
                .stream().filter(question -> question.getQuestionType().name().equals("MULTIPLE_CHOICE"))
                .findFirst().orElseThrow();
        mockMvc.perform(post("/api/study-packs/" + created.packId() + "/questions/" + practice.getId() + "/reveal")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correctAnswer").value("Mitochondrion"));

        Question mockQuestion = mockQuestions.get(0);
        mockMvc.perform(post("/api/study-packs/" + created.packId() + "/questions/" + mockQuestion.getId()
                        + "/reveal").with(csrf()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/study-packs/" + created.packId() + "/tutor")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What do mitochondria produce?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("ATP")))
                .andExpect(jsonPath("$.sourceReferences[0]").value("Page 2"));

        assertThat(studyPacks.getTutorConversation(created.packId(), userId("study-owner@example.edu")))
                .hasSize(2);
    }

    @Test
    @WithMockStudent(email = "study-fail@example.edu")
    void failedGenerationIsRecordedWithoutFakeContent() throws Exception {
        // doReturn avoids invoking the existing answer with matcher nulls while re-stubbing.
        doReturn("{\"not\":\"the expected shape\"}").when(geminiGateway).generateJson(any());
        UUID documentId = uploadDocument("study-fail@example.edu");
        StudyPackCreationResultHolder created = createPack(documentId, "study-fail@example.edu", 5, 5, 5, 10);

        StudyPackStatusDto failed = awaitPack(created.packId(), "study-fail@example.edu");

        assertThat(failed.status()).isEqualTo(StudyPackStatus.FAILED);
        assertThat(failed.failureReason()).isNotBlank();
        assertThat(questions.findByStudyPackIdOrderByMockExamAscPositionAsc(created.packId())).isEmpty();
        assertThatThrownBy(() -> studyPacks.getStudyPack(created.packId(), userId("study-fail@example.edu")))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    @WithMockStudent(email = "study-intruder@example.edu")
    void anotherStudentCannotReachAStudyPackageOrItsAttempts() throws Exception {
        ensureUser("study-victim@example.edu");
        UUID documentId = uploadDocument("study-victim@example.edu");
        StudyPackCreationResultHolder created = createPackViaService(documentId, "study-victim@example.edu");
        awaitPack(created.packId(), "study-victim@example.edu");

        UUID intruder = userId("study-intruder@example.edu");
        assertThatThrownBy(() -> studyPacks.getStatus(created.packId(), intruder))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> studyPacks.getStudyPack(created.packId(), intruder))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> studyPacks.getExamQuestions(created.packId(), intruder))
                .isInstanceOf(ResourceNotFoundException.class);

        mockMvc.perform(get("/api/study-packs/" + created.packId() + "/status"))
                .andExpect(status().isNotFound());
    }

    private record StudyPackCreationResultHolder(UUID packId) {
    }

    private UUID uploadDocument(String email) throws Exception {
        UUID ownerId = userId(email);
        UUID documentId = documents.upload(new MockMultipartFile("file", "cells.pdf", "application/pdf",
                        TestDocuments.pdf(TestDocuments.PAGE_ONE, TestDocuments.PAGE_TWO)),
                ownerId).documentId();
        Await.until(Duration.ofSeconds(25), () -> documents.getStatus(documentId, ownerId),
                status -> status.status() == ProcessingStatus.READY, "document extraction");
        return documentId;
    }

    private StudyPackCreationResultHolder createPack(UUID documentId, String email, int practice, int mock,
                                                     int cards, int minutes) throws Exception {
        UUID ownerId = userId(email);
        MvcResult result = mockMvc.perform(post("/api/study-packs").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "documentId", documentId.toString(),
                                "practiceQuestionCount", practice,
                                "mockQuestionCount", mock,
                                "flashcardCount", cards,
                                "mockDurationMinutes", minutes))))
                .andExpect(status().isAccepted())
                .andReturn();
        String packId = JsonPath.read(result.getResponse().getContentAsString(), "$.studyPackId");
        return new StudyPackCreationResultHolder(UUID.fromString(packId));
    }

    private StudyPackCreationResultHolder createPackViaService(UUID documentId, String email) {
        UUID ownerId = userId(email);
        UUID packId = studyPacks.create(documentId, ownerId,
                new StudyPreferences("English", "Undergraduate", null, 6, 4, 6, 20)).studyPackId();
        return new StudyPackCreationResultHolder(packId);
    }

    private StudyPackStatusDto awaitPack(UUID packId, String email) {
        UUID ownerId = userId(email);
        return Await.until(Duration.ofSeconds(30), () -> studyPacks.getStatus(packId, ownerId),
                status -> status.status() == StudyPackStatus.READY || status.status() == StudyPackStatus.FAILED,
                "study generation");
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
}

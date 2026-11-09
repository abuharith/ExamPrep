package com.examprep.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.examprep.ai.client.GeminiGateway;
import com.examprep.ai.client.GeminiPrompt;
import com.examprep.ai.retrieval.ChunkRetriever;
import com.examprep.api.document.DocumentChunkDto;
import com.examprep.api.document.DocumentContext;
import com.examprep.api.study.Difficulty;
import com.examprep.api.study.StudyPreferences;
import com.examprep.common.exception.AiServiceException;
import com.examprep.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StudyContentGeneratorTest {

    private static final String OVERVIEW = """
            {"title":"Cell Biology","introduction":"An introduction to cells.","summary":"A summary of cell structure.",
             "learningObjectives":["Describe organelles"],"revisionFocus":["Membranes"],
             "topics":[{"name":"Organelles","explanation":"Structures inside a cell.","keyConcepts":["Nucleus"],
             "sourceReferences":["p1","p99"]}]}
            """;

    private static final String NOTES = """
            {"sections":[{"heading":"Cell theory","body":"All living things are made of cells.","sourceReferences":["p1"]}]}
            """;

    private static final String GLOSSARY = """
            {"definitions":[{"term":"Organelle","definition":"A specialised cell structure.","sourceReference":"p2"}],
             "keyFacts":[{"label":"Surface area","detail":"Area increases with the square of size.",
             "sourceReference":"p2","formula":false}]}
            """;

    private static final String FLASHCARDS = """
            {"flashcards":[{"front":"What is a cell?","back":"The basic unit of life.","sourceReference":"p1"},
             {"front":"What is ATP?","back":"The energy currency of the cell.","sourceReference":"p2"}]}
            """;

    private static final String QUESTIONS = """
            {"practice":[{"type":"MULTIPLE_CHOICE","prompt":"Which organelle makes ATP?",
             "options":["Nucleus","Mitochondrion"],"correctAnswer":"Mitochondrion","explanation":"It performs respiration.",
             "difficulty":"EASY","sourceReference":"p2"},
             {"type":"SHORT_ANSWER","prompt":"Define osmosis.","options":[],"correctAnswer":"Water movement across a membrane.",
             "explanation":"Water moves down its gradient.","difficulty":"MEDIUM","sourceReference":"p1"}],
             "mock":[{"type":"TRUE_FALSE","prompt":"Cells contain DNA.","options":["True","False"],"correctAnswer":"true",
             "explanation":"DNA is in the nucleus.","difficulty":"EASY","sourceReference":"p1"}]}
            """;

    private static AppProperties properties() {
        return new AppProperties(
                new AppProperties.Storage("/tmp/examprep-test"),
                new AppProperties.Upload(25),
                new AppProperties.Async(1, 1, 1),
                new AppProperties.Ai("test-key", "gemini-3.8-flash", 30, 1, 1, 4096),
                new AppProperties.Generation(60000, 60000));
    }

    private static DocumentContext context() {
        return new DocumentContext(UUID.randomUUID(), "lecture.pdf", "lecture.pdf", List.of(
                new DocumentChunkDto("p1", "Page 1", "Cells are the basic unit of life. DNA is stored in the nucleus."),
                new DocumentChunkDto("p2", "Page 2", "Mitochondria produce ATP. Organelles are specialised structures.")));
    }

    @Test
    void generatesValidatedContentAndDropsFabricatedCitations() {
        Deque<String> responses = new ArrayDeque<>(List.of(OVERVIEW, NOTES, GLOSSARY, FLASHCARDS, QUESTIONS));
        StudyContentGenerator generator = new StudyContentGenerator(
                new QueueGateway(responses), new ObjectMapper(), new ChunkRetriever(), properties());

        GeneratedStudyContent content = generator.generate(context(), StudyPreferences.defaults(),
                ProgressListener.NOOP);

        assertThat(content.overview().title()).isEqualTo("Cell Biology");
        assertThat(content.topics()).hasSize(1);
        // p99 does not exist in the document, so it must not become a citation.
        assertThat(content.topics().get(0).sourceReferences()).containsExactly("Page 1");
        assertThat(content.revisionNotes()).hasSize(1);
        assertThat(content.definitions()).hasSize(1);
        assertThat(content.keyFacts()).hasSize(1);
        assertThat(content.flashcards()).hasSize(2);
        assertThat(content.practiceQuestions()).hasSize(2);
        assertThat(content.practiceQuestions().get(0).correctAnswer()).isEqualTo("Mitochondrion");
        assertThat(content.practiceQuestions().get(1).type().name()).isEqualTo("SHORT_ANSWER");
        assertThat(content.mockQuestions()).hasSize(1);
        assertThat(content.mockQuestions().get(0).correctAnswer()).isEqualTo("True");
        assertThat(content.mockQuestions().get(0).difficulty()).isEqualTo(Difficulty.EASY);
        assertThat(content.mockQuestions().get(0).sourceReference()).isEqualTo("Page 1");
        assertThat(responses).isEmpty();
    }

    @Test
    void rejectsUnparseableJsonAfterOneRetry() {
        StudyContentGenerator generator = new StudyContentGenerator(
                new QueueGateway(new ArrayDeque<>(List.of("not json", "still not json"))),
                new ObjectMapper(), new ChunkRetriever(), properties());

        assertThatThrownBy(() -> generator.generate(context(), StudyPreferences.defaults(), ProgressListener.NOOP))
                .isInstanceOf(AiServiceException.class)
                .hasMessageContaining("could not be read");
    }

    @Test
    void rejectsQuestionSetsWhereTheAnswerIsNotAnOption() {
        String invalidQuestion = """
                {"practice":[{"type":"MULTIPLE_CHOICE","prompt":"Which organelle makes ATP?",
                 "options":["Nucleus","Ribosome"],"correctAnswer":"Mitochondrion","explanation":"...",
                 "difficulty":"EASY","sourceReference":"p2"}],
                 "mock":[]}
                """;
        StudyContentGenerator generator = new StudyContentGenerator(
                new QueueGateway(new ArrayDeque<>(List.of(OVERVIEW, NOTES, GLOSSARY, FLASHCARDS, invalidQuestion))),
                new ObjectMapper(), new ChunkRetriever(), properties());

        assertThatThrownBy(() -> generator.generate(context(), StudyPreferences.defaults(), ProgressListener.NOOP))
                .isInstanceOf(AiServiceException.class)
                .hasMessageContaining("questions");
    }

    private static final class QueueGateway implements GeminiGateway {

        private final Deque<String> responses;

        private QueueGateway(Deque<String> responses) {
            this.responses = responses;
        }

        @Override
        public String generateJson(GeminiPrompt prompt) {
            String response = responses.poll();
            if (response == null) {
                throw new AssertionError("The generator made more Gemini calls than expected");
            }
            return response;
        }

        @Override
        public boolean isConfigured() {
            return true;
        }
    }
}

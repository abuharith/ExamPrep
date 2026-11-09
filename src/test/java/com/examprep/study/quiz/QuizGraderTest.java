package com.examprep.study.quiz;

import static org.assertj.core.api.Assertions.assertThat;

import com.examprep.api.study.Difficulty;
import com.examprep.api.study.QuestionType;
import com.examprep.study.domain.Question;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuizGraderTest {

    private static Question question(QuestionType type, String options, String answer) {
        return new Question(UUID.randomUUID(), UUID.randomUUID(), type, "Prompt", options, answer,
                "Because the material says so.", Difficulty.MEDIUM, "Page 2", true, 0);
    }

    @Test
    void scoresMultipleChoiceCaseInsensitively() {
        Question q = question(QuestionType.MULTIPLE_CHOICE, "[\"Osmosis\",\"Diffusion\"]", "Osmosis");

        QuizGrader.Grade grade = QuizGrader.grade(List.of(q), Map.of(q.getId(), " osmosis "));

        assertThat(grade.score()).isEqualTo(1);
        assertThat(grade.totalQuestions()).isEqualTo(1);
        assertThat(grade.outcomes().get(0).correct()).isTrue();
    }

    @Test
    void acceptsOptionLettersAndNumbers() {
        Question q = question(QuestionType.MULTIPLE_CHOICE, "[\"First\",\"Second\",\"Third\"]", "Third");

        assertThat(QuizGrader.grade(List.of(q), Map.of(q.getId(), "C")).score()).isEqualTo(1);
        assertThat(QuizGrader.grade(List.of(q), Map.of(q.getId(), "3")).score()).isEqualTo(1);
        assertThat(QuizGrader.grade(List.of(q), Map.of(q.getId(), "A")).score()).isZero();
    }

    @Test
    void handlesTrueFalseVariants() {
        Question q = question(QuestionType.TRUE_FALSE, "[\"True\",\"False\"]", "True");

        assertThat(QuizGrader.grade(List.of(q), Map.of(q.getId(), "true")).score()).isEqualTo(1);
        assertThat(QuizGrader.grade(List.of(q), Map.of(q.getId(), "False")).score()).isZero();
    }

    @Test
    void doesNotAwardAutomaticMarksForShortAnswers() {
        Question q = question(QuestionType.SHORT_ANSWER, null, "A model answer");

        QuizGrader.Grade grade = QuizGrader.grade(List.of(q), Map.of(q.getId(), "A model answer"));

        assertThat(grade.score()).isZero();
        assertThat(grade.totalQuestions()).isZero();
        assertThat(grade.outcomes()).hasSize(1);
        assertThat(grade.outcomes().get(0).autoGraded()).isFalse();
    }

    @Test
    void ignoresUnansweredAndEmptyAnswers() {
        Question q = question(QuestionType.MULTIPLE_CHOICE, "[\"A\",\"B\"]", "A");

        assertThat(QuizGrader.grade(List.of(q), Map.of()).outcomes()).isEmpty();
        assertThat(QuizGrader.grade(List.of(q), Map.of(q.getId(), "  ")).score()).isZero();
    }

    @Test
    void parsesOptionJsonSafely() throws Exception {
        String options = new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(List.of("Energy, stored", "Energy \"released\""));
        Question q = question(QuestionType.MULTIPLE_CHOICE, options, "Energy, stored");

        assertThat(QuizGrader.optionList(q)).containsExactly("Energy, stored", "Energy \"released\"");
    }
}

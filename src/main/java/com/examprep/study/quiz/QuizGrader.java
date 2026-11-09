package com.examprep.study.quiz;

import com.examprep.api.study.QuestionType;
import com.examprep.study.domain.Question;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side grading. Multiple-choice and true/false answers are scored automatically;
 * short-answer responses are stored and reviewed but are never awarded automatic marks.
 */
public final class QuizGrader {

    private static final ObjectMapper JSON = new ObjectMapper();

    private QuizGrader() {
    }

    public record Outcome(Question question, String submittedAnswer, boolean correct, boolean autoGraded) {
    }

    public record Grade(List<Outcome> outcomes, int score, int totalQuestions) {
    }

    public static Grade grade(List<Question> questions, Map<UUID, String> answers) {
        List<Outcome> outcomes = new ArrayList<>();
        int score = 0;
        int total = 0;
        for (Question question : questions) {
            if (!answers.containsKey(question.getId())) {
                continue;
            }
            String submitted = answers.get(question.getId());
            boolean autoGraded = question.getQuestionType() != QuestionType.SHORT_ANSWER;
            boolean correct = autoGraded && isCorrect(question, submitted);
            if (autoGraded) {
                total++;
                if (correct) {
                    score++;
                }
            }
            outcomes.add(new Outcome(question, submitted, correct, autoGraded));
        }
        return new Grade(outcomes, score, total);
    }

    static boolean isCorrect(Question question, String submitted) {
        if (submitted == null || submitted.isBlank()) {
            return false;
        }
        String expected = question.getCorrectAnswer() == null ? "" : question.getCorrectAnswer().trim();
        String given = submitted.trim();
        if (question.getQuestionType() == QuestionType.TRUE_FALSE) {
            return normaliseBoolean(expected).equals(normaliseBoolean(given));
        }
        if (expected.equalsIgnoreCase(given)) {
            return true;
        }
        List<String> options = optionList(question);
        if (given.length() == 1) {
            int index = Character.toUpperCase(given.charAt(0)) - 'A';
            if (index >= 0 && index < options.size()) {
                return options.get(index).trim().equalsIgnoreCase(expected);
            }
        }
        try {
            int index = Integer.parseInt(given) - 1;
            return index >= 0 && index < options.size() && options.get(index).trim().equalsIgnoreCase(expected);
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static String normaliseBoolean(String value) {
        String normalised = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalised) {
            case "true", "t", "yes", "y", "correct", "1" -> "true";
            case "false", "f", "no", "n", "incorrect", "0" -> "false";
            default -> normalised;
        };
    }

    public static List<String> optionList(Question question) {
        if (question.getOptions() == null || question.getOptions().isBlank()) {
            return List.of();
        }
        String raw = question.getOptions().trim();
        if (raw.startsWith("[")) {
            try {
                // Options are stored as JSON, so escaped quotes and commas must survive intact.
                return JSON.readValue(raw, new TypeReference<List<String>>() {
                }).stream().map(String::trim).toList();
            } catch (Exception ex) {
                return List.of();
            }
        }
        return List.of(raw);
    }
}

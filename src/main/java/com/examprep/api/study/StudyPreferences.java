package com.examprep.api.study;

import java.time.LocalDate;

/**
 * Study generation options chosen by the student.
 *
 * @param language             study material language, e.g. "English"
 * @param academicLevel        e.g. "Undergraduate"
 * @param examDate             optional target date, never invented by the model
 * @param practiceQuestionCount number of practice questions to generate
 * @param mockQuestionCount    number of mock examination questions to generate
 * @param flashcardCount       number of flashcards to generate
 * @param mockDurationMinutes  examination timer in minutes
 */
public record StudyPreferences(
        String language,
        String academicLevel,
        LocalDate examDate,
        int practiceQuestionCount,
        int mockQuestionCount,
        int flashcardCount,
        int mockDurationMinutes) {

    public static StudyPreferences defaults() {
        return new StudyPreferences("English", "Undergraduate", null, 8, 10, 12, 30);
    }
}

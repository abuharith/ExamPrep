package com.examprep.study.domain;

import com.examprep.api.study.Difficulty;
import com.examprep.api.study.QuestionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "questions")
public class Question {

    @Id
    private UUID id;

    @Column(name = "study_pack_id", nullable = false)
    private UUID studyPackId;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, length = 24)
    private QuestionType questionType;

    @Column(nullable = false)
    private String prompt;

    private String options;

    @Column(name = "correct_answer", nullable = false)
    private String correctAnswer;

    @Column(nullable = false)
    private String explanation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Difficulty difficulty;

    @Column(name = "source_reference", length = 160)
    private String sourceReference;

    @Column(name = "mock_exam", nullable = false)
    private boolean mockExam;

    @Column(nullable = false)
    private int position;

    protected Question() {
    }

    public Question(UUID id, UUID studyPackId, QuestionType questionType, String prompt, String optionsJson,
                    String correctAnswer, String explanation, Difficulty difficulty, String sourceReference,
                    boolean mockExam, int position) {
        this.id = id;
        this.studyPackId = studyPackId;
        this.questionType = questionType;
        this.prompt = prompt;
        this.options = optionsJson;
        this.correctAnswer = correctAnswer;
        this.explanation = explanation;
        this.difficulty = difficulty;
        this.sourceReference = sourceReference;
        this.mockExam = mockExam;
        this.position = position;
    }

    public UUID getId() {
        return id;
    }

    public UUID getStudyPackId() {
        return studyPackId;
    }

    public QuestionType getQuestionType() {
        return questionType;
    }

    public String getPrompt() {
        return prompt;
    }

    public String getOptions() {
        return options;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public String getExplanation() {
        return explanation;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public String getSourceReference() {
        return sourceReference;
    }

    public boolean isMockExam() {
        return mockExam;
    }

    public int getPosition() {
        return position;
    }
}

package com.examprep.study.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "flashcards")
public class Flashcard {

    @Id
    private UUID id;

    @Column(name = "study_pack_id", nullable = false)
    private UUID studyPackId;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private String front;

    @Column(nullable = false)
    private String back;

    @Column(name = "source_reference", length = 160)
    private String sourceReference;

    protected Flashcard() {
    }

    public Flashcard(UUID id, UUID studyPackId, int position, String front, String back, String sourceReference) {
        this.id = id;
        this.studyPackId = studyPackId;
        this.position = position;
        this.front = front;
        this.back = back;
        this.sourceReference = sourceReference;
    }

    public UUID getId() {
        return id;
    }

    public int getPosition() {
        return position;
    }

    public String getFront() {
        return front;
    }

    public String getBack() {
        return back;
    }

    public String getSourceReference() {
        return sourceReference;
    }
}

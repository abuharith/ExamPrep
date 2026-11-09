-- Generated study packages, their learning resources and study history.
CREATE TABLE study_packs (
    id                    UUID         PRIMARY KEY,
    owner_id              UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    document_id           UUID         NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    status                VARCHAR(24)  NOT NULL,
    progress_percent      INTEGER      NOT NULL DEFAULT 0,
    progress_message      VARCHAR(300),
    failure_reason        VARCHAR(1000),
    title                 VARCHAR(300) NOT NULL,
    document_filename     VARCHAR(255) NOT NULL,
    summary               TEXT,
    overview              TEXT,
    topics                TEXT,
    revision_notes        TEXT,
    definitions           TEXT,
    key_facts             TEXT,
    language              VARCHAR(32)  NOT NULL,
    academic_level        VARCHAR(40)  NOT NULL,
    exam_date             DATE,
    mock_duration_minutes INTEGER      NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL,
    completed_at          TIMESTAMPTZ,
    CONSTRAINT ck_study_packs_progress CHECK (progress_percent BETWEEN 0 AND 100)
);

CREATE INDEX ix_study_packs_owner_created ON study_packs (owner_id, created_at DESC);
CREATE INDEX ix_study_packs_document ON study_packs (document_id);

CREATE TABLE flashcards (
    id               UUID        PRIMARY KEY,
    study_pack_id    UUID        NOT NULL REFERENCES study_packs (id) ON DELETE CASCADE,
    position         INTEGER     NOT NULL,
    front            TEXT        NOT NULL,
    back             TEXT        NOT NULL,
    source_reference VARCHAR(160)
);

CREATE INDEX ix_flashcards_pack ON flashcards (study_pack_id, position);

CREATE TABLE questions (
    id               UUID        PRIMARY KEY,
    study_pack_id    UUID        NOT NULL REFERENCES study_packs (id) ON DELETE CASCADE,
    question_type    VARCHAR(24) NOT NULL,
    prompt           TEXT        NOT NULL,
    options          TEXT,
    correct_answer   TEXT        NOT NULL,
    explanation      TEXT        NOT NULL,
    difficulty       VARCHAR(16) NOT NULL,
    source_reference VARCHAR(160),
    mock_exam        BOOLEAN     NOT NULL DEFAULT FALSE,
    position         INTEGER     NOT NULL
);

CREATE INDEX ix_questions_pack ON questions (study_pack_id, mock_exam, position);

CREATE TABLE quiz_attempts (
    id               UUID         PRIMARY KEY,
    owner_id         UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    study_pack_id    UUID         NOT NULL REFERENCES study_packs (id) ON DELETE CASCADE,
    mode             VARCHAR(16)  NOT NULL,
    score            INTEGER      NOT NULL,
    total_questions  INTEGER      NOT NULL,
    duration_seconds INTEGER      NOT NULL,
    submitted_at     TIMESTAMPTZ  NOT NULL
);

CREATE INDEX ix_quiz_attempts_owner ON quiz_attempts (owner_id, submitted_at DESC);

CREATE TABLE quiz_answers (
    id               UUID    PRIMARY KEY,
    attempt_id       UUID    NOT NULL REFERENCES quiz_attempts (id) ON DELETE CASCADE,
    question_id      UUID    NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    submitted_answer TEXT,
    correct          BOOLEAN NOT NULL,
    awarded_points   INTEGER NOT NULL
);

CREATE INDEX ix_quiz_answers_attempt ON quiz_answers (attempt_id);

CREATE TABLE tutor_messages (
    id                UUID        PRIMARY KEY,
    owner_id          UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    study_pack_id     UUID        NOT NULL REFERENCES study_packs (id) ON DELETE CASCADE,
    role              VARCHAR(16) NOT NULL,
    content           TEXT        NOT NULL,
    source_references TEXT,
    created_at        TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_tutor_messages_pack ON tutor_messages (study_pack_id, created_at);

CREATE TABLE flashcard_reviews (
    id            UUID        PRIMARY KEY,
    owner_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    study_pack_id UUID        NOT NULL REFERENCES study_packs (id) ON DELETE CASCADE,
    cards_reviewed INTEGER    NOT NULL,
    reviewed_at   TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_flashcard_reviews_owner ON flashcard_reviews (owner_id, reviewed_at DESC);

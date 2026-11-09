-- Uploaded study material and its extracted, citable chunks.
CREATE TABLE documents (
    id                   UUID         PRIMARY KEY,
    owner_id             UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    original_filename    VARCHAR(255) NOT NULL,
    detected_content_type VARCHAR(160) NOT NULL,
    extension            VARCHAR(16)  NOT NULL,
    size_bytes           BIGINT       NOT NULL,
    storage_key          VARCHAR(160) NOT NULL,
    status               VARCHAR(24)  NOT NULL,
    failure_reason       VARCHAR(1000),
    page_count           INTEGER,
    char_count           INTEGER,
    chunk_count          INTEGER      NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ  NOT NULL,
    updated_at           TIMESTAMPTZ  NOT NULL,
    processed_at         TIMESTAMPTZ,
    CONSTRAINT ck_documents_size CHECK (size_bytes >= 0)
);

CREATE INDEX ix_documents_owner_created ON documents (owner_id, created_at DESC);

CREATE TABLE document_chunks (
    id             UUID         PRIMARY KEY,
    document_id    UUID         NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    chunk_index    INTEGER      NOT NULL,
    reference_id   VARCHAR(40)  NOT NULL,
    location_label VARCHAR(160) NOT NULL,
    content        TEXT         NOT NULL,
    char_count     INTEGER      NOT NULL,
    CONSTRAINT ux_document_chunks_order UNIQUE (document_id, chunk_index)
);

CREATE INDEX ix_document_chunks_document ON document_chunks (document_id, chunk_index);

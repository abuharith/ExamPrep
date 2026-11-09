-- ExamPrep foundation: student accounts.
CREATE TABLE users (
    id            UUID         PRIMARY KEY,
    full_name     VARCHAR(120) NOT NULL,
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL
);

-- Emails are compared case-insensitively; registration rejects duplicates.
CREATE UNIQUE INDEX ux_users_email_lower ON users (LOWER(email));

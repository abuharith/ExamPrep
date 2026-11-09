# ExamPrep

ExamPrep is a browser-based examination preparation platform. A student uploads their own
lecture notes, handouts or textbooks, ExamPrep extracts the material and Google Gemini turns it
into a structured study package: an overview, topics, revision notes, a glossary, key facts and
formulas, flashcards, practice questions, a timed mock examination and a tutor that answers from
the uploaded document.

The application is a modular Spring Boot monolith that serves the Thymeleaf interface directly.
There is no separate JavaScript frontend, no microservice fleet and no vector database.

## Technology

| Concern            | Choice                                             |
| ------------------ | -------------------------------------------------- |
| Language           | Java 21                                            |
| Framework          | Spring Boot 3.5.16                                 |
| Interface          | Thymeleaf, HTML5, CSS, vanilla JavaScript          |
| Database           | PostgreSQL                                         |
| Persistence        | Spring Data JPA / Hibernate (validate after Flyway)|
| Migrations         | Flyway                                             |
| Authentication     | Spring Security, BCrypt, server-side sessions      |
| AI                 | `com.google.genai:google-genai` 1.79.0             |
| Document parsing   | Apache Tika 3.3.3, PDFBox 3.0.8, Apache POI 5.4.1  |
| Build / tests      | Maven, JUnit 5, Mockito, MockMvc, embedded PostgreSQL |
| Deployment         | Docker, Docker Compose                             |

`google-genai` 1.80.0 requested in the brief does not exist on Maven Central; 1.79.0 is the newest
published 1.x release and is what the project uses. The model is configuration, not code:
`GEMINI_MODEL` defaults to `gemini-3.8-flash`.

## Feature overview

**Accounts and isolation.** Registration, email/password login, BCrypt hashing, CSRF-protected
forms and AJAX calls, logout, and protected pages. Every document, study pack, attempt and tutor
conversation is looked up by `(id, ownerId)`; another student's identifiers return `404`.

**Upload and extraction.** PDF, DOCX, DOC, PPTX, PPT, TXT, Markdown, RTF, ODT and ODP up to 25 MB
(configurable). The extension is checked against Tika's detection of the real bytes. Files are
stored under generated names outside the web root and are never served by URL. Extraction runs
asynchronously with `UPLOADED → PROCESSING → READY/FAILED` states. PDF extraction keeps page
boundaries, presentations keep slide boundaries, DOCX keeps heading sections, and every chunk has
a stable reference (`p12`, `s3`, `d7`). Scanned PDFs with no selectable text are detected and
explained instead of producing empty study material.

**Study package.** Gemini returns structured JSON which is deserialised into typed DTOs and
validated. Citations are returned as chunk IDs and mapped to real locations; references that do
not exist in the document are discarded rather than displayed. Large documents are summarised
section by section before the package is generated, so a whole textbook is never sent in one
request. Generation is asynchronous (`QUEUED → PROCESSING → READY/FAILED`) with progress polling.
If the API key is absent or Gemini fails, generation fails honestly with a readable reason — no
canned content is substituted.

**Learning experience.** Study pages cover the overview, topic map, revision notes, glossary,
key facts and formulas, an interactive flashcard deck, practice questions with server-side answer
reveal, and a timed mock examination. Only multiple-choice and true/false questions appear in the
mock so it can be scored automatically; short-answer questions live in practice and are revealed
on request. Correct answers are never sent to the browser before submission. Results show score,
time, per-question review and explanations, and attempts are stored for the progress view.

**Tutor.** A conversation per study pack. The question is used to retrieve the most relevant
document chunks (BM25-style lexical ranking, no embeddings), those chunks are supplied as context,
and the answer is stored with validated source references. The tutor is instructed to say when the
material does not contain the answer. Uploaded content is always treated as untrusted study
material, never as instructions.

## Quick start with Docker

1. Copy the environment template and set a database password and, optionally, a Gemini key:

   ```bash
   cp .env.example .env
   # edit .env
   ```

2. Build and start PostgreSQL and the application:

   ```bash
   docker compose up --build
   ```

3. Open <http://localhost:8080>, register an account and upload a document.

Flyway applies the schema on startup. PostgreSQL data and uploaded files live in the named
volumes `examprep-db` and `examprep-uploads`, so `docker compose down` keeps them. The container
runs as an unprivileged user (uid 10001) and contains no secrets.

## Local development without Docker

Requirements: Java 21, a PostgreSQL 14+ server, and network access for the Maven wrapper's first
run.

```bash
createdb examprep
createuser examprep --pwprompt

export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/examprep
export SPRING_DATASOURCE_USERNAME=examprep
export SPRING_DATASOURCE_PASSWORD='your-password'
export APP_STORAGE_LOCATION=./data/uploads
export GOOGLE_API_KEY=''            # optional for startup

./mvnw spring-boot:run
```

Open <http://localhost:8080>. The application starts without a Gemini key; any AI action then
returns a clear "Gemini is not configured" error. Uploads work either way.

## Configuration

All configuration is environment based. See `.env.example`.

| Variable | Default | Purpose |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/examprep` | JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `examprep` | Database user |
| `SPRING_DATASOURCE_PASSWORD` | `examprep` | Database password |
| `GOOGLE_API_KEY` | empty | Gemini Developer API key; never logged or exposed |
| `GEMINI_MODEL` | `gemini-3.8-flash` | Any Gemini model supporting JSON output |
| `GEMINI_TIMEOUT_SECONDS` | `90` | Per-request timeout |
| `GEMINI_MAX_ATTEMPTS` | `3` | Attempts for transient failures (408/429/5xx) |
| `APP_STORAGE_LOCATION` | `./data/uploads` | Private upload directory |
| `APP_MAX_UPLOAD_SIZE_MB` | `25` | Upload limit (Spring multipart limit is derived from it) |
| `APP_ASYNC_CORE_POOL_SIZE` / `APP_ASYNC_MAX_POOL_SIZE` | `2` / `4` | Extraction and generation workers |
| `APP_GENERATION_MAX_SOURCE_CHARS` | `60000` | Source characters per generation call |
| `APP_GENERATION_SUMMARISE_THRESHOLD_CHARS` | `60000` | Above this the document is summarised first |
| `SERVER_PORT` | `8080` | HTTP port |
| `SESSION_COOKIE_SECURE` | `false` | Set `true` when serving over HTTPS |

### Gemini key

Create a key in [Google AI Studio](https://aistudio.google.com/apikey) and put it in `.env`
(Docker) or your shell environment (local). The key is read only by the server-side SDK client.
It is never written to templates, JavaScript, API responses or logs — the SDK's own logging is
pinned to `WARN` for that reason.

## Testing

```bash
./mvnw clean verify
```

Unit tests cover chunking, upload validation, quiz scoring and the AI generation pipeline with
mocked Gemini responses. Integration tests start a real PostgreSQL instance in-process (Zonky
embedded PostgreSQL), run the real Flyway migrations, and exercise registration, login, upload,
asynchronous extraction, generation, scoring, tutor persistence and ownership isolation through
MockMvc. No API key is needed and no paid calls are made.

More detail, including the manual Gemini smoke test, is in [docs/testing.md](docs/testing.md).

## Project structure

```
src/main/java/com/examprep
├── api/          shared contracts: DTOs and service interfaces (module boundaries)
├── config/       application properties, async executor, Jackson
├── security/     Spring Security configuration, user details, current user
├── user/         accounts, registration, repositories
├── document/     upload validation, storage, parsers, chunking, async ingestion
├── ai/           Gemini gateway, prompts, retrieval, generation, tutor
├── study/        study packs, quizzes, flashcards, attempts, tutor persistence
├── web/          Thymeleaf controllers, JSON APIs, exception handling
└── common/       exceptions and shared helpers
src/main/resources
├── db/migration  Flyway migrations
├── templates     Thymeleaf pages and layout fragments
└── static        CSS and vanilla JavaScript
```

Architecture and design decisions: [docs/architecture.md](docs/architecture.md).
Cross-module contracts: [docs/agent-contracts.md](docs/agent-contracts.md).

## Security notes

- Passwords are BCrypt hashed; sessions are HTTP-only, SameSite=Lax, and secure when configured.
- CSRF protection is on for forms and for `fetch` calls, which read tokens from meta tags.
- Uploads are validated by extension, size and real detected content; storage names are generated
  UUIDs; files are outside the static resource path and cannot be fetched by URL.
- Responses carry a Content-Security-Policy, Referrer-Policy and Permissions-Policy. Sign-in is
  limited to 10 attempts per client address per 5 minutes (in-memory, single node). Behind a
  reverse proxy, set `FORWARD_HEADERS_STRATEGY=framework` so the real client address is used.
- Ownership is enforced in the service layer, not only in controllers.
- Both SQL (parameterised JPA) and HTML (Thymeleaf escaping) injection paths are covered.
- Gemini output is validated; unknown citations are dropped. Uploaded text is delimited and
  explicitly labelled as untrusted study material in every prompt.
- The privacy page explains that extracted content is sent to Google's Gemini API. Deleting a
  document cascades to its chunks, study packs, flashcards, questions, attempts and messages.

## Known limitations

- **No OCR.** Scanned PDFs are detected and rejected with a clear message.
- **No vector search.** Tutor retrieval is lexical (BM25-style) and intentionally simple.
- **Gemini requires network access and a funded key.** Automated tests mock the gateway; a real
  call must be verified manually as described in `docs/testing.md`.
- **Docker files are provided but were not executed in the build environment used to develop this
  project** (no Docker daemon available). The image definition and Compose stack follow the same
  Maven build and environment variables that were verified locally, but the container itself has
  not been smoke-tested here.
- **HTML/PDF exports and spaced-repetition scheduling** are outside the initial scope.

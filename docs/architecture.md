# ExamPrep architecture

## Shape of the system

ExamPrep is one Spring Boot process with a modular package structure. Each module owns its
domain, repositories and services; other modules see only the interfaces and records in
`com.examprep.api`. That boundary is what would let a module be extracted later, and it keeps the
web layer from reaching into persistence.

```
Browser (Thymeleaf + progressive JavaScript)
        │  forms, fetch JSON, CSRF tokens
        ▼
com.examprep.web ── controllers, JSON APIs, exception advice
        │
        ▼
com.examprep.api ── DocumentApplicationService, StudyPackApplicationService, DTOs
        │
   ┌────┴───────────────┬────────────────────┐
   ▼                    ▼                    ▼
document module      study module          ai module
upload/validate      packs, quizzes,       Gemini gateway,
storage, parsers     attempts, flashcards, prompts, retrieval,
chunking, async      tutor persistence     generation, tutor
   │                    │                    │
   └────────────► PostgreSQL (Flyway) ◄──────┘
```

Key rules:

- Controllers never touch repositories or the Gemini SDK.
- The document module has no dependency on the AI module; it produces `DocumentContext` chunks.
- The study module depends on the document contract for source context and on the AI contract for
  generation.
- All owner-scoped service methods take `ownerId` and filter queries by it. A foreign id is
  indistinguishable from a missing id (`ResourceNotFoundException` → 404).

## Processing pipelines

### Document ingestion

```
POST /api/documents
  → UploadValidator: extension allow-list, size, non-empty, Tika detection vs extension
  → LocalDocumentStorage: write to <storage>/.incoming, then atomic move to <uuid>.<ext>
  → documents row (UPLOADED)
  → return 202 { documentId } immediately
  → DocumentIngestionService (bounded executor):
        PROCESSING → parser → chunks → READY
                              └──────► FAILED (+ safe, human-readable reason)
```

Parsers and the locations they can honestly produce:

| Format | Parser | Reference | Location |
| --- | --- | --- | --- |
| PDF | PDFBox `PDFTextStripper` per page | `p<n>` | `Page n` |
| PPTX / PPT | POI XSLF / HSLF per slide | `s<n>` | `Slide n` |
| DOCX | POI XWPF, split on heading styles | `s<n>` | `Section: <heading>` |
| DOC | POI HWPF paragraphs | `d<n>` | `Section n` |
| TXT / MD / RTF / ODT / ODP | Tika, then heading-aware sectioning | `d<n>` | `Section: <heading>` or `Section n` |

Chunks are limited to roughly 3,500 characters on paragraph boundaries and split with a
`-part` suffix when needed. If a format cannot provide reliable locations, the parser emits a
general section reference instead of inventing page numbers.

Recovery: `DocumentRecovery` runs at startup and re-queues documents left in `PROCESSING`, because
the executor is in-memory. Temporary `.incoming` files are removed on startup.

### Study generation

```
POST /api/study-packs (document must be READY)
  → study pack row (QUEUED)
  → executor:
        PROCESSING (10%)
        → optional hierarchical digest for large documents (20%)
        → overview + topics            (35%)
        → revision notes               (50%)
        → glossary + key facts         (65%)
        → flashcards                   (80%)
        → practice + mock questions    (90%)
        → persist all resources, validate counts → READY (100%)
        └─ any failure → FAILED with a reason, no partial package exposed
```

Each Gemini call requests `application/json`, is deserialised by Jackson into a typed record, and
is validated (non-empty fields, option count, correct answer present among options, counts capped
at the student's request). Malformed output is retried once with a stricter reminder; after that
the pack fails honestly.

Citations: the prompt supplies chunks as `[ref] location: text`. The model returns refs; the
generator maps refs to locations and drops anything not in the document's chunk list.

### Mock examination

Only `MULTIPLE_CHOICE` and `TRUE_FALSE` questions are stored as mock questions; short-answer
questions are stored as practice. `QuestionDto` (served to the browser) contains no answer or
explanation. `POST /quiz` grades server-side, stores `QuizAttempt` + `QuizAnswer` rows and returns
the score plus per-question review. Practice answers are revealed only through
`POST /questions/{id}/reveal`, which rejects mock questions.

### Tutor

1. Load the study pack (owner-checked) and its recent messages.
2. `ChunkRetriever` ranks the pack's document chunks against the question using IDF-weighted term
   overlap, with a small heading bonus; the top chunks (bounded characters) become context.
3. Prompt: trusted instructions + delimited document excerpts + conversation history + question.
4. Validate the JSON answer, map citations against the supplied chunks, persist both messages.
5. If the model reports insufficient information, the UI shows that instead of an invented answer.

## Database

Flyway owns the schema; Hibernate is `validate` only. Migrations:

- `V1__users.sql` — `users` (UUID pk, unique lowercase email, BCrypt hash, timestamps).
- `V2__documents.sql` — `documents` + `document_chunks`, cascade delete, indexes on owner and
  document ordering.
- `V3__study_and_ai.sql` — `study_packs`, `flashcards`, `questions`, `quiz_attempts`,
  `quiz_answers`, `tutor_messages`, `flashcard_reviews`, with FKs and indexes.

Generated package content is stored as JSON text columns on `study_packs` (overview, topics,
revision notes, definitions, key facts). Flashcards, questions, attempts and tutor messages are
relational because they are queried, scored and ownership-checked individually.

## Concurrency and failure handling

- Uploads and generation run on a bounded `ThreadPoolTaskExecutor`; requests return `202` and the
  browser polls status endpoints.
- Short write transactions (`TransactionTemplate`) wrap persistence; Gemini calls happen outside
  transactions so a slow API never holds a database connection.
- Transient Gemini failures (408, 429, 500, 502, 503, 504) retry with backoff; other 4xx errors
  fail immediately with a safe message.
- The API key is read once at startup. If absent, every AI path raises `AiNotConfiguredException`
  with an actionable message; no fake content is produced.

## Security model

- Spring Security form login at `/login`, session fixation protection, BCrypt, CSRF for forms and
  `fetch` (token in `<meta>` tags).
- Uploads: extension allow-list, size limit enforced by Spring and the validator, Tika content
  detection must agree with the extension, generated storage keys, no static serving.
- Ownership checks in services; list queries always filter by owner.
- AI prompts label document text as untrusted and instruct the model to ignore instructions inside
  it. Output is parsed, never executed; citations are validated.
- Logging excludes document text, tutor conversations and the API key; the Gen AI SDK logger is
  set to `WARN`.

## Deliberate decisions

- **SDK 1.79.0** instead of the requested 1.80.0, which does not exist on Maven Central.
- **Structured JSON via `responseMimeType`** plus typed DTO validation rather than exhaustive
  response schemas, which the SDK/model combination does not reliably enforce for nested arrays.
- **Lexical retrieval** instead of embeddings, as required.
- **Mock exams are objective-only** so automatic scoring is honest.
- **No OCR**, as required; scanned files fail with guidance.

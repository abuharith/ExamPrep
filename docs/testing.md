# Testing guide

## Running the suite

```bash
./mvnw clean verify
```

The suite needs Java 21 and network access on its first run (to download the embedded PostgreSQL
binaries and Maven dependencies). It needs **no** Docker daemon, no external PostgreSQL and no
Gemini API key. `pom.xml` clears `GOOGLE_API_KEY` for Surefire, so a developer's real key can
never be used by a test by accident.

To run one class:

```bash
./mvnw -Dtest=DocumentIngestionIntegrationTest test
```

Reports are written to `target/surefire-reports`.

## How the integration tests work

`IntegrationTestBase` starts a real PostgreSQL 14 server in-process with
`io.zonky.test:embedded-postgres` before Spring loads, and registers its JDBC URL through
`@DynamicPropertySource`. That means:

- the production Flyway migrations run for real;
- Hibernate's `ddl-auto=validate` must agree with the migrated schema — a mismatch fails the
  build;
- JPQL, constraints, cascades and JSON columns behave as in production;
- uploads go to a per-run temporary directory, not `./data/uploads`.

Authentication in tests uses `@WithMockStudent`, which inserts a real `users` row and puts a
`SecurityUser` principal in the context, so `CurrentUserService` behaves exactly as in the browser.
The alternative annotation-based mock principal (a plain string) would not exercise that code.

Asynchronous work is awaited with a bounded poll (`Await.until`) against the real status service,
which is the same path the browser polls. Tests never sleep for a fixed time and never assert
success before the state machine reaches it.

Gemini is replaced by `@MockitoBean GeminiGateway` in `StudyFlowIntegrationTest`. The stub routes
on the prompt content and returns realistic structured JSON. Nothing in the suite makes a paid
call.

## What is covered

Unit tests:

| Class | Coverage |
| --- | --- |
| `ChunkBuilderTest` | section preservation, long-section splitting, reference suffixes, whitespace normalisation |
| `UploadValidatorTest` | supported extension, unsupported extension, empty file, oversize, content/extension mismatch, real text detection |
| `QuizGraderTest` | MCQ scoring, letter/number option answers, true/false, short answers excluded from auto-scoring, unanswered questions, JSON option parsing |
| `StudyContentGeneratorTest` | full five-call generation, citation filtering (`p99` dropped), malformed JSON retry then failure, answer-not-in-options rejection |
| `GoogleGenAiGeminiGatewayTest` | missing key raises `AiNotConfiguredException`, API key never appears in configuration `toString` |

Integration tests:

| Class | Coverage |
| --- | --- |
| `AuthFlowTest` | landing page, CSS, registration + BCrypt hash, duplicate email, invalid input, protected redirects, valid/invalid login, logout, personalised dashboard |
| `DocumentIngestionIntegrationTest` | PDF page references, DOCX headings, PPTX slide references, Markdown sections, scanned-PDF detection, corrupted PDF failure, unsupported/mismatched uploads, delete + file removal, cross-student 404 |
| `StudyFlowIntegrationTest` | end-to-end generation with mocked Gemini, persistence of all resources, citations mapped to real pages, study pages render, exam submission/scoring/result page, practice reveal allowed, mock reveal rejected, tutor answer + persistence, failed generation records a reason and no content, cross-student isolation of status/pack/exam |

Fast unit tests use AssertJ and JUnit 5. Integration tests use MockMvc with CSRF tokens, exactly
as the browser sends them.

## Manual Gemini smoke test

Automated tests cannot prove the live API integration. With a funded key available:

1. Set `GOOGLE_API_KEY` and start the app (`docker compose up --build` or `./mvnw spring-boot:run`).
2. Register, upload a text-based PDF of at least a couple of pages.
3. Confirm the document reaches `READY` and page references look correct.
4. Generate a study package. Confirm it reaches `READY`, and that the overview, topics, notes,
   definitions, flashcards, practice questions and mock questions all contain subject-specific
   content (not generic filler).
5. Open the mock examination, submit answers, and check the score and explanations.
6. Ask the tutor a question that is answered in the document, then one that is not. The first
   should cite a real page/section; the second should say the material is insufficient.
7. Confirm no key appears in the page source, browser network responses or server logs.

Record the model used (`GEMINI_MODEL`), the date and the outcome. If the key is unavailable, state
plainly that the live call was not verified — mocked tests are not a substitute.

## Checks that were not possible in the development environment

- **Docker image build / `docker compose up`**: the build machine had no accessible Docker daemon
  and no root privileges. The Dockerfile and Compose file were reviewed but not executed. Verify
  them with `docker compose up --build` before relying on them.
- **Real Gemini request**: no API key was available. All AI paths are covered with mocked
  responses; the manual smoke test above is still required.
- **Browser rendering**: pages are exercised through MockMvc (HTTP status and rendered HTML
  content), which catches template failures, but no real browser layout pass was performed.
  Check the responsive layout at desktop, tablet and mobile widths manually.

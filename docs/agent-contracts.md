# Module contracts

These are the boundaries between the five workstreams. Anything in `com.examprep.api` is shared;
everything else is private to its module. The web layer consumes these interfaces through Spring
injection and never sees entities.

## Service interfaces

### `com.examprep.api.user`

```java
public interface CurrentUserService {
    UUID requireCurrentUserId();          // 401 when unauthenticated
    Optional<UUID> currentUserId();
    String currentDisplayName();
}

public interface UserRegistrationService {
    UserAccountDto register(RegisterUserRequest request);   // InvalidRequestException on duplicate
}
```

### `com.examprep.api.document.DocumentApplicationService`

```java
DocumentUploadResult upload(MultipartFile file, UUID ownerId);
DocumentStatusDto getStatus(UUID documentId, UUID ownerId);
List<DocumentSummaryDto> listDocuments(UUID ownerId);
DocumentContext getContext(UUID documentId, UUID ownerId);
void delete(UUID documentId, UUID ownerId);
```

`upload` validates and stores synchronously, then queues extraction; it returns `202` with the new
id. `getContext` is only valid for `READY` documents and is used by the study and AI modules.

### `com.examprep.api.study.StudyPackApplicationService`

```java
StudyPackCreationResult create(UUID documentId, UUID ownerId, StudyPreferences preferences);
StudyPackStatusDto getStatus(UUID studyPackId, UUID ownerId);
StudyPackDto getStudyPack(UUID studyPackId, UUID ownerId);
List<QuestionDto> getExamQuestions(UUID studyPackId, UUID ownerId);   // no answers
QuizResultDto submitQuiz(UUID studyPackId, UUID ownerId, QuizSubmissionDto submission);
QuizAttemptResultDto getAttemptResult(UUID studyPackId, UUID attemptId, UUID ownerId);
QuestionAnswerDto revealPracticeQuestion(UUID studyPackId, UUID questionId, UUID ownerId);
TutorAnswerDto askTutor(UUID studyPackId, UUID ownerId, TutorQuestionDto question);
List<TutorMessageDto> getTutorConversation(UUID studyPackId, UUID ownerId);
void reviewFlashcard(UUID studyPackId, UUID flashcardId, UUID ownerId, boolean remembered);
List<StudyPackSummaryDto> listStudyPacks(UUID ownerId);
void delete(UUID studyPackId, UUID ownerId);
```

## DTOs

Records, all fields immutable:

- `DocumentUploadResult(UUID documentId, ProcessingStatus status)`
- `DocumentStatusDto(UUID documentId, String filename, ProcessingStatus status, String message,
  String failureReason, int chunkCount, int pageCount, Instant updatedAt)`
- `DocumentSummaryDto(UUID documentId, String filename, ProcessingStatus status, int chunkCount,
  int pageCount, Instant createdAt, String failureReason)`
- `DocumentChunkDto(String referenceId, String location, String text)`
- `DocumentContext(UUID documentId, String filename, String title, List<DocumentChunkDto> chunks)`

- `StudyPreferences(String language, String academicLevel, LocalDate examDate,
  int practiceQuestionCount, int mockQuestionCount, int flashcardCount, int mockDurationMinutes)`
- `StudyPackStatusDto(UUID studyPackId, UUID documentId, StudyPackStatus status,
  int progressPercent, String message, String failureReason, Instant updatedAt)`
- `StudyPackDto(UUID id, UUID documentId, String documentFilename, String title,
  StudyOverview overview, List<StudyTopic> topics, List<RevisionSection> revisionNotes,
  List<DefinitionDto> definitions, List<KeyFactDto> keyFacts, List<FlashcardDto> flashcards,
  List<QuestionDto> practiceQuestions, int mockQuestionCount, int mockDurationMinutes,
  StudyPackStatus status, Instant createdAt)`
- `StudyOverview(String title, String introduction, String summary,
  List<String> learningObjectives, List<String> revisionFocus)`
- `StudyTopic(String name, String explanation, List<String> keyConcepts, List<String> sourceReferences)`
- `RevisionSection(String heading, String body, List<String> sourceReferences)`
- `DefinitionDto(String term, String definition, String sourceReference)`
- `KeyFactDto(String label, String detail, String sourceReference, boolean formula)`
- `FlashcardDto(UUID id, String front, String back, String sourceReference, int position)`
- `QuestionDto(UUID id, QuestionType type, String prompt, List<String> options,
  Difficulty difficulty, String sourceReference, int position)` — deliberately no answer
- `QuizSubmissionDto(Map<UUID, String> answers, int durationSeconds)`
- `QuizResultDto(UUID attemptId, int score, int totalQuestions, int percentage, int durationSeconds,
  List<QuizQuestionReviewDto> review)`
- `QuizQuestionReviewDto(UUID questionId, QuestionType type, String prompt, List<String> options,
  String submittedAnswer, String correctAnswer, String explanation, boolean correct,
  boolean autoGraded, String sourceReference)`
- `TutorQuestionDto(String question)`
- `TutorAnswerDto(String answer, List<String> sourceReferences, boolean insufficientInformation)`
- `TutorMessageDto(UUID id, String role, String content, List<String> sourceReferences, Instant createdAt)`

Enums: `ProcessingStatus { UPLOADED, PROCESSING, READY, FAILED }`,
`StudyPackStatus { QUEUED, PROCESSING, READY, FAILED }`,
`QuestionType { MULTIPLE_CHOICE, TRUE_FALSE, SHORT_ANSWER }`,
`Difficulty { EASY, MEDIUM, HARD }`.

## HTTP surface

Pages (Thymeleaf, session-authenticated):

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/` | Landing page |
| GET/POST | `/register` | Registration |
| GET/POST | `/login` | Login (fields `email`, `password`) |
| POST | `/logout` | Logout |
| GET | `/dashboard` | Documents, study packs, attempts, progress |
| GET | `/documents` | Upload page and document list |
| GET | `/documents/{id}` | Document detail and extraction status |
| GET | `/study-packs/{id}` | Overview, topics, notes, glossary, formulas |
| GET | `/study-packs/{id}/flashcards` | Flashcards |
| GET | `/study-packs/{id}/exam` | Mock examination |
| GET | `/study-packs/{id}/attempts/{attemptId}` | Results and review |
| GET | `/study-packs/{id}/tutor` | Tutor |
| GET | `/privacy` | Privacy notice |

JSON APIs (all CSRF-protected for mutations, all owner-scoped):

| Method | Path |
| --- | --- |
| POST | `/api/documents` (multipart `file`) |
| GET | `/api/documents` |
| GET | `/api/documents/{id}/status` |
| DELETE | `/api/documents/{id}` |
| POST | `/api/study-packs` |
| GET | `/api/study-packs/{id}/status` |
| GET | `/api/study-packs/{id}/exam` |
| POST | `/api/study-packs/{id}/quiz` |
| POST | `/api/study-packs/{id}/questions/{questionId}/reveal` |
| POST | `/api/study-packs/{id}/flashcards/{flashcardId}/review` |
| POST | `/api/study-packs/{id}/tutor` |
| GET | `/api/study-packs/{id}/tutor` |
| DELETE | `/api/study-packs/{id}` |

## Errors

`common.exception` types map to RFC 7807-style JSON for `/api/**` and to the error page for pages:

| Exception | HTTP | Meaning |
| --- | --- | --- |
| `ResourceNotFoundException` | 404 | Missing **or not owned**; intentionally indistinguishable |
| `InvalidRequestException` | 400 | Bad input, wrong state (e.g. document not READY) |
| `InvalidUploadException` | 400 | Upload rejected before storage |
| `AiNotConfiguredException` | 503 | `GOOGLE_API_KEY` missing |
| `AiServiceException` | 502 | Gemini failed, timed out or returned unusable output |
| `AccessDeniedException` | 403 | Authenticated but not permitted |

Messages are safe for students: no stack traces, SQL, document text or keys.

## Rules that keep modules compatible

1. Do not add fields to shared records without updating every constructor and the templates that
   read them; records are positional.
2. Migrations are append-only and numbered (`V4__…` next); never edit an applied migration.
3. The document module never imports `com.examprep.ai` or `com.examprep.study`.
4. The AI module never imports web classes or repositories from other modules; it receives a
   `DocumentContext` and returns validated content.
5. Only the `api` package is visible across modules.

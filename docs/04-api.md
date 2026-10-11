# UniSphere — REST API Specification

Base path: `/api`. JSON over HTTPS. Interactive docs (generated from code) at
`/swagger-ui.html` in non-production profiles; this document is the design
contract they implement.

## 1. Conventions

**Authentication.** `Authorization: Bearer <accessToken>` on every endpoint
except those marked 🌐 (public). Refresh/logout use the `refresh_token`
HttpOnly cookie plus header `X-Requested-With: XMLHttpRequest`.

**Roles.** `S` = STUDENT, `L` = LECTURER, `A` = ADMIN, `F` = FINANCE. "own" means
the service additionally checks the record belongs to the caller.

**Pagination.** List endpoints accept `page` (0-based), `size` (default 20,
max 100), `sort=field,asc|desc` (whitelisted fields), plus endpoint-specific
filters. Response:

```json
{
  "content": [ ... ],
  "page": 0,
  "size": 20,
  "totalElements": 134,
  "totalPages": 7
}
```

**Errors** — RFC 9457 Problem Details, `Content-Type: application/problem+json`:

```json
{
  "type": "https://unisphere.dev/problems/validation-error",
  "title": "Validation failed",
  "status": 400,
  "detail": "One or more fields are invalid.",
  "instance": "/api/admin/courses",
  "code": "VALIDATION_ERROR",
  "errors": [ { "field": "credits", "message": "must be between 1 and 10" } ],
  "requestId": "6f1c…",
  "timestamp": "2026-10-04T09:12:44Z"
}
```

| Status | `code` examples |
|--------|-----------------|
| 400 | `VALIDATION_ERROR`, `MALFORMED_REQUEST`, `WEAK_PASSWORD` |
| 401 | `UNAUTHENTICATED`, `INVALID_CREDENTIALS`, `TOKEN_EXPIRED`, `INVALID_TOKEN` |
| 403 | `FORBIDDEN`, `ACCOUNT_DEACTIVATED`, `CSRF_CHECK_FAILED`, `STUDENT_NOT_ELIGIBLE` |
| 404 | `NOT_FOUND` |
| 409 | `DUPLICATE_RESOURCE`, `RESOURCE_IN_USE`, `PREREQUISITE_CYCLE`, `SCHEDULE_CONFLICT`, `DUPLICATE_COURSE`, `COURSE_FULL`, `TIMETABLE_CLASH`, `CONCURRENT_MODIFICATION`, `INVALID_STATE` |
| 422 | `REGISTRATION_CLOSED`, `PREREQUISITE_NOT_MET`, `CREDIT_LIMIT`, `ADD_DROP_DEADLINE_PASSED`, `MARKS_INCOMPLETE` |
| 423 | `ACCOUNT_LOCKED` |
| 429 | `RATE_LIMITED` |
| 500 | `INTERNAL_ERROR` (generic message, no stack trace) |

## 2. Endpoints

### 2.1 Auth — `/api/auth`
| Method | Path | Access | Description |
|--------|------|--------|-------------|
| POST | `/login` | 🌐 | `{email, password}` → `200 {accessToken, expiresIn, user}` + refresh cookie |
| POST | `/refresh` | cookie | Rotate refresh token → `200 {accessToken, expiresIn, user}` |
| POST | `/logout` | cookie | Revoke refresh token → `204` |
| GET | `/me` | any | Current user profile + roles |
| POST | `/activate` | 🌐 | `{token, password}` → `204`; sets password, status ACTIVE |
| POST | `/password/forgot` | 🌐 | `{email}` → always `202` |
| POST | `/password/reset` | 🌐 | `{token, password}` → `204`; revokes all sessions |
| POST | `/password/change` | any | `{currentPassword, newPassword}` → `204` |

### 2.2 Admin — users — `/api/admin/users`
| Method | Path | Access | Description |
|--------|------|--------|-------------|
| GET | `/` | A | Search users (`q`, `role`, `status`), paginated |
| GET | `/{id}` | A | User detail |
| POST | `/staff` | A | Create ADMIN and/or FINANCE account → `201`, activation link issued |
| PUT | `/{id}` | A | Update name and phone |
| POST | `/{id}/deactivate` | A | Deactivate + revoke sessions → `204` |
| POST | `/{id}/activate` | A | Reactivate → `204` |
| POST | `/{id}/resend-activation` | A | New activation link (pending accounts only; older links stop working) → `204` |

### 2.2b Admin — students & lecturers
| Method | Path | Access | Description |
|--------|------|--------|-------------|
| GET | `/api/admin/students` | A | Search `q` (name/email/number), `programId`, `departmentId`, `status`, `yearOfStudy`; sort `studentNumber`, `lastName`, `yearOfStudy`, `admissionDate`, `status` |
| GET | `/api/admin/students/{id}` | A | Student record |
| POST | `/api/admin/students` | A | Create account + profile; number generated (`S2026-00042`) → `201` |
| PUT | `/api/admin/students/{id}` | A | `{programId, yearOfStudy, status}` |
| GET | `/api/admin/lecturers` | A | Search `q`, `departmentId`; sort `lastName`, `staffNumber`, `department` |
| GET | `/api/admin/lecturers/{id}` | A | Lecturer profile |
| POST | `/api/admin/lecturers` | A | Create account + profile; staff number generated (`STF-00007`) → `201` |
| PUT | `/api/admin/lecturers/{id}` | A | `{departmentId, title, office}` |

### 2.3 Academic structure
| Method | Path | Access | Description |
|--------|------|--------|-------------|
| GET | `/api/departments` | any | List (small, unpaginated) |
| POST / PUT | `/api/admin/departments[/{id}]` | A | Create / update |
| DELETE | `/api/admin/departments/{id}` | A | Only when it has no programs, courses or lecturers; otherwise `409 RESOURCE_IN_USE` |
| GET | `/api/programs` | any | List, `?departmentId` |
| POST / PUT | `/api/admin/programs[/{id}]` | A | Create / update |
| GET | `/api/courses` | any | Search `q`, `departmentId`, `level`, `active`; paginated |
| GET | `/api/courses/{id}` | any | Detail incl. prerequisites |
| POST / PUT | `/api/admin/courses[/{id}]` | A | Create / update |
| PUT | `/api/admin/courses/{id}/prerequisites` | A | `{prerequisiteIds: []}`; a cycle → `409 PREREQUISITE_CYCLE` naming the path (e.g. `CS101 → CS301 → CS201 → CS101`) |

### 2.4 Semesters & offerings
| Method | Path | Access | Description |
|--------|------|--------|-------------|
| GET | `/api/semesters` | any | List |
| GET | `/api/semesters/current` | any | Current semester (404 if none) |
| POST / PUT | `/api/admin/semesters[/{id}]` | A | Create / update |
| POST | `/api/admin/semesters/{id}/make-current` | A | Set current |
| POST | `/api/admin/semesters/{id}/registration/open` | A | Open (notifies students) |
| POST | `/api/admin/semesters/{id}/registration/close` | A | Close |
| GET | `/api/offerings` | any | `semesterId` (default current), `q`, `departmentId`, `level`; paginated; includes seats left |
| GET | `/api/offerings/{id}` | any | Detail incl. sessions, prerequisites |
| POST / PUT | `/api/admin/offerings[/{id}]` | A | Create / update (lecturer, capacity, sessions, exam slot, weights). Venue or lecturer clash → `409 SCHEDULE_CONFLICT` |
| DELETE | `/api/admin/offerings/{id}` | A | Only when no students are enrolled |

### 2.5 Students — `/api/students`
| Method | Path | Access | Description |
|--------|------|--------|-------------|
| GET | `/me` | S | Own student profile |
| GET | `/me/timetable` | S | Weekly sessions of active items, current semester |
| GET | `/me/dashboard` | S | Aggregated dashboard payload |
| GET | `/{id}` | A, F | Student profile |

### 2.6 Registration — `/api/registrations`
Lifecycle: `DRAFT → SUBMITTED → APPROVED` (straight to `APPROVED` when the semester needs no approval);
`SUBMITTED → REJECTED` releases seats and the student's next change reopens it as a `DRAFT`.
Drafts hold no seats; seats are taken at submission, all or nothing.

| Method | Path | Access | Description |
|--------|------|--------|-------------|
| GET | `/me/current` | S | Own registration for the current semester (`status: null` before the first course) with `permissions` (`canAdd`, `canRemove`, `canSubmit`, `removeMeansDrop`, `lockedReason`) |
| GET | `/me/catalogue` | S | Current semester's offerings (`q`, `departmentId`, `level`, paginated), each with prerequisite status and `availability` (`AVAILABLE`, `IN_REGISTRATION`, `SAME_COURSE_REGISTERED`, `PREREQUISITES_MISSING`, `FULL`, `TIMETABLE_CLASH`, `CREDIT_LIMIT`) plus a reason |
| POST | `/me/current/items` | S | `{offeringId}` → updated view. `422 REGISTRATION_CLOSED` / `PREREQUISITE_NOT_MET` / `CREDIT_LIMIT` / `ADD_DROP_DEADLINE_PASSED`; `409 DUPLICATE_COURSE` / `COURSE_FULL` / `TIMETABLE_CLASH` / `INVALID_STATE` |
| DELETE | `/me/current/items/{itemId}` | S own | Remove from a draft, or drop after submission (releases the seat; not below the minimum load; before the add/drop deadline) |
| POST | `/me/current/submit` | S | Re-checks the rules, enforces min/max credits, reserves every seat in one transaction |
| GET | `/me` | S | Registration history |
| GET | `/api/students/me/timetable` | S | Weekly sessions of the submitted registration for the current semester |
| GET | `/api/admin/registrations` | A | `semesterId` (default current), `status`, `q`; sort `submittedAt`, `status`, `studentNumber` |
| GET | `/api/admin/registrations/{id}` | A | Detail with items and credit limits |
| POST | `/api/admin/registrations/{id}/approve` | A | `SUBMITTED → APPROVED` |
| POST | `/api/admin/registrations/{id}/reject` | A | `{reason}` → `REJECTED`, seats released |

### 2.7 Marks entry — `/api/lecturer`
Results lifecycle per offering: `DRAFT → SUBMITTED → FINALIZED`; `SUBMITTED → DRAFT` when the registrar
returns the marks. CAT marks are out of the offering's CAT weight and exam marks out of its exam weight
(default 30 + 70), so totals are out of 100. Only the lecturer assigned to an offering may enter its marks;
registrars can view the sheet but correct marks by returning them (separation of duties). Once results
are submitted the class list is frozen: adding or dropping that course returns `409 INVALID_STATE`, and
the offering itself can no longer be edited (its weights are fixed as soon as any mark exists).

| Method | Path | Access | Description |
|--------|------|--------|-------------|
| GET | `/offerings` | L | Own offerings, `?semesterId` (default current), each with `classSize` and `marksComplete` |
| GET | `/offerings/{id}/results` | L own, A | Marks sheet: class list (approved registrations) with marks, total, grade (preview from the current scale until finalized), `permissions` (`canEdit`, `canSubmit`, `lockedReason`) and the registrar's `returnComment` |
| PUT | `/offerings/{id}/results` | L own, `DRAFT` only | `{entries: [{registrationItemId, catMark, examMark}]}` — saves a draft; only listed students change, `null` = not entered. `400` with `errors[].field` such as `entries[2].catMark` for marks above the weight, negative, more than 2 decimals, or students not in the class |
| POST | `/offerings/{id}/results/submit` | L own | `DRAFT → SUBMITTED`; `422 MARKS_INCOMPLETE` while anyone is missing a mark |
| GET | `/offerings/{id}/statistics` | L own, A | Class size, graded count, mean, median, highest, lowest, pass rate and grade distribution |
| POST | `/offerings/{id}/announcements` | L own, A | Post an announcement to the class (see 2.11) |

### 2.8 Results & grading scale
| Method | Path | Access | Description |
|--------|------|--------|-------------|
| GET | `/api/results/me` | S | Finalized results only, grouped by semester (newest first) with semester GPA, credits attempted/earned and `cgpa`; each course says whether it `countsTowardCgpa` |
| GET | `/api/admin/students/{studentId}/results` | A | The same record for any student |
| GET | `/api/admin/results` | A | Offerings of a semester (default current) with results status and marks progress; `status`, `q`; sort `code`, `status`, `submittedAt` |
| POST | `/api/admin/offerings/{id}/results/finalize` | A | `SUBMITTED → FINALIZED`; re-checks completeness, stores grade, grade points and pass/fail from the active scale |
| POST | `/api/admin/offerings/{id}/results/return` | A | `{comment}` → `SUBMITTED → DRAFT` |
| GET | `/api/grading-scale` | any | Active scale, bands highest first (`letter`, `minMark`, `gradePoints`, `passing`) |
| PUT | `/api/admin/grading-scale` | A | `{bands}` replaces the bands. Rejected with every problem listed (`errors[].field = "bands"`) unless: at least one pass and one fail, unique letters and thresholds, lowest band starts at 0, grade points never rise and passes never sit below fails as marks go down. Finalized results keep their grades |

**GPA.** Semester GPA = Σ(credits × grade points) / Σ credits over that semester's finalized results.
CGPA uses the same formula over all semesters but counts only the latest attempt of a repeated course.
Both are computed in `GpaCalculator` with exact decimals and rounded half-up to 2 places.

### 2.9 Transcripts — `/api/transcripts`
| Method | Path | Access | Description |
|--------|------|--------|-------------|
Built from finalized results only, semesters oldest first, with semester GPA, CGPA, the configured
university name (`UNIVERSITY_NAME`), the watermark `UNOFFICIAL — NOT VALID WITHOUT REGISTRAR CERTIFICATION`,
the generation time and a **verification reference** (`XXXX-XXXX-XXXX-XXXX`). The reference is the first
64 bits of a SHA-256 over the student number and every finalized grade: re-issuing an unchanged record
gives the same reference, any change gives a new one. Every issue is recorded (and audited) so the
registrar can check a printed copy. PDFs are sent with `Content-Disposition: attachment` and
`Cache-Control: no-store`.

| Method | Path | Access | Description |
|--------|------|--------|-------------|
| GET | `/me` | S | Own transcript (JSON) |
| GET | `/me/pdf` | S | Own transcript, `application/pdf`; watermark, timestamp and reference on every page |
| GET | `/students/{studentId}` | A | Any student's transcript (JSON) |
| GET | `/students/{studentId}/pdf` | A | Any student's transcript (PDF) |
| GET | `/verify/{reference}` | A | Student, CGPA, credits and course count recorded for the reference, first/last issue and count, and `current` (false once the record has changed since). `404` if never issued |

### 2.10 Finance — `/api/fees`
Money is `NUMERIC(12,2)` in the database and `BigDecimal` in Java; responses carry amounts as JSON numbers
with two decimals and requests take them as decimal strings (`"1500.00"`) so nothing is rounded through
floating point. One currency is configured per installation (`FEE_CURRENCY`, default `KES`). The balance
is never stored: it is Σ active charges − Σ completed payments, so `balance > 0` is owed and `< 0` is credit.
Nothing is deleted: a charge is **voided** and a payment **reversed**, each with a reason, and both stay in
the history (and the audit log). Finance staff (`F`) manage fees; administrators/registrar (`A`) can read
accounts, fee structures and reports but not change them.

| Method | Path | Access | Description |
|--------|------|--------|-------------|
| GET | `/me` | S | Own account: student, currency, `summary {totalCharged, totalPaid, balance}`, charges and payments (newest first) |
| POST | `/me/payments/simulate` | S | `{amount, method, idempotencyKey}` → `{payment, account}`. Method `MOBILE_MONEY` or `CARD`; amount ≤ balance (`422` otherwise). A declined payment returns `200` with `payment.status = FAILED` and a `failureReason`, and changes nothing. Repeating a key returns the first outcome instead of paying again |
| GET | `/accounts` | F, A | Students with balances: `q`, `programId`, `hasBalance`; sort `balance` (default desc), `name`, `studentNumber`; paginated |
| GET | `/accounts/{studentId}` | F, A | Account detail (same shape as `/me`) |
| POST | `/accounts/{studentId}/charges` | F | `{description, amount, semesterId?}` manual charge → account |
| POST | `/accounts/{studentId}/payments` | F | `{amount, method, reference, paidAt?, note?}` office payment → account. `reference` is unique (case-insensitive, `409` if recorded before); `paidAt` defaults to now and cannot be in the future |
| POST | `/payments/{paymentId}/reverse` | F | `{reason}` → `REVERSED`; only completed payments (`409` otherwise) |
| POST | `/charges/{chargeId}/void` | F | `{reason}` → `VOIDED` (`409` if already voided) |
| GET | `/structures?semesterId=` | F, A | One row per active program: its fee for the semester (or `null`) and how many students were charged it |
| PUT | `/structures` | F | `{programId, semesterId, amount, description}` create or update. The amount is fixed once anyone was charged (`409`); the description can still change |
| DELETE | `/structures/{structureId}` | F | `204`; `409` once charged |
| GET | `/semesters/{semesterId}/charges/preview` | F | Registered (approved) students, already charged, to charge now and the amount, and programs with registered students but no fee |
| POST | `/semesters/{semesterId}/generate-charges` | F | Charge every registered student their program's fee → `{charged, alreadyCharged, withoutFee, amountCharged}`. Safe to repeat: a unique index stops a student being billed twice for one fee |
| GET | `/reports/outstanding` | F, A | Students who owe money, largest first; `programId`; `?format=csv` for a spreadsheet |
| GET | `/reports/payments` | F, A | `from`, `to` (ISO dates, inclusive, UTC) → rows and `totalCompleted`; `?format=csv` |

**Simulated gateway.** `SimulatedPaymentGateway` stands in for a payment provider behind the
`PaymentGateway` interface. It approves every amount except those ending in `.13`, which it declines so
the failure path can be demonstrated. Online payments get a `UNI-yyyyMMdd-XXXXXXXX` reference.

**CSV.** UTF-8 with a BOM and CRLF line endings so Excel opens it correctly; text cells starting with `=`,
`+`, `-` or `@` are neutralised against formula injection. Sent as attachments with `Cache-Control: no-store`.

### 2.11 Notifications & announcements
| Method | Path | Access | Description |
|--------|------|--------|-------------|
| GET | `/api/notifications` | any | Own notifications, newest first, `?unreadOnly=true`, paginated |
| GET | `/api/notifications/unread-count` | any | `{count}` for the bell badge |
| POST | `/api/notifications/{id}/read` | any own | Mark read → `204` (again: no-op; someone else's: `404`) |
| POST | `/api/notifications/read-all` | any | Mark all read → `204` |
| GET | `/api/announcements` | S, L, A | Announcements of offerings the caller is enrolled in (approved registration) or teaches; all for A. `?offeringId`, paginated, newest first |
| POST | `/api/lecturer/offerings/{id}/announcements` | L own, A | `{title, body}` (≤ 200 / ≤ 5000 chars) → `201 {announcement, notified}`; every enrolled student is notified in the same transaction |

A notification is `{id, type, title, message, link, read, createdAt}`. `link` is always a path inside the
web app (a database CHECK rejects anything else). Types and what sends them:

| Type | Sent to | When |
|------|---------|------|
| `REGISTRATION_OPENED` | every active student | registration is opened for a semester (once per semester, even if re-opened) |
| `REGISTRATION_SUBMITTED` / `_APPROVED` / `_REJECTED` | the student | submit, approval (or submit without approval), rejection with the reason |
| `RESULTS_RELEASED` | the class list | the registrar finalizes an offering's results |
| `ANNOUNCEMENT` | the class list | an announcement is posted (message shows the first 300 characters) |
| `REGISTRATION_DEADLINE` | active students without a submitted or approved registration | reminder job, once per semester, when registration closes within 48 h |
| `FEE_REMINDER` | active students with a positive balance | reminder job, at most once per 14-day window, with the balance |

Event notifications are written **after** the business transaction commits, in a transaction of their own:
a rolled-back action never notifies anyone, and a failure to notify is logged without undoing the action.
The reminder job runs on `NOTIFICATION_REMINDER_CRON` (daily 06:00 UTC by default). Reminders carry a dedupe
key with a unique index, so extra runs, or runs on several instances at once, never send duplicates.

### 2.12 Admin dashboard & audit
| Method | Path | Access | Description |
|--------|------|--------|-------------|
| GET | `/api/admin/dashboard` | A | KPIs, registration activity series, recent results |
| GET | `/api/lecturer/dashboard` | L | Own KPIs |
| GET | `/api/fees/dashboard` | F | Collections KPIs (Phase 8) |
| GET | `/api/admin/audit-logs` | A | `actorId`, `action`, `entityType`, `from`, `to`; paginated |

### 2.13 Operational
| Method | Path | Access | Description |
|--------|------|--------|-------------|
| GET | `/actuator/health` | 🌐 | Liveness/readiness |
| GET | `/v3/api-docs`, `/swagger-ui.html` | 🌐 (dev only) | OpenAPI |

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
| 403 | `FORBIDDEN`, `ACCOUNT_DEACTIVATED` |
| 404 | `NOT_FOUND` |
| 409 | `DUPLICATE_RESOURCE`, `RESOURCE_IN_USE`, `PREREQUISITE_CYCLE`, `SCHEDULE_CONFLICT`, `DUPLICATE_COURSE`, `COURSE_FULL`, `CONCURRENT_MODIFICATION`, `INVALID_STATE` |
| 422 | `REGISTRATION_CLOSED`, `PREREQUISITE_NOT_MET`, `CREDIT_LIMIT`, `ADD_DROP_DEADLINE_PASSED` |
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
| Method | Path | Access | Description |
|--------|------|--------|-------------|
| GET | `/me/current` | S | Own registration for current semester (creates DRAFT lazily) incl. items, credits, warnings |
| POST | `/me/current/items` | S | `{offeringId}` → add; may return `409`/`422` |
| DELETE | `/me/current/items/{itemId}` | S | Remove (draft) or drop (submitted, before add/drop deadline) |
| POST | `/me/current/submit` | S | Validate all rules, reserve seats → `SUBMITTED` or `APPROVED` |
| GET | `/me` | S | Registration history |
| GET | `/api/admin/registrations` | A | Queue: `semesterId`, `status`, `q`; paginated |
| POST | `/api/admin/registrations/{id}/approve` | A | → `APPROVED`, notifies |
| POST | `/api/admin/registrations/{id}/reject` | A | `{reason}` → `REJECTED`, releases seats, notifies |

### 2.7 Lecturer — `/api/lecturer`
| Method | Path | Access | Description |
|--------|------|--------|-------------|
| GET | `/offerings` | L | Own offerings, `?semesterId` |
| GET | `/offerings/{id}/students` | L own, A | Class list |
| GET | `/offerings/{id}/results` | L own, A | Marks grid |
| PUT | `/offerings/{id}/results` | L own (DRAFT), A | Bulk upsert `[{registrationItemId, catMark, examMark}]` |
| POST | `/offerings/{id}/results/submit` | L own | `DRAFT → SUBMITTED` |
| GET | `/offerings/{id}/statistics` | L own, A | Mean, median, pass rate, distribution |
| POST | `/offerings/{id}/announcements` | L own, A | Publish announcement |

### 2.8 Results — `/api/results`
| Method | Path | Access | Description |
|--------|------|--------|-------------|
| GET | `/me` | S | Finalized results grouped by semester with GPA, plus CGPA |
| GET | `/api/admin/results/pending` | A | Offerings in `SUBMITTED` |
| POST | `/api/admin/offerings/{id}/results/finalize` | A | `SUBMITTED → FINALIZED`, snapshot grades, notify |
| POST | `/api/admin/offerings/{id}/results/return` | A | `SUBMITTED → DRAFT` with comment |
| GET | `/api/admin/grading-scale` | A | Active scale |
| PUT | `/api/admin/grading-scale` | A | Replace bands (validated: contiguous, non-overlapping 0–100) |

### 2.9 Transcripts — `/api/transcripts`
| Method | Path | Access | Description |
|--------|------|--------|-------------|
| GET | `/me` | S | Transcript JSON |
| GET | `/me/pdf` | S | `application/pdf` |
| GET | `/students/{studentId}` | A | Transcript JSON for any student |
| GET | `/students/{studentId}/pdf` | A | PDF |

### 2.10 Finance — `/api/fees`
| Method | Path | Access | Description |
|--------|------|--------|-------------|
| GET | `/me` | S | Own account: totals, balance, charges, payments |
| POST | `/me/payments/simulate` | S | `{amount, method}` → simulated gateway payment |
| GET | `/accounts` | F, A | Search `q`, `programId`, `hasBalance`; sort by balance; paginated |
| GET | `/accounts/{studentId}` | F, A | Account detail |
| POST | `/accounts/{studentId}/charges` | F | Manual charge |
| POST | `/accounts/{studentId}/payments` | F | Record payment (unique `reference`) |
| POST | `/payments/{id}/reverse` | F | `{reason}` → `REVERSED` |
| POST | `/semesters/{semesterId}/generate-charges` | F, A | Apply fee structure to registered students (idempotent) |
| GET | `/reports/outstanding` | F, A | Report JSON; `?format=csv` for CSV |
| GET | `/reports/payments` | F, A | Payments in date range; CSV option |

### 2.11 Notifications — `/api/notifications`
| Method | Path | Access | Description |
|--------|------|--------|-------------|
| GET | `/` | any | Own notifications, `?unreadOnly`, paginated |
| GET | `/unread-count` | any | `{count}` |
| POST | `/{id}/read` | any own | Mark read → `204` |
| POST | `/read-all` | any | Mark all read → `204` |
| GET | `/api/announcements` | S, L | Announcements for own offerings |

### 2.12 Admin dashboard & audit
| Method | Path | Access | Description |
|--------|------|--------|-------------|
| GET | `/api/admin/dashboard` | A | KPIs, registration activity series, recent results |
| GET | `/api/lecturer/dashboard` | L | Own KPIs |
| GET | `/api/fees/dashboard` | F | Collections KPIs |
| GET | `/api/admin/audit-logs` | A | `actorId`, `action`, `entityType`, `from`, `to`; paginated |

### 2.13 Operational
| Method | Path | Access | Description |
|--------|------|--------|-------------|
| GET | `/actuator/health` | 🌐 | Liveness/readiness |
| GET | `/v3/api-docs`, `/swagger-ui.html` | 🌐 (dev only) | OpenAPI |

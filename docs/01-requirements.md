# UniSphere — Requirements Specification

> Version 1.0 · Status: Baseline for implementation

## 1. Purpose

UniSphere is a university student-management and academic portal. It centralises
the academic lifecycle of a student — admission record, course registration,
assessment, results, transcripts and fees — and gives each staff role a focused
workspace with strictly enforced permissions.

The system is a portfolio-grade simulation of a real university information
system (SIS). It is not connected to a real payment provider or identity
provider, but it is structured so those integrations can be added without
rewriting the core.

## 2. Scope

### In scope
- Identity & access: login, logout, token refresh, account activation,
  password reset, role-based access control (RBAC), account deactivation.
- Academic structure: departments, programs, courses, prerequisites.
- Academic calendar: semesters, registration windows, add/drop deadlines.
- Course offerings: a course taught in a semester, with lecturer, capacity,
  weekly schedule and exam slot.
- Course registration with business-rule validation.
- Results: continuous assessment (CAT) + exam marks, configurable grading
  scale, result lifecycle (draft → submitted → finalized).
- GPA / CGPA computation (single, tested implementation on the backend).
- Unofficial transcript (HTML print view + PDF download).
- Fee ledger: charges, simulated payments, balances, reports.
- In-app notifications and course announcements.
- Dashboards for every role, admin tables with search/filter/sort/paginate.
- Audit logging of security-relevant and data-changing actions.

### Out of scope (documented as future work)
- Real payment gateway (an adapter interface is provided).
- Email/SMS delivery (a `MailSender` interface with a logging implementation
  is provided).
- Single sign-on (SAML / OIDC).
- Timetable clash optimisation, hostel management, library, alumni.

## 3. Roles

| Role | Description |
|------|-------------|
| `STUDENT` | Enrolled learner. Can only ever see their own data. |
| `LECTURER` | Teaching staff. Sees only offerings assigned to them. |
| `ADMIN` | Registrar / academic administrator. Manages structure, calendar, accounts, finalizes results. |
| `FINANCE` | Finance officer. Manages fee ledger and payments; read-only view of student identity. |

A user may hold more than one role (e.g. a lecturer who is also an admin);
permissions are the union of the roles held.

## 4. Functional requirements

IDs are referenced from user stories, tests and commit messages.

### 4.1 Authentication & accounts (AUTH)
| ID | Requirement |
|----|-------------|
| AUTH-1 | Users log in with email + password. Failed attempts return a generic "invalid credentials" error (no user enumeration). |
| AUTH-2 | Passwords are hashed with BCrypt (cost 12). Plaintext is never stored or logged. |
| AUTH-3 | Password policy: ≥ 10 chars, at least one letter and one digit, not equal to the email. |
| AUTH-4 | Successful login issues a short-lived access token (JWT, 15 min) and a rotating refresh token (7 days) in an `HttpOnly; Secure; SameSite=Strict` cookie. |
| AUTH-5 | Refresh tokens are stored hashed, rotated on every use, and reuse of a revoked token revokes the whole token family. |
| AUTH-6 | Logout revokes the current refresh token and clears the cookie. |
| AUTH-7 | Accounts created by an admin start as `PENDING_ACTIVATION`; the user sets a password through a one-time activation link. |
| AUTH-8 | Password reset: request (always returns 202), one-time token valid 30 min, stored hashed. |
| AUTH-9 | Deactivated accounts cannot log in and their refresh tokens are revoked immediately. |
| AUTH-10 | 5 failed logins lock the account for 15 minutes; login endpoints are rate-limited per IP. |
| AUTH-11 | Every API endpoint except `/api/auth/**`, health and API docs requires authentication; role checks are enforced server-side on every endpoint. |

### 4.2 Academic structure (ACAD)
| ID | Requirement |
|----|-------------|
| ACAD-1 | Admin can create/update departments (unique code & name). |
| ACAD-2 | Admin can create/update programs belonging to a department (unique code). |
| ACAD-3 | Admin can create/update/deactivate courses (unique code, credits 1–10, level 100–900). |
| ACAD-4 | Admin can define course prerequisites; self-references and cycles are rejected. |
| ACAD-5 | Admin can create/update/deactivate student and lecturer accounts. |

### 4.3 Semesters & offerings (SEM)
| ID | Requirement |
|----|-------------|
| SEM-1 | Admin creates semesters with start/end dates, registration window and add/drop deadline. |
| SEM-2 | Exactly one semester can be marked current. |
| SEM-3 | Admin opens/closes registration for a semester explicitly; registration is also closed automatically outside its window. |
| SEM-4 | Admin creates offerings (course × semester × section) with lecturer, capacity, weekly schedule and exam slot. |
| SEM-5 | Min/max credit load is configured per semester (defaults 12 / 24). |

### 4.4 Course registration (REG)
| ID | Requirement |
|----|-------------|
| REG-1 | Student sees offerings for the current semester with search (code/title) and filters (department, level). |
| REG-2 | Student builds a draft registration by adding/removing offerings. |
| REG-3 | Duplicate course in the same semester is rejected. |
| REG-4 | Missing prerequisites (no passing finalized result) produce a blocking error. |
| REG-5 | A full offering cannot be added; capacity is enforced atomically to survive concurrent requests. |
| REG-6 | Total credits must be within the semester min/max on confirmation. |
| REG-7 | Registration actions are rejected when the registration window is closed. |
| REG-8 | Confirming a registration reserves seats and sets status `SUBMITTED`; if the semester requires approval, admin approves/rejects with a reason, otherwise it is auto-approved. |
| REG-9 | Student may drop a course until the add/drop deadline; the seat is released. |
| REG-10 | Student receives a notification on submission, approval and rejection (Phase 7). |
| REG-11 | A course whose sessions overlap another registered course cannot be added (timetable clash). |

### 4.5 Results & GPA (RES)
| ID | Requirement |
|----|-------------|
| RES-1 | Lecturer enters CAT and exam marks for students in their own offerings only. |
| RES-2 | Marks are validated against the offering's weights (default CAT 30, exam 70). |
| RES-3 | Grade and grade points are derived from the active grading scale; the scale is data, not code. |
| RES-4 | Result lifecycle per offering: `DRAFT → SUBMITTED → FINALIZED`. Lecturers edit only in `DRAFT`; admin can return to `DRAFT` or finalize. |
| RES-5 | On finalization, grade and grade points are snapshotted so later scale changes do not rewrite history. |
| RES-6 | Students see only finalized results. Finalization notifies affected students. |
| RES-7 | Semester GPA = Σ(credits × grade points) / Σ(credits) over finalized results in that semester. |
| RES-8 | CGPA uses the same formula over all semesters; when a course is repeated, only the latest attempt counts. |
| RES-9 | GPA values are rounded half-up to 2 decimal places, computed in one backend component, and covered by unit tests. |
| RES-10 | Lecturer sees per-offering statistics: mean, median, pass rate, grade distribution. |

### 4.6 Transcripts (TRN)
| ID | Requirement |
|----|-------------|
| TRN-1 | Student (own) and admin (any student) can generate a transcript grouped by semester. |
| TRN-2 | Contains university name, student info, program, department, per-course credits/grade/points, semester GPA, CGPA. |
| TRN-3 | Available as a printable HTML view and as a PDF download. |
| TRN-4 | Every page is watermarked "UNOFFICIAL — NOT VALID WITHOUT REGISTRAR CERTIFICATION" with generation timestamp and a verification reference. |

### 4.7 Finance (FIN)
| ID | Requirement |
|----|-------------|
| FIN-1 | Each student has one fee account; balance = Σ charges − Σ completed payments. |
| FIN-2 | Charges are generated from the fee structure (program × semester) or added manually by finance. |
| FIN-3 | Finance records payments (amount > 0, method, unique reference). Payments are never deleted; mistakes are reversed. |
| FIN-4 | Students view balance and payment history; may make a *simulated* payment through a `PaymentGateway` interface. |
| FIN-5 | Finance views outstanding balances (filter by program/semester, sort by amount) and exports CSV reports. |
| FIN-6 | No card numbers or payment credentials are ever stored. |

### 4.8 Notifications & announcements (NTF)
| ID | Requirement |
|----|-------------|
| NTF-1 | In-app notifications have type, title, message, optional link, timestamp and read state. |
| NTF-2 | Users list notifications (paginated), see unread count, mark one/all as read. |
| NTF-3 | Lecturers publish announcements to an offering; enrolled students are notified. |
| NTF-4 | System events generate notifications: registration opened, deadline approaching (scheduled job), registration result, results released, fee reminder. |

### 4.9 Administration & reporting (ADM)
| ID | Requirement |
|----|-------------|
| ADM-1 | Admin dashboard: total/active students, lecturers, courses, current registrations, outstanding fees, registration activity chart, recent finalized results. |
| ADM-2 | All admin tables support server-side search, filters, sorting and pagination. |
| ADM-3 | Destructive actions require a confirmation dialog; entities with history are deactivated, not deleted. |
| ADM-4 | Audit log viewer (filter by actor, action, entity, date range). |

## 5. Non-functional requirements

| ID | Category | Requirement |
|----|----------|-------------|
| NFR-1 | Security | OWASP Top 10 mitigations (see `docs/05-security.md` once written in Phase 9). Secrets only via environment variables. |
| NFR-2 | Security | Least privilege: the application DB user owns no superuser rights; migrations run with the same schema owner only. |
| NFR-3 | Reliability | All schema changes through Flyway migrations; no `ddl-auto=update`. |
| NFR-4 | Consistency | Capacity and registration operations are transactional and safe under concurrency. |
| NFR-5 | Performance | List endpoints are paginated (max page size 100); hot queries are indexed. p95 < 300 ms on seeded data locally. |
| NFR-6 | Usability | Responsive from 360 px wide; WCAG 2.1 AA colour contrast; every form control labelled; keyboard navigable. |
| NFR-7 | Observability | Structured logs with request IDs; `/actuator/health` for orchestration. |
| NFR-8 | Maintainability | Package-by-feature backend, typed API client on the frontend, CI on every push. |
| NFR-9 | Testability | Business rules (registration, GPA, grading, ledger) covered by unit tests; endpoints covered by MockMvc tests including 401/403 cases; integration tests run against real PostgreSQL via Testcontainers. |

## 6. Assumptions & policies

- **Currency:** a single configurable currency (default `KES`). Money is
  `NUMERIC(12,2)` in the DB and `BigDecimal` in Java — never floating point.
- **Time:** stored as `TIMESTAMPTZ` in UTC; the UI renders in the browser's
  time zone.
- **Grading scale (default, configurable):** A 70–100 (4.0), B 60–69 (3.0),
  C 50–59 (2.0), D 40–49 (1.0), E 0–39 (0.0, fail).
- **Repeated courses:** latest finalized attempt replaces earlier ones in CGPA;
  all attempts still appear on the transcript, earlier ones marked "Repeated".
- **Approval:** registrations are auto-approved unless the semester's
  `requires_approval` flag is set.

---

## 7. Feature list

Grouped by module; ★ marks features that are especially worth demonstrating
in an interview.

**Identity & security**
- JWT access tokens + rotating, hashed refresh tokens with reuse detection ★
- Account activation and password reset flows (token-based, hashed, single-use)
- Account lockout and per-IP rate limiting on auth endpoints
- Method-level RBAC + data-ownership checks ("a student can only read their own results") ★
- Audit log of logins, account changes, registration and result actions

**Academic administration**
- Departments, programs, courses with prerequisite graph and cycle detection ★
- Semesters with registration windows, single "current" semester
- Course offerings with lecturer, capacity, schedule and exam slot
- Student and lecturer account management with activation emails (logged in dev)

**Student experience**
- Dashboard: GPA trend chart, credit-load meter, fee status, upcoming exams, notifications
- Course catalogue with search, filters and prerequisite warnings
- Registration cart with live credit total and rule validation ★
- Weekly timetable view
- Results by semester with GPA/CGPA
- Unofficial transcript: print view + PDF ★
- Fee balance, payment history, simulated payment

**Teaching**
- My offerings, class lists
- Marks entry grid with validation and draft/submit workflow ★
- Course statistics (distribution chart, pass rate)
- Announcements to enrolled students

**Finance**
- Fee ledger (charges, payments, reversals) with derived balance ★
- Outstanding balances report, CSV export
- Payment gateway abstraction with a simulated provider

**Platform**
- Notifications with unread badge
- Consistent error format (RFC 9457 Problem Details) ★
- OpenAPI/Swagger docs + Postman collection
- Docker Compose dev stack, multi-stage production images, GitHub Actions CI ★

---

## 8. User stories

Format: *As a ⟨role⟩, I want ⟨goal⟩ so that ⟨benefit⟩.* Acceptance criteria (AC)
are written so they can become tests.

### Authentication
**US-01** As any user, I want to log in with my email and password so that I can access my workspace.
- AC1: Valid credentials → redirected to my role's dashboard.
- AC2: Wrong password or unknown email → identical "Invalid email or password" message.
- AC3: Deactivated account → "Account is deactivated" only *after* a correct password.
- AC4: After 5 failures the account is locked for 15 minutes.

**US-02** As any user, I want my session to stay alive while I work and end when I log out so that I don't re-login constantly but stay secure on shared computers.
- AC1: Access token refreshes silently before expiry.
- AC2: Logout invalidates the refresh token server-side; Back button does not restore access.

**US-03** As a newly created user, I want to set my own password from an activation link so that the admin never knows my password.

**US-04** As any user, I want to reset a forgotten password so that I can regain access.
- AC1: Requesting a reset never reveals whether the email exists.
- AC2: The link works once and expires after 30 minutes.

### Student
**US-10** As a student, I want a dashboard summarising GPA, credits, fees, exams and notifications so that I see my status at a glance.

**US-11** As a student, I want to search and filter this semester's courses so that I can find what to take.

**US-12** As a student, I want to add courses to a registration and see my total credits so that I stay within the allowed load.
- AC1: Adding a course I already have → "Course already in registration".
- AC2: Adding a course whose prerequisite I haven't passed → blocking error naming the prerequisite.
- AC3: Adding a full offering → "Course is full".
- AC4: Confirming with credits outside min/max → error showing the allowed range.
- AC5: Any change after the registration window closes → "Registration is closed for <semester>".

**US-13** As a student, I want to drop a course before the add/drop deadline so that I can adjust my load.

**US-14** As a student, I want to see my weekly timetable so that I know where to be.

**US-15** As a student, I want to see my results with semester GPA and CGPA so that I can track my performance.
- AC1: Only finalized results are visible.

**US-16** As a student, I want to download an unofficial transcript so that I can share my academic record informally.

**US-17** As a student, I want to see my fee balance and payment history so that I know what I owe.

**US-18** As a student, I want notifications about registration, results and fees so that I don't miss deadlines.

### Lecturer
**US-20** As a lecturer, I want to see my assigned offerings and class lists so that I know who I teach.
- AC1: I cannot open another lecturer's offering (403), even by editing the URL.

**US-21** As a lecturer, I want to enter CAT and exam marks in a grid and save drafts so that I can work incrementally.
- AC1: Marks above the component weight or below 0 are rejected with a field-level message.

**US-22** As a lecturer, I want to submit marks for approval so that the registrar can finalize them.
- AC1: After submission the grid is read-only.

**US-23** As a lecturer, I want grade distribution and pass rate per course so that I can evaluate performance.

**US-24** As a lecturer, I want to post announcements to a course so that enrolled students are notified.

### Admin / Registrar
**US-30** As an admin, I want to manage departments, programs and courses (incl. prerequisites) so that the catalogue is accurate.
- AC1: A prerequisite cycle is rejected with a clear message.

**US-31** As an admin, I want to create student and lecturer accounts so that people can access the system.

**US-32** As an admin, I want to deactivate accounts with confirmation so that leavers lose access without losing history.

**US-33** As an admin, I want to create semesters and open/close registration so that registration happens on schedule.

**US-34** As an admin, I want to create offerings with lecturer, capacity and schedule so that students can register.

**US-35** As an admin, I want to approve or reject registrations with a reason when approval is required.

**US-36** As an admin, I want to finalize submitted results so that students can see them and GPAs update.

**US-37** As an admin, I want a dashboard with system-wide statistics and charts so that I can monitor the semester.

**US-38** As an admin, I want to view the audit log so that I can investigate changes.

### Finance
**US-40** As a finance officer, I want to search students' fee accounts so that I can answer queries.

**US-41** As a finance officer, I want to record a payment with a unique reference so that balances are correct and duplicates impossible.

**US-42** As a finance officer, I want to reverse an erroneous payment (with reason) rather than delete it so that the ledger stays auditable.

**US-43** As a finance officer, I want an outstanding-balances report with CSV export so that I can follow up arrears.

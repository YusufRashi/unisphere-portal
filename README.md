# UniSphere — University Student Management & Academic Portal

A full-stack university information system: course registration, results and GPA, transcripts, fees and
notifications, with separate workspaces for students, lecturers, the registrar and finance.

**Stack:** Java 21 · Spring Boot 4.1 · Spring Security (JWT + rotating refresh tokens) · Spring Data JPA ·
PostgreSQL 16 · Flyway · React 19 · TypeScript · Vite · Tailwind CSS 4 · TanStack Query · Docker · GitHub Actions

> **Status: Phase 6 of 9 complete — foundation, authentication, academic structure, accounts, course registration, results & GPA, transcripts, and finance.** See the [roadmap](docs/05-roadmap.md)
> for what each phase delivers. Features listed below are marked ✅ when implemented.

---

## Contents

1. [Features](#features)
2. [Architecture](#architecture)
3. [Prerequisites](#prerequisites)
4. [Quick start with Docker](#quick-start-with-docker)
5. [Environment variables](#environment-variables)
6. [Running locally (without Docker for the app)](#running-locally)
7. [Running tests](#running-tests)
8. [Building for production](#building-for-production)
9. [Deployment](#deployment)
10. [API documentation](#api-documentation)
11. [Project structure](#project-structure)
12. [Security notes](#security-notes)

---

## Features

| Area | Feature | Status |
|------|---------|--------|
| Identity | Email/password login, BCrypt (cost 12) | ✅ |
| | 15-min JWT access token in memory + 7-day rotating refresh token in `HttpOnly; SameSite=Strict` cookie | ✅ |
| | Refresh-token reuse detection (revokes the whole token family) | ✅ |
| | Instant revocation of access tokens on deactivation / password change | ✅ |
| | Account lockout (5 attempts / 15 min) and per-IP rate limiting on auth endpoints | ✅ |
| | Account activation & password reset with single-use, hashed, expiring tokens | ✅ |
| | Role-based access control enforced on the server *and* reflected in the UI | ✅ |
| Platform | RFC 9457 Problem Details for every error, with request IDs | ✅ |
| | Audit log of security-relevant actions | ✅ |
| | Admin user directory: search, filter, sort, paginate, deactivate (with confirmation) | ✅ |
| | Docker Compose stack, least-privilege DB role, CI pipeline | ✅ |
| Academic | Departments, programs and courses with prerequisite **cycle detection** | ✅ |
| | Semesters with registration windows and a single current semester | ✅ |
| | Course offerings with weekly timetable and **venue / lecturer clash detection** | ✅ |
| People | Admin-created student, lecturer and staff accounts with activation links and generated student/staff numbers | ✅ |
| Registration | Course catalogue with per-course eligibility (prerequisites, seats, clashes) and reasons | ✅ |
| | Draft → submit → approve/return workflow with credit limits, deadlines and add/drop | ✅ |
| | **Race-free seat reservation** (guarded atomic update; tested with concurrent students) | ✅ |
| | Weekly student timetable (grid on desktop, agenda on phones) | ✅ |
| Results | Lecturer marks sheet (draft → submit), registrar finalize/return, grades fixed at finalization | ✅ |
| | Grading scale as data (validated: starts at 0, no gaps or overlaps, passes above fails) | ✅ |
| | Semester GPA and CGPA in **one tested `GpaCalculator`** (credit-weighted, latest attempt of a repeated course, half-up to 2 dp) | ✅ |
| | Class statistics; finalized passes satisfy registration prerequisites | ✅ |
| Transcripts | Unofficial transcript: print view and PDF, watermarked with generation time and reference on every page | ✅ |
| | Content-derived **verification reference** the registrar can check (genuine / superseded / never issued) | ✅ |
| Finance | Fee structures and semester billing, ledger with voids and reversals, office payments, repeat-safe online payments through a simulated gateway, outstanding and payment reports with CSV | ✅ |
| Engagement | Notifications & announcements | Phase 7 |
| | Role dashboards with charts | Phase 8 |

Full requirements and user stories: [docs/01-requirements.md](docs/01-requirements.md).

## Architecture

```mermaid
flowchart LR
    Browser["React SPA"] -->|"/ and /assets"| Nginx
    Browser -->|"/api/*"| Nginx
    Nginx -->|proxy| API["Spring Boot API"]
    API --> DB[("PostgreSQL")]
```

The SPA and API share one origin through Nginx (and through the Vite proxy in development), so there is
no CORS in normal operation and `SameSite=Strict` cookies just work. Details, decisions and diagrams:

- [System architecture](docs/02-architecture.md)
- [Database design & ERD](docs/03-database.md)
- [REST API specification](docs/04-api.md)
- [Development roadmap](docs/05-roadmap.md)

## Prerequisites

| Tool | Version | Needed for |
|------|---------|-----------|
| Docker Desktop / Docker Engine + Compose v2 | 24+ | Everything (simplest path) |
| JDK | 21 | Running/testing the backend outside Docker |
| Node.js | 22+ (24 recommended) | Running/testing the frontend outside Docker |
| OpenSSL (or any random generator) | — | Generating secrets |

Maven is **not** required: the backend ships with the Maven Wrapper (`./mvnw`).

## Quick start with Docker

```bash
git clone <your-fork-url> unisphere && cd unisphere
cp .env.example .env
```

Edit `.env` and set at least:

```bash
POSTGRES_SUPERUSER_PASSWORD=<random>          # openssl rand -hex 24
DB_PASSWORD=<random>                          # openssl rand -hex 24
JWT_SECRET=<random base64, ≥ 32 bytes>        # openssl rand -base64 48
BOOTSTRAP_ADMIN_PASSWORD=<10+ chars, letter + digit, must not contain "registrar">
```

Then:

```bash
docker compose up --build
```

| URL | What |
|-----|------|
| http://localhost:3000 | Web app — sign in with `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD` |
| http://localhost:8080/swagger-ui.html | Interactive API docs |
| http://localhost:8080/actuator/health | Health check |

Stop with `docker compose down` (add `-v` to also delete the database volume).

### Database setup

There is nothing to run by hand:

1. On first start the Postgres container runs [`docker/postgres/init/01-create-app-role.sh`](docker/postgres/init/01-create-app-role.sh),
   which creates the application role (`DB_USERNAME`) **without** superuser, create-database or
   create-role rights, and a `unisphere` schema owned by that role.
2. When the API starts, **Flyway** applies the versioned migrations in
   [`backend/src/main/resources/db/migration`](backend/src/main/resources/db/migration).
   Hibernate only *validates* the schema (`ddl-auto=validate`).
3. If no administrator exists, the API creates one from `BOOTSTRAP_ADMIN_*`. Remove those variables after
   your first login.

## Environment variables

All configuration comes from environment variables; nothing secret is committed. `.env.example` lists
every variable with a description.

| Variable | Required | Description |
|----------|----------|-------------|
| `DB_URL` | no | JDBC URL (default `jdbc:postgresql://localhost:5432/unisphere`) |
| `DB_USERNAME` / `DB_PASSWORD` | **yes** | Application database role |
| `POSTGRES_SUPERUSER_PASSWORD` | Docker only | Superuser for the DB container's init script |
| `JWT_SECRET` | **yes** | Base64 HMAC key, ≥ 32 bytes. The API refuses to start otherwise |
| `JWT_ACCESS_TOKEN_TTL` / `REFRESH_TOKEN_TTL` | no | Defaults `15m` / `7d` |
| `COOKIE_SECURE` | no | `true` (default) in production; `false` only for plain-HTTP local dev |
| `CORS_ALLOWED_ORIGINS` | no | Only needed if SPA and API are on different origins |
| `APP_BASE_URL` | no | Public web-app URL used in activation / reset links |
| `UNIVERSITY_NAME` | no | Name printed on transcripts (default `UniSphere University`) |
| `FEE_CURRENCY` | no | ISO 4217 currency for fee accounts (default `KES`) |
| `AUTH_RATE_LIMIT_PER_MINUTE` | no | Per-IP limit on login, activation and password-reset requests (default 20; token refresh is not limited) |
| `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD` | first run | Creates the first admin if none exists |
| `MAIL_LOG_LINKS` | no | `true` prints activation/reset links to the log (development only) |
| `API_DOCS_ENABLED` | no | Swagger UI / OpenAPI JSON (default `true`; set `false` in production) |

## Running locally

Run PostgreSQL in Docker and the app with hot reload on your machine:

```bash
docker compose up -d db                      # database only

# Terminal 1 — API on :8080 (reads ../.env automatically)
cd backend
./mvnw spring-boot:run                       # Windows: mvnw.cmd spring-boot:run

# Terminal 2 — web app on :5173 (proxies /api to :8080)
cd frontend
npm install
npm run dev
```

Open http://localhost:5173. For this setup set `APP_BASE_URL=http://localhost:5173` in `.env` so emailed
links point at the dev server.

> **No JDK installed?** You can build and test the backend in a container instead:
> `docker run --rm -v "$PWD/backend:/app" -w /app maven:3.9-eclipse-temurin-21 mvn verify`
> (integration tests additionally need `-v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`).

## Running tests

### Backend

```bash
cd backend
./mvnw test       # unit tests (fast, no Docker needed)
./mvnw verify     # unit + integration tests (*IT) against real PostgreSQL via Testcontainers — needs Docker
```

| Suite | What it covers |
|-------|----------------|
| `AuthServiceTest` | Login, lockout & expiry, deactivated/pending accounts, refresh rotation, reuse detection & grace period, reset, activation |
| `PasswordPolicyTest`, `FixedWindowRateLimiterTest`, `ActiveSessionValidatorTest`, `PageableFactoryTest` | Security and paging building blocks |
| `AuthFlowIT` | Full HTTP flows: cookies & headers, Problem Details shape, lockout, rotation + theft detection, CSRF header, logout, activation, reset, change password, security headers |
| `UserAdminIT` | RBAC (401 vs 403 for every non-admin role), search/filter/sort/paginate, LIKE-wildcard escaping, instant revocation on deactivation |
| `AuthRateLimitIT` | 429 + `Retry-After` on login; token refresh is never throttled (regression) |
| `PrerequisiteGraphTest`, `TimetableRulesTest`, `SemesterTest` | Cycle detection, timetable overlaps and clashes, semester date rules and status |
| `AcademicStructureIT` | Department/program/course CRUD, uniqueness, safe delete, field errors, prerequisite cycles, catalogue search, read-only access for students |
| `SemesterAndOfferingIT` | Semester validation, single current semester, registration flag + window, offering sections, weights, venue and lecturer clashes |
| `RegistrationRulesTest` | Window and add/drop deadline, duplicate course in any section, prerequisites, seats, credit limits, timetable clashes |
| `RegistrationFlowIT` | Draft → submit → timetable, every rule over HTTP, closed registration, **two students racing for the last seat**, approval with return and resubmission, add/drop moving seats until the deadline, ownership, suspended students |
| `GpaCalculatorTest`, `GradingRulesTest`, `ResultStatisticsTest` | GPA weighting and half-up rounding, repeated courses, band boundaries, mark limits, scale validation, mean/median/pass rate |
| `ResultsFlowIT` | Marks draft → incomplete submit rejected → submit → return → resubmit → finalize, student GPA/CGPA, grades unchanged by later scale edits, lecturer ownership and role boundaries, weight and class-list freezes |
| `TranscriptFingerprintTest`, `TranscriptPdfRendererTest` | Stable, content-derived references; PDF content, and watermark + timestamp + reference on every page of a multi-page transcript |
| `TranscriptIT` | JSON and PDF for the student, headers, re-issue stability, verification (current vs superseded), registrar access, role boundaries, empty record |
| `LedgerTest`, `SimulatedPaymentGatewayTest`, `CsvWriterTest` | Balance = active charges − completed payments with exact decimals, credit, online payment limits; deliberate declines; CSV quoting, BOM and formula neutralising |
| `FinanceFlowIT` | Fee structures → idempotent billing → office payment, duplicate reference, void and reversal keeping the ledger balanced; online payments repeat-safe by idempotency key, declined and over-balance payments; every role's access |
| `AccountProvisioningIT` | Student creation → activation → sign-in, unique numbers and emails, resend activation, record updates, staff role limits, RBAC |

### Frontend

```bash
cd frontend
npm test            # Vitest + Testing Library
npm run typecheck
npm run lint
```

Covers login form validation and redirects (including open-redirect protection), route guards for every
role, password-rule feedback, the accessible confirmation dialog, role-based navigation, the searchable
select (keyboard and async search), offering timetable validation, server-error mapping onto form fields,
the departments screen (listing, safe delete with confirmation, validation and duplicate conflicts), the
registration screen (eligibility reasons, add, server rule violations, submit and drop confirmations),
the timetable layout and credit meter, and the fees screen (statement, online payment, a declined payment
retried with a new idempotency key, amounts above the balance blocked) with the money helpers.

### API testing collection

A Postman collection is delivered in Phase 9 (`postman/`). Until then, use Swagger UI at
`/swagger-ui.html`: call `POST /api/auth/login`, copy `accessToken`, and click **Authorize**.

## Building for production

```bash
# Backend: executable jar → backend/target/unisphere-api.jar
cd backend && ./mvnw -B package

# Frontend: static files → frontend/dist
cd frontend && npm ci && npm run build

# Or the production images (multi-stage, non-root API user)
docker build -t unisphere-api ./backend
docker build -t unisphere-web ./frontend
```

## Deployment

The images are platform-neutral. The reference setup is:

1. **Database:** a managed PostgreSQL 16 instance. Create the application role with the same privileges as
   the init script (no superuser) and point `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` at it.
2. **API container:** `unisphere-api` with all required variables, `COOKIE_SECURE=true`,
   `API_DOCS_ENABLED=false`, `MAIL_LOG_LINKS=false`. Readiness probe: `/actuator/health/readiness`.
3. **Web container:** `unisphere-web` (Nginx), which serves the SPA and proxies `/api` to the API
   (service name `backend`). Terminate TLS in front of it (platform edge, load balancer, or Caddy/Traefik).
4. **CI:** every push runs [`.github/workflows/ci.yml`](.github/workflows/ci.yml) — backend tests with
   Testcontainers, frontend type-check/lint/tests/build, and both Docker image builds.

A production Compose file and a release workflow that publishes images to GHCR arrive in Phase 9.

## API documentation

- Design contract: [docs/04-api.md](docs/04-api.md)
- Live OpenAPI: `GET /v3/api-docs`, Swagger UI at `/swagger-ui.html` (when `API_DOCS_ENABLED=true`)

Example error response (all errors share this shape):

```json
{
  "type": "https://unisphere.dev/problems/invalid-credentials",
  "title": "Invalid credentials",
  "status": 401,
  "detail": "Invalid email or password.",
  "instance": "/api/auth/login",
  "code": "INVALID_CREDENTIALS",
  "timestamp": "2026-10-04T09:17:45.420Z",
  "requestId": "37f56ea089549ce65ffd1916e8bc1d1f"
}
```

## Project structure

```
unisphere/
├── backend/                 Spring Boot API (package-by-feature: auth, user, audit, common, …)
│   └── src/main/resources/db/migration   Flyway migrations
├── frontend/                React + TypeScript SPA (app, api, auth, components, features, …)
│   └── nginx/               Production Nginx config + security headers
├── docker/postgres/init/    Least-privilege database role setup
├── docs/                    Requirements, architecture, ERD, API spec, roadmap
├── .github/workflows/       CI
├── docker-compose.yml       Local full stack
└── .env.example             Configuration template (placeholders only)
```

## Security notes

- Passwords: BCrypt cost 12; policy enforced server-side (the UI mirrors it for instant feedback).
- Tokens: access token only in memory; refresh token `HttpOnly`, `SameSite=Strict`, path-scoped,
  stored as SHA-256 hash, rotated on every use, reuse revokes the family.
- CSRF: bearer-token API; the only cookie endpoints require `SameSite=Strict` **and** a custom header.
- Authorization: URL rules + `@PreAuthorize` on every controller; route guards in the UI are UX only.
- Input: Bean Validation on every request body; JPA parameter binding (no string-built SQL);
  sort fields whitelisted; LIKE wildcards escaped.
- Output: Problem Details without stack traces or framework messages; CSP, `X-Frame-Options: DENY`,
  `nosniff`, `Referrer-Policy`, HSTS.
- Abuse: per-IP rate limit on auth endpoints + per-account lockout; generic login errors and
  always-`202` password reset requests prevent account enumeration.
- Operations: secrets only from the environment; app DB role is not a superuser; containers run as
  non-root.

## License

[MIT](LICENSE)

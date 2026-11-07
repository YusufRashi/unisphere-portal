# UniSphere — Development Roadmap

Each phase ends with a working, tested, committed system. Later phases never
break earlier ones: CI runs the full test suite on every push.

| Phase | Name | Delivers | Key demo points |
|-------|------|----------|-----------------|
| 0 | Planning | Requirements, user stories, architecture, ERD, API spec, roadmap | Design thinking before code |
| 1 ✅ | Foundation & authentication | Repo, Docker Compose, Flyway V1, Spring Security + JWT/refresh rotation, rate limit, lockout, Problem Details, audit service, admin bootstrap; React shell, login, protected & role routes, sidebar layout; CI | Secure auth done properly |
| 2 ✅ | Academic structure & accounts | Flyway V2–V3; departments, programs, courses + prerequisite cycle detection, semesters, offerings; student/lecturer account management with activation; admin data tables (search/filter/sort/paginate), forms, confirm dialogs | Clean CRUD at scale, reusable table component |
| 3 ✅ | Course registration | Flyway V4; catalogue, registration cart, rule engine (`RegistrationRules`), atomic capacity, submit/approve/reject, add/drop, timetable | Business rules + concurrency |
| 4 ✅ | Results & GPA | Flyway V5; grading scale, marks grid, result lifecycle, `GpaCalculator`, student results page, lecturer statistics | Tested domain logic |
| 5 ✅ | Transcripts | Flyway V6; transcript assembly, print view, PDF (OpenPDF) with watermark, verification references | Document generation |
| 6 ✅ | Finance | Flyway V7; ledger, fee structures, payments, reversals, simulated gateway, reports + CSV | Money handled correctly |
| 7 ✅ | Notifications & announcements | Flyway V8; notification service, event hooks, scheduled reminders, bell + inbox UI, announcements | Event-driven touches |
| 8 ✅ | Dashboards | Student/lecturer/admin/finance dashboards with charts (Recharts) | Polished UX |
| 9 ✅ | Hardening & delivery | Security headers review, audit-log viewer, demo-data seeder, Postman collection, screenshots, production compose + Nginx, release workflow, final README | Production readiness |

## Definition of done (every phase)

- Code compiles; `mvn verify` and `npm run test`, `npm run lint`, `npm run build` pass.
- New endpoints documented (OpenAPI annotations + `docs/04-api.md` if contract changed).
- New business rules have unit tests; new endpoints have MockMvc tests incl. 401/403.
- Migrations are additive; no edits to already-applied migrations.
- README updated where setup changed.
- Verification checklist at the end of the phase walked through manually.

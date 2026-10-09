# SaveMon Tasks

| Task | Agent | Status | Depends On / Notes |
| --- | --- | --- | --- |
| Security final review | reviewer | DONE | Backend security foundation; prior final review passed. |
| Identity foundation | backend-developer | DONE | Backend foundation. |
| Frontend auth foundation | frontend-developer | DONE | Frontend foundation. |
| Registration | backend-developer | DONE | Canonical email, password policy, post-commit token port, duplicate conflict, and issuer-unavailable behavior implemented. |
| Login | backend-developer | DONE | Same success shape as registration; dummy Argon2id verification for unknown email. |
| User Profile | backend-developer | DONE | GET/PATCH behavior and displayName validation implemented. |
| Registration/Login UI | frontend-developer | BLOCKED | Implemented; tests authored, but Flutter/Dart verification unavailable. |
| Authentication QA | qa-qc | DONE | Static contract review passed; login/registration/profile backend behavior reviewed. |
| Personal Finance domain/API foundation | backend-developer | BLOCKED | Endpoints, persistence, reversal, history, calendar/summary implemented and focused tests pass; requires PostgreSQL migration/JDBC workflow verification. |
| Personal Finance frontend foundation | frontend-developer | BLOCKED | Read-only accounts, transaction history/detail, summary, calendar implemented; Flutter/Dart tests/analyzer unavailable. |
| Personal Finance authentication integration | backend-developer + frontend-developer | BLOCKED | Backend authorization integrated; mobile sends stored Bearer token, but mobile verification unavailable and finance DB integration remains unverified. |
| Shared Finance domain/API foundation | backend-developer | BLOCKED | Membership, contribution/expense, accounting, and port boundaries implemented; focused tests pass; requires PostgreSQL migration/atomic workflow verification. |
| Shared Finance frontend foundation | frontend-developer | BLOCKED | Read-only vault/member/contribution/expense screens and pagination implemented; Flutter/Dart tests/analyzer unavailable. |
| Shared Finance authentication integration | backend-developer + frontend-developer | BLOCKED | Backend authorization and mobile token integration implemented; mobile verification unavailable and finance DB integration remains unverified. |
| Backend financial database verification | qa-qc | PENDING | Requires PostgreSQL availability to apply V2 and test SQL-backed endpoints/rollback. |
| Mobile verification | qa-qc | PENDING | Requires Flutter/Dart tooling to run format, analyze, and tests. |

## Verification Record (2026-10-09)

- Backend developer reported a full Maven run with 45 tests passing, followed by focused runs covering 14 tests, 6 tests, 4 tests, and 3 reversal/audit tests. QA revalidated four previous backend contract findings with a further targeted run of 4 passing tests.
- Backend QA found and the developer fixed: optional reversal body, dedicated reversal response including idempotent replay, full history item shape, and pagination for Shared Finance collections.
- Reversal audit metadata now identifies both the original transaction and reversal counter-entry.
- Financial SQL and V2 migration have not been exercised against a live PostgreSQL instance.
- Mobile implementation and focused tests were updated for login, stored Bearer authorization, transaction and shared pagination, currency-aware calendar/summary, and shared read-only expense detail. Flutter/Dart tests, formatting, and analyzer were not run because tooling is unavailable; static QA identified and workers addressed request-header, fixture, pagination parsing, contribution currency, optional description, and filter-forwarding issues.
- Approved contracts are documented in `docs/API.md`, `docs/DOMAIN.md`, `docs/ARCHITECTURE.md`, and `docs/DB_SCHEMA.md`; no ADRs were added in this execution.
- `docs/FEATURES.md` remains unchanged. F-013's category-spending example conflicts with the approved deferral of category breakdown; the approved deferral governs MVP implementation.
- Refresh/logout remains a real-user release blocker only; it does not block internal development.
- The unresolved token-issuer deployment topology does not block implementation against the `TokenIssuer` port. With no adapter configured, the fallback intentionally returns `503 TOKEN_ISSUER_UNAVAILABLE`.

## Rules

- Orchestrator owns task status.
- Workers report results to the Orchestrator.
- Workers must not coordinate directly with other workers.
- Do not mark a task DONE without appropriate verification.
- Blocked tasks require an explicit blocker.
- Human approval is required for unresolved product, architecture, security, financial, or API contract decisions.
- A task depends only on work that is technically or contractually required for that task.
- Do not use release sequencing as a technical dependency.
- Independent tasks may run in parallel.

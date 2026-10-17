# SaveMon Tasks

| Task | Agent | Status | Depends On / Notes |
| --- | --- | --- | --- |
| Security final review | reviewer | DONE | Backend security foundation; prior final review passed. |
| Identity foundation | backend-developer | DONE | Backend foundation. |
| Frontend auth foundation | frontend-developer | DONE | Frontend foundation. |
| Registration | backend-developer | DONE | Canonical email, password policy, post-commit token port, duplicate conflict, and issuer-unavailable behavior implemented. |
| Login | backend-developer | DONE | Same success shape as registration; dummy Argon2id verification for unknown email. |
| User Profile | backend-developer | DONE | GET/PATCH behavior and displayName validation implemented. |
| Registration/Login UI | frontend-developer | BLOCKED | Implemented; tests authored, but targeted verification for the auth forms remains outstanding. |
| Authentication QA | qa-qc | DONE | Static contract review passed; login/registration/profile backend behavior reviewed. |
| Personal Finance domain/API foundation | backend-developer | BLOCKED | Endpoints, persistence, reversal, history, calendar/summary implemented and focused tests pass; requires PostgreSQL migration/JDBC workflow verification. |
| Personal Finance frontend foundation | frontend-developer | BLOCKED | Read-only accounts, transaction history/detail, summary, calendar implemented; full verification remains outstanding. Flutter tooling is available; prior full test/analyzer results are not established. |
| Personal Finance authentication integration | backend-developer + frontend-developer | BLOCKED | Backend authorization integrated; mobile sends stored Bearer token, but mobile verification unavailable and finance DB integration remains unverified. |
| Shared Finance domain/API foundation | backend-developer | BLOCKED | Membership, contribution/expense, accounting, and port boundaries implemented; focused tests pass; requires PostgreSQL migration/atomic workflow verification. |
| Shared Finance frontend foundation | frontend-developer | BLOCKED | Read-only vault/member/contribution/expense screens and pagination implemented; full verification remains outstanding. Flutter tooling is available; prior full test/analyzer results are not established. |
| Shared Finance authentication integration | backend-developer + frontend-developer | BLOCKED | Backend authorization and mobile token integration implemented; mobile verification unavailable and finance DB integration remains unverified. |
| Backend financial database verification | qa-qc | BLOCKED | PostgreSQL 16 disposable DB available. Clean-checkout compile fails because prior repair commit omitted refresh-session classes/AutoConfiguration imports resource; testCompile also expects T-017/T-080 implementation. Zero PG tests executed. |
| Backend compile repair for financial DB QA | backend-developer | PARTIAL | Commit `2f47e25cbbca37a7dfb5e8f6d61205e88680a825` not self-contained: IdentityPersistenceAutoConfiguration references untracked refresh-session classes and untracked registration resource. Reviewer FAIL; fix only in a new commit. |
| T-017 Refresh + logout (`/auth/refresh`, `/auth/logout`) | backend-developer | PARTIAL | Routes, rotation/hash-only/30-day family/reuse revocation/logout idempotence implemented; ActiveSubjectFilter and AUTH_SERVICE_UNAVAILABLE mapping added per findings. Main-source compile passes. Focused tests blocked by T-080 testCompile mismatch; independent QA of latest fixes pending. |
| Mobile verification | qa-qc | PARTIAL | Flutter tooling is available; targeted T-037 tests/analyzer pass. Full route/scanner tests hit pre-existing compile errors; simulator/accessibility validation remains outstanding. |
| T-037 Reference SaveMon Flutter UI | frontend-developer | PARTIAL | Final commit `6ac80a3c0963248b61c343c7b88aaac74a68618a` pushed; full series and mock-history label reviewed PASS. Focused Home/auth/finance/session/history tests and targeted analyzer pass. Bottom-nav runtime blocked by pre-existing shared-finance const-map error; simulator visual/accessibility QA unavailable. UI implementation completed; task retains these verification limits. |

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

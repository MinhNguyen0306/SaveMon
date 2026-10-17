# SaveMon Orchestration State

## Project Status

Current phase: Personal and Shared Finance Foundation

Overall status: IN_PROGRESS

## Completed

* Backend project foundation
* Frontend project foundation
* Identity domain/application foundation
* Frontend authentication foundation
* Backend security foundation final review
* Registration, login, and user profile backend implementation
* Personal Finance backend/API implementation
* Shared Finance backend/API implementation
* Personal and Shared Finance mobile read-only implementation
* Authentication contract QA and targeted backend contract revalidation

## In Progress

* Verification of finance persistence/migration against PostgreSQL
* Mobile verification and final integration QA

| Current task | Status |
|---|---|
| T-037 Flutter reference UI | Final commit `6ac80a3c0963248b61c343c7b88aaac74a68618a` pushed to `dev`; full series and label review PASS. Focused Home/auth/finance/session/history tests and targeted analyzer pass; full bottom-nav runtime has a pre-existing compile blocker; simulator visual/accessibility QA remains outstanding. PARTIAL |
| Current | T-017 code/security implementation passed independent QA, including authorized PostgreSQL16 refresh/auth integration tests. DevOps must commit all T-017 dependencies with compile repair into a self-contained new dev commit; no amend. |
| Backend compile repair | Commit `2f47e25cbbca37a7dfb5e8f6d61205e88680a825` review FAIL because T-017 dependencies/AutoConfiguration registration were omitted. T-017 follow-up now includes those sources; clean-checkout build still needs verification after new commit. |

## Blocked

* Backend financial DB verification: authorized PostgreSQL 16 disposable container is available. Clean-checkout compile is blocked by missing tracked refresh-session classes/auto-config registration; testCompile also expects T-017 and T-080 implementations. No PG tests ran.
* Personal Finance backend foundation: V2 migration and financial JDBC workflows remain unverified pending compile repair and DB QA.
* Shared Finance backend foundation: V2 migration and atomic cross-module workflows remain unverified pending compile repair and DB QA.
* Mobile full-suite validation is incomplete: Flutter tools are available; targeted T-037 widget tests and analysis pass, while broader route/scanner tests are blocked by existing compile errors (`shared_finance_api.dart` const map; `expense_scan_repository.dart` missing `ApiClient.sendMultipartFile`). Simulator/accessibility verification remains unrun.

## Open Implementation Notes

* `TokenIssuer` is implemented as an application port. The current fallback returns `503 TOKEN_ISSUER_UNAVAILABLE` if no issuer adapter is supplied; deployment topology remains open and is not an implementation gate.
* Login, registration, profile, transaction history/reversal, calendar/summary, and Shared Finance read/write response contracts are documented in `docs/API.md`.
* F-013 lists spending by category as a Basic Financial Summary example, while the approved decision defers category breakdown. `FEATURES.md` remains unchanged; category breakdown stays deferred under the explicit approved decision.
* Settlement remains post-MVP. Refresh/logout remain deferred for internal development but block real-user builds.

## Current Security Baseline

* JWT algorithm: RS256
* JWT subject (`sub`): UUID string
* JWT issuer (`iss`): `savemon`
* JWT audience (`aud`): `savemon-api`
* Access token TTL: 900 seconds
* JWT key ID (`kid`): required
* Private key: environment/secret only
* Password hashing: Argon2id
* Authorization: Bearer access token
* Stateless access-token validation
* No automated key rotation in MVP

## Next Actions

1. Apply V2 migration and run backend financial endpoint/integration verification against PostgreSQL when the database is available.
2. Resolve existing Flutter compile errors, then rerun broader route/scanner tests and simulator/accessibility verification.
3. Keep finance and mobile tasks blocked until their required verification succeeds.

## Rules

* Orchestrator is the single coordination hub.
* Workers communicate only through the Orchestrator.
* Approved documentation is the source of truth.
* Do not silently change approved contracts.
* Human is the final decision maker.
* Never mark work as DONE without verification.

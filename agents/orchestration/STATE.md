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
| T-037 Flutter reference UI | Final commit `6ac80a3c0963248b61c343c7b88aaac74a68618a` pushed to `dev`; full T-037 series and final mock-history label review PASS. Focused Home/auth/finance/session/history tests and targeted analyzer pass; bottom-nav runtime test remains blocked by pre-existing shared-finance compile error; simulator visual/accessibility review not run. Implementation is in repo; task remains PARTIAL for those verification limits. |

## Blocked

* Personal Finance backend foundation: compilation and focused tests pass, but the V2 migration and financial JDBC workflows have not been exercised against PostgreSQL in this session.
* Shared Finance backend foundation: compilation and focused tests pass, but the V2 migration and atomic cross-module financial workflows have not been exercised against PostgreSQL in this session.
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

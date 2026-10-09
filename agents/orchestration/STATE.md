# SaveMon Orchestration State

## Project Status

Current phase: Personal and Shared Finance Foundation; QR/Bill Scanning Product Specification

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
* Approved QR/Bill expense-scanning product scope documented in `docs/PRODUCT.md` and `docs/FEATURES.md` (pending task-branch review/merge)

## In Progress

* Verification of finance persistence/migration against PostgreSQL
* Mobile verification and final integration QA
* QR/Bill scanning product documentation task on `dev/1/ai-qr-bill-scanning-product-spec`

## Blocked

* Personal Finance backend foundation: compilation and focused tests pass, but the V2 migration and financial JDBC workflows have not been exercised against PostgreSQL in this session.
* Shared Finance backend foundation: compilation and focused tests pass, but the V2 migration and atomic cross-module financial workflows have not been exercised against PostgreSQL in this session.
* Registration/Login UI and Personal/Shared Finance mobile integration: implementation and tests are present, but Flutter/Dart tools are unavailable, so tests/analyzer/build could not be run.
* QR/Bill scanning technical design/implementation: blocked until the product owner reconciles whether historical expense notes remain subject to current personal account/shared vault balance behavior, including insufficient-balance rejection. Scanning must not bypass existing financial rules.

## Open Implementation Notes

* `TokenIssuer` is implemented as an application port. The current fallback returns `503 TOKEN_ISSUER_UNAVAILABLE` if no issuer adapter is supplied; deployment topology remains open and is not an implementation gate.
* Login, registration, profile, transaction history/reversal, calendar/summary, and Shared Finance read/write response contracts are documented in `docs/API.md`.
* F-013 lists spending by category as a Basic Financial Summary example, while the approved decision defers category breakdown. `FEATURES.md` remains unchanged; category breakdown stays deferred under the explicit approved decision.
* Settlement remains post-MVP. Refresh/logout remain deferred for internal development but block real-user builds.
* Approved QR/Bill scanning is a Premium-only POST-MVP L1 expense-note feature: supported receipt/bill/bill-QR inputs only; user confirms extracted draft; no image retention; deliberate rescans are distinct, retries must not duplicate; broad optional user-selected categories; user supplies all personal/shared finance context; monthly/yearly Premium is unlimited with no free scan quota or trial. It does not initiate or verify payment or process refunds/credits.

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
2. Run mobile formatting, analysis, and tests when Flutter/Dart tooling is available.
3. Keep finance and mobile tasks blocked until their required verification succeeds.
4. Complete documentation task 1 review/merge; do not start QR/Bill technical design until the recorded balance-policy blocker is resolved.

## Current Task

* Task number: 1
* Task branch: `dev/1/ai-qr-bill-scanning-product-spec`
* Base: `origin/dev` at `f5c193c4cd9e16235f30ebc1960706672b9d971f`
* Local original `dev` branch was preserved as `dev-local-backup` to resolve Git's `dev`/`dev/...` ref namespace conflict.

## Rules

* Orchestrator is the single coordination hub.
* Workers communicate only through the Orchestrator.
* Approved documentation is the source of truth.
* Do not silently change approved contracts.
* Human is the final decision maker.
* Never mark work as DONE without verification.

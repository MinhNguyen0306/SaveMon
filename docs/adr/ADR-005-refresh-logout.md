Status
Accepted for MVP deferral.

Context
Refresh-token and logout behavior is deferred for the initial release. The current access-token TTL remains 900 seconds, and clients must clear local session state on 401 responses. The system has no implemented refresh/logout flow in MVP. The future contract requires explicit request and response definitions, mobile token-storage rules, rotation/revocation handling, and a decision on whether Redis-backed state is necessary. The product owner has marked refresh/logout as a release blocker for real-user builds and allowed deferral only for internal development builds.

Decision
Refresh and logout remain unimplemented in MVP. Clients will clear local session data on any 401 response and continue to operate with the current 900-second access-token lifetime. The future refresh/logout contract will define requests, responses, error codes, mobile storage requirements, token rotation and revocation behavior, and whether Redis-backed state is required for session revocation. The release requirement is explicit: refresh/logout implementation is a blocker for real-user builds, while internal development builds may defer it.

Consequences
MVP remains secure and operationally simple, but the client must handle expired or invalid tokens by clearing local session state. The system will require a future design milestone for refresh-token rotation and revocation before production deployment to real users. The open requirement clarifies that refresh/logout is not merely a convenience feature but a production readiness requirement.

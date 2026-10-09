Status
Accepted.

Context
Registration validates input, creates the user, and commits the database transaction before invoking the token issuer through an application port. The database transaction must not remain open while the network call occurs. If token issuance fails after the commit, the backend must return 503 TOKEN_ISSUER_UNAVAILABLE without reporting registration success, while keeping the created user. The client is expected to recover by logging in. A repeated registration attempt for the same canonical email must return 409 IDENTITY_CONFLICT. The source documentation does not explicitly declare whether the token issuer is a separate deployable service or an in-process module; the integration contract is therefore treated as a separate service boundary unless future design says otherwise.

Decision
The backend will create the user and commit the database transaction before calling the token issuer synchronously through an application port. It will never hold the DB transaction open during the network call. If the token issuer is unavailable after the commit, the response will be 503 TOKEN_ISSUER_UNAVAILABLE, the created user will remain persisted, and the client will recover by logging in. Duplicate registrations intentionally reveal that the identity exists, and the backend will return 409 IDENTITY_CONFLICT on repeated attempts. The intended integration model is a separate token-issuer service behind the application port; deployment topology remains an open question until the infrastructure design is explicitly approved.

Consequences
The registration flow preserves a clean transaction boundary and avoids long-lived DB transactions. The API contract guarantees that the client never receives a success response without a usable token. The system must handle a post-commit token-issuer outage as a recoverable application failure rather than a database rollback. The separate-service assumption creates a clear external dependency boundary and keeps the deployment model open for follow-up design.

Status
Accepted.

Context
Identity checks depend on a stable canonical email value. The account model must support unique identity detection, consistent comparisons, and predictable duplicate-registration behavior. Existing user identity rules require the canonical email value to be used for storage and comparison, while duplicate registration should intentionally reveal whether the identity already exists as an MVP trade-off. The system must reject blank passwords and preserve the exact password value while hashing it securely.

Decision
The email value will be normalized with trim + lowercase(Locale.ROOT) before storage and comparison. The canonical value will be the unique identity key for registration and login checks. Duplicate registration will intentionally reveal an existing account, and the accepted MVP behavior is to return a duplicate-identity error rather than suppressing the enumeration signal. Password validation will reject blank values and accept 8–128 characters without altering the original password value before hashing. Passwords will be stored only as secure Argon2id hashes.

Consequences
Registration and login behavior are deterministic and consistent across case variations in user input. The database can enforce uniqueness on the canonical email, which simplifies identity checks and reduces ambiguity. The accepted duplicate-registration behavior creates an account-enumeration risk that is intentionally accepted for MVP; rate limiting and stronger concealment are deliberately deferred. Secure password handling remains consistent with the approved authentication baseline.

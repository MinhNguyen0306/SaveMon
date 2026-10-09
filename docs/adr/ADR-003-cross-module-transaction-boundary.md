Status
Accepted.

Context
Shared Finance must mutate personal accounts and vault state as part of one financial operation, but the system must preserve module boundaries. Direct repository access from Shared Finance into Personal Finance is forbidden. The backend must still perform all related updates in one database transaction and ensure currency compatibility, authorization, and accounting consistency without reaching across module implementations.

Decision
Shared Finance will not call Personal Finance repositories directly. Instead, it will use an explicit application-level command or port to request the required personal-account debit behavior as part of the same database transaction. Shared Finance and Personal Finance remain separate modules, and cross-module coordination occurs through approved application contracts rather than repository coupling. The source account currency must match the vault currency, and mismatched currencies will be rejected with a documented error code.

Consequences
Module boundaries remain intact while still allowing one atomic financial mutation across account and vault state. The explicit command boundary makes the cross-module accounting behavior auditable and testable. The same-transaction rule prevents partial state updates across modules, while currency mismatch checks make invalid multi-currency operations explicit and non-ambiguous.

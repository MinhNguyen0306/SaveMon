Status
Accepted.

Context
The MVP must avoid foreign exchange behavior and must maintain strict financial integrity. Personal accounts must not become negative under normal operations, while reversals are the explicit exception that may create a negative balance for corrective audit purposes. Transactions may be unitemized, and when items exist, each amount must be positive and their sum must equal the transaction amount exactly. Summary and calendar views must group by currency or require a currency filter, because no FX is supported in MVP.

Decision
No foreign exchange is implemented in MVP. The backend will reject any operation that would create a negative balance for a normal account action, and it will return a documented error code for the invalid operation. Reversal is allowed to create a negative balance as an exception, and the reversal must be auditable as a compensating transaction. Transaction items, when present, must each be positive and sum to the exact transaction amount. Summary and calendar queries must not aggregate amounts across currencies; they must group by currency or require a currency filter. All amounts in a financial operation must use the same currency as the account and vault involved.

Consequences
The financial model preserves deterministic balance integrity while still allowing corrective reversals with explicit auditability. The system avoids FX assumptions in MVP and keeps currency handling explicit. Reporting must remain currency-aware, which protects correctness in aggregate views and prevents cross-currency totalization mistakes.

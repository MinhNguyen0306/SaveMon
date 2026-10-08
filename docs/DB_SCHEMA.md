# SaveMon — Database Schema

## 1. Purpose

This document defines the PostgreSQL database schema for SaveMon.

The schema must support:

- User identity
- Personal financial accounts
- Categories
- Income and expense transactions
- Transaction itemization
- Shared vaults
- Vault membership
- Vault contributions
- Shared expenses
- Expense splits
- Financial consistency
- Idempotent financial commands
- Auditability

The database is the **source of truth for financial state**.

Redis, mobile clients, AI services, and cached values must never become the authoritative source of financial data.

---

# 2. Database Principles

## 2.1 PostgreSQL is the financial source of truth

All financial state must ultimately be persisted in PostgreSQL.

The following must not be treated as authoritative:

- Flutter-calculated balances
- Redis balances
- AI-generated financial state
- Cached API responses
- Client-side derived totals

---

## 2.2 Financial mutations are atomic

A financial operation must update all related financial state inside one database transaction.

Example:

```text
Record Contribution
    ↓
Validate account ownership
    ↓
Validate vault membership
    ↓
Insert transaction
    ↓
Insert transaction item if applicable
    ↓
Decrease personal account balance
    ↓
Increase vault balance
    ↓
Commit
```

If any step fails, the entire operation must roll back.

---

## 2.3 Balance strategy

SaveMon uses a **hybrid balance strategy**.

### Authoritative data

Transactions and their financial records remain the historical source of truth.

### Stored balance

The following balances may be stored for efficient reads:

```text
accounts.current_balance
shared_vaults.current_balance
```

These are **denormalized projections of financial state**, not independent sources of truth.

Whenever a financial mutation changes a balance:

```text
Ledger mutation
+
Balance update
```

must happen in the **same PostgreSQL transaction**.

The application must never update a balance separately from the financial operation that caused the change.

---

# 3. Transaction Lifecycle

SaveMon does not use unrestricted hard deletion for financial records.

Financial history should remain auditable.

The preferred lifecycle is:

```text
ACTIVE
  ↓
REVERSED
```

A reversal represents a corrective financial operation rather than physically removing the historical record.

For example:

```text
Original Expense
    -500,000

Reversal
    +500,000
```

The original transaction remains historically visible.

### MVP rule

Financial transactions should not be physically deleted through normal application APIs.

A reversal/correction mechanism is preferred for financial mutations.

Technical deletion may only be considered for non-financial/supporting data where historical integrity is not affected.

---

# 4. Core Tables

The initial schema consists of:

```text
users
accounts
categories
transactions
transaction_items

shared_vaults
vault_members
contributions
shared_expenses
expense_splits

idempotency_keys
audit_logs
```

Supporting receipt storage is post-MVP.

No calendar table is required.

Calendar views are derived from:

```text
transactions.transaction_date
```

---

# 5. users

Stores application users.

```sql
users
-----
id
email
password_hash
display_name
status
created_at
updated_at
```

### Columns

| Column        | Type        | Constraints      |
| ------------- | ----------- | ---------------- |
| id            | UUID        | PK               |
| email         | VARCHAR     | NOT NULL, UNIQUE |
| password_hash | VARCHAR     | NOT NULL         |
| display_name  | VARCHAR     | NOT NULL         |
| status        | VARCHAR     | NOT NULL         |
| created_at    | TIMESTAMPTZ | NOT NULL         |
| updated_at    | TIMESTAMPTZ | NOT NULL         |

### Status

Initial values:

```text
ACTIVE
SUSPENDED
```

Additional account lifecycle states can be introduced later if required.

---

# 6. accounts

Represents a personal financial account owned by exactly one user.

Examples:

```text
Cash
Bank account
E-wallet
```

```sql
accounts
--------
id
user_id
name
type
current_balance
currency
status
version
created_at
updated_at
```

### Columns

| Column          | Type          | Constraints            |
| --------------- | ------------- | ---------------------- |
| id              | UUID          | PK                     |
| user_id         | UUID          | FK users(id), NOT NULL |
| name            | VARCHAR       | NOT NULL               |
| type            | VARCHAR       | NOT NULL               |
| current_balance | NUMERIC(19,4) | NOT NULL               |
| currency        | VARCHAR(3)    | NOT NULL               |
| status          | VARCHAR       | NOT NULL               |
| version         | BIGINT        | NOT NULL               |
| created_at      | TIMESTAMPTZ   | NOT NULL               |
| updated_at      | TIMESTAMPTZ   | NOT NULL               |

### Initial account types

```text
CASH
BANK
EWALLET
OTHER
```

### Balance invariant

`current_balance` may be positive, zero, or negative depending on future account rules.

The application must define whether negative balances are allowed for each account type.

The MVP should not introduce overdraft-specific financial logic unless required.

### Indexes

```text
INDEX accounts_user_id_idx
INDEX accounts_user_id_status_idx
```

---

# 7. categories

Categories classify transaction items.

Categories may form a hierarchy.

```sql
categories
----------
id
user_id
parent_id
name
type
status
created_at
updated_at
```

### Columns

| Column     | Type        | Constraints             |
| ---------- | ----------- | ----------------------- |
| id         | UUID        | PK                      |
| user_id    | UUID        | FK users(id), NOT NULL  |
| parent_id  | UUID        | FK categories(id), NULL |
| name       | VARCHAR     | NOT NULL                |
| type       | VARCHAR     | NOT NULL                |
| status     | VARCHAR     | NOT NULL                |
| created_at | TIMESTAMPTZ | NOT NULL                |
| updated_at | TIMESTAMPTZ | NOT NULL                |

### Category types

```text
INCOME
EXPENSE
```

### Rules

A category:

- belongs to one user
- may have a parent
- must not form a circular hierarchy
- should not be physically deleted when already referenced by financial history

Prefer:

```text
ACTIVE
ARCHIVED
```

over destructive deletion.

### Constraints

A category's parent should belong to the same user.

This rule should be enforced at the application/domain level and, where practical, reinforced through database constraints.

### Indexes

```text
INDEX categories_user_id_idx
INDEX categories_user_id_parent_id_idx
```

---

# 8. transactions

`transactions` is the central financial ledger.

```sql
transactions
------------
id
user_id
account_id
vault_id
type
amount
currency
transaction_date
description
status
idempotency_key
created_at
updated_at
version
```

### Columns

| Column           | Type          | Constraints                |
| ---------------- | ------------- | -------------------------- |
| id               | UUID          | PK                         |
| user_id          | UUID          | FK users(id), NOT NULL     |
| account_id       | UUID          | FK accounts(id), NULL      |
| vault_id         | UUID          | FK shared_vaults(id), NULL |
| type             | VARCHAR       | NOT NULL                   |
| amount           | NUMERIC(19,4) | NOT NULL                   |
| currency         | VARCHAR(3)    | NOT NULL                   |
| transaction_date | TIMESTAMPTZ   | NOT NULL                   |
| description      | VARCHAR       | NULL                       |
| status           | VARCHAR       | NOT NULL                   |
| idempotency_key  | VARCHAR       | NULL                       |
| created_at       | TIMESTAMPTZ   | NOT NULL                   |
| updated_at       | TIMESTAMPTZ   | NOT NULL                   |
| version          | BIGINT        | NOT NULL                   |

### Transaction types

```text
INCOME
EXPENSE
```

### Transaction status

```text
ACTIVE
REVERSED
```

### Amount

```text
amount > 0
```

Negative financial amounts should not be represented by a negative `amount`.

The semantic direction comes from the transaction type/context.

---

# 9. Transaction Financial Context

A transaction may belong to:

```text
Personal account
Shared vault
```

The schema must prevent ambiguous financial ownership.

Examples:

### Personal expense

```text
account_id = personal account
vault_id   = NULL
```

### Vault contribution

```text
account_id = contributor's personal account
vault_id   = target vault
```

### Vault expense

```text
vault_id   = target vault
```

The exact combination of fields must be validated by the application/domain layer.

Database constraints should additionally prevent obviously invalid states.

---

# 10. transaction_items

Transaction items provide detailed categorization.

```sql
transaction_items
-----------------
id
transaction_id
category_id
description
amount
created_at
updated_at
```

### Columns

| Column         | Type          | Constraints                   |
| -------------- | ------------- | ----------------------------- |
| id             | UUID          | PK                            |
| transaction_id | UUID          | FK transactions(id), NOT NULL |
| category_id    | UUID          | FK categories(id), NOT NULL   |
| description    | VARCHAR       | NULL                          |
| amount         | NUMERIC(19,4) | NOT NULL                      |
| created_at     | TIMESTAMPTZ   | NOT NULL                      |
| updated_at     | TIMESTAMPTZ   | NOT NULL                      |

### Invariant

For a fully itemized transaction:

```text
SUM(transaction_items.amount)
=
transactions.amount
```

and:

```text
transaction_items.amount > 0
```

The application must ensure item categories are valid for the transaction type.

### Indexes

```text
INDEX transaction_items_transaction_id_idx
INDEX transaction_items_category_id_idx
```

---

# 11. Shared Vault

## 11.1 shared_vaults

Represents a shared financial context.

```sql
shared_vaults
------------
id
name
currency
current_balance
status
created_by
version
created_at
updated_at
```

### Columns

| Column          | Type          | Constraints            |
| --------------- | ------------- | ---------------------- |
| id              | UUID          | PK                     |
| name            | VARCHAR       | NOT NULL               |
| currency        | VARCHAR(3)    | NOT NULL               |
| current_balance | NUMERIC(19,4) | NOT NULL               |
| status          | VARCHAR       | NOT NULL               |
| created_by      | UUID          | FK users(id), NOT NULL |
| version         | BIGINT        | NOT NULL               |
| created_at      | TIMESTAMPTZ   | NOT NULL               |
| updated_at      | TIMESTAMPTZ   | NOT NULL               |

### Status

```text
ACTIVE
ARCHIVED
```

### Balance

`current_balance` is a denormalized value updated atomically with vault financial operations.

---

# 12. vault_members

Represents membership of a user in a shared vault.

```sql
vault_members
-------------
id
vault_id
user_id
role
status
joined_at
left_at
created_at
updated_at
```

### Columns

| Column     | Type        | Constraints                    |
| ---------- | ----------- | ------------------------------ |
| id         | UUID        | PK                             |
| vault_id   | UUID        | FK shared_vaults(id), NOT NULL |
| user_id    | UUID        | FK users(id), NOT NULL         |
| role       | VARCHAR     | NOT NULL                       |
| status     | VARCHAR     | NOT NULL                       |
| joined_at  | TIMESTAMPTZ | NOT NULL                       |
| left_at    | TIMESTAMPTZ | NULL                           |
| created_at | TIMESTAMPTZ | NOT NULL                       |
| updated_at | TIMESTAMPTZ | NOT NULL                       |

### Roles

```text
OWNER
MEMBER
```

### Status

```text
ACTIVE
REMOVED
```

### Membership rule

A user must not have multiple active memberships in the same vault.

Recommended database protection:

```text
UNIQUE(vault_id, user_id)
```

combined with lifecycle handling rather than creating duplicate membership records.

Historical membership must remain available when required for financial history.

---

# 13. contributions

A contribution represents money transferred from a member's personal financial context into a shared vault.

```sql
contributions
-------------
id
vault_id
member_id
transaction_id
amount
contributed_at
created_at
```

### Columns

| Column         | Type          | Constraints                    |
| -------------- | ------------- | ------------------------------ |
| id             | UUID          | PK                             |
| vault_id       | UUID          | FK shared_vaults(id), NOT NULL |
| member_id      | UUID          | FK vault_members(id), NOT NULL |
| transaction_id | UUID          | FK transactions(id), NOT NULL  |
| amount         | NUMERIC(19,4) | NOT NULL                       |
| contributed_at | TIMESTAMPTZ   | NOT NULL                       |
| created_at     | TIMESTAMPTZ   | NOT NULL                       |

### Rules

```text
amount > 0
```

The referenced member must belong to the referenced vault.

The referenced transaction must represent the corresponding financial movement.

### Atomic contribution

Conceptually:

```text
Personal Account
    - contribution amount

Vault
    + contribution amount
```

Both balance changes and ledger records occur in one PostgreSQL transaction.

---

# 14. shared_expenses

Represents an expense incurred within a shared vault.

```sql
shared_expenses
--------------
id
vault_id
transaction_id
paid_by_member_id
amount
description
expense_date
created_at
updated_at
```

### Columns

| Column            | Type          | Constraints                    |
| ----------------- | ------------- | ------------------------------ |
| id                | UUID          | PK                             |
| vault_id          | UUID          | FK shared_vaults(id), NOT NULL |
| transaction_id    | UUID          | FK transactions(id), NOT NULL  |
| paid_by_member_id | UUID          | FK vault_members(id), NULL     |
| amount            | NUMERIC(19,4) | NOT NULL                       |
| description       | VARCHAR       | NULL                           |
| expense_date      | TIMESTAMPTZ   | NOT NULL                       |
| created_at        | TIMESTAMPTZ   | NOT NULL                       |
| updated_at        | TIMESTAMPTZ   | NOT NULL                       |

### Funding model

SaveMon supports the following conceptual cases:

### Case A — Vault-funded expense

```text
Vault balance
    ↓
Shared Expense
```

`paid_by_member_id = NULL`

### Case B — Member-paid expense

```text
Member's personal account
    ↓
Shared Expense
```

`paid_by_member_id = member`

This allows SaveMon to distinguish:

```text
Who is responsible for the expense?
```

from:

```text
Who actually paid?
```

This distinction is important for future settlement functionality.

---

# 15. expense_splits

Defines how a shared expense is allocated among vault members.

```sql
expense_splits
-------------
id
shared_expense_id
member_id
amount
created_at
```

### Columns

| Column            | Type          | Constraints                      |
| ----------------- | ------------- | -------------------------------- |
| id                | UUID          | PK                               |
| shared_expense_id | UUID          | FK shared_expenses(id), NOT NULL |
| member_id         | UUID          | FK vault_members(id), NOT NULL   |
| amount            | NUMERIC(19,4) | NOT NULL                         |
| created_at        | TIMESTAMPTZ   | NOT NULL                         |

### Core invariant

```text
SUM(expense_splits.amount)
=
shared_expenses.amount
```

Each amount must satisfy:

```text
amount > 0
```

A member may appear at most once per expense:

```text
UNIQUE(shared_expense_id, member_id)
```

The member must be an active member of the same vault when the expense is created.

Historical references should remain valid even if membership later changes.

---

# 16. Settlement

Settlement is intentionally **not part of the MVP schema**.

The current model distinguishes:

```text
Contribution
Shared Expense
Expense Split
Settlement
```

These are different concepts.

Do not introduce a generic `settlements` table until the settlement business rules are explicitly designed.

This avoids encoding an incomplete financial model into the database.

---

# 17. Idempotency

Mobile applications can retry requests because of:

- unstable network
- request timeout
- app restart
- duplicate user action
- server response lost after successful processing

Financial write operations therefore require idempotency protection.

## idempotency_keys

```sql
idempotency_keys
----------------
id
user_id
key
operation
request_hash
response_reference
created_at
expires_at
```

### Columns

| Column             | Type        | Constraints            |
| ------------------ | ----------- | ---------------------- |
| id                 | UUID        | PK                     |
| user_id            | UUID        | FK users(id), NOT NULL |
| key                | VARCHAR     | NOT NULL               |
| operation          | VARCHAR     | NOT NULL               |
| request_hash       | VARCHAR     | NOT NULL               |
| response_reference | VARCHAR     | NULL                   |
| created_at         | TIMESTAMPTZ | NOT NULL               |
| expires_at         | TIMESTAMPTZ | NULL                   |

### Constraint

```text
UNIQUE(user_id, operation, key)
```

The application must reject reuse of the same idempotency key with a materially different request.

Idempotency processing must happen inside the same transaction as the financial operation where necessary.

---

# 18. Audit Logs

Financial and security-sensitive actions should be auditable.

```sql
audit_logs
----------
id
actor_user_id
action
entity_type
entity_id
metadata
created_at
```

### Columns

| Column        | Type        | Constraints        |
| ------------- | ----------- | ------------------ |
| id            | UUID        | PK                 |
| actor_user_id | UUID        | FK users(id), NULL |
| action        | VARCHAR     | NOT NULL           |
| entity_type   | VARCHAR     | NOT NULL           |
| entity_id     | UUID        | NULL               |
| metadata      | JSONB       | NULL               |
| created_at    | TIMESTAMPTZ | NOT NULL           |

Examples:

```text
USER_LOGIN
ACCOUNT_CREATED
TRANSACTION_CREATED
TRANSACTION_REVERSED
VAULT_CREATED
MEMBER_ADDED
MEMBER_REMOVED
CONTRIBUTION_CREATED
SHARED_EXPENSE_CREATED
```

Audit logs are not the financial ledger.

They provide operational/security traceability.

---

# 19. Foreign Key Strategy

Core relationships:

```text
users
 ├── accounts
 ├── categories
 ├── transactions
 └── shared_vaults

accounts
 └── transactions

transactions
 └── transaction_items

shared_vaults
 ├── vault_members
 ├── contributions
 └── shared_expenses

vault_members
 ├── contributions
 └── expense_splits

shared_expenses
 └── expense_splits
```

Foreign keys should be used for core ownership and referential integrity.

Financial records should generally use restrictive deletion behavior.

Avoid cascading deletion of historical financial data.

---

# 20. Money Representation

All monetary values use:

```text
NUMERIC(19,4)
```

Do not use:

```text
FLOAT
DOUBLE
```

for financial amounts.

The application must use a decimal/exact numeric representation that maps safely to PostgreSQL `NUMERIC`.

Currency is represented using:

```text
CHAR(3)
```

using ISO-style currency codes.

---

# 21. Timestamps

Use:

```text
TIMESTAMPTZ
```

for timestamps.

Store timestamps in UTC at the persistence layer.

User-facing timezone conversion belongs to the application/presentation layer.

---

# 22. Optimistic Concurrency

Entities with mutable financial state use a version field:

```text
accounts.version
shared_vaults.version
transactions.version
```

The application may use optimistic locking to detect concurrent updates.

Example:

```text
UPDATE accounts
SET current_balance = ?,
    version = version + 1
WHERE id = ?
  AND version = ?
```

If no row is updated, the application detects a concurrency conflict and handles it appropriately.

For particularly sensitive balance mutations, database locking/atomic update strategies may be used where optimistic locking alone is insufficient.

---

# 23. Database Constraints

Important invariants should be protected at the database level where practical.

Minimum constraints:

```text
amount > 0

transaction_items.amount > 0

expense_splits.amount > 0

contributions.amount > 0

currency is valid length

required foreign keys exist

unique vault membership

unique expense split per member

unique idempotency key per operation/user
```

Some cross-row invariants cannot be expressed cleanly using simple CHECK constraints.

For example:

```text
SUM(transaction_items.amount)
=
transactions.amount
```

and:

```text
SUM(expense_splits.amount)
=
shared_expenses.amount
```

These must be enforced by the application transaction, with appropriate database locking/consistency strategy.

---

# 24. Transaction Boundaries

## Personal expense

```text
BEGIN

validate account
validate transaction
insert transaction
insert transaction items
update account balance
write audit record
commit
```

---

## Personal income

```text
BEGIN

validate account
insert transaction
insert transaction items
update account balance
write audit record
commit
```

---

## Vault contribution

```text
BEGIN

validate active membership
validate source account ownership
insert transaction
insert contribution
decrease account balance
increase vault balance
write audit record
commit
```

---

## Vault-funded shared expense

```text
BEGIN

validate active vault
validate split participants
validate split total
insert transaction
insert shared expense
insert expense splits
decrease vault balance
write audit record
commit
```

---

## Member-paid shared expense

```text
BEGIN

validate active vault
validate payer membership
validate payer account
validate split participants
validate split total
insert transaction
insert shared expense
insert expense splits
decrease payer account balance
write audit record
commit
```

The exact responsibility/settlement effect is derived from these records rather than prematurely introducing a settlement ledger.

---

# 25. Indexing Strategy

Indexes should support actual application queries rather than speculative optimization.

Initial indexes:

```text
users
  UNIQUE(email)

accounts
  INDEX(user_id)
  INDEX(user_id, status)

categories
  INDEX(user_id)
  INDEX(user_id, parent_id)

transactions
  INDEX(user_id, transaction_date)
  INDEX(account_id, transaction_date)
  INDEX(vault_id, transaction_date)
  INDEX(status)

transaction_items
  INDEX(transaction_id)
  INDEX(category_id)

shared_vaults
  INDEX(created_by)

vault_members
  INDEX(vault_id, status)
  INDEX(user_id, status)

contributions
  INDEX(vault_id, contributed_at)
  INDEX(member_id, contributed_at)

shared_expenses
  INDEX(vault_id, expense_date)

expense_splits
  INDEX(shared_expense_id)
  INDEX(member_id)

audit_logs
  INDEX(actor_user_id, created_at)
  INDEX(entity_type, entity_id)
```

Indexes should be reviewed against real query patterns after implementation.

---

# 26. Data Integrity Rules

The following rules are mandatory.

### Account ownership

A user can only mutate their own personal accounts.

### Category ownership

A user can only use categories they are authorized to use.

### Vault membership

A user must be a valid vault member before performing member-level vault operations.

### Contribution

```text
contribution.amount > 0
```

and the source account must belong to the contributing user.

### Shared expense

The vault must be active.

### Expense split

```text
SUM(split.amount) = expense.amount
```

All participants must belong to the vault.

### Transaction amount

```text
transaction.amount > 0
```

### Historical integrity

Financial history should not disappear because a user deletes or leaves an account/vault/category.

---

# 27. AI and Database Boundary

AI has **no direct database access**.

AI must never receive generic database tools such as:

```text
execute_sql
update_database
delete_transaction
modify_balance
```

Instead:

```text
AI
 ↓
Structured Intent
 ↓
Application Command
 ↓
Authorization
 ↓
Validation
 ↓
Domain Logic
 ↓
PostgreSQL Transaction
```

Example:

```text
"Add 200k coffee expense"
        ↓
AI extracts:
{
  type: EXPENSE,
  amount: 200000,
  category: COFFEE
}
        ↓
CreateExpenseCommand
        ↓
validate user/account/category
        ↓
Transaction aggregate
        ↓
PostgreSQL
```

The database remains the final persistence boundary for deterministic financial state.

---

# 28. Things Explicitly NOT in the MVP Schema

The following are intentionally excluded:

```text
calendar table
settlement table
recurring transaction engine
budget engine
investment portfolio
bank synchronization
multi-currency conversion engine
event sourcing
event store
CQRS read database
Kafka
RabbitMQ
Elasticsearch
microservice-specific databases
```

These may be introduced later only when product requirements justify them.

---

# 29. Logical Schema Overview

```text
                        ┌──────────────┐
                        │    users     │
                        └──────┬───────┘
                               │
              ┌────────────────┼─────────────────┐
              │                │                 │
              ▼                ▼                 ▼
        ┌──────────┐    ┌────────────┐    ┌──────────────┐
        │ accounts │    │ categories │    │ shared_vaults│
        └────┬─────┘    └──────┬─────┘    └──────┬───────┘
             │                 │                  │
             │                 │          ┌───────┴────────┐
             │                 │          │                │
             │                 │          ▼                ▼
             │                 │   ┌─────────────┐  ┌───────────────┐
             │                 │   │vault_members│  │shared_expenses│
             │                 │   └──────┬──────┘  └───────┬───────┘
             │                 │          │                  │
             │                 │          ▼                  ▼
             │                 │   ┌────────────┐     ┌─────────────┐
             │                 │   │contributions│     │expense_splits│
             │                 │   └──────┬─────┘     └─────────────┘
             │                 │          │
             └─────────────────┼──────────┘
                               ▼
                       ┌──────────────┐
                       │ transactions │
                       └──────┬───────┘
                              │
                              ▼
                     ┌──────────────────┐
                     │transaction_items │
                     └──────────────────┘
```

---

# 30. Final Database Decisions

The following decisions are considered part of the SaveMon baseline:

| Decision                  | Choice                                              |
| ------------------------- | --------------------------------------------------- |
| Database                  | PostgreSQL                                          |
| Financial source of truth | PostgreSQL                                          |
| Balance strategy          | Stored/denormalized + ledger-derived truth          |
| Account balance           | `accounts.current_balance`                          |
| Vault balance             | `shared_vaults.current_balance`                     |
| Balance update            | Same DB transaction as financial mutation           |
| Money type                | `NUMERIC(19,4)`                                     |
| Currency                  | 3-character code                                    |
| Transaction lifecycle     | Active → Reversed                                   |
| Financial hard delete     | Not allowed through normal APIs                     |
| Shared expense funding    | Vault-funded + member-paid                          |
| Split invariant           | Sum splits = expense amount                         |
| Contribution              | Separate domain concept                             |
| Settlement                | Post-MVP                                            |
| Calendar                  | Derived from transaction date                       |
| Idempotency               | Required for retry-prone financial writes           |
| Concurrency               | Versioning + appropriate DB locking                 |
| Audit                     | Required for significant financial/security actions |
| AI DB access              | Prohibited                                          |
| Redis                     | Supporting infrastructure only                      |
| Event sourcing            | Not used                                            |
| CQRS database             | Not used                                            |
| Microservices             | Not used                                            |
| Separate calendar table   | Not used                                            |

---

# 31. Schema Design Principle

The database should preserve one fundamental property:

> **Every financial state must be explainable by deterministic, auditable financial records.**

Cached balances improve performance.

Indexes improve query speed.

AI improves interaction.

Neither replaces the underlying financial truth.

# SaveMon — API Contract

## 1. Purpose

This document defines the HTTP API contract for SaveMon.

The API is designed around:

- RESTful resource boundaries
- Explicit financial commands
- Strong authorization
- Idempotent financial writes
- Deterministic validation
- Consistent error handling
- Mobile-first usage
- AI-safe execution
- Clear separation between commands and queries

The API is a transport contract.

Business rules remain in the Application and Domain layers.

---

# 2. API Principles

## 2.1 Backend is authoritative

The Flutter application must not be responsible for enforcing financial invariants.

The backend validates:

- ownership
- membership
- permissions
- amounts
- transaction rules
- split totals
- account state
- vault state
- idempotency
- concurrency

---

## 2.2 REST API

Initial API style:

```text
HTTP/JSON
REST-oriented
```

Base path:

```text
/api/v1
```

Example:

```text
GET /api/v1/accounts
```

---

# 3. Authentication

SaveMon uses JWT-based authentication.

## Access Token

The client sends:

```http
Authorization: Bearer <access-token>
```

Access tokens are intended to be short-lived.
For protected requests, the access token is sent using the `Bearer` scheme.

The backend extracts the authenticated user identity from the token.

The client must never provide `user_id` as the authority for ownership.

---

# 4. Authentication Endpoints

## Register

```http
POST /api/v1/auth/register
```

Request:

```json
{
  "email": "user@example.com",
  "password": "password",
  "displayName": "Minh"
}
```

Response:

```http
201 Created
```

```json
{
  "user": {
    "id": "uuid",
    "email": "user@example.com",
    "displayName": "Minh"
  },
  "accessToken": "...",
  "expiresIn": 900
}
```

---

## Login

```http
POST /api/v1/auth/login
```

Request:

```json
{
  "email": "user@example.com",
  "password": "password"
}
```

Response:

```http
200 OK
```

```json
{
  "user": {
    "id": "uuid",
    "email": "user@example.com",
    "displayName": "Minh"
  },
  "accessToken": "...",
  "expiresIn": 900
}
```

Login returns the same response shape as registration. MVP responses do not
include refresh-token fields.

---

## Refresh Token

Refresh-token implementation is deferred in the MVP.

```http
POST /api/v1/auth/refresh
```

Until refresh is implemented, clients clear their local session after any
`401` response. The access-token TTL remains 900 seconds.

---

## Logout

Logout implementation is deferred in the MVP. The server does not currently
revoke access tokens or refresh-token state.

```http
POST /api/v1/auth/logout
```

The future refresh/logout contract must specify requests, responses, error
codes, mobile token storage, rotation/revocation, and whether Redis-backed
state is required. Refresh/logout implementation is a release blocker for
real-user builds; internal development builds may defer it.

If registration commits a new user but token issuance fails, the endpoint
returns `503 TOKEN_ISSUER_UNAVAILABLE`; the user remains created and the
client can recover by logging in. A repeated registration returns
`409 IDENTITY_CONFLICT`.

---

# 5. User Profile

## Get Current User

```http
GET /api/v1/me
```

Response:

```json
{
  "id": "uuid",
  "email": "user@example.com",
  "displayName": "Minh"
}
```

---

## Update Profile

```http
PATCH /api/v1/me
```

Request:

```json
{
  "displayName": "New Name"
}
```

The authenticated user is taken from the JWT.

No user ID is required.

`displayName` is trimmed, must not be empty, and is limited to 100
characters. A successful update returns `200 OK` with the same profile
response shape as `GET /api/v1/me`.

---

# 6. Personal Accounts

## List Accounts

```http
GET /api/v1/accounts
```

Optional query:

```text
?status=ACTIVE
```

Response:

```json
{
  "items": [
    {
      "id": "uuid",
      "name": "Cash",
      "type": "CASH",
      "currency": "VND",
      "balance": 2500000,
      "status": "ACTIVE"
    }
  ],
  "page": 0,
  "size": 20,
  "totalItems": 1,
  "totalPages": 1
}
```

---

## Get Account

```http
GET /api/v1/accounts/{accountId}
```

---

## Create Account

```http
POST /api/v1/accounts
```

Request:

```json
{
  "name": "My Bank",
  "type": "BANK",
  "currency": "VND"
}
```

Initial balance:

```text
0
```

If an initial balance feature is introduced, it must be represented through a deterministic financial operation rather than arbitrary balance mutation.

---

## Update Account

```http
PATCH /api/v1/accounts/{accountId}
```

Request:

```json
{
  "name": "Main Bank"
}
```

---

## Archive Account

```http
POST /api/v1/accounts/{accountId}/archive
```

An account containing financial history should not be physically deleted.

Normal financial operations that would make an account balance negative are
rejected with `422 INSUFFICIENT_BALANCE`. Transaction reversal is the
auditable exception and may make the balance negative. No foreign exchange
is performed; each financial operation must use the account's currency.

---

# 7. Categories

## List Categories

```http
GET /api/v1/categories
```

Optional:

```text
?type=EXPENSE
```

---

## Create Category

```http
POST /api/v1/categories
```

Request:

```json
{
  "name": "Food",
  "type": "EXPENSE",
  "parentId": null
}
```

---

## Update Category

```http
PATCH /api/v1/categories/{categoryId}
```

---

## Archive Category

```http
POST /api/v1/categories/{categoryId}/archive
```

Historical transactions must continue to reference archived categories.

---

# 8. Personal Transactions

Transactions are financial commands.

They are not treated as arbitrary CRUD records.

---

## Record Income

```http
POST /api/v1/transactions/income
```

Headers:

```http
Idempotency-Key: <unique-key>
```

Request:

```json
{
  "accountId": "uuid",
  "amount": 15000000,
  "currency": "VND",
  "transactionDate": "2026-10-08T09:00:00Z",
  "description": "Salary",
  "items": [
    {
      "categoryId": "uuid",
      "amount": 15000000,
      "description": "Salary"
    }
  ]
}
```

Backend validates:

- account ownership
- account status
- currency
- amount
- transaction date
- category ownership
- item totals
- idempotency

---

## Record Expense

```http
POST /api/v1/transactions/expense
```

Headers:

```http
Idempotency-Key: <unique-key>
```

Request:

```json
{
  "accountId": "uuid",
  "amount": 200000,
  "currency": "VND",
  "transactionDate": "2026-10-08T12:00:00Z",
  "description": "Lunch",
  "items": [
    {
      "categoryId": "uuid",
      "amount": 200000,
      "description": "Lunch"
    }
  ]
}
```

Invariant:

```text
sum(items.amount) = transaction.amount
```

when the transaction is fully itemized.

---

# 9. Transaction Queries

## Get Transaction

```http
GET /api/v1/transactions/{transactionId}
```

Response:

```json
{
  "id": "uuid",
  "type": "EXPENSE",
  "amount": 200000,
  "currency": "VND",
  "transactionDate": "2026-10-08T12:00:00Z",
  "description": "Lunch",
  "status": "ACTIVE",
  "account": {
    "id": "uuid",
    "name": "Cash"
  },
  "items": [
    {
      "categoryId": "uuid",
      "categoryName": "Food",
      "amount": 200000
    }
  ]
}
```

---

## Transaction History

```http
GET /api/v1/transactions
```

Query parameters:

```text
page (zero-based, default 0)
size (default 20, range 1–100)
accountId
type
status
from
to
categoryId
currency
```

Example:

```text
GET /api/v1/transactions?accountId={id}&from=2026-10-01&to=2026-10-31
```

The response is paginated:

```json
{
  "items": [
    {
      "id": "uuid",
      "type": "EXPENSE",
      "amount": 200000,
      "currency": "VND",
      "transactionDate": "2026-10-08T12:00:00Z",
      "description": "Lunch",
      "status": "ACTIVE",
      "account": { "id": "uuid", "name": "Cash" },
      "items": [
        { "categoryId": "uuid", "categoryName": "Food", "amount": 200000 }
      ]
    }
  ],
  "page": 0,
  "size": 20,
  "totalItems": 42,
  "totalPages": 3
}
```

Cursor pagination can be introduced later if query volume requires it.

---

# 10. Update Transaction

Editing a financial transaction is a controlled financial command.

```http
PATCH /api/v1/transactions/{transactionId}
```

Possible editable fields:

```text
description
transactionDate
items
```

Changing financial amount/account should be treated as a financial correction operation rather than an unrestricted CRUD update.

Only an `ACTIVE` transaction may be edited. The amount, currency, account,
owner, and transaction type are immutable through this endpoint. If `items`
are provided, every item amount must be positive and their sum must equal the
transaction amount. Omitting `items` leaves the transaction unitemized.
Concurrent modification returns `409 TRANSACTION_CONCURRENCY_CONFLICT`.

Response:

```http
200 OK
```

The response is the updated transaction detail shape documented in Section 9.

---

# 11. Reverse Transaction

Financial records are not hard-deleted.

```http
POST /api/v1/transactions/{transactionId}/reverse
```

Headers:

```http
Idempotency-Key: <unique-key>
```

Request:

```json
{
  "reason": "Recorded by mistake"
}
```

The request body is optional. If supplied, `reason` is an optional audit
description.

The operation:

1. verifies transaction state
2. verifies authorization
3. creates the required corrective financial record
4. updates affected balance(s)
5. marks original transaction as `REVERSED`
6. writes audit information

All steps occur atomically.

Reversal is permitted even when applying it makes the account balance
negative. The reversal and original transaction update must be auditable and
committed in one database transaction. A transaction already in `REVERSED`
state returns `409 TRANSACTION_ALREADY_REVERSED`.

Response:

```http
201 Created
```

```json
{
  "id": "uuid",
  "originalTransactionId": "uuid",
  "reversalTransactionId": "uuid",
  "status": "REVERSED",
  "reversedAt": "2026-10-08T12:15:00Z"
}
```

---

# 12. Calendar

Calendar is a query projection over transactions.

No calendar table exists.

## Get Transactions by Date

```http
GET /api/v1/calendar
```

Example:

```text
GET /api/v1/calendar?currency=VND&from=2026-10-01&to=2026-10-31
```

`currency` is required. Calendar results contain daily summaries only; to
inspect transaction details, use transaction history with date filters.
`from` and `to` are optional inclusive date bounds. If neither is supplied,
the query covers all available transaction dates; if only one is supplied,
it bounds that side of the range.

Response:

```json
{
  "currency": "VND",
  "days": [
    {
      "date": "2026-10-08",
      "income": 15000000,
      "expense": 350000,
      "transactionCount": 4
    }
  ]
}
```

The backend derives this information from financial records.

---

# 13. Financial Summary

## Get Summary

```http
GET /api/v1/summary
```

Example:

```text
GET /api/v1/summary?currency=VND&from=2026-10-01&to=2026-10-31
```

`currency` is required. MVP does not convert or aggregate amounts across
currencies. `from` and `to` are optional inclusive date bounds with the same
behavior as the calendar query.

Response:

```json
{
  "currency": "VND",
  "income": 25000000,
  "expense": 12000000,
  "net": 13000000
}
```

Additional breakdowns may later include:

```text
byCategory
byAccount
daily
monthly
```

The API must derive these from deterministic financial data.

---

# 14. Shared Vaults

## Create Vault

```http
POST /api/v1/vaults
```

Request:

```json
{
  "name": "Trip Fund",
  "currency": "VND"
}
```

The creator automatically becomes:

```text
OWNER
```

---

## List Vaults

```http
GET /api/v1/vaults
```

Only vaults where the authenticated user has valid membership should be returned.

Response:

```json
{
  "items": [
    {
      "id": "uuid",
      "name": "Trip Fund",
      "currency": "VND",
      "balance": 5000000,
      "status": "ACTIVE"
    }
  ],
  "page": 0,
  "size": 20,
  "totalItems": 1,
  "totalPages": 1
}
```

---

## Get Vault

```http
GET /api/v1/vaults/{vaultId}
```

Response:

```json
{
  "id": "uuid",
  "name": "Trip Fund",
  "currency": "VND",
  "balance": 5000000,
  "status": "ACTIVE"
}
```

---

# 15. Vault Members

## List Members

```http
GET /api/v1/vaults/{vaultId}/members
```

Only active members may view vault data. The response is:

```json
{
  "items": [
    {
      "id": "uuid",
      "userId": "uuid",
      "displayName": "Minh",
      "role": "OWNER",
      "status": "ACTIVE",
      "joinedAt": "2026-10-08T12:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalItems": 1,
  "totalPages": 1
}
```

Query parameters are `page` (zero-based, default `0`) and `size` (default
`20`, range `1–100`).

---

## Add Member

```http
POST /api/v1/vaults/{vaultId}/members
```

Request:

```json
{
  "userId": "uuid"
}
```

Authorization:

```text
OWNER
```

Only an active `OWNER` may add a member. An existing inactive membership is
reactivated rather than replaced, preserving membership history. A user may
have only one active membership in a vault.

---

## Remove Member

```http
POST /api/v1/vaults/{vaultId}/members/{memberId}/remove
```

Historical financial records must remain valid.

Removing membership does not erase historical contributions or expenses.
Only an active `OWNER` may remove a member. The last active owner cannot be
removed or leave. Removed members cannot perform new vault operations.

---

# 16. Vault Contributions

Contribution is a financial command.

## Create Contribution

```http
POST /api/v1/vaults/{vaultId}/contributions
```

Headers:

```http
Idempotency-Key: <unique-key>
```

Request:

```json
{
  "sourceAccountId": "uuid",
  "amount": 1000000,
  "currency": "VND",
  "description": "Monthly contribution",
  "contributedAt": "2026-10-08T12:00:00Z"
}
```

`description` is optional. The authenticated active member is the contributor;
the caller does not provide a member identity. If omitted, `description` is
omitted from the response.

Backend validates:

```text
authenticated user
        ↓
active vault membership
        ↓
source account ownership
        ↓
source account balance/rules
        ↓
amount
        ↓
currency
```

Then atomically:

```text
personal account balance -= amount
vault balance += amount
contribution created
transaction created
```

The source-account currency must equal the vault currency. Contributions
cannot make a personal account negative. All personal-account and vault
ledger/balance changes occur atomically through the explicit Personal Finance
application command/port; Shared Finance does not access Personal Finance
repositories directly.

---

## List Contributions

```http
GET /api/v1/vaults/{vaultId}/contributions
```

Optional:

```text
?page=0&size=20
&memberId={id}
&from={date}
&to={date}
```

Response:

```json
{
  "items": [
    {
      "id": "uuid",
      "vaultId": "uuid",
      "memberId": "uuid",
      "sourceAccountId": "uuid",
      "amount": 1000000,
      "currency": "VND",
      "description": "Monthly contribution",
      "contributedAt": "2026-10-08T12:00:00Z",
      "transactionId": "uuid",
      "status": "ACTIVE"
    }
  ],
  "page": 0,
  "size": 20,
  "totalItems": 1,
  "totalPages": 1
}
```

---

# 17. Shared Expenses

## Create Shared Expense

```http
POST /api/v1/vaults/{vaultId}/expenses
```

Headers:

```http
Idempotency-Key: <unique-key>
```

Request:

```json
{
  "amount": 900000,
  "currency": "VND",
  "description": "Dinner",
  "expenseDate": "2026-10-08T19:00:00Z",
  "funding": {
    "type": "VAULT"
  },
  "splits": [
    {
      "memberId": "member-a",
      "amount": 300000
    },
    {
      "memberId": "member-b",
      "amount": 300000
    },
    {
      "memberId": "member-c",
      "amount": 300000
    }
  ]
}
```

Invariant:

```text
sum(splits.amount) = expense.amount
```

The sum of splits must equal the expense amount; each split amount is
positive; each member may appear once and must be an active member of the
vault. Only an active member may create an expense.

---

# 18. Member-Paid Shared Expense

A member may pay a shared expense personally.

Request:

```json
{
  "amount": 900000,
  "currency": "VND",
  "description": "Dinner",
  "expenseDate": "2026-10-08T19:00:00Z",
  "funding": {
    "type": "MEMBER",
    "payerMemberId": "member-a",
    "sourceAccountId": "account-a"
  },
  "splits": [
    {
      "memberId": "member-a",
      "amount": 300000
    },
    {
      "memberId": "member-b",
      "amount": 300000
    },
    {
      "memberId": "member-c",
      "amount": 300000
    }
  ]
}
```

Backend validates that:

```text
payerMemberId belongs to vault
sourceAccountId belongs to payer
```

The authenticated caller must equal `payerMemberId`. The source account
currency must match the vault currency.

The payer's account is decreased.

The expense is allocated through the split records.

---

# 19. Shared Expense Queries

## Get Expense

```http
GET /api/v1/vaults/{vaultId}/expenses/{expenseId}
```

Returns `200` with the expense resource, including funding, payer (if
member-funded), splits, currency, transaction ID, and status.

Expense resource:

```json
{
  "id": "uuid",
  "vaultId": "uuid",
  "amount": 900000,
  "currency": "VND",
  "description": "Dinner",
  "expenseDate": "2026-10-08T19:00:00Z",
  "funding": {
    "type": "MEMBER",
    "sourceAccountId": "uuid"
  },
  "paidByMemberId": "uuid",
  "splits": [
    { "memberId": "uuid", "amount": 450000 },
    { "memberId": "uuid", "amount": 450000 }
  ],
  "transactionId": "uuid",
  "status": "ACTIVE"
}
```

For vault-funded expenses, `funding.type` is `VAULT` and
`paidByMemberId` is `null`; `sourceAccountId` is omitted.

---

## List Expenses

```http
GET /api/v1/vaults/{vaultId}/expenses
```

Optional:

```text
?page=0&size=20
&from={date}
&to={date}
&memberId={memberId}
```

Returns `200` with the expense resource shape in `items`, plus `page`,
`size`, `totalItems`, and `totalPages`.

---

# 20. Member Responsibility

Member responsibility is a **derived financial view**, not a direct CRUD entity.

## Get Member Responsibility

```http
GET /api/v1/vaults/{vaultId}/members/{memberId}/responsibility
```

Example:

```json
{
  "memberId": "uuid",
  "currency": "VND",
  "contributed": 3000000,
  "allocatedExpenses": 1500000,
  "paidOnBehalf": 500000,
  "settled": 0,
  "netPosition": 2000000
}
```

The exact settlement calculation must be finalized before implementing settlement functionality.

This endpoint must not allow the client to directly modify responsibility.

---

# 21. Error Contract

All API errors use a consistent structure.

Example:

```json
{
  "code": "ACCOUNT_NOT_FOUND",
  "message": "Account was not found.",
  "traceId": "..."
}
```

Validation example:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed.",
  "fields": {
    "amount": "Amount must be greater than zero."
  },
  "traceId": "..."
}
```

---

# 22. HTTP Status Codes

Initial mapping:

| Status | Meaning                                  |
| ------ | ---------------------------------------- |
| 200    | Successful query/update                  |
| 201    | Resource/financial command created       |
| 204    | Successful operation with no body        |
| 400    | Invalid request                          |
| 401    | Unauthenticated                          |
| 403    | Unauthorized                             |
| 404    | Resource not found                       |
| 409    | Conflict / concurrency / duplicate state |
| 422    | Business validation failure              |
| 429    | Rate limit exceeded                      |
| 503    | Required token issuer unavailable        |
| 500    | Unexpected server error                  |

---

# 23. Business Error Codes

Initial important errors:

```text
AUTHENTICATION_FAILED
ACCESS_DENIED
IDENTITY_CONFLICT
TOKEN_ISSUER_UNAVAILABLE

ACCOUNT_NOT_FOUND
ACCOUNT_NOT_OWNED
ACCOUNT_ARCHIVED

CATEGORY_NOT_FOUND
CATEGORY_NOT_OWNED

TRANSACTION_NOT_FOUND
TRANSACTION_ALREADY_REVERSED
TRANSACTION_CONCURRENCY_CONFLICT

INVALID_TRANSACTION_AMOUNT
TRANSACTION_ITEM_TOTAL_MISMATCH

VAULT_NOT_FOUND
VAULT_NOT_ACTIVE
VAULT_MEMBERSHIP_REQUIRED
VAULT_OWNER_REQUIRED

MEMBER_NOT_FOUND
MEMBER_ALREADY_EXISTS
MEMBER_NOT_ACTIVE

CONTRIBUTION_INVALID
INSUFFICIENT_BALANCE

SHARED_EXPENSE_INVALID
EXPENSE_SPLIT_TOTAL_MISMATCH
EXPENSE_SPLIT_MEMBER_INVALID

IDEMPOTENCY_KEY_REUSED
IDEMPOTENCY_REQUEST_MISMATCH
```

Error codes are stable API contracts.

Human-readable messages may change.

---

# 24. Idempotency Contract

Financial commands require:

```http
Idempotency-Key: <client-generated-key>
```

Examples:

```text
POST /transactions/income
POST /transactions/expense
POST /transactions/{id}/reverse
POST /vaults/{id}/contributions
POST /vaults/{id}/expenses
```

The server stores enough information to detect:

### Same request

Return the original result.

### Same key + different request

Return:

```text
409 Conflict
```

with:

```text
IDEMPOTENCY_REQUEST_MISMATCH
```

This prevents accidental duplicate financial operations.

---

# 25. Concurrency

Financial mutations must account for concurrent requests.

Example:

```text
Account balance = 500,000

Request A → spend 400,000
Request B → spend 300,000
```

The system must not allow both operations to incorrectly succeed because they independently read the same stale balance.

Possible implementation mechanisms:

```text
optimistic locking
+
database atomic updates
+
row-level locking where required
```

The final mechanism is an implementation decision based on the application service/use case.

The API contract must remain independent of that implementation.

---

# 26. Pagination

Collection endpoints should use pagination.

Initial format:

```text
?page=0&size=20
```

Response:

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalItems": 100,
  "totalPages": 5
}
```

For high-volume transaction history, cursor pagination may be introduced later.

The initial page is zero-based. `page` defaults to `0`; `size` defaults to
`20` and must be between `1` and `100`. Members, contributions, expenses,
accounts, categories, and vault collections return this metadata in addition
to `items`.

Do not introduce cursor complexity before it is needed.

---

# 27. Sorting

Default transaction sorting:

```text
transactionDate DESC
```

The backend should define allowed sortable fields rather than accepting arbitrary SQL-derived field names.

Example:

```text
?sort=transactionDate,desc
```

---

# 28. AI API Boundary

AI functionality is exposed through explicit application-level APIs.

The AI API must not expose database operations.

Example:

```http
POST /api/v1/ai/interpret
```

Request:

```json
{
  "message": "Ăn trưa 80 nghìn"
}
```

Potential response:

```json
{
  "intent": "CREATE_EXPENSE",
  "confidence": 0.94,
  "parameters": {
    "amount": 80000,
    "currency": "VND",
    "suggestedCategory": "FOOD"
  }
}
```

This endpoint does **not** directly create the expense.

The flow is:

```text
User
 ↓
AI API
 ↓
Structured Intent
 ↓
Application Command
 ↓
Authorization
 ↓
Validation
 ↓
Domain
 ↓
PostgreSQL
```

---

# 29. AI Write Operations

For an AI-assisted financial operation:

```text
POST /api/v1/ai/...
```

must never bypass the normal financial command path.

For example:

```text
AI
 ↓
CreateExpenseIntent
 ↓
CreateExpenseCommand
 ↓
ExpenseApplicationService
 ↓
Authorization
 ↓
Domain validation
 ↓
Transaction
```

Therefore:

> AI is another client of the Application layer, not another business-logic layer.

---

# 30. DTO Rules

API DTOs must not expose persistence entities directly.

Do not return:

```text
JPA Entity
```

from controllers.

Use:

```text
Request DTO
Response DTO
```

with explicit mapping.

This prevents:

- persistence leakage
- accidental field exposure
- API/domain coupling
- lazy-loading problems
- uncontrolled serialization

---

# 31. Controller Responsibility

Controllers should only handle:

```text
HTTP request
 ↓
authentication context
 ↓
DTO validation
 ↓
application command/query
 ↓
response mapping
```

Controllers must not contain:

```text
balance calculation
authorization business rules
split calculation
financial mutation
database access
AI decision logic
```

---

# 32. Application Command Mapping

The API maps to explicit application use cases.

Examples:

```text
POST /transactions/income
        ↓
RecordIncomeCommand

POST /transactions/expense
        ↓
RecordExpenseCommand

POST /transactions/{id}/reverse
        ↓
ReverseTransactionCommand

POST /vaults/{id}/contributions
        ↓
CreateContributionCommand

POST /vaults/{id}/expenses
        ↓
CreateSharedExpenseCommand

GET /transactions
        ↓
GetTransactionHistoryQuery
```

This keeps HTTP concerns separate from business behavior.

---

# 33. API Module Ownership

| API Area                                  | Owning Module    |
| ----------------------------------------- | ---------------- |
| `/auth`                                   | Identity         |
| `/me`                                     | Identity         |
| `/accounts`                               | Personal Finance |
| `/categories`                             | Personal Finance |
| `/transactions`                           | Personal Finance |
| `/calendar`                               | Personal Finance |
| `/summary`                                | Personal Finance |
| `/vaults`                                 | Shared Finance   |
| `/vaults/{id}/members`                    | Shared Finance   |
| `/vaults/{id}/contributions`              | Shared Finance   |
| `/vaults/{id}/expenses`                   | Shared Finance   |
| `/vaults/{id}/members/.../responsibility` | Shared Finance   |
| `/ai`                                     | AI               |

A module owns its API contract and application use cases.

---

# 34. API Versioning

Initial version:

```text
/api/v1
```

Breaking API changes require a new API version.

Non-breaking additions should not require a new version.

Do not introduce versioning complexity beyond this until there is a real compatibility requirement.

---

# 35. API Security Rules

Every protected endpoint must derive the current user from authentication context.

Never trust:

```text
userId
ownerId
memberId
accountId
```

from the client as proof of authorization.

The backend must verify relationships.

For example:

```text
Authenticated User
        ↓
Account ID
        ↓
Does account.user_id == authenticated user?
        ↓
YES → continue
NO  → reject
```

For vault operations:

```text
Authenticated User
        ↓
Vault ID
        ↓
Active Membership?
        ↓
Role sufficient?
        ↓
continue
```

---

# 36. API Design Guardrails

The following are mandatory:

1. Financial writes are commands, not generic CRUD.
2. Financial writes require idempotency where retry can duplicate money movement.
3. Backend owns all financial validation.
4. Backend owns authorization.
5. Client never controls authoritative balance.
6. AI never bypasses Application services.
7. AI never accesses the database directly.
8. Controllers remain thin.
9. Persistence entities are not exposed as API contracts.
10. Financial history is not hard-deleted.
11. Cross-module persistence access is prohibited.
12. API complexity must be justified by actual product requirements.

---

# 37. MVP API Surface

The initial MVP should implement only the following:

### Identity

```text
POST   /auth/register
POST   /auth/login
POST   /auth/refresh
POST   /auth/logout
GET    /me
PATCH  /me
```

### Personal Finance

```text
GET    /accounts
POST   /accounts
GET    /accounts/{id}
PATCH  /accounts/{id}
POST   /accounts/{id}/archive

GET    /categories
POST   /categories
PATCH  /categories/{id}
POST   /categories/{id}/archive

POST   /transactions/income
POST   /transactions/expense
GET    /transactions
GET    /transactions/{id}
PATCH  /transactions/{id}
POST   /transactions/{id}/reverse

GET    /calendar
GET    /summary
```

### Shared Finance

```text
POST   /vaults
GET    /vaults
GET    /vaults/{id}

GET    /vaults/{id}/members
POST   /vaults/{id}/members
POST   /vaults/{id}/members/{memberId}/remove

POST   /vaults/{id}/contributions
GET    /vaults/{id}/contributions

POST   /vaults/{id}/expenses
GET    /vaults/{id}/expenses
GET    /vaults/{id}/expenses/{expenseId}

GET    /vaults/{id}/members/{memberId}/responsibility
```

AI endpoints are **not required for the deterministic MVP**.

---

# 38. Post-MVP API

Future endpoints may include:

```text
POST /transactions/{id}/receipt
POST /ai/ocr
POST /ai/interpret
POST /ai/assistant
POST /ai/agent/plan
POST /vaults/{id}/settlements
```

These must be introduced only after the underlying deterministic domain contracts are stable.

---

# 39. Final API Architecture

The complete request flow is:

```text
Flutter
   │
   ▼
REST Controller
   │
   ▼
Request DTO
   │
   ▼
Application Command / Query
   │
   ├── Authorization
   │
   ├── Validation
   │
   ▼
Domain
   │
   ▼
Repository / Port
   │
   ▼
PostgreSQL
```

For AI-assisted operations:

```text
Flutter
   │
   ▼
AI API
   │
   ▼
AI Interpretation
   │
   ▼
Structured Intent
   │
   ▼
Application Command
   │
   ▼
Authorization
   │
   ▼
Domain
   │
   ▼
PostgreSQL
```

The API therefore preserves the core SaveMon principle:

> **AI can understand intent, but only deterministic application/domain logic can change financial state.**

# SaveMon — Architecture

## 1. Purpose

This document defines the technical architecture of SaveMon.

SaveMon uses a **Modular Monolith** architecture with **Clean Architecture principles**.

The architecture is designed to:

- Preserve clear business boundaries.
- Keep financial logic deterministic.
- Prevent uncontrolled coupling between modules.
- Support rapid development by AI agents.
- Keep the system simple enough for an individual developer.
- Allow future extraction of modules into services if genuinely required.
- Provide a safe integration boundary for AI capabilities.

---

# 2. Architecture Principles

## 2.1 Modular Monolith First

SaveMon is initially deployed as a single backend application.

```text
                    SaveMon Backend
                         │
        ┌────────────────┼────────────────┐
        │                │                │
    Identity       Personal Finance   Shared Finance
        │                │                │
        └────────────────┼────────────────┘
                         │
                    PostgreSQL
```

We do not introduce microservices unless there is a demonstrated business or operational reason.

---

## 2.2 Domain Boundaries Over Technical Layers

Modules are organized around business capabilities rather than technical concerns.

Preferred:

```text
identity/
personal-finance/
shared-finance/
```

Avoid a globally shared structure such as:

```text
controllers/
services/
repositories/
entities/
```

where unrelated business domains become mixed together.

---

## 2.3 Clean Architecture

Each business module follows a dependency direction similar to:

```text
┌──────────────────────────────────────┐
│           Infrastructure             │
│  PostgreSQL / Redis / Storage / AI  │
└──────────────────┬───────────────────┘
                   ↓
┌──────────────────────────────────────┐
│            Application               │
│ Commands / Queries / Use Cases       │
└──────────────────┬───────────────────┘
                   ↓
┌──────────────────────────────────────┐
│              Domain                  │
│ Entities / Rules / Value Objects     │
└──────────────────────────────────────┘
```

The domain must not depend on infrastructure.

---

# 3. High-Level System Architecture

```text
                           Flutter Mobile
                                │
                                │ HTTPS
                                ↓
                     ┌─────────────────────┐
                     │   API / Interface   │
                     └──────────┬──────────┘
                                │
                    ┌───────────┴───────────┐
                    │     Application       │
                    │ Commands / Queries    │
                    └───────────┬───────────┘
                                │
              ┌─────────────────┼─────────────────┐
              │                 │                 │
              ↓                 ↓                 ↓
        Identity Domain   Personal Finance   Shared Finance
              │                 │                 │
              └─────────────────┼─────────────────┘
                                │
                         PostgreSQL
                                │
                 ┌──────────────┴──────────────┐
                 ↓                             ↓
               Redis                      Object Storage
```

AI capabilities sit outside the deterministic domain:

```text
Flutter
   │
   ↓
Application Layer
   │
   ├──────────────→ Deterministic Domain
   │
   └──────────────→ AI Layer
                         │
                         ↓
                  Structured Output
                         │
                         ↓
                  Approved Command
                         │
                         ↓
                 Deterministic Domain
```

---

# 4. Backend Module Structure

The backend is organized into bounded business modules.

```text
backend/
└── src/
    └── main/
        └── java/
            └── .../
                ├── identity/
                ├── personalfinance/
                ├── sharedfinance/
                ├── ai/
                └── shared/
```

Each module owns its internal implementation.

Conceptually:

```text
identity/
├── domain/
├── application/
├── infrastructure/
└── interfaces/

personalfinance/
├── domain/
├── application/
├── infrastructure/
└── interfaces/

sharedfinance/
├── domain/
├── application/
├── infrastructure/
└── interfaces/

ai/
├── application/
├── infrastructure/
└── interfaces/
```

The exact Java package structure can be refined during implementation.

---

# 5. Identity Module

## Responsibilities

- User identity
- Authentication
- Authorization context
- User profile

The Identity module is responsible for determining who the caller is.

It should not own personal financial rules.

---

# 6. Personal Finance Module

## Responsibilities

- Personal accounts
- Categories
- Personal transactions
- Transaction items
- Financial history
- Personal financial summaries

Core concepts:

```text
Account
Category
Transaction
Transaction Item
Receipt metadata
```

The module owns personal financial business rules.

---

# 7. Shared Finance Module

## Responsibilities

- Shared vaults
- Membership
- Contributions
- Shared expenses
- Expense splits
- Member responsibility

Core concepts:

```text
Shared Vault
Vault Member
Contribution
Shared Expense
Transaction Split
```

Shared Finance must not directly manipulate Personal Finance internals.

Communication should occur through explicit application contracts.

---

# 8. AI Module

The AI module provides intelligence capabilities without becoming the source of financial truth.

Responsibilities may include:

- Natural-language interpretation
- OCR/extraction
- Classification
- Recommendation
- Assistant orchestration
- Agent planning
- Structured output generation

The AI module must not directly access:

```text
PostgreSQL
Redis financial state
Personal Finance repositories
Shared Finance repositories
```

Instead:

```text
AI
 ↓
Structured Intent / Recommendation
 ↓
Application Command
 ↓
Authorization
 ↓
Validation
 ↓
Domain
```

---

# 9. Shared Module

A small shared module may contain genuinely cross-cutting technical concepts.

Examples:

- Error model
- Result types where justified
- Security abstractions
- Time abstraction
- ID generation abstraction
- Common technical utilities

The shared module must remain intentionally small.

It must not become a dumping ground for business logic.

### Forbidden

```text
shared/
├── TransactionService
├── AccountService
├── VaultService
└── MiscBusinessLogic
```

Business behavior belongs to its owning module.

---

# 10. Module Dependency Rules

The dependency graph should remain directional.

```text
                    Identity
                       ↑
                       │
                authentication
                       │
                       │
Flutter → Interfaces → Application → Domain
                            ↑
                            │
                      Infrastructure
```

More specifically:

```text
Identity
   ↑
Personal Finance
   ↑
Shared Finance
```

is **not** a required hierarchy.

Instead, modules should depend on explicit contracts.

### Rule

> A module must not reach into another module's internal implementation.

For example:

```text
Shared Finance
    ❌ → PersonalFinanceRepository
    ❌ → PersonalFinanceEntity
    ❌ → PersonalFinanceDatabaseTable
```

Instead:

```text
Shared Finance
    ↓
Approved application/domain contract
```

---

# 11. Clean Architecture Layers

Each module conceptually contains four layers.

## 11.1 Domain

Contains:

- Entities
- Value Objects
- Aggregates
- Domain rules
- Domain services where genuinely necessary
- Domain events

Domain must not depend on:

- Spring
- JPA
- PostgreSQL
- Redis
- HTTP
- Flutter
- AI SDKs

---

## 11.2 Application

Contains use cases and orchestration.

Examples:

```text
CreateExpense
RecordIncome
UpdateTransaction
CreateVault
RecordContribution
RecordSharedExpense
SplitExpense
```

Application layer is responsible for:

- Authorization orchestration
- Loading required data
- Invoking domain behavior
- Transaction boundaries
- Calling external ports
- Returning application results

Application layer must not contain duplicated domain rules.

---

## 11.3 Infrastructure

Contains implementations of technical dependencies.

Examples:

```text
PostgreSQL
JPA
Redis
Object Storage
JWT implementation
AI provider SDK
OCR provider
```

Infrastructure implements interfaces/ports required by the application or domain.

---

## 11.4 Interfaces

Responsible for external interaction.

Examples:

```text
REST Controller
Request DTO
Response DTO
Exception mapping
Authentication adapter
```

Controllers must remain thin.

Preferred:

```text
Controller
    ↓
Application Use Case
    ↓
Domain
```

Avoid:

```text
Controller
    ↓
Repository
```

---

# 12. Dependency Rule

The fundamental rule is:

```text
Dependencies point inward.
```

For example:

```text
Interfaces
    ↓
Application
    ↓
Domain

Infrastructure
    ↓
Application / Domain abstractions
```

The domain must remain independent of implementation technology.

---

# 13. Application Command Boundary

All state-changing operations should pass through application commands/use cases.

Example:

```text
CreateExpenseCommand
RecordIncomeCommand
RecordContributionCommand
RecordSharedExpenseCommand
SplitExpenseCommand
```

The application layer performs:

```text
Request
 ↓
Authentication Context
 ↓
Authorization
 ↓
Load Aggregate
 ↓
Invoke Domain Behavior
 ↓
Persist
 ↓
Commit
 ↓
Audit / Events
```

---

# 14. Query Boundary

Read operations may use dedicated queries.

Example:

```text
GetTransactionHistory
GetCalendarTransactions
GetAccountSummary
GetVaultSummary
GetMemberResponsibility
```

Queries should not mutate financial state.

The architecture may use a pragmatic CQRS-style separation without introducing a distributed/event-sourced CQRS system.

---

# 15. Transaction Boundaries

Financial state changes must be atomic.

For example:

```text
Record Expense
    │
    ├── Validate Account
    ├── Create Transaction
    ├── Create Transaction Items
    ├── Update required financial state
    └── Persist
```

These operations must commit together.

If any required operation fails:

```text
ROLLBACK
```

Partial financial state is unacceptable.

---

# 16. Concurrency

Financial operations must account for concurrent requests.

Potential scenarios:

```text
Mobile retry
Two devices
Concurrent vault operations
Concurrent account updates
```

Concurrency control must ensure that financial invariants cannot be violated.

The exact PostgreSQL/JPA strategy is defined in DB and technical design documents.

Possible mechanisms include:

- Optimistic locking
- Database constraints
- Atomic updates
- Appropriate transaction isolation
- Idempotency keys

The architecture does not require one universal mechanism for every operation.

---

# 17. Idempotency

Mobile clients may retry requests because of:

- Network timeout
- Connection loss
- App restart
- User retry

Financial commands that can accidentally create duplicate state should support idempotency.

Example:

```text
POST Record Expense
        │
        ↓
Idempotency Key
        │
        ↓
Check previous result
        │
    ┌───┴───┐
    │       │
Existing   New
    │       │
Return    Execute
result    command
```

The exact storage and expiration policy are defined during DB/API design.

---

# 18. Security Architecture

Security follows:

```text
Authentication
      ↓
Identity
      ↓
Authorization
      ↓
Application Command
      ↓
Domain Validation
      ↓
Persistence
```

Authentication alone is insufficient.

Every protected financial operation must verify resource ownership or membership.

Examples:

```text
Personal Account
    → user owns account

Personal Transaction
    → user owns relevant financial context

Shared Expense
    → user has valid vault permission

Split Participant
    → participant belongs to vault
```

---

# 19. JWT Authentication

SaveMon uses stateless JWT access-token authentication.

Conceptually:

```text
Login
 ↓
Authentication
 ↓
Access Token
 ↓
API Request
 ↓
JWT Validation
 ↓
Authenticated User Context
```

Access tokens should remain short-lived according to the security policy.

Refresh-token handling may use Redis for revocation/session-related supporting concerns.

Authentication implementation details are defined separately from domain logic.

---

# 20. Redis

Redis is a supporting infrastructure component.

Potential uses include:

- Refresh-token revocation
- Temporary state
- Rate limiting
- Caching where justified
- Idempotency support where appropriate

Redis must not become the source of truth for financial state.

```text
PostgreSQL
    = financial source of truth

Redis
    = supporting infrastructure
```

---

# 21. PostgreSQL

PostgreSQL is the primary persistent store for deterministic financial state.

It stores:

- Users
- Accounts
- Categories
- Transactions
- Transaction items
- Vaults
- Memberships
- Contributions
- Shared expenses
- Splits
- Relevant audit metadata

Database schema and constraints are defined in `DB_SCHEMA.md`.

---

# 22. File Storage

Receipt/image binary data should not be stored directly in PostgreSQL.

Preferred architecture:

```text
Transaction
    │
    └── Receipt Metadata
             │
             └── Object Storage Reference
                         │
                         ↓
                   Object Storage
```

Database stores metadata/reference.

Object storage stores binary content.

Authorization must be enforced before accessing the file.

---

# 23. AI Architecture Boundary

AI is an external intelligence capability.

The architecture must enforce:

```text
                 ┌─────────────┐
                 │     AI      │
                 └──────┬──────┘
                        │
                Structured Output
                        │
                        ↓
               Application Boundary
                        │
                  Authorization
                        │
                    Validation
                        │
                        ↓
                     Domain
                        │
                        ↓
                       DB
```

AI cannot bypass the application boundary.

---

# 24. AI Tool Boundary

When agents are introduced, tools must be explicitly defined.

Example:

```text
Agent
 ├── get_account_summary()
 ├── get_transactions()
 ├── create_expense()
 ├── create_income()
 └── create_shared_expense()
```

Each tool maps to an approved application operation.

There must be no generic:

```text
execute_sql()
execute_code()
call_internal_service()
```

tool exposed to an AI agent.

---

# 25. AI Write Operations

AI-generated write requests follow:

```text
User Intent
     ↓
AI
     ↓
Structured Intent
     ↓
Application Command
     ↓
Authentication
     ↓
Authorization
     ↓
Business Validation
     ↓
Domain Operation
     ↓
Database Transaction
     ↓
Audit
```

AI output is considered untrusted input.

---

# 26. Audit

Financially significant and security-sensitive operations should be auditable.

Examples:

```text
Authentication events
Financial mutations
Vault membership changes
Shared expense operations
AI-triggered financial commands
```

Audit design should record enough information to answer:

```text
Who?
What?
When?
Which resource?
What action?
What result?
```

The exact audit schema is defined separately.

---

# 27. Error Handling

Errors should be represented consistently across the application boundary.

Conceptually:

```text
Domain Error
     ↓
Application Error
     ↓
Interface Error
     ↓
HTTP Response
```

Internal implementation details must not leak through API responses.

Examples of business errors:

```text
ACCOUNT_NOT_FOUND
UNAUTHORIZED_ACCOUNT
INVALID_TRANSACTION_AMOUNT
CATEGORY_NOT_FOUND
VAULT_ACCESS_DENIED
NOT_A_VAULT_MEMBER
INVALID_SPLIT_TOTAL
```

The final error taxonomy is defined during API design.

---

# 28. API Boundary

Flutter communicates with SaveMon through explicit API contracts.

```text
Flutter
   ↓
HTTP/JSON
   ↓
API Interface
   ↓
Application
```

Frontend must not depend on internal domain entities.

Backend API contracts are defined in `API.md`.

---

# 29. Flutter Boundary

The mobile application should follow a similar separation:

```text
Presentation
    ↓
Application / State Management
    ↓
Domain-oriented models
    ↓
Data / API layer
```

Flutter must:

- Render approved UI.
- Collect user input.
- Call backend APIs.
- Handle application state.

Flutter must not become the authority for financial business rules.

Backend remains authoritative.

---

# 30. AI-Native Frontend Boundary

Generative UI, when introduced, must use an allowlisted schema.

Example:

```text
AI
 ↓
UI Intent
 ↓
Schema Validation
 ↓
Allowed Component Registry
 ↓
Flutter Renderer
```

AI must not send arbitrary executable UI code.

Example conceptual intent:

```text
{
  "type": "expense_summary",
  "period": "current_month"
}
```

Flutter maps the intent to known components.

---

# 31. Observability

The system should provide enough observability to diagnose:

- API failures
- Database failures
- Slow requests
- Authentication failures
- Redis failures
- AI failures
- Financial command failures

Important financial operations should have correlation/request identifiers.

AI operations should additionally be traceable to the originating user action where appropriate.

---

# 32. Testing Architecture

Testing follows the architecture layers.

### Domain Tests

Test:

- Business invariants
- Entity behavior
- Value objects
- Aggregate rules

### Application Tests

Test:

- Use cases
- Authorization orchestration
- Transaction behavior
- Port interactions

### Infrastructure Tests

Test:

- PostgreSQL persistence
- Redis integration
- Object storage integration
- External AI integrations

### API Tests

Test:

- Authentication
- Authorization
- Request validation
- Response contracts
- Error handling

### End-to-End Tests

Test critical user journeys.

Examples:

```text
Create Account
 → Record Expense
 → View Balance
```

```text
Create Vault
 → Add Member
 → Record Contribution
 → Record Shared Expense
 → Split Expense
 → View Responsibility
```

---

# 33. AI Testing

AI features require additional tests.

Test categories include:

- Extraction accuracy
- Structured output validation
- Invalid model output
- Prompt injection attempts
- Tool misuse
- Unauthorized requests
- Hallucinated data
- Duplicate commands
- Agent failure/retry
- Human approval boundaries

AI tests must verify that model errors cannot corrupt deterministic financial state.

---

# 34. Deployment Model

Initial deployment:

```text
                   Internet
                      │
                      ↓
                Load Balancer
                      │
                      ↓
              SaveMon Backend
                      │
          ┌───────────┼───────────┐
          ↓           ↓           ↓
      PostgreSQL    Redis    Object Storage
```

The exact cloud provider and infrastructure topology are DevOps concerns.

The architecture should remain deployable as a single backend service.

---

# 35. Scalability Strategy

SaveMon does not optimize for hypothetical large-scale traffic during MVP.

Initial priorities:

```text
Correctness
Security
Maintainability
Development speed
Observability
```

When scale becomes necessary, preferred evolution is:

```text
Modular Monolith
      ↓
Identify bottleneck
      ↓
Optimize module/database/query
      ↓
Introduce caching
      ↓
Introduce async processing
      ↓
Extract service only if justified
```

Microservices are not the default scalability strategy.

---

# 36. Architecture Decision Summary

The current architecture decisions are:

| Decision                    | Choice                                     |
| --------------------------- | ------------------------------------------ |
| Backend architecture        | Modular Monolith                           |
| Architecture style          | Clean Architecture principles              |
| Backend                     | Java 21 + Spring Boot 3.x                  |
| Database                    | PostgreSQL                                 |
| Cache/supporting infra      | Redis                                      |
| Mobile                      | Flutter / Dart                             |
| Authentication              | Stateless JWT access tokens                |
| Financial source of truth   | PostgreSQL + deterministic domain          |
| File storage                | Object Storage                             |
| AI architecture             | Intelligence layer over deterministic core |
| AI DB access                | Prohibited                                 |
| AI arbitrary code execution | Prohibited                                 |
| Inter-module access         | Explicit contracts                         |
| Financial mutations         | Application commands                       |
| Query model                 | Dedicated application queries where useful |
| Distributed microservices   | Not initially                              |
| Event-driven architecture   | Only where justified                       |
| Generative UI               | Structured allowlisted schema              |
| Human approval              | Required for important agent actions       |

---

# 37. Architecture Invariants

The following architectural rules are mandatory:

### Rule 1

> Domain must not depend on infrastructure.

### Rule 2

> AI must not directly modify financial state.

### Rule 3

> Financial mutations must pass through application boundaries.

### Rule 4

> Authorization must occur before financial mutation.

### Rule 5

> Domain validation cannot be bypassed by AI or UI.

### Rule 6

> PostgreSQL is the source of truth for financial state.

### Rule 7

> Redis is not a financial source of truth.

### Rule 8

> Modules cannot access another module's internal persistence implementation.

### Rule 9

> Controllers must not contain business logic.

### Rule 10

> Flutter is not the authority for financial business rules.

### Rule 11

> AI-generated output is untrusted input.

### Rule 12

> Architecture complexity must be justified by an actual product or operational requirement.

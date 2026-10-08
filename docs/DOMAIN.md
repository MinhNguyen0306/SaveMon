# SaveMon — Domain Model

## 1. Purpose

This document defines the business domain model of SaveMon.

It establishes:

- Domain modules
- Core concepts
- Entities
- Value objects
- Aggregate boundaries
- Relationships
- Business invariants
- Domain rules
- Ownership and lifecycle rules

This document is technology-independent.

It does not define:

- Java classes
- JPA mappings
- REST APIs
- PostgreSQL implementation
- Flutter models
- Redis implementation
- Infrastructure details

Those concerns belong to later technical design documents.

---

# 2. Domain Overview

SaveMon is composed of two primary business areas:

```text
SaveMon
│
├── Identity
│
├── Personal Finance
│
└── Shared Finance
```

Supporting concepts:

```text
Personal Finance
├── Account
├── Category
├── Transaction
├── Transaction Item
└── Receipt
```

Shared Finance:

```text
Shared Finance
├── Shared Vault
├── Vault Member
├── Contribution
├── Shared Expense
└── Transaction Split
```

The domain should remain centered around financial state and ownership.

AI is **not a domain owner**.

AI belongs to an intelligence/application layer that interacts with the domain through controlled commands and queries.

---

# 3. Domain Modules

## 3.1 Identity Module

Responsible for:

- User identity
- Authentication-related user information
- User profile

Core concept:

```text
User
```

---

## 3.2 Personal Finance Module

Responsible for:

- Personal accounts
- Income
- Expenses
- Categories
- Transaction history
- Transaction items

Core concepts:

```text
Account
Category
Transaction
Transaction Item
```

---

## 3.3 Shared Finance Module

Responsible for:

- Shared vaults
- Membership
- Contributions
- Shared expenses
- Expense splits
- Member financial responsibility

Core concepts:

```text
Shared Vault
Vault Member
Contribution
Shared Expense
Transaction Split
```

---

# 4. User

## Definition

A User represents a person who has a SaveMon identity.

### Responsibilities

A user may:

- Own personal accounts.
- Create transactions.
- Create/manage categories.
- Create or participate in shared vaults.
- Record contributions.
- Participate in shared expenses.

### Ownership

A user owns personal financial resources.

A user does not automatically have access to another user's financial resources.

### Invariants

- User identity must be unique according to the authentication identity rule.
- User must be authenticated before accessing protected financial resources.

---

# 5. Account

## Definition

An Account represents a financial container owned by a user.

Examples:

```text
Cash
Bank Account
E-Wallet
```

### Ownership

```text
User
  │
  └── Account
```

An account belongs to exactly one user.

### Responsibilities

An account provides the financial context for personal transactions.

### Invariants

1. Account must have an owner.
2. Only authorized users can operate on the account.
3. Financial state associated with the account must remain consistent.
4. An account cannot be used as another user's personal account.

### Lifecycle

An account may become inactive without destroying historical financial information.

---

# 6. Category

## Definition

A Category classifies a transaction item.

Examples:

```text
Food
├── Restaurant
├── Groceries
└── Coffee

Transport
├── Taxi
├── Fuel
└── Public Transport
```

### Ownership

Categories are associated with a user or an explicitly defined shared/system scope.

The initial MVP should primarily support user-owned categories.

### Hierarchy

A category may have:

```text
parent_category
```

or no parent.

Therefore:

```text
Category
   ├── parent
   └── children
```

### Invariants

- A category cannot be its own parent.
- A category hierarchy must not contain cycles.
- A transaction item may reference a valid category.
- Historical transactions must remain interpretable when categories are changed.

### Lifecycle

Categories should support deactivation/archival rather than destructive deletion when historical transactions depend on them.

---

# 7. Transaction

## Definition

A Transaction represents a financial event that changes or records financial state.

The initial domain supports:

```text
INCOME
EXPENSE
```

### Core Attributes

Conceptually:

```text
Transaction
├── identity
├── type
├── amount
├── transaction date
├── description
├── owner/context
└── items
```

Technical representation is intentionally deferred.

### Ownership

A personal transaction belongs to a user context and is associated with an account owned by that user.

A shared transaction belongs to a shared-vault context.

### Invariants

- Amount must be valid and positive.
- Transaction type must be valid.
- Transaction must belong to a valid financial context.
- Referenced account must be authorized.
- Transaction items must satisfy transaction-level financial rules.
- Financial state changes must be atomic.

---

# 8. Transaction Item

## Definition

A Transaction Item represents a detailed component of a transaction.

Example:

```text
Transaction = 500,000

Transaction Items:

Food       300,000
Transport  100,000
Other      100,000
```

### Purpose

Transaction items allow SaveMon to support detailed classification and future analytics.

### Category Relationship

```text
Transaction
   │
   ├── Transaction Item → Category
   ├── Transaction Item → Category
   └── Transaction Item → Category
```

### Invariants

- An item belongs to exactly one transaction.
- Item amount must be positive or otherwise satisfy the defined financial representation.
- The sum of item amounts must satisfy the transaction amount rule.

For a fully itemized transaction:

```text
Σ item.amount = transaction.amount
```

The exact support for partially itemized transactions is a later product/domain decision.

---

# 9. Receipt

## Definition

A Receipt represents evidence or metadata associated with a financial transaction.

A receipt may contain:

- File reference
- MIME type
- Original filename
- Extraction metadata
- Creation timestamp

### Ownership

Receipt access follows the authorization boundary of its associated transaction.

### Storage

The domain only cares about the receipt reference.

Actual file storage is an infrastructure concern.

Conceptually:

```text
Transaction
    │
    └── Receipt
          │
          └── File Reference
```

The file itself does not belong in the relational domain model.

---

# 10. Shared Vault

## Definition

A Shared Vault represents a financial context shared by multiple users.

Examples:

```text
Couple's household fund
Trip fund
Roommate fund
Family savings fund
```

### Responsibilities

A vault provides:

- Shared financial context
- Membership
- Contributions
- Shared expenses
- Expense allocation

### Ownership

A vault is not simply owned by one user in the same way as a personal account.

It has:

```text
Vault
  │
  └── Members
```

One member may have administrative privileges according to the membership rules.

### Invariants

- Vault must have at least one valid member.
- Every vault operation must occur within a valid vault context.
- Only authorized members can access protected vault information.
- Financial records associated with a vault must reference the same vault context.

---

# 11. Vault Member

## Definition

A Vault Member represents the relationship between a User and a Shared Vault.

Conceptually:

```text
User ←→ Vault
       │
       └── Membership
```

A membership is not merely a direct User/Vault relationship because the membership itself has business meaning.

Potential attributes include:

- Member identity
- Role
- Status
- Joined date

### Roles

The initial model may support roles such as:

```text
OWNER
MEMBER
```

Additional roles should not be introduced without a concrete requirement.

### Invariants

- A user cannot have duplicate active membership in the same vault.
- Only valid members may participate in shared financial activities.
- Membership status must be respected by authorization rules.

### Historical Rule

Removing/deactivating membership must not invalidate historical transactions.

---

# 12. Contribution

## Definition

A Contribution represents money contributed by a vault member to a shared vault.

Example:

```text
Alice contributes 1,000,000
Bob contributes   500,000
```

### Relationship

```text
Vault
  │
  └── Contribution
         └── Contributor → Vault Member
```

### Invariants

- Contribution belongs to exactly one vault.
- Contributor must be a valid member of the vault.
- Amount must be positive.
- Contribution must have a valid date.
- Contribution must not be attributed to a member of another vault.

---

# 13. Shared Expense

## Definition

A Shared Expense represents an expense occurring within a shared vault.

A shared expense may be funded by the shared vault or associated with a member who paid on behalf of the group, depending on the final financial model.

### Core Information

Conceptually:

```text
Shared Expense
├── Vault
├── Amount
├── Date
├── Description
└── Participants
```

### Invariants

- Expense belongs to exactly one vault.
- Expense amount must be positive.
- Actor must have appropriate vault permission.
- Participants must be valid vault members.
- Split allocations must satisfy the expense amount rule.

---

# 14. Transaction Split

## Definition

A Transaction Split represents the portion of a shared expense allocated to a vault member.

Example:

```text
Expense = 600,000

Alice → 300,000
Bob   → 200,000
Carol → 100,000
```

### Relationship

```text
Shared Expense
     │
     ├── Split → Member
     ├── Split → Member
     └── Split → Member
```

### Core Invariant

For a fully allocated expense:

```text
Σ split.amount = shared_expense.amount
```

### Participant Rule

A split participant must be an active/eligible member of the vault at the time the expense is created, according to the membership policy.

### Invalid States

The domain must reject:

```text
Negative split amount
Unknown participant
Participant outside vault
Split total != expense amount
Duplicate participant allocation
```

---

# 15. Member Financial Responsibility

Member responsibility is a derived business concept rather than necessarily a standalone entity.

Conceptually:

```text
Member Responsibility
=
Contributions
+
Payments / Funding
-
Allocated Expenses
-
Settlements
```

The exact formula depends on the final shared-finance accounting model.

The MVP must establish the basic invariant:

> The system can determine each member's financial responsibility from recorded financial events.

Settlement is intentionally separated from responsibility calculation.

---

# 16. Aggregate Boundaries

Aggregate boundaries protect business invariants.

The initial proposed aggregate model is:

```text
User
```

```text
Account
```

```text
Category
```

```text
Transaction
  └── Transaction Items
```

```text
Shared Vault
  └── Memberships
```

```text
Contribution
```

```text
Shared Expense
  └── Transaction Splits
```

These boundaries are business-oriented and may be refined during architecture design.

---

# 17. Aggregate Rules

## Transaction Aggregate

The Transaction aggregate owns its transaction items.

The aggregate must ensure:

```text
Σ item.amount = transaction.amount
```

when the transaction is fully itemized.

External components should not independently modify transaction items in a way that bypasses transaction invariants.

---

## Shared Expense Aggregate

The Shared Expense aggregate owns its splits.

It must ensure:

```text
Σ split.amount = expense.amount
```

and:

```text
every split participant ∈ valid vault members
```

according to the applicable membership policy.

---

## Shared Vault Aggregate

The Shared Vault controls membership-related invariants that require vault context.

Examples:

- Membership uniqueness.
- Membership status.
- Vault-level membership rules.

Financial events should not be modified by arbitrary membership operations.

---

# 18. Domain Invariants

The following invariants are considered critical.

## Financial

```text
amount > 0
```

```text
Σ transaction_items = transaction.amount
```

```text
Σ expense_splits = shared_expense.amount
```

## Ownership

```text
Account.owner == authorized_user
```

## Vault Membership

```text
participant ∈ vault.members
```

## Authorization

A user may only operate on resources they are authorized to access.

## Atomicity

A financial operation must not leave partially updated financial state.

## Historical Integrity

Changes to mutable metadata such as categories or memberships must not corrupt historical financial records.

---

# 19. Personal vs Shared Financial Context

SaveMon distinguishes between:

### Personal Finance

```text
User
 ↓
Account
 ↓
Transaction
```

### Shared Finance

```text
User
 ↓
Vault Membership
 ↓
Shared Vault
 ↓
Shared Financial Events
```

These contexts must not be implicitly interchangeable.

A personal account cannot be accessed merely because a user belongs to a shared vault.

Likewise, vault membership does not grant access to another member's personal accounts.

---

# 20. Domain Relationship Overview

```text
                         User
                       /  |  \
                      /   |   \
                     /    |    \
                    ↓     ↓     ↓
                Account Category Vault
                   │       ↑      │
                   │       │      ↓
                   │       │   Membership
                   │       │      │
                   ↓       │      ↓
                Transaction ──→ Member
                   │
                   ↓
             Transaction Item
                   │
                   ↓
                Category


Shared Vault
     │
     ├── Membership
     │
     ├── Contribution
     │
     └── Shared Expense
             │
             └── Transaction Split
                     │
                     ↓
                  Member
```

---

# 21. Important Domain Decisions

## 21.1 Transaction Is the Core Financial Event

Income and expense are transaction types rather than completely unrelated financial concepts.

```text
Transaction
├── INCOME
└── EXPENSE
```

This allows transaction history and reporting to operate on a unified financial event model.

---

## 21.2 Transaction Items Are First-Class Domain Concepts

Transaction items exist to support detailed categorization.

Example:

```text
Expense = 1,000,000

Items:
Food       500,000
Shopping   300,000
Transport  200,000
```

This enables future:

- Category analytics
- AI classification
- Receipt extraction
- Spending insights

---

## 21.3 Membership Is a Domain Concept

Vault membership is not merely a technical join table.

Membership determines whether a user is eligible to participate in shared financial operations.

---

## 21.4 Split Is Not Settlement

These concepts must remain separate.

```text
Split
=
Who is responsible for an expense?

Settlement
=
Who has actually paid/reconciled that responsibility?
```

Therefore:

```text
Expense
   ↓
Split
   ↓
Responsibility
   ↓
Settlement
```

Settlement is POST-MVP.

---

# 22. Domain Events

Domain events are not yet required to drive asynchronous architecture.

However, the domain may expose meaningful business events in the future, such as:

```text
TransactionRecorded
TransactionUpdated
TransactionReversed

VaultCreated
MemberAdded
MemberRemoved

ContributionRecorded
SharedExpenseRecorded
ExpenseSplitCreated
```

Whether these become technical event/message infrastructure is an architecture decision.

---

# 23. AI and the Domain

AI is explicitly outside the financial domain model.

AI may produce:

```text
Intent
Extraction Result
Classification
Recommendation
Plan
```

These outputs must enter the deterministic application layer through controlled commands.

Example:

```text
Natural Language
      ↓
AI
      ↓
Structured Intent
      ↓
Application Command
      ↓
Authorization
      ↓
Domain Validation
      ↓
Domain Operation
      ↓
Financial State
```

The domain never trusts AI output merely because it was generated by an AI model.

---

# 24. Domain Boundary Summary

The core domain can be summarized as:

```text
IDENTITY
    User
      │
      ├─────────────────────────────┐
      ↓                             ↓
PERSONAL FINANCE              SHARED FINANCE
      │                             │
      ├── Account                   ├── Shared Vault
      ├── Category                  ├── Membership
      ├── Transaction               ├── Contribution
      └── Transaction Item          ├── Shared Expense
                                    └── Split
```

The central principle is:

> SaveMon models financial state through explicit ownership, financial events, membership, allocation, and domain invariants.

AI is an intelligence layer around this domain, not a replacement for it.

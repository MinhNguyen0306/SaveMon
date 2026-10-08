# SaveMon — Product Definition

## 1. Product Vision

SaveMon is a personal finance application designed to help individuals and small groups manage spending, accounts, savings, and shared financial activities in a simple and trustworthy way.

SaveMon combines a deterministic financial core with an AI intelligence layer.

The deterministic core is responsible for financial truth, business rules, authorization, validation, persistence, and transaction consistency.

The AI layer provides natural-language interaction, extraction, classification, reasoning, recommendations, and agent capabilities without directly controlling financial state.

### Vision

> Make personal and shared money management simple enough to use every day, while making financial data and financial actions trustworthy enough to rely on.

---

## 2. Target Users

### Primary User

Individuals who want to:

- Track income and expenses.
- Manage multiple personal accounts.
- Organize transactions with categories.
- Understand where their money goes.
- Review financial activity by date.
- Manage shared expenses with other people.
- Eventually interact with their finances using natural language and AI.

### Secondary User

Small groups such as:

- Couples
- Families
- Friends
- Roommates
- Small teams sharing a common fund

These users need to manage a shared pool of money while maintaining clear ownership and contribution records.

---

## 3. Problems

### Personal Finance Problems

Users often:

- Forget to record expenses.
- Find manual expense entry inconvenient.
- Have transactions spread across multiple accounts.
- Have difficulty understanding spending patterns.
- Need to review spending by date, category, or account.
- Lose context around why a transaction happened.

### Shared Finance Problems

Shared spending introduces additional complexity:

- Who contributed money?
- Who participated in an expense?
- How much did each person owe?
- Which expenses belong to the shared fund?
- How much money remains in the shared fund?
- How should a shared expense be divided?

SaveMon aims to provide a single consistent model for these activities.

### Interaction Problem

Traditional finance applications require users to navigate forms and screens for every action.

SaveMon will progressively reduce this friction through AI-assisted interaction.

For example:

> "Ăn trưa 80 nghìn ở Phở Thìn."

can eventually become a structured transaction draft without allowing AI to bypass normal financial validation and authorization.

---

## 4. Core Value Proposition

SaveMon provides four core values.

### 4.1 Simple Financial Tracking

Users can quickly record and review income and expenses across their accounts.

### 4.2 Shared Money Management

Users can manage shared funds and shared expenses with explicit membership, contribution, and split information.

### 4.3 Trustworthy Financial State

Financial state is controlled by deterministic business logic.

AI may interpret or recommend an action, but it cannot directly modify financial state.

### 4.4 AI-Native Interaction

AI progressively becomes an interaction layer over the financial system.

The intended evolution is:

```text
Traditional UI
    ↓
AI-assisted input
    ↓
Natural-language assistant
    ↓
AI agent with controlled tools
    ↓
Approved autonomous workflows
```

AI capability must never compromise financial correctness, authorization, auditability, or user control.

---

## 5. Product Principles

### 5.1 Financial Truth Comes From the Deterministic Core

The database and deterministic domain/application logic are the source of truth for financial state.

AI output is never treated as financial truth without validation.

### 5.2 AI Does Not Own Business Logic

AI may:

- Understand user intent.
- Extract information.
- Classify information.
- Recommend actions.
- Plan actions.

AI must not:

- Directly modify the database.
- Bypass authorization.
- Bypass validation.
- Execute arbitrary application code.
- Directly manipulate financial state.

All financial actions must pass through approved application commands/services.

### 5.3 Human Control

Important actions and system changes must remain under human control.

AI agents are assistants, not unrestricted autonomous developers or financial operators.

### 5.4 Correctness Over Cleverness

For financial operations:

> Correctness > convenience > AI sophistication.

A deterministic and slightly slower workflow is preferable to an intelligent but unreliable financial operation.

### 5.5 Mobile-First Experience

SaveMon is primarily a mobile application.

Common financial actions should require minimal interaction and should work well for frequent daily use.

### 5.6 Progressive AI Adoption

AI capabilities will be introduced incrementally:

```text
L0 — No AI
L1 — AI Extraction
L2 — AI Assistant
L3 — AI Agent + Tools
L4 — Autonomous Agent
```

The product must remain fully functional without advanced AI capabilities.

### 5.7 Build for Commercialization, Avoid Premature Complexity

SaveMon should be designed so that successful MVP concepts can evolve into a commercial product.

However, speculative features and infrastructure should not be implemented before there is a concrete product requirement.

---

## 6. MVP Definition

The MVP focuses on establishing a reliable deterministic financial foundation.

### MVP Scope

#### Identity

- User registration
- Authentication
- User profile

#### Personal Finance

- Personal accounts
- Account balance
- Categories
- Hierarchical categories
- Income transactions
- Expense transactions
- Transaction items
- Transaction history
- Transaction details

#### Financial Overview

- Calendar-based transaction view
- Basic income/expense summary

#### Shared Finance

- Shared Vault
- Vault members
- Contributions
- Shared expenses
- Expense splits
- Member-level responsibility/balance information

### MVP Core User Journey

```text
Register
    ↓
Login
    ↓
Create Account
    ↓
Create Categories
    ↓
Record Income / Expense
    ↓
View Transaction History
    ↓
Review transactions by date
    ↓
View basic financial summary
```

Shared finance journey:

```text
Create Shared Vault
    ↓
Add Members
    ↓
Members Contribute
    ↓
Record Shared Expense
    ↓
Split Expense
    ↓
View Member Responsibility
```

---

## 7. MVP Non-Goals

The following are explicitly outside the initial MVP:

- Autonomous AI agents
- Generative UI
- AI financial coach
- Advanced financial forecasting
- Investment management
- Bank account integration
- Complex budgeting
- Advanced analytics
- Automated settlement optimization
- Complex recurring transaction engine
- Multi-currency financial system
- Full OCR workflow
- Natural-language transaction creation

These may be introduced after the deterministic MVP is stable.

---

## 8. AI Product Roadmap

### L0 — Deterministic Product

The product operates entirely through conventional UI and deterministic backend logic.

### L1 — AI Extraction

Example:

```text
Receipt image
    ↓
OCR / AI extraction
    ↓
Structured transaction draft
    ↓
User review
    ↓
Normal transaction command
    ↓
Validation
    ↓
Persistence
```

AI extracts information but does not commit financial state.

### L2 — AI Assistant

Users can ask questions about their financial data.

Examples:

- "Tôi đã tiêu bao nhiêu cho ăn uống tháng này?"
- "Khoản chi lớn nhất tuần này là gì?"
- "Tháng này tôi chi nhiều hơn tháng trước ở đâu?"

The assistant retrieves authorized data and produces explanations or recommendations.

### L3 — AI Agent + Tools

AI can plan multi-step operations using explicitly approved tools.

```text
User Intent
    ↓
AI Agent
    ↓
Tool Selection
    ↓
Authorization
    ↓
Validation
    ↓
Deterministic Application Service
    ↓
Transaction
    ↓
Audit
```

### L4 — Autonomous Agent

Selected workflows may be executed with limited autonomy and explicit safety boundaries.

Financially sensitive operations should retain appropriate approval and audit mechanisms.

---

## 9. Product Success Criteria

The MVP should demonstrate that SaveMon can reliably support:

1. Recording personal income and expenses.
2. Maintaining correct account balances.
3. Categorizing transactions.
4. Reviewing financial activity.
5. Managing shared funds.
6. Recording contributions.
7. Splitting shared expenses correctly.
8. Enforcing authorization and business rules.
9. Maintaining transaction consistency.
10. Providing a foundation for future AI capabilities without coupling financial correctness to AI behavior.

The most important success criterion is:

> Users can trust that the financial state represented by SaveMon is correct.

---

## 10. Product Boundary

SaveMon is primarily a:

> **Personal and shared finance management application with an AI-native interaction layer.**

SaveMon is not initially intended to be:

- A bank.
- An accounting system for businesses.
- An investment platform.
- A lending platform.
- A payment processor.
- A fully autonomous financial advisor.

Future commercial expansion may introduce additional capabilities, but those capabilities must not weaken the core principles of correctness, security, authorization, and user control.

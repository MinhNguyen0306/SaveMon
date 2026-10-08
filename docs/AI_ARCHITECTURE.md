# SaveMon AI Architecture

## 1. Purpose

This document defines the AI-Native architecture, boundaries, governance, and cost-control principles of SaveMon.

## 2. AI-Native Principle

AI is used where reasoning, natural-language understanding, extraction, prediction, recommendation, or agentic behavior provides meaningful product value.

Deterministic software remains responsible for:

- Correctness
- Security
- Authorization
- Business rules
- Financial calculations
- Validation
- Persistence

> **AI provides intelligence; deterministic software owns truth and execution.**

## 3. AI Capability Levels

### L0 — No AI

Pure deterministic implementation.

### L1 — AI Extraction

AI extracts structured information from unstructured input.

Examples:

- SmartOCR
- Natural-language expense extraction
- Classification

### L2 — AI Assistant

AI reads approved application data and provides explanations, summaries, and recommendations.

### L3 — AI Agent

AI can reason and invoke explicitly approved tools.

### L4 — Autonomous Agent

AI can execute approved workflows under explicit permissions, limits, validation, audit, and human-defined policies.

AI capability should evolve progressively. Do not build L3/L4 infrastructure before L1/L2 demonstrate real product value.

## 4. AI Safety Boundary

AI must never directly access or modify the database.

AI must not bypass:

- Authentication
- Authorization
- Validation
- Business rules
- Transaction boundaries
- Audit requirements

All executable actions must pass through approved deterministic tools or application services:

```text
AI
 ↓
Structured Intent / Tool Call
 ↓
Application Service
 ↓
Authorization
 ↓
Validation
 ↓
Domain Logic
 ↓
Database Transaction
 ↓
Audit
```

AI output is always treated as **untrusted input**.

Prohibited generic capabilities include:

- `execute_sql`
- `execute_code`
- Direct balance mutation
- Arbitrary database writes
- Arbitrary infrastructure access

## 5. Prompt Context Strategy

AI context is divided into:

### Stable Context

Long-lived project rules, architecture principles, and agent instructions.

This context is suitable for prompt caching.

### Task Context

Only documentation, source code, and requirements relevant to the current task.

### Dynamic Context

Current user request, code diff, compiler errors, test results, and other rapidly changing information.

Do not load the entire repository or documentation set into every request.

## 6. Prompt Caching

Use provider/API-supported prompt caching where available.

Prioritize caching of stable, repeatedly used context.

Caching configuration must be validated against the actual AI provider and development tooling before being considered active.

Caching should reduce cost and latency without encouraging unnecessarily large prompts.

## 7. AI Cost Control

Every AI workflow should consider:

- Model selection
- Prompt/context size
- Cached context
- Request count
- Retry count
- Tool-call count
- Response size
- Invocation frequency

Prefer deterministic code when AI reasoning is unnecessary.

## 8. Credit Guardrails

AI workflows should define explicit limits for:

- Maximum iterations
- Maximum retries
- Maximum requests
- Maximum context size
- Maximum execution time
- Model escalation

Agents must stop when limits are reached.

Repeated failure must never create an infinite retry loop.

## 9. Model Escalation

Use the smallest model that reliably solves the task.

Escalate to a stronger reasoning model only when justified by task complexity or risk.

Model escalation must be controlled by the workflow, not freely decided by the agent.

## 10. Human Approval

Human approval is required before:

- Architecture changes
- Security-sensitive changes
- Financial actions
- Autonomous actions
- Production-impacting changes
- Major AI capability changes

Human remains the final decision maker.

## 11. Observability

AI operations should eventually record sufficient metadata to understand:

- Capability invoked
- Model used
- Token/cost information where available
- Latency
- Success/failure
- Tool calls
- Validation failures
- User approval where applicable

Sensitive user data must not be unnecessarily logged.

## 12. Current Status

This document defines **principles and governance**, not provider-specific implementation.

Concrete model routing, prompt configuration, caching, agent runtime, tool implementation, and operational limits will be defined in later technical phases.

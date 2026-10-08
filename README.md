# Expense AI

AI-Native personal finance mobile application built with a Hybrid AI-Native architecture.

## Core Stack

- Backend: Java 21, Spring Boot 3.x
- Mobile: Flutter / Dart
- Database: PostgreSQL
- Cache: Redis
- AI: LLM + Vision/OCR + AI orchestration

## Architecture Principle

The system separates deterministic business logic from AI-native capabilities.

### Deterministic Core

Traditional application logic is responsible for:

- Authentication and authorization
- CRUD operations
- Financial calculations
- Business rules and validation
- Database persistence
- File and storage operations
- Security
- Transaction consistency

### AI-Native Layer

AI is responsible for tasks requiring natural-language understanding, reasoning, extraction, prediction, and intelligent recommendations.

Examples:

- Natural-language expense entry
- Smart OCR
- Financial analysis
- Spending prediction
- AI financial assistant
- Agent-based automation
- Context-aware UI

### Critical Rule

AI must not directly access or modify the database.

AI interacts with the deterministic application through approved APIs, services, or tools.

Financial mutations must always pass through deterministic business logic, validation, authorization, and transaction boundaries.

## Development Principle

No implementation starts before the feature has been:

1. Planned
2. Specified
3. Architecturally designed
4. Approved by the human owner

The human owner has final authority over product, architecture, and implementation decisions.

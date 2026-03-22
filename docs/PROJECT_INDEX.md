# Project Index

## Repository Structure
```text
EnvelopeMoney/
+-- docs/
¦   +-- PROJECT_INDEX.md
¦   +-- ARCHITECTURE.md
¦   +-- DATA_SCHEMA.md
¦   +-- features.md
¦   +-- user-flows.md
¦   +-- AI_CHANGE_PROTOCOL.md
+-- state/
¦   +-- TASK_STATE.json
+-- prompts/
¦   +-- codex_rules.md
+-- app/
¦   +-- src/main/java/com/example/envelopemoney/
+-- README.md
```

## Key Runtime Areas
- `MainActivity.java`: primary activity, transaction UI, envelope UI, month navigation, transfers, recurring transactions.
- `Envelope.java`: envelope domain model, monthly data, transfer metadata, remaining balance calculations.
- `Transaction.java`: transaction domain model with transfer and recurring metadata.
- `MonthTracker.java`: persisted month state and rollover detection.
- `PrefManager.java`: persisted envelopes and UI preferences.

## Memory Files
- `ARCHITECTURE.md`: module boundaries and runtime responsibilities.
- `DATA_SCHEMA.md`: persisted JSON/shared-preferences model shape.
- `features.md`: user-facing behavior definitions.
- `user-flows.md`: navigation and interaction flows.
- `AI_CHANGE_PROTOCOL.md`: mandatory AI change workflow.
- `TASK_STATE.json`: current active/completed tasks.

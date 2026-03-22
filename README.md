# EnvelopeMoney

EnvelopeMoney is an Android envelope-budgeting application focused on monthly budget tracking, transaction history, envelope-to-envelope transfers, and recurring transactions.

## Repository Memory System
This repository uses a lightweight project memory system so AI-assisted development has stable architectural context, user-flow context, and task history before code changes are made.

## Documentation Index
- `docs/PROJECT_INDEX.md`
- `docs/ARCHITECTURE.md`
- `docs/DATA_SCHEMA.md`
- `docs/features.md`
- `docs/user-flows.md`
- `docs/AI_CHANGE_PROTOCOL.md`
- `state/TASK_STATE.json`
- `prompts/codex_rules.md`

## Development Expectations
- Read the repository memory files before changing code.
- Verify changes against architecture, feature behavior, and user flows.
- Add or update tests when behavior changes.
- Confirm compile/test status before finalizing.
- Update docs and task state whenever behavior or structure changes.
- Stage commits locally with a structured message; do not push automatically.

## Visuals
### Repository Memory Structure
```text
EnvelopeMoney/
+-- docs/
+-- state/
+-- prompts/
+-- README.md
+-- app/
```

### Month Rollover Flow
```text
Stored state -> sanitize -> decide target month -> rebuild target month on deep copy -> adopt repaired state
```

### Test Surface Added
```text
MonthRolloverHelperTest
MonthTrackerTest
```

## Change Log
- 2026-03-21: Initialized repository memory system.
- 2026-03-21: Added `MonthRolloverHelper`, startup rollover sanitization, envelope state repair helpers, and month normalization tests.
- 2026-03-21: Verification is currently blocked locally because this machine only exposes Java 25 while Gradle 6.7.1 requires an older compatible JDK.

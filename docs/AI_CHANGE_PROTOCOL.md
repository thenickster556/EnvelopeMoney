# AI Change Protocol

The following workflow is mandatory for every AI-assisted code change in this repository.

## Required Workflow
1. Load repository memory.
2. Log development intent.
3. Verify architecture and user-flow impact.
4. Implement changes.
5. Run compile and regression checks.
6. Add or update unit tests when behavior changes.
7. Update documentation and task state.
8. Update the README change log.
9. Stage a local commit with a structured message.
10. Do not push automatically.

## Repository Memory To Load Before Changes
- `/docs/PROJECT_INDEX.md`
- `/docs/ARCHITECTURE.md`
- `/docs/DATA_SCHEMA.md`
- `/docs/features.md`
- `/docs/user-flows.md`
- `/state/TASK_STATE.json`
- `/prompts/codex_rules.md`
- `/README.md`

## Required Pre-Implementation Summary
Before code changes, summarize:
- relevant architecture components
- persisted data involved
- user flow affected
- files to be changed

## Finalization Protocol
After implementation:
1. Ensure code compiles.
2. Run regression tests.
3. Add or update unit tests.
4. Update repository memory files affected by the change.
5. Update `README.md` change log.
6. Stage a local commit.

## Commit Format
Allowed prefixes:
- `feat`
- `fix`
- `refactor`
- `docs`
- `test`
- `perf`

Each commit message must include:
- systems affected
- behavior changed
- tests added or updated

# AI Change Protocol

Mandatory workflow for **every** AI-assisted change in this repository. Keeps architecture stable, docs synchronized, and history auditable.

## 1. Load project memory

Read (authoritative; conflicts with ad-hoc requests → **documents win**):

- `docs/PROJECT_INDEX.md`
- `docs/ARCHITECTURE.md`
- `docs/DATA_SCHEMA.md`
- `docs/features.md`
- `docs/user-flows.md`
- `state/TASK_STATE.json`
- `prompts/codex_rules.md`
- `README.md`

## 2. Log development intent

Update `state/TASK_STATE.json` (e.g. add or adjust an `active_tasks` entry) so the session intent is recoverable.

## 3. Verify architecture and user-flow impact

**Pre-implementation summary** (required before coding):

1. Relevant architecture components  
2. Persisted data involved (see `DATA_SCHEMA.md`; no separate DB)  
3. User flow affected  
4. Files expected to change  

## 4. Implement changes

Make the smallest change that satisfies the request; match existing patterns.

## 5. Run compile and regression checks

Ensure the project compiles and existing tests pass (use a JDK compatible with this repo’s Gradle version if local Gradle fails).

## 6. Add or update unit tests

When behavior changes, add or extend unit tests (pure Java helpers first when possible).

## 7. Update documentation and task state

Update any of: `ARCHITECTURE.md`, `DATA_SCHEMA.md`, `features.md`, `user-flows.md`, `PROJECT_INDEX.md`, `TASK_STATE.json` — only what the change actually affects.

## 8. Update README change log

Append a dated line under **Change Log** describing what changed (session log for when AI context resets).

## 9. Stage a local commit

Use an allowed prefix and a structured body (systems affected, behavior, tests). **Do not push** unless the user explicitly asks.

## 10. Do not push automatically

Commits stay local unless instructed otherwise.

---

## Commit format

**Allowed prefixes:** `feat` | `fix` | `refactor` | `docs` | `test` | `perf`

Each message should cover:

- Systems affected  
- Behavior changed  
- Tests added or updated  

Example:

```text
feat: add semantic verse search

Systems affected:
- SearchActivity
- SearchEngine

Changes:
- implemented semantic similarity scoring
- added context window display

Tests:
- added SearchEngineTest
```

---

## Testing expectations

All substantive changes should satisfy:

- Successful compilation (on a supported toolchain)  
- Passing regression tests  
- New/updated tests when behavior changes  
- No broken user flows described in `user-flows.md`  

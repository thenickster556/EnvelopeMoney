# Project index

This file maps the **repository memory system**: what each document is for and where to read first.

| File | Purpose |
|------|---------|
| [PROJECT_INDEX.md](PROJECT_INDEX.md) | **This file** — structure of docs, state, prompts, and pointers into the rest of memory. |
| [ARCHITECTURE.md](ARCHITECTURE.md) | System architecture: main components, boundaries, startup/rollover flow, risks. |
| [DATA_SCHEMA.md](DATA_SCHEMA.md) | **Persistence schema** (SharedPreferences keys and Gson models) — the app has no SQL database; this is the data contract. |
| [features.md](features.md) | User-facing features and expected behavior. |
| [user-flows.md](user-flows.md) | How users move through screens and dialogs. |
| [AI_CHANGE_PROTOCOL.md](AI_CHANGE_PROTOCOL.md) | Mandatory AI workflow: load memory ? intent ? verify ? implement ? test ? docs ? commit. |
| [../state/TASK_STATE.json](../state/TASK_STATE.json) | Active and completed tasks; update when work starts or finishes. |
| [../prompts/codex_rules.md](../prompts/codex_rules.md) | Prompt discipline and **context-loading block** appended to AI instructions. |
| [../README.md](../README.md) | Project entry point, expectations, and **change log** (session log when context resets). |

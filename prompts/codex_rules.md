Before making any code changes you MUST load and read:

/docs/PROJECT_INDEX.md
/docs/ARCHITECTURE.md
/docs/DATA_SCHEMA.md
/docs/features.md
/docs/user-flows.md
/state/TASK_STATE.json
/prompts/codex_rules.md
README.md

These files define the authoritative architecture and behavior.  
If requested changes conflict with these documents, **the documents take precedence**.

---

## Implementation precheck (required)

Before implementing code changes, summarize:

1. Relevant architecture components  
2. Persisted data involved (SharedPreferences / models per `DATA_SCHEMA.md`)  
3. User flow affected  
4. Files that must be modified  

Only after this summary may implementation begin.

---

## FINALIZATION PROTOCOL

After completing implementation the AI must:

1. Ensure code compiles (on a compatible JDK/Gradle where possible).  
2. Run regression tests.  
3. Add or update unit tests when behavior changed.  
4. Update repository memory as needed:  
   - `state/TASK_STATE.json`  
   - `docs/ARCHITECTURE.md` if system structure or boundaries changed  
   - `docs/DATA_SCHEMA.md` if persistence or model fields changed  
   - `docs/features.md` if user-visible feature behavior changed  
   - `docs/user-flows.md` if navigation or dialog flows changed  
5. Update **README.md** Change Log with completed work.  
6. Stage a **local** commit including code, docs, task state, and README log.  
   - Use prefixes: `feat`, `fix`, `refactor`, `docs`, `test`, `perf`  
   - Describe systems affected, behavioral changes, and tests  
   - **Do not push** automatically unless the user explicitly requests it.

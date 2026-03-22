# Architecture

## Application Shape
EnvelopeMoney is a single-activity Android application with state persisted through SharedPreferences using Gson serialization for envelope and transaction data.

## Core Components
- `MainActivity`
  - Owns screen initialization, envelope list, transactions list, month navigation, transfer summaries, dialogs, and rollover triggering.
- `MonthRolloverHelper`
  - Sanitizes persisted envelope state, repairs legacy month data, and computes a safe launch month on a deep copy before the activity adopts it.
- `Envelope`
  - Stores envelope balances, month snapshots, transaction membership, transfer definitions, and manual override state.
- `Transaction`
  - Stores amount, date, comment, transfer linkage, and recurring metadata.
- `MonthTracker`
  - Stores the current persisted month, normalizes month values, and determines whether rollover is required.
- `PrefManager`
  - Serializes/deserializes envelope state and UI preference state.

## State Boundaries
- UI state lives primarily in `MainActivity`.
- Persisted business state lives in the serialized `Envelope` and `Transaction` models.
- Month rollover is a business-state transition and must be deterministic, validated, and idempotent.

## Startup Month Flow
```text
App launch
  -> load persisted envelopes
  -> sanitize/repair with MonthRolloverHelper
  -> compute active month from stored month vs real month
  -> rebuild target month data on a deep copy
  -> adopt repaired envelopes only after success
  -> persist current month and repaired state once
```

## Current Risk Areas
- SharedPreferences can contain malformed or legacy state that must be repaired before rollover logic executes.
- Gradle 6.7.1 verification is currently blocked on this machine by missing JDK 11/17 compatibility.

## Target Architecture Rule
Month rollover logic must stay isolated in testable helpers and must not mutate live persisted state until rollover inputs are sanitized and the transition is valid.

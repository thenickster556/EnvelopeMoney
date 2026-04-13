# Architecture

## Application Shape
Mountain Money (package `com.example.envelopemoney`) is a single-activity Android application with state persisted through SharedPreferences using Gson serialization for envelope and transaction data.

## Core Components
- `MainActivity`
  - Owns screen initialization, custom top bar (`app_bar_main` outlined bar: theme-driven title and icon tints; DayNight bar fill/stroke via color resources), pond list, transactions list, month navigation, transfer summaries, dialogs, bills-period filter, and rollover triggering.
  - Uses **`MaterialAlertDialogBuilder`** for modal dialogs so buttons and surfaces follow the Material / Mountain theme.
- `MonthRolloverHelper`
  - Sanitizes persisted envelope state, repairs legacy month data, and computes a safe launch month on a deep copy before the activity adopts it.
- `BillsDayAnchor`
  - Pure helper resolving the latest bills day-of-month on or before “today,” walking backward by month when needed (unit-tested).
- `Envelope`
  - Stores pond balances, optional `accountBalance`, month snapshots, transaction membership, transfer definitions, and manual override state.
- `Transaction`
  - Stores amount, date, comment, transfer linkage, and recurring metadata.
- `MonthTracker`
  - Stores the current persisted month, normalizes month values, and determines whether rollover is required.
- `PrefManager`
  - Serializes/deserializes envelope state, UI preference state, bills days JSON, and bills-filter state.

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
  -> rebuild target month on a deep copy
  -> adopt repaired envelopes only after success
  -> persist current month and repaired state once
```

## Current Risk Areas
- SharedPreferences can contain malformed or legacy state that must be repaired before rollover logic executes.
- Gradle 6.7.1 verification may be blocked when the default JDK is too new (e.g. Java 25); use a Gradle-compatible JDK for local builds.

## Target Architecture Rule
Month rollover logic must stay isolated in testable helpers and must not mutate live persisted state until rollover inputs are sanitized and the transition is valid.

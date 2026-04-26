# Architecture

## Application Shape
Mountain Money (package `com.example.envelopemoney`) is a single-activity Android application with state persisted through SharedPreferences using Gson serialization for envelope and transaction data.

## Core Components
- `MainActivity`
  - Owns screen initialization, custom top bar (`app_bar_main` outlined bar: theme-driven title and icon tints; DayNight bar fill/stroke via color resources), pond list, transactions list, month navigation, transfer totals spinner (destination ponds only), dialogs, bills-period filter, and rollover triggering.
  - Uses **`MaterialAlertDialogBuilder`** for modal dialogs; add/edit transaction dialogs call **`applyIconMaterialDialogActions`** for icon-only confirm/dismiss actions on the dialog and recurring sub-pickers.
- `MonthRolloverHelper`
  - Sanitizes persisted envelope state, repairs legacy month data, and computes a safe launch month on a deep copy before the activity adopts it. On rollover, **carry increases the available pool** (`remaining`, baselines, `MonthData`) while **`Envelope.limit` stays the user’s base monthly budget** (`originalLimit`).
- `BillsDayAnchor`
  - Pure helper resolving the latest bills day-of-month on or before “today,” walking backward by month when needed (unit-tested). If exactly one bills day is configured and it equals today’s calendar day, the anchor is that day in the **previous** month so the filter range spans through today.
- `TransferDestinationList`
  - Builds the transfer destination pond name list (all ponds except the source) for add/edit transfer UI.
- `Envelope`
  - Stores pond balances, optional `accountBalance`, month snapshots, transaction membership, transfer definitions, and manual override state.
- `Transaction`
  - Stores amount, date, comment, transfer linkage, and recurring metadata.
- `MonthTracker`
  - Stores the current persisted month, normalizes month values, and determines whether rollover is required.
- `PrefManager`
  - Serializes/deserializes envelope state, UI preference state, bills days JSON, and bills-filter state.
- Receipt capture (`com.example.envelopemoney.receipt`)
  - `ReceiptCaptureActivity` — CameraX preview, capture mode, shutter; persists JPEG via `MediaStoreReceiptSaver` (`Pictures/Mountain Money`).
  - `ReceiptOcrPipeline` — preprocess bitmap, `OcrEngine` (default: on-device Latin text recognition; slot for PaddleOCR), `ReceiptFieldParser` heuristics (top-line merchant pick + title case for ALL CAPS OCR; bottom-up “amount due” / last total for restaurant and receipt modes).
  - `ReceiptRowUi` — pure helper for when to show the list-row receipt thumbnail.
  - Wired from `MainActivity` add/edit transaction dialogs (`ActivityResultContracts`); `receiptDialogHostView` selects the active dialog for OCR results. Fullscreen image preview: `ReceiptPreviewActivity`, `ReceiptZoomImageView`, `ReceiptBitmapLoader`.

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

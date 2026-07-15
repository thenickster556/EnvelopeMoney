# Architecture

## Application Shape
Mountain Money (package `com.example.envelopemoney`) is a single-activity Android application with state persisted through SharedPreferences using Gson serialization for envelope and transaction data.

## Core Components
- `MainActivity`
  - Owns screen initialization, custom top bar (`app_bar_main` outlined bar: theme-driven title and icon tints; DayNight bar fill/stroke via color resources), pond list (single-row header: title + selected count + icon actions), transactions list, month navigation, transfer totals spinner (destination ponds only), dialogs, bills-period filter, and rollover triggering.
  - Add/edit transaction dialogs use **TabLayout** rows for **Spending / Transfer / Split purchase** (time tabs **One-time / Recurring** only on Spending), placed **below the comment** field so core fields are entered before mode. `expandedSplitGroupIds` toggles synchronized inline split breakdown rows in the transaction list.
  - Uses **`MaterialAlertDialogBuilder`** for modal dialogs; add/edit transaction layouts use **`BoundedNestedScrollView`** + receipt **icon toolbar** (`wireReceiptRow`, `syncReceiptActionUi`, `receiptDialogHostView`) with **`applyIconMaterialDialogActions`** for icon-only confirm/dismiss on the shell and recurring sub-pickers.
- `MonthRolloverHelper`
  - Sanitizes persisted envelope state, repairs legacy month data, and computes a safe launch month on a deep copy before the activity adopts it. On rollover, **carry increases the available pool** (`remaining`, baselines, `MonthData`) while **`Envelope.limit` stays the user’s base monthly budget** (`originalLimit`).
- `BillsDayAnchor`
  - Pure helper for bills-period filter start (unit-tested). Unified month walk: latest bills day on or before today in current month, else walk backward; period-start adjustment when today lands on a bills-day boundary (multi-day → prior day in set; single-day on today → previous month).
- `MoneyMath` / `PondBankReconciliationHelper`
  - Cent-rounded bank reconciliation (`roundToCents`, 2 dp). When global paydays and per-pond Account are set: **Still to deposit** = Limit shares for paydays not yet arrived; **Remaining** = Account + unlocked shares − month spend (paydays count on/after their day; resets each month). Footer, pond row (with payday progress), and edit preview show **In bank** and **Still to deposit** only (limit shown separately).
- `TransferDestinationList`
  - Builds the transfer destination pond name list (all ponds except the source) for add/edit transfer UI.
- `TransferGroupDraft` / `TransferSyncHelper`
  - Pure grouped-transfer helpers for transfer-bucket validation, bucket-summary math, legacy single-transfer migration, and source/mirror synchronization for one-to-many transfers.
- `SplitPurchaseGroupDraft` / `SplitPurchaseSyncHelper`
  - Pure helpers for **split purchases**: multiple positive expense slices in different ponds sharing one `splitPurchaseGroupId`, validation that slice amounts sum to the purchase total, `applyGroup` / `removeGroup` persistence, and list breakdown text.
- `TransferBucketUiHelper`
  - Pure UI helper for grouped-transfer controls: fixed-step slider snapping, scale-label generation, and validation-message gating so dialog UX changes stay testable outside `MainActivity`.
- `Envelope`
  - Stores pond balances, optional `accountBalance`, month snapshots, transaction membership, transfer definitions, and manual override state.
- `Transaction`
  - Stores amount, date, comment, grouped transfer linkage (`transferId`, optional `transferBucketId`), **split purchase** linkage (`splitPurchaseGroupId`, `splitPurchaseBucketId` for multi-pond purchases), and recurring metadata.
- `MonthTracker`
  - Stores the current persisted month, normalizes month values, and determines whether rollover is required.
- `PrefManager`
  - Serializes/deserializes envelope state, UI preference state, bills days JSON, paydays JSON, and bills-filter state.
- Receipt capture (`com.example.envelopemoney.receipt`)
  - `ReceiptCaptureActivity` — CameraX preview, capture mode, shutter; persists upright JPEG via EXIF-aware decode + `MediaStoreReceiptSaver` (`Pictures/Mountain Money`).
  - `ReceiptExifBitmapLoader` — applies EXIF orientation when decoding capture/picker JPEGs.
  - `ReceiptPickerUriNormalizer` / `ReceiptSourceDeleter` — import gallery URIs into **Pictures/Mountain Money** on the main thread (move when delete succeeds); stable URI before OCR and save.
  - `ReceiptOcrPipeline` — preprocess bitmap, `OcrEngine` (default: on-device Latin text recognition; slot for PaddleOCR), `ReceiptFieldParser` heuristics (merchant junk filters incl. order/receipt/invoice headers + title case for ALL CAPS OCR; bottom-up “amount due” / last labeled total, then bottom-most money fallback for restaurant/receipt modes).
  - `ReceiptRowUi` — pure helper for when to show the list-row receipt thumbnail.
  - Wired from `MainActivity` add/edit transaction dialogs (`ActivityResultContracts`); `receiptDialogHostView` selects the active dialog for OCR results. Fullscreen image preview: `ReceiptPreviewActivity` (view-only 90° until **Save rotation**; `ReceiptRotatedJpegWriter` decodes, **Matrix**-rotates, overwrites same `content://` URI at JPEG **92**; reload bitmap; `MaterialAlertDialogBuilder` for replace + discard-when-dirty), `ReceiptZoomImageView`, `ReceiptBitmapLoader`.

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

# Architecture

## Application Shape
Mountain Money (package `com.example.envelopemoney`) is a single-activity Android application with state persisted through SharedPreferences using Gson serialization for envelope and transaction data.

A sibling **web demo** lives in `web/`: Node/Express + MongoDB + mobile HTML/CSS. It mirrors the same pond/transaction models and helper math, with one Mongo `profiles` document per registered account. It does not share storage with the Android app. See [WEB_DEMO.md](WEB_DEMO.md).

Comment typeahead and OCR amount-correction weights live in a **sidecar SQLite** file (`LearningDb` / `mountain_money_learning.db`), not in Envelope Gson.

## Core Components
- `MainActivity`
  - Owns screen initialization, custom top bar (`app_bar_main` outlined bar: theme-driven title and icon tints; DayNight bar fill/stroke via color resources), pond list (single-row header: title + selected count + icon actions), transactions list, month navigation, transfer totals spinner (destination ponds only), dialogs, bills-period filter, and rollover triggering.
  - Add/edit transaction dialogs use **TabLayout** rows for **Spending / Transfer / Split purchase** (time tabs **One-time / Recurring** only on Spending), placed **below the comment** field so core fields are entered before mode. Comment typeahead is an **inline 3-row list** under the comment field (not a floating popup). `expandedSplitGroupIds` toggles synchronized inline split breakdown rows in the transaction list.
  - Uses **`MaterialAlertDialogBuilder`** for modal dialogs; add/edit transaction layouts use **`BoundedNestedScrollView`** + receipt **icon toolbar** (`wireReceiptRow`, `syncReceiptActionUi`, `receiptDialogHostView`) with **`applyIconMaterialDialogActions`** for icon-only confirm/dismiss on the shell and recurring sub-pickers. Top-bar **Analysis** opens a full-height dialog (check/X close) with Last 3/6/12 chips, pond chips, and Canvas bars (`SpendBarChartView`); it does not change History Start/End or the bills-period filter.
- `MonthRolloverHelper`
  - Sanitizes persisted envelope state, repairs legacy month data, and computes a safe launch month on a deep copy before the activity adopts it. On rollover, **carry increases the available pool** (`remaining`, baselines, `MonthData`) while **`Envelope.limit` stays the user’s base monthly budget** (`originalLimit`).
- `BillsDayAnchor`
  - Pure helper for bills-period filter start (unit-tested). Unified month walk: latest bills day on or before today in current month, else walk backward; period-start adjustment when today lands on a bills-day boundary (multi-day → prior day in set; single-day on today → previous month).
- `MoneyMath` / `PondBankReconciliationHelper`
  - Cent-rounded bank reconciliation (`roundToCents`, 2 dp). When global paydays and per-pond Account are set: **Still to deposit** = Limit shares for paydays not yet arrived; **Remaining** = Account + unlocked shares − month spend (paydays count on/after their day; resets each month). Footer, pond row (with payday progress), and edit preview show **In bank** and **Still to deposit** only (limit shown separately).
- `SpendAnalysisHelper` / `SpendBarChartView`
  - Pure helper for last-N calendar months of spend vs **`Envelope.limit`**, over-budget pond-months (`roundToCents(spend) > roundToCents(limit)`), by-pond totals, and this-month snapshot. Default spend excludes transfer rows (`transferId` set); **Include transfers** uses the same all-amounts sum as Remaining. No new persistence. Web port: `web/domain/spendAnalysis.js`. Charts are Canvas/CSS bars (no chart library).
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
- `LearningDb`
  - Sidecar SQLite (`mountain_money_learning.db`) for remembered comments and the OCR amount weight vector. Envelope Gson is unchanged. Corrupt files fall back to default weights.
- `CommentHistory` / `OcrAmountLearner` / `OcrAmountWeights`
  - Pure helpers for typeahead ranking, float32 weight encoding, and one-pass amount-correction updates.
- Receipt capture (`com.example.envelopemoney.receipt`)
  - `ReceiptCaptureActivity` — CameraX preview, capture mode, shutter; decode/JPEG/MediaStore run on a background executor after `takePicture` (shutter disabled while saving); persists upright JPEG via EXIF-aware decode + `MediaStoreReceiptSaver` (`Pictures/Mountain Money`).
  - `ReceiptExifBitmapLoader` — applies EXIF orientation when decoding capture/picker JPEGs.
  - `ReceiptPickerUriNormalizer` / `ReceiptSourceDeleter` — import gallery URIs into **Pictures/Mountain Money** once before OCR; persist the MediaStore insert URI even when the picker source cannot be deleted; preview opens that stored URI (plus `DISPLAY_NAME` lookup) without re-import.
  - `ReceiptOcrPipeline` — preprocess bitmap, `OcrEngine` (default: on-device Latin text recognition; slot for PaddleOCR), `ReceiptFieldParser` heuristics (merchant junk filters incl. order/receipt/invoice headers + title case for ALL CAPS OCR; bottom-up “amount due” / last labeled total, then bottom-most money fallback for restaurant/receipt modes). Fallback money scoring can use the sidecar weight vector; defaults match the original constants.
  - `ReceiptRowUi` — pure helper for when to show the list-row receipt thumbnail.
  - Wired from `MainActivity` add/edit transaction dialogs (`ActivityResultContracts.GetContent` for **From gallery** only — photo picker, not DocumentsUI; preview/recovery never launch a picker). `receiptDialogHostView` selects the active dialog for OCR results. Gallery bytes are read on a worker, then imported into **Pictures/Mountain Money** (persist the album URI, never a picker grant). Fullscreen image preview: `ReceiptPreviewActivity` via `EXTRA_IMAGE_URI` only (no `file://` on `Intent.setData`). Candidate slides have a chrome-hiding fullscreen control (`ReceiptPreviewImmersive`). View-only 90° until **Save rotation**; `ReceiptRotatedJpegWriter` decodes, **Matrix**-rotates, overwrites same `content://` URI at JPEG **92**; reload bitmap; `MaterialAlertDialogBuilder` for replace + discard-when-dirty; `ReceiptZoomImageView`, `ReceiptBitmapLoader`.

## State Boundaries
- UI state lives primarily in `MainActivity`.
- Persisted business state lives in the serialized `Envelope` and `Transaction` models.
- Comment history and OCR amount weights live in the learning sidecar, not Envelope JSON.
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

## Receipt reference recovery (2026-09-10, widened 2026-09-14)
- `ReceiptReferenceResolver` owns read-only resolution outcomes: resolved, permission required, missing, ambiguous, or corrupt. Single-row `resolve()` verifies the stored URI then a unique filename (exact first, then a normalized name that folds case, `.jpeg`→`.jpg`, percent-encoding, and Windows ` (1)` copy suffixes); it does not guess from dates. `ReceiptAlbumMatcher` plus `ReceiptReferenceRepair.resolveAll` first bind identity matches that need no date evidence — a unique exact filename, normalized filename, or shared epoch token (the digit run inside `MountainMoney_{epochMs}.jpg` survives renames and copies). Leftover claims then take the only unused album file whose capture **local calendar day** uniquely matches one unmatched receipt (`Transaction.date`); a lone claim whose day holds no file retries one day either side so backdated entries and scan-time drift recover. Two files in a window, or two claims contesting one adjacent file, stay ambiguous. Capture time comes from `MountainMoney_{epochMs}.jpg`, else MediaStore `DATE_TAKEN` / `DATE_ADDED`. A date match legitimately carries a renamed album filename — `resolveAll` replaces the stale stored name after decode verification and only rejects a candidate whose name changed between listing and inspection. `AndroidReceiptSource` preserves metadata even after a stream fails, understands media-image document IDs, and indexes the exact `Pictures/Mountain Money` folder (path casing insensitive) through MediaStore plus accessible unindexed disk files; an empty granted index triggers one best-effort media rescan. No age cutoff is applied. A partial inventory (no `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE`) is restricted access: identity matches may still resolve, but date leftovers and missing names are permission-required rather than missing. Android 14 `READ_MEDIA_VISUAL_USER_SELECTED` ("Select photos") counts as restricted-but-listable access that stops prompting. Album inventory and per-claim outcomes log under the `EnvelopeMoney` tag for on-device diagnosis.
- `ReceiptReferenceRepair` snapshots current and monthly-history transactions (including `transactionDate`), runs one worker pass, then applies only verified results to still-live, unchanged records on the UI thread. `PrefManager` persists once per changed pass. Folder indexing and reference lookup target O(transactions + folder entries), with no gallery I/O on the UI thread. Since 2026-09-14 AMBIGUOUS outcomes carry the competing files (`Result.alternatives`) so the tap flow can offer a picker; **every attach is confirmed on a fullscreen candidate check** (`ReceiptPreviewActivity` candidate mode: `ReceiptCandidateSummary` transaction header, `‹`/`›` arrows **or a horizontal swipe at fit-scale**, chrome-hiding fullscreen button, `Use this picture` result) — row taps and lone survivors open the check rather than attaching, unreadable candidates are dropped, and session picks are excluded from later choosers. The attached-receipt preview's `Choose different` icon re-queries `ReceiptReferenceRepair.swapCandidates` and reuses the same picker → check flow. `swapCandidates` builds the pool as the union of an identity scan (exact, normalized, or epoch-token filename matches — deliberately without the matcher's tier short-circuit, because the stored name now belongs to the picked file) and one nameless-claim date pass, deduped by reference and minus the attached file plus every other transaction's reference (reserved, fragment-stripped); a replaced pick is released from the session set when its rows move, so previous choices reappear. The preview toolbar is icon-only (56dp white vectors, 8dp gutters, screen-reader labels, dimmed-when-disabled). The pinch/pan hint is a second row of the top chrome (never over Select); `Use this picture` is `mountain_primary` with a white label. All preview launches go through one `ActivityResultLauncher`; candidate intents carry every `content://` reference in one ClipData so the read grant covers arrow **and swipe** navigation. Verification is a **single header decode per file** (`inspect` opens each stream once); the full pixel decode belongs to the fullscreen preview. The tap path resolves only the tapped row's entries — the whole-app sweep stays in the startup repair — and every pass logs its duration and album inventory under `EnvelopeMoney`.
- Startup schedules repair, then requests photo-library access after resume when stored receipts exist and permission is missing. Grants (including returning from Settings) re-run repair. Tapping a receipt tries the stored URI first (one-shot byte decode, no re-import). If that read fails, the same one-pass matcher can persist a unique album match and then open it. Launch uses `EXTRA_IMAGE_URI` plus `content://` `Intent.setData` / `ClipData` and `FLAG_GRANT_READ_URI_PERMISSION` only. `file://` is never put on Intent data. Gallery pick (add/edit receipt only) uses `GetContent` (`image/*`) so the photo gallery opens, not Files/DocumentsUI; persistable grants are attempted but import still copies into **Pictures/Mountain Money**. `ReceiptBitmapLoader` reads the URI **once** into bytes (some providers allow only one open) and leftover `file://` uses `FileInputStream`.
- Recovery never imports, deletes or moves images and never asks the user to locate a picture. Missing identity or duplicate matches leave the association intact and offer automatic retry. Gallery import remains a separate existing workflow.

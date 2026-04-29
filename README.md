# Mountain Money

Mountain Money is an Android pond-budgeting application (user-facing “ponds”; internal types may still read `Envelope`) focused on monthly budget tracking, transaction history, pond-to-pond transfers, recurring transactions, optional per-pond **Account** balances for bank reconciliation, and **bills days** (recurring days-of-month plus a filter through the last bills day).

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
- Read the repository memory files before changing code (`docs/AI_CHANGE_PROTOCOL.md`, `prompts/codex_rules.md`).
- Verify changes against architecture, feature behavior, and user flows.
- Prefer **theme attributes** (`?attr/colorControlNormal`, `@color/mountain_primary`, Material overlays) for new buttons and spinners so UI stays consistent with DayNight.
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

### Test Surface
```text
MonthRolloverHelperTest
MonthTrackerTest
BillsDayAnchorTest
```

## Change Log
- 2026-03-21: Initialized repository memory system.
- 2026-03-21: Added `MonthRolloverHelper`, startup rollover sanitization, envelope state repair helpers, and month normalization tests.
- 2026-03-21: Verification is currently blocked locally because this machine only exposes Java 25 while Gradle 6.7.1 requires an older compatible JDK.
- 2026-04-12: Mountain Money rebrand (strings/theme), custom outlined top bar with bills-days calendar and recalculate, per-pond `accountBalance`, bills-period filter next to transfers, `BillsDayAnchor` + `BillsDayAnchorTest`, `docs/PROJECT_INDEX.md`, and expanded docs. Local `./gradlew` may still require a JDK compatible with Gradle 6.7.1.
- 2026-04-12: Repository memory protocol refreshed (`PROJECT_INDEX`, `AI_CHANGE_PROTOCOL`, `codex_rules` with precheck + finalization). UI aligned to theme: `MaterialAlertDialogBuilder`, spinner overlay, `colorControlNormal` tints, totals row + recurring chip colors, `DATA_SCHEMA` note on persistence vs SQL.
- 2026-04-12: Bills-days calendar weekday header text now uses `mountain_primary` instead of platform holo green.
- 2026-04-12: Full control theme pass: `btnAddTransaction` tint; spinner overlay DayNight (`values-night/colors`, non-Light overlay parent); recurring calendar unselected cell + totals row night colors; `MainActivity` `resolveThemeColor` for chips, monthly grid, bills-day cells, transfer/bills icon filters (`colorControlNormal` when inactive).
- 2026-04-12: Custom top bar DayNight: `values-night` `mountain_top_bar_fill` / `mountain_top_bar_stroke`; `tvAppTitle` uses `?attr/colorPrimary` (`app_bar_main.xml`).
- 2026-04-12: Carry-over invariant: `Envelope.limit` stays the user monthly budget; unused funds roll into **remaining** / month snapshots only. `reset(true)` no longer inflates limit; `initializeMonth` missing-month seed aligns with rollover; `MonthRolloverHelper` / `MainActivity` recalculate (Option A) documented; tests (`MonthRolloverHelperTest`, `EnvelopeCarryoverInvariantTest`).
- 2026-04-13: Bills-period filter behavior: **start** date snaps to `BillsDayAnchor`, **end** date is **today** (was incorrectly setting end to anchor). `applyPersistedBillsFilterState` matches; prefs still store pre-filter range for toggle-off restore. Docs, `strings.xml`, `PrefManager` javadoc updated.
- 2026-04-13: Add/edit transaction dialogs: recurring section uses themed chips (`recurring_*_ripple`, `drawable-v21` ripples), `AppCompatCheckBox` / `AppCompatTextView` + tinted calendar icon; `applyIconMaterialDialogActions` (check + close drawables) on new/edit and recurring sub-dialogs.
- 2026-04-14: Transfer **Transfer To** list uses `TransferDestinationList` (source pond excluded; trim-safe). **BillsDayAnchor**: when only one bills day is set and today is that day, anchor is that day in the previous month (clamped); tests `TransferDestinationListTest`, `BillsDayAnchorTest`.
- 2026-04-14: Transactions header transfer totals **spinner** lists destination ponds only (amount transferred **to** each), not “from” source rows; `MainActivity` + docs.
- 2026-04-19: **Manifest:** `MainActivity` sets `android:exported="true"` (required for API 31+ when a launcher `intent-filter` is present; fixes `processDebugMainManifest` merger failure).
- 2026-04-20: **Receipt preview fullscreen:** `ReceiptPreviewActivity` + `ReceiptZoomImageView` (pinch-zoom, pan, double-tap refit) and rotate buttons; `ReceiptBitmapLoader` shared sampling; removed dialog preview layout. Docs updated.
- 2026-04-21: **Receipt OCR heuristics:** `ReceiptFieldParser` picks a **better store line** (skip phone, URL, guest/table, street, zip-only), **title-cases** typical ALL CAPS names, and for **restaurant / receipt** capture prefers **amount due** / the **last** “total” line so the autofill amount tracks the **final** charge after tip when labels support it. `ReceiptFieldParserTest` goldens updated. Verified locally: Gradle 6.7.1 requires a compatible JDK; Java 25 fails settings compilation (“Unsupported class file major version 69”).
- 2026-04-29: **Receipt preview:** **Save rotation** — **view-only** 90° steps until save; **Material** replace + discard-on-close; **ReceiptRotatedJpegWriter.writeRotatedJpegOverwrite** decodes from the same URI, **Matrix**-rotates, writes JPEG **92** via `openOutputStream(uri, "w")`, reloads preview. Failures: **Material** alert. **ReceiptFieldParser** verified; **ReceiptFieldParserTest** extended (`merchant_skipsHttpUrlLine`, `receipt_prefersBottomStrongPayThisAmount`). **`./gradlew`:** Java **25** still fails Gradle 6.7.1 settings (“Unsupported class file major version **69**”); use JDK 8–15.
- 2026-04-19: **Receipt preview UX:** Add/edit dialogs — **Preview receipt**, **Remove receipt**, shared `receiptDialogHostView` for OCR; edit persists `receiptImageUri`; list-row photo icon + preview; `ReceiptRowUi` + `ReceiptRowUiTest`. **Recurring chips:** `recurring_chip_fill_unselected` DayNight + unselected label uses `textColorPrimary` (fixes dark-mode white-on-white). Docs updated. `./gradlew` still requires JDK 8–15 here.
- 2026-04-19: **Build:** `com.google.android.material:material` pinned to **1.6.1** (was 1.8.0). Material 1.8+ embeds `<macro>` tags that **AGP 4.2** cannot merge (`mergeDebugResources` / “Can't determine type for tag … m3_comp_assist_chip_container_shape”).
- 2026-04-15: **Receipt capture** in new-transaction dialog: CameraX (`ReceiptCaptureActivity`), gallery pick, `MediaStore` **Pictures/Mountain Money**, on-device OCR via `OcrEngine` (ML Kit Latin default; Paddle-shaped `PaddleOcrAdapter`), `ReceiptFieldParser` + tests, optional `Transaction.receiptImageUri`. **minSdk 21**, compile/target **33**. See `docs/receipt-ocr.md`. Local `./gradlew` still needs JDK 8–15 for Gradle 6.7.1.

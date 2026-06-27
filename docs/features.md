# Features

## Pond budgeting (user-facing “ponds”; code may still use `Envelope` types)
- Users create ponds with monthly limits.
- **Limit** in the pond list is the **monthly budget** the user set; **carry-over adds to Remaining** (available to spend), not to Limit. Per-month snapshots (`MonthData`) may store an effective ceiling that includes carry for that month’s math.
- Ponds track remaining funds (budget math) and month-specific snapshots.
- Optional **Account** field per pond: user-entered cash in the bank for that slice, for reconciliation against budget remainder.
- Ponds can be collapsed in the UI and that preference is persisted.
- Pond list header is a **single row**: **Ponds** title + **N selected** on the left; **icon toolbar** on the right (select all / unselect all, reorder / done, collapse, add) matching the Transaction History header pattern. Drag handles + shadow while reordering; order persists in the envelopes JSON array. Collapsing the section auto-exits reorder mode.
- Footer under the pond list shows sums of entered Account values, sum of Remaining, and the difference when any Account is set.

## Transactions
- Users can add, edit, and delete transactions.
- **Receipt capture:** Add and **edit** transaction dialogs show a top **icon toolbar** (camera, gallery, preview, trash) inside a horizontally scrollable strip on very narrow widths. The form below (pond/envelope, fields, recurring, **Transfer To**) sits in **`BoundedNestedScrollView`** (`@dimen/dialog_transaction_scroll_max_height`) with **visible vertical scrollbars** when content overflows so the bottom is reachable with the keyboard open. **Preview** and **Remove** stay disabled until an image URI is attached; **Preview** opens the same **fullscreen** viewer (pinch-zoom, pan, double-tap refit, 90° **view** rotation until saved). **Save rotation** / replace / discard behavior is unchanged. Camera capture and **gallery picks** apply **EXIF orientation** and copy into **Pictures/Mountain Money** before OCR. On-device OCR prefills amount, date, and **comment with a short store brand** (top-of-receipt, one–two words via line order + height heuristics; junk filters for boilerplate/order lines) via **`ReceiptFieldParser`**; totals prefer **labeled `$` amounts** and restaurant **tip-inclusive** finals; if OCR date falls **outside the active Start/End filter**, the dialog shows a hint (save still works). **Pond is always chosen manually**. Rows with `receiptImageUri` show a **photo** icon to the same preview. See [receipt-ocr.md](receipt-ocr.md).
- **Transaction type tabs:** **Spending**, **Transfer**, and **Split purchase** are mutually exclusive modes (replacing the old transfer checkbox). In add/edit dialogs the **type** row and the **One-time** / **Recurring** time row sit **below the comment** field so pond, date, amount, and note are filled first; mode is chosen last (similar placement to the legacy transfer toggle). **One-time** / **Recurring** applies only on **Spending** (no separate recurring checkbox); default **One-time** on add. Recurring frequency and day controls stay **hidden** until **Recurring** is selected (the layout defaults to gone and **MainActivity** re-syncs visibility after programmatic tab selection, because **TabLayout** may not fire **onTabSelected** when the first tab is already selected). Switching type tabs **does not clear** in-dialog transfer buckets or split slices (only hides the section); spending fields (pond, date, amount, comment, receipt) stay as entered until save. Pond selection resolves against the live pond list via **`PondLookup`** (trim-safe); **No pond found** appears only when the chosen name is missing (e.g. pond deleted while the dialog was open). **Transfer** defaults the first bucket to the full source amount when unset; **Add transfer bucket** redistributes the source total across all buckets using integer percent weights (first share `ceil(100/n)`, e.g. three buckets → 34/33/33) mapped to dollars via **`MoneyMath`**. **Split purchase** copies an empty purchase total from the spending amount when first opened, applies the same even-split defaults to two or more slices when unset, and redistributes on **Add slice**; dragging a slice slider to **100%** absorbs leftover cents under the `$0.50` step; edit loads existing groups without overwriting amounts. Split rows share `splitPurchaseGroupId` / `splitPurchaseBucketId`; deleting any slice removes the whole group (confirm). Edit enforces staying on the split tab for an existing split (no type conversion in v1).
- **Recurring chips:** Frequency (Weekly / Bi-weekly / Monthly) and weekday toggles use **DayNight-aware** unselected fills and `textColorPrimary` for unselected labels so labels stay readable in dark dialogs (no white-on-white).
- Transactions belong to ponds and contribute to month totals.
- Transactions can be filtered by selected ponds and date range.

## Bills days and paydays
- Users configure recurring **days of each month** (1–31) from the **calendar icon on the custom top bar** via a **Bills days / Paydays** tabbed dialog (not a full month calendar).
- **Bills days** drive the bills-period transaction filter only.
- **Paydays** drive optional **bank reconciliation** when a pond has **Account (bank slice)** entered: **Still to deposit** is the **schedule gap** as of today (`max(0, expected in bank by paydays reached − account)`); **Remaining** auto-syncs to that gap when reconciliation is active. Pond row also shows **Payday progress** (`N/M reached`). Footer and edit preview show **In bank | Still to deposit** (monthly **Limit** stays on the pond row / edit limit field—no duplicate Target). Ponds without Account keep standard transaction-driven remaining (limit + carry-over − spending).
- A separate **bills-period filter** icon next to the transfers toggle sets the **end date** to **today**. With **one** configured bills day, the **start date** is always that day in the **previous** calendar month (clamped), e.g. bills on the 12th and today May 13 → April 12 (not May 12). With **multiple** bills days, the start is the latest bills day on or before today, walking to prior months when needed. Toggling off restores the previously saved date range.
- Changing the visible month clears the bills-period filter.

## Transfers
- Transfers may reserve multiple destination buckets from one source transaction total.
- Each transfer bucket chooses a destination pond from ponds other than the source.
- In add/edit transaction dialogs, the source pond selector and transfer destinations use anchored exposed dropdown fields inside the modal instead of `Spinner` popups.
- The source summary transaction keeps the full user-entered total; the unallocated remainder stays normal spending in the source pond.
- Source and mirrored bucket rows share a transfer-group ID; each bucket also has its own bucket ID for edit/delete targeting.
- Transfer buckets use a fixed `$0.50` slider and `+/-` stepper for fast snapping, keep the slider stacked below the amount row on tighter screens, collapse scale landmarks when width is limited, and still allow exact-cent manual entry.
- Transfer summary text is larger and validation stays quiet until the user interacts or attempts save; toggling transfer mode auto-scrolls the dialog section into view and scrolling dismisses any open dropdown popup.
- Transfer visibility can be toggled in the transactions view.
- With transfers visible, the header **spinner** lists each destination pond and how much was transferred **to** it in the date range (no separate “from” rows).

## Recurring Transactions
- Recurring transactions support weekly, bi-weekly, and monthly patterns.
- Weekly/bi-weekly use weekday toggles.
- Monthly uses a day-picker calendar.
- In **add/edit transaction** dialogs, recurring frequency controls use **ripple chip backgrounds** (API 21+), **`AppCompatTextView`** monthly row with **tinted calendar** icon, and **check / close** icon actions (themed) instead of “Save”/“Cancel” text on the shell and on nested recurring day pickers.

## Month Rollover
- On a new month, the app sanitizes persisted state before switching months.
- Carry-over behavior is computed on a deep copy so startup never adopts half-migrated state.
- Malformed month strings, null month maps, null collections, and legacy transactions without a month are repaired safely.
- The active month is persisted only after the repaired envelope state is ready.

## Chrome
- Primary screen uses a **custom outlined top bar** (no ActionBar menu for reset): app title, bills-days setup calendar, and recalculate balances. Background uses **`mountain_top_bar_fill` / `mountain_top_bar_stroke`** (`bg_top_bar_outline`) with **`values-night`** overrides; title uses **`?attr/colorPrimary`**; bar actions use **`?attr/colorControlNormal`** tints like the rest of the app.

## UI and theming
- App theme: **`Theme.EnvelopeMoney`** / **`Theme.EnvelopeMoney.NoActionBar`** (`Theme.MaterialComponents.DayNight`) with Mountain palette (`mountain_primary`, teal accents).
- **ImageButtons** use `?attr/selectableItemBackgroundBorderless` and `?attr/colorControlNormal` tint where icons are platform vectors.
- **Spinners** use `ThemeOverlay.MountainMoney.Spinner` (Material overlay + `spinner_popup_*` / night `values-night/colors`) so dropdown surfaces follow DayNight.
- **Alerts** from `MainActivity` use **`MaterialAlertDialogBuilder`**; add/edit transaction and recurring sub-dialogs use **`applyIconMaterialDialogActions`** (check = confirm, X = dismiss) with **`mountain_primary`** / **`colorControlNormal`** tints.
- Recurring day chips use drawables tied to **`recurring_chip_*`** colors, not stock holo greens.
- Programmatic controls in **`MainActivity`** (recurring frequency/weekday chips, monthly day grid, bills-days grid, transfer/bills toggles) resolve **`textColorPrimary` / `textColorSecondary` / `colorControlNormal`** and **`mountain_primary`** instead of platform black/gray/teal fills.

# Data Schema

> **Note:** There is no separate SQL database in this app. This document is the **persistence schema**: SharedPreferences keys and Gson-serialized models. In protocol terms, “tables” = **preference keys and model types**.

## Persistence Store
The app persists most business state via SharedPreferences.

## SharedPreferences Areas
- `app_prefs`
  - `current_month`: persisted active month in `yyyy-MM`
- `envelope_prefs`
  - `envelopes`: Gson-serialized list of `Envelope`
  - `envelopes_collapsed`: envelopes section UI state
  - `last_add_transaction_envelope`
  - `last_add_transfer_destination_<sourceEnvelope>`
  - `last_transfer_totals_option`
  - `bills_days_json`: Gson-serialized list of integers (day-of-month 1–31); empty means none configured
  - `paydays_json`: Gson-serialized list of integers (day-of-month 1–31); global pay schedule for bank reconciliation
  - `bills_filter_active`: whether the bills-period filter is on (UI start = anchor, end = today)
  - `bills_filter_saved_start_display` / `bills_filter_saved_end_display`: `MMM d, yyyy` strings for the user's range **before** enabling the filter, restored when disabling

## Envelope Model
- `name: String`
- `limit: double` — **user-defined monthly budget** for the pond (same meaning as `originalLimit` after edits; **not** inflated by month carry-over; carry increases **remaining** and per-month pools instead).
- `originalLimit: double`
- `remaining: double`
- `transactions: List<Transaction>`
- `selected: boolean`
- `monthlyData: Map<String, MonthData>`
- `transfers: List<TransferData>`
- `manualRemaining: Double?`
- `baselineLimit: double`
- `baselineRemaining: double`
- `accountBalance: Double?` — optional real-world bank slice for this pond (not the budget remainder)

### Bank reconciliation mode (optional)
When `paydays_json` is non-empty **and** a pond has `accountBalance` set, `remaining` may be overwritten by `PondBankReconciliationHelper` using the **payday schedule gap** as of today: `MoneyMath.roundToCents(max(0, expectedInBankByToday - accountBalance))` where `expectedInBankByToday = (limit / paydaysInMonth) * paydaysPassed`. Displayed **Still to deposit** uses the same schedule gap (not full-month `limit - account`). Pond order is the `envelopes` JSON array order (reorder persists by rewriting that list). Manual remainder override is cleared when reconciliation applies to that pond. Ponds without paydays or without Account keep transaction-driven `remaining` via `calculateRemaining`.

## MonthData Model
- `limit: double` — **effective budget ceiling for that calendar month** in snapshots (may equal base + unused from the prior month when carry-over applies); distinct from envelope `limit` above.
- `remaining: double`
- `transactions: List<Transaction>`

## Transaction Model
- `envelopeName: String`
- `amount: double`
- `date: String`
- `comment: String` — for receipt capture, OCR may prefill with **merchant name only** (amount stays in `amount`).
- `month: String`
- `transferId: String?`
- `transferBucketId: String?` — `null` on the source summary transaction; set on mirrored destination rows so one source total can feed many transfer buckets
- `splitPurchaseGroupId: String?` — when set, this row is one slice of a **split purchase** (multi-pond expense); all slices share the same group id
- `splitPurchaseBucketId: String?` — stable id for that slice within the group (for edit/replace)
- `recurring: boolean`
- `recurringFrequency: String?`
- `recurringDays: List<Integer>`
- `recurringSeriesId: String?`
- `recurringTemplate: boolean`
- `receiptImageUri: String?` — optional `content://` URI for a JPEG saved under **Pictures/Mountain Money** (camera path) or the picked gallery URI.

## TransferData Model
- `id: String`
- `bucketId: String?` â€” stable per-bucket id inside a grouped transfer; legacy single-destination transfers may deserialize without it
- `toEnvelope: String`
- `amount: double`

## Grouped transfer semantics
- One source transaction may reserve part of its total into multiple destination transfer buckets.
- The source summary transaction keeps the full user-entered amount.
- Each `TransferData` row stores one reserved bucket amount for that transfer group.
- Each mirrored destination transaction stores both `transferId` and `transferBucketId` so edit/delete can target one grouped transfer while keeping bucket rows distinct.

## Split purchase semantics
- Each slice is a normal positive `Transaction` in its pond (`amount` is that pond’s portion of the purchase).
- Slices in one purchase share `splitPurchaseGroupId` and are distinguished by `splitPurchaseBucketId`.
- A transaction must not combine a non-empty `splitPurchaseGroupId` with a non-empty `transferId` (UI enforces mutual exclusion). Split purchases do not use `Envelope.TransferData` or negative mirror rows.
- Recurring is not supported for split purchases in v1 (dialog hides time/recurring on non-Spending tabs).

## Pond totals footer
- **Reconciliation mode** (paydays configured + at least one Account): one line, two values — **In bank** (sum of accounts), **Still to deposit** (sum of schedule-gap still-to-deposit per pond with Account). Monthly **limit** is shown separately on each pond row / edit dialog (not repeated as Target). All amounts cent-rounded.
- **Legacy** (otherwise): **Account (entered)** sum, **Remaining** sum, **Difference** when any Account exists; or Remaining only when none.

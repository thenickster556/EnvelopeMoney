# User Flows

## Launch Flow
1. App loads persisted ponds (`Envelope` list) and stored month state.
2. `MonthRolloverHelper` sanitizes envelope collections, numeric fields, and legacy transaction months.
3. If a new month is required, rollover is applied on a deep copy.
4. The repaired envelopes and active month are committed once.
5. If the bills-period filter was left on, the start date is set to the computed bills anchor and the end date to today (saved prefs hold the pre-filter range for restore when toggled off).
6. Pond and transaction lists render for the active month.

## Add Transaction Flow
1. User opens the transaction dialog (check icon confirms, close icon cancels).
2. User selects pond, amount, date, comment, and optional recurring/transfer settings (recurring chips, weekday toggles, monthly calendar row follow Mountain / DayNight theme).
3. Optionally: user taps **Camera scan** (capture mode chips on scan screen; image saved to **Pictures/Mountain Money**) or **From gallery**; app runs on-device OCR and prefills amount, date when found, and comment with **merchant name only**; user may **Preview receipt** or **Remove receipt** before save; new capture/pick replaces the attached image and re-runs OCR.
4. Validation runs without dismissing the dialog on errors.
5. Transaction is persisted and visible in history (optional `receiptImageUri` stored when applicable). Rows with a receipt show a **photo** icon; tap opens preview.

## Edit Transaction Flow
1. User opens **Edit** from the transaction list options.
2. Same receipt actions as add (**Camera scan**, **From gallery**, **Preview receipt**, **Remove receipt**); URI changes persist on save (or clear when removed).
3. Other fields and validation behave as before.

## Receipt preview (list)
1. On the main transaction list, if a row has a stored receipt URI, a **photo** icon appears next to edit.
2. Tapping the icon opens the receipt image preview dialog (read-only).

## Transfer Flow
1. User enables transfer mode in the transaction dialog.
2. User selects the destination pond from ponds other than the source (the source never appears in **Transfer To**).
3. App persists linked source/destination transactions.
4. Transfer rows and totals appear in the transactions view when transfer visibility is enabled. The transfer summary **spinner** shows amounts **to** each destination pond only.

## Bills days configuration
1. User taps the **calendar** on the custom top bar.
2. User toggles days 1–31 and saves; list is stored in `envelope_prefs`.

## Bills period filter
1. User taps the **filter** icon beside the transfers toggle (disabled or toast if no bills days configured).
2. App saves the current start/end display strings, sets **start** to the bills anchor date and **end** to today, and persists filter state. If there is a single configured bills day and today matches it, **start** is that calendar day in the **prior** month (not today).
3. User taps again to turn off; previous start/end strings are restored.

## Month Navigation Flow
1. User navigates between months.
2. Bills-period filter is cleared; date range resets to the month’s first/last day.
3. App loads the relevant month snapshots and transactions.
4. Future months are not navigable.

## Recalculate balances (top bar)
1. User taps **Recalculate balances** on the custom top bar.
2. For each pond, the app runs **`reset(false)`** (syncs `limit`/`remaining` to the stored monthly budget and clears manual remainder override) then **`calculateRemaining`** for the active month so remaining matches transactions.
3. State is saved and lists refresh. This is **Option A** behavior: it does not implement carry in this step; it realigns math to the user’s budget and current activity.

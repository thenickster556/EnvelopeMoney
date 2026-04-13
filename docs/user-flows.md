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
3. Validation runs without dismissing the dialog on errors.
4. Transaction is persisted and visible in history.

## Transfer Flow
1. User enables transfer mode in the transaction dialog.
2. User selects the destination pond.
3. App persists linked source/destination transactions.
4. Transfer rows and totals appear in the transactions view when transfer visibility is enabled.

## Bills days configuration
1. User taps the **calendar** on the custom top bar.
2. User toggles days 1–31 and saves; list is stored in `envelope_prefs`.

## Bills period filter
1. User taps the **filter** icon beside the transfers toggle (disabled or toast if no bills days configured).
2. App saves the current start/end display strings, sets **start** to the bills anchor date and **end** to today, and persists filter state.
3. User taps again to turn off; previous start/end strings are restored.

## Month Navigation Flow
1. User navigates between months.
2. Bills-period filter is cleared; date range resets to the month’s first/last day.
3. App loads the relevant month snapshots and transactions.
4. Future months are not navigable.

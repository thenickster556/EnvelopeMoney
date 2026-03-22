# Features

## Envelope Budgeting
- Users can create envelopes with monthly limits.
- Envelopes track remaining funds and month-specific snapshots.
- Envelopes can be collapsed in the UI and that preference is persisted.

## Transactions
- Users can add, edit, and delete transactions.
- Transactions belong to envelopes and contribute to month totals.
- Transactions can be filtered by selected envelopes and date range.

## Transfers
- Transfers move money between envelopes using linked transactions.
- Source and destination sides share a transfer ID.
- Transfer visibility can be toggled in the transactions view.
- Transfer summaries appear in the transactions header area.

## Recurring Transactions
- Recurring transactions support weekly, bi-weekly, and monthly patterns.
- Weekly/bi-weekly use weekday toggles.
- Monthly uses a day-picker calendar.

## Month Rollover
- On a new month, the app sanitizes persisted state before switching months.
- Carry-over behavior is computed on a deep copy so startup never adopts half-migrated state.
- Malformed month strings, null month maps, null collections, and legacy transactions without a month are repaired safely.
- The active month is persisted only after the repaired envelope state is ready.

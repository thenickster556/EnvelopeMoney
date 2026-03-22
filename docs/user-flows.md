# User Flows

## Launch Flow
1. App loads persisted envelopes and stored month state.
2. `MonthRolloverHelper` sanitizes envelope collections, numeric fields, and legacy transaction months.
3. If a new month is required, rollover is applied on a deep copy.
4. The repaired envelopes and active month are committed once.
5. Envelope and transaction lists render for the active month.

## Add Transaction Flow
1. User opens the transaction dialog.
2. User selects envelope, amount, date, comment, and optional recurring/transfer settings.
3. Validation runs without dismissing the dialog on errors.
4. Transaction is persisted and visible in history.

## Transfer Flow
1. User enables transfer mode in the transaction dialog.
2. User selects the destination envelope.
3. App persists linked source/destination transactions.
4. Transfer rows and totals appear in the transactions view when transfer visibility is enabled.

## Month Navigation Flow
1. User navigates between months.
2. App loads the relevant month snapshots and transactions.
3. Future months are not navigable.

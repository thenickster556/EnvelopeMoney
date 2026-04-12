# Features

## Pond budgeting (user-facing “ponds”; code may still use `Envelope` types)
- Users create ponds with monthly limits.
- Ponds track remaining funds (budget math) and month-specific snapshots.
- Optional **Account** field per pond: user-entered cash in the bank for that slice, for reconciliation against budget remainder.
- Ponds can be collapsed in the UI and that preference is persisted.
- Footer under the pond list shows sums of entered Account values, sum of Remaining, and the difference when any Account is set.

## Transactions
- Users can add, edit, and delete transactions.
- Transactions belong to ponds and contribute to month totals.
- Transactions can be filtered by selected ponds and date range.

## Bills days
- Users configure recurring **days of each month** (1–31) from the **calendar icon on the custom top bar** (not a full month calendar).
- A separate **bills-period filter** icon next to the transfers toggle snaps the **end date** of the transaction filter to the latest configured bills day on or before today (walking to prior months if needed). Toggling off restores the previously saved date range.
- Changing the visible month clears the bills-period filter.

## Transfers
- Transfers move money between ponds using linked transactions.
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

## Chrome
- Primary screen uses a **custom outlined top bar** (no ActionBar menu for reset): app title, bills-days setup calendar, and recalculate balances.

## UI and theming
- App theme: **`Theme.EnvelopeMoney`** / **`Theme.EnvelopeMoney.NoActionBar`** (`Theme.MaterialComponents.DayNight`) with Mountain palette (`mountain_primary`, teal accents).
- **ImageButtons** use `?attr/selectableItemBackgroundBorderless` and `?attr/colorControlNormal` tint where icons are platform vectors.
- **Spinners** use `ThemeOverlay.MountainMoney.Spinner` so dropdowns match primary colors.
- **Alerts** from `MainActivity` use **`MaterialAlertDialogBuilder`** so action buttons follow Material styling.
- Recurring day chips use drawables tied to **`recurring_chip_*`** colors, not stock holo greens.

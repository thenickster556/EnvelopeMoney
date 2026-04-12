# Data Schema

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
  - `bills_filter_active`: whether the bills-period filter (end date snapped to anchor) is on
  - `bills_filter_saved_start_display` / `bills_filter_saved_end_display`: `MMM d, yyyy` strings saved when enabling the filter, restored when disabling

## Envelope Model
- `name: String`
- `limit: double`
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

## MonthData Model
- `limit: double`
- `remaining: double`
- `transactions: List<Transaction>`

## Transaction Model
- `envelopeName: String`
- `amount: double`
- `date: String`
- `comment: String`
- `month: String`
- `transferId: String?`
- `recurring: boolean`
- `recurringFrequency: String?`
- `recurringDays: List<Integer>`
- `recurringSeriesId: String?`
- `recurringTemplate: boolean`

## TransferData Model
- `id: String`
- `toEnvelope: String`
- `amount: double`

## Pond totals footer
- **Account (entered):** sum of `accountBalance` where not null.
- **Remaining:** sum of each envelope’s `remaining` (budget).
- **Difference:** Account sum minus Remaining sum when at least one account value exists; otherwise only Remaining is shown.

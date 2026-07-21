# User Flows

## Launch Flow
1. App loads persisted ponds (`Envelope` list) and stored month state.
2. `MonthRolloverHelper` sanitizes envelope collections, numeric fields, and legacy transaction months.
3. If a new month is required, rollover is applied on a deep copy.
4. The repaired envelopes and active month are committed once.
5. If the bills-period filter was left on, the start date is set to the computed bills anchor and the end date to today (saved prefs hold the pre-filter range for restore when toggled off).
6. Pond and transaction lists render for the active month.

## Pond list flow
1. Pond section header is one row: **Ponds** + **N selected** on the left; **select**, **reorder**, **collapse**, and **add** icons on the right (same pattern as Transaction History).
2. Checkbox toggles filter the transaction list (footer totals still sum all ponds).
3. **Reorder** icon shows drag handles; drag a row (or long-press handle) to reorder with a lifted shadow; order persists in the envelopes JSON array.
4. **Done** icon exits reorder mode; collapsing the pond section also exits reorder mode.

## Add Transaction Flow
1. User opens the transaction dialog (check icon confirms, close icon cancels).
2. User selects pond, date, amount, and comment (fields appear first in the scrollable body).
3. User picks **Spending**, **Transfer**, or **Split purchase** on the **type** tabs below the comment; on **Spending**, **One-time** or **Recurring** appears on the **time** row directly under that (add defaults to **Spending** + **One-time**).
4. If **Spending** + **Recurring**, frequency chips, weekday toggles, and the monthly day row appear under the tabs (Mountain / DayNight theme). On **Transfer** or **Split purchase**, recurring is off and time tabs are hidden.
5. Optionally: user taps the **camera** or **gallery** icon (top toolbar; row scrolls horizontally if needed); capture mode chips on scan screen; image saved to **Pictures/Mountain Money** (gallery picks are **imported** there on the main thread before OCR—**moved** when the system allows delete, otherwise one app-owned copy); OK is disabled until import attaches the receipt URI; app runs on-device OCR and prefills amount (into amount or **purchase total** on Split), date when found, and comment with **merchant name only**; if the receipt date is **outside the current Start/End filter**, a hint appears (widen the range to see the row after save); user may **preview** or **remove** (icon actions) before save; new capture/pick replaces the attached image and re-runs OCR. The dialog body scrolls vertically when tall so transfer or split controls stay reachable.
6. User may switch **Spending**, **Transfer**, and **Split purchase** tabs to compare modes; transfer buckets and split slices stay in memory (sections hide/show only) until save or cancel.
7. If **Transfer** is selected, the dialog auto-scrolls to the transfer summary/buckets; the first bucket defaults to **100%** of the source amount when still unallocated. **Add transfer bucket** re-splits the source total evenly (integer percents with the first bucket taking the ceiling share, e.g. $100 across three buckets → $34 / $33 / $33). Pond and destination selectors open as anchored dropdowns inside the modal; scrolling dismisses those dropdowns cleanly.
8. If **Split purchase** is selected, an empty **purchase total** may be prefilled from the spending **amount**; two default slices receive an even split of the purchase total when unset, and **Add slice** redistributes like transfer buckets. Scrolling dismisses slice dropdowns.
9. Transfer and split bucket amounts can be adjusted by fixed `$0.50` slider/stepper controls or exact-cent manual entry; split slices at slider **100%** absorb odd-cent remainders automatically; validation stays quiet until the user interacts or attempts save.
10. Validation runs without dismissing the dialog on errors.
11. Transaction is persisted and visible in history (optional `receiptImageUri` stored when applicable). Rows with a receipt show a **photo** icon; tap opens preview. **Split purchase** rows show a compact slice line by default; an expand control toggles the full allocation breakdown for that group (all sibling rows stay in sync).

## Edit Transaction Flow
1. User opens **Edit** from the transaction list options.
2. The dialog opens on the tab that matches the stored transaction (**Spending**, **Transfer**, or **Split purchase**); type and time tabs sit below the comment field like add. Type conversion between plain/transfer and split is not supported in v1 (errors on save if mismatched).
3. Same receipt icon actions as add (**camera**, **gallery**, **preview**, **remove**); URI changes persist on save (or clear when removed).
4. Transfer mode uses the same anchored dropdowns, auto-scroll, fixed-step slider landmarks, exact-cent manual entry, and delayed validation behavior as the add flow.
5. Split edit loads all slices for the group; save replaces the group atomically.
6. Other fields and validation behave as before.

## Receipt preview (list and dialogs)
1. On the main transaction list, if a row has a stored receipt URI, a **photo** icon appears next to edit.
2. **Preview** icon in add/edit dialogs, or the list **photo** icon, opens **ReceiptPreviewActivity** using the **stored** `receiptImageUri` (no second gallery import/move). Fullscreen image, **pinch to zoom**, **drag** to pan when zoomed, **double-tap** to refit. **Rotate left / Rotate right** for 90° **view-only** steps. **Save rotation** is **enabled** (not merely visible) when the net angle mod 360° is non-zero; it prompts **Replace / Cancel**, then decodes from the same URI, **Matrix**-rotates pixels, overwrites the JPEG at quality **92**, **reloads** the image, and resets view rotation to **0°**. **Back** or **close** with unsaved rotation: **Keep editing** or **Discard**. Failed writes show a **Material** alert. Unreadable URIs show **Could not open this image.**

## Transfer Flow
1. User selects the **Transfer** tab in the add or edit transaction dialog.
2. The dialog auto-scrolls the transfer section into view and keeps the modal visually focused while the background stays dimmed.
3. App shows a transfer summary plus one or more transfer buckets. The first bucket starts at the full source total when unset; adding buckets re-splits the total evenly (first bucket gets the ceiling share of integer percents). Each bucket chooses a destination pond from ponds other than the source and sets an amount through a fixed-step slider, `+/-` buttons, or exact manual entry.
4. On tighter screens, the amount row stays separate from the slider and the landmark row compacts so controls do not crowd or clip; scrolling the dialog dismisses any open transfer dropdown popup.
5. The source total stays intact; each bucket reserves part of that total for transfer, and any unallocated remainder stays normal spending in the source pond.
6. App persists one source summary transaction, one `TransferData` row per bucket, and one mirrored destination transaction per bucket.
7. Transfer rows and totals appear in the transactions view when transfer visibility is enabled. The transfer summary **spinner** shows amounts **to** each destination pond only.

## Bills days and paydays configuration
1. User taps the **calendar** on the custom top bar.
2. **Bills days** tab: toggle days 1–31 and save to `bills_days_json` (unchanged filter behavior).
3. **Paydays** tab: toggle paydays 1–31 and save to `paydays_json`. Saving applies bank reconciliation to ponds that have Account set (check icon confirms): Remaining becomes Account + unlocked payday slices − month spend; Still to deposit becomes unpassed payday slices of Limit.

## Bills period filter
1. User taps the **filter** icon beside the transfers toggle (disabled or toast if no bills days configured).
2. App saves the current start/end display strings, sets **start** to the bills anchor date and **end** to today, and persists filter state. Anchor rules: latest passed bills day in the current month when applicable (May 15, bills 10 → May 10); prior month when none passed yet (Feb 10, bills 15 → Jan 15); on a multi-day period-end, previous bills day in the set (Apr 15, [1,15] → Apr 1); single bills day on today → same day previous month.
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

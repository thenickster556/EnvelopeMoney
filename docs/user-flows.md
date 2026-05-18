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
2. User picks **Spending**, **Transfer**, or **Split purchase** on the type tabs; on **Spending**, **One-time** or **Recurring** time tabs appear (add defaults to **Spending** + **One-time**).
3. On **Spending**: user selects pond, amount, date, comment, and optional recurring (chips, weekday toggles, monthly calendar row follow Mountain / DayNight theme). On **Transfer** or **Split purchase**, recurring is off and time tabs are hidden.
4. Optionally: user taps the **camera** or **gallery** icon (top toolbar; row scrolls horizontally if needed); capture mode chips on scan screen; image saved to **Pictures/Mountain Money**; app runs on-device OCR and prefills amount (into amount or **purchase total** on Split), date when found, and comment with **merchant name only**; user may **preview** or **remove** (icon actions) before save; new capture/pick replaces the attached image and re-runs OCR. The dialog body scrolls vertically when tall so transfer or split controls stay reachable.
5. If **Transfer** is selected, the dialog auto-scrolls to the transfer summary/buckets; the pond and destination selectors open as anchored dropdowns inside the modal, and scrolling dismisses those dropdowns cleanly.
6. If **Split purchase** is selected, the user enters a **purchase total** and two or more pond slices that sum to that total; scrolling dismisses slice dropdowns.
7. Transfer bucket amounts can be adjusted by fixed `$0.50` slider/stepper controls or exact-cent manual entry; validation stays quiet until the user interacts or attempts save.
8. Validation runs without dismissing the dialog on errors.
9. Transaction is persisted and visible in history (optional `receiptImageUri` stored when applicable). Rows with a receipt show a **photo** icon; tap opens preview. **Split purchase** rows show a compact slice line by default; an expand control toggles the full allocation breakdown for that group (all sibling rows stay in sync).

## Edit Transaction Flow
1. User opens **Edit** from the transaction list options.
2. The dialog opens on the tab that matches the stored transaction (**Spending**, **Transfer**, or **Split purchase**); type conversion between plain/transfer and split is not supported in v1 (errors on save if mismatched).
3. Same receipt icon actions as add (**camera**, **gallery**, **preview**, **remove**); URI changes persist on save (or clear when removed).
4. Transfer mode uses the same anchored dropdowns, auto-scroll, fixed-step slider landmarks, exact-cent manual entry, and delayed validation behavior as the add flow.
5. Split edit loads all slices for the group; save replaces the group atomically.
6. Other fields and validation behave as before.

## Receipt preview (list and dialogs)
1. On the main transaction list, if a row has a stored receipt URI, a **photo** icon appears next to edit.
2. **Preview** icon in add/edit dialogs, or the list **photo** icon, opens **ReceiptPreviewActivity**: fullscreen image, **pinch to zoom**, **drag** to pan when zoomed, **double-tap** to refit, **Rotate left / Rotate right** for 90° **view-only** steps. **Save rotation** is **enabled** (not merely visible) when the net angle mod 360° is non-zero; it prompts **Replace / Cancel**, then decodes from the same URI, **Matrix**-rotates pixels, overwrites the JPEG at quality **92**, **reloads** the image, and resets view rotation to **0°**. **Back** or **close** with unsaved rotation: **Keep editing** or **Discard**. Failed writes show a **Material** alert.

## Transfer Flow
1. User selects the **Transfer** tab in the add or edit transaction dialog.
2. The dialog auto-scrolls the transfer section into view and keeps the modal visually focused while the background stays dimmed.
3. App shows a transfer summary plus one or more transfer buckets. Each bucket chooses a destination pond from ponds other than the source and sets an amount through a fixed-step slider, `+/-` buttons, or exact manual entry.
4. On tighter screens, the amount row stays separate from the slider and the landmark row compacts so controls do not crowd or clip; scrolling the dialog dismisses any open transfer dropdown popup.
5. The source total stays intact; each bucket reserves part of that total for transfer, and any unallocated remainder stays normal spending in the source pond.
6. App persists one source summary transaction, one `TransferData` row per bucket, and one mirrored destination transaction per bucket.
7. Transfer rows and totals appear in the transactions view when transfer visibility is enabled. The transfer summary **spinner** shows amounts **to** each destination pond only.

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

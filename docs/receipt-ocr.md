# Receipt OCR (Mountain Money)

## Architecture

- **UI:** **Add** and **edit** transaction dialogs — **Camera scan** opens `ReceiptCaptureActivity` (CameraX); **From gallery** uses the Photo Picker / `GetContent`. **Preview receipt** / **Remove receipt** use the same outlined `MaterialButton` styling as the scan row. `MainActivity` keeps a single `receiptDialogHostView` while either dialog is open so activity results route OCR to the active form.
- **List:** `item_transaction` **photo** `ImageButton` when `ReceiptRowUi.showReceiptThumbnail` is true; opens the same preview as the dialogs.
- **Preview:** `ReceiptPreviewActivity` — **fullscreen** (`Theme.EnvelopeMoney.ReceiptPreview`), sampled decode via `ReceiptBitmapLoader` (screen-based max dimension, cap 4096). **Pinch-zoom**, **drag** when zoomed, **double-tap** to refit, **Rotate left/right** (90°). `ReceiptZoomImageView` implements gestures; list and add/edit dialogs start this activity with `EXTRA_IMAGE_URI`.
- **Gallery file:** Camera shots are saved with `MediaStore` under **`Pictures/Mountain Money`** (`MediaStoreReceiptSaver`).
- **OCR slot:** `OcrEngine` + `PaddleOcrAdapter.createDefaultEngine()` — currently **on-device ML Kit Latin** text recognition (offline after model init). Swap in **PaddleOCR JNI** + models when packaged without changing `ReceiptFieldParser` tests.
- **Parsing:** `ReceiptFieldParser` (pure Java) + `ReceiptFieldParserTest` golden cases.
  - **Merchant (comment):** among the first lines, pick a scored candidate that is not a phone, URL, email, “thank you”, “guest check” / table / server, street-style address, or zip-only line. When the line is mostly **ALL CAPS** (typical OCR), **title-case** words for display; **mixed case** from OCR is left as-is. Heuristics are **English-oriented**; garbled or international receipts may still need a manual comment edit.
  - **Total:** for **RETAURANT** and **RECEIPT** capture modes, look from the **bottom** of the text for a strong “final” label (`amount due`, `balance due`, `total due`, `grand total`, `pay this amount`, plus generic `total` / `amount due` phrasing) so the field prefers the **last** such line (often the charge **after** tip) over an earlier pre-tip total. **GAS** still ignores gallon lines and takes the max money candidate. If nothing matches, behavior falls back to the largest money amount with lower confidence.
- **Persistence:** Optional `Transaction.receiptImageUri` (Gson) for the saved JPEG URI.
- **Tests:** `ReceiptRowUiTest` for list thumbnail visibility rules (placeholder row excluded).

## Protocol

Implementations follow [AI_CHANGE_PROTOCOL.md](AI_CHANGE_PROTOCOL.md): update [DATA_SCHEMA.md](DATA_SCHEMA.md), [TASK_STATE.json](../state/TASK_STATE.json), README Change Log, and run tests on a Gradle-compatible JDK.

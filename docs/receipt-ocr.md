# Receipt OCR (Mountain Money)

## Architecture

- **UI:** **Add** and **edit** transaction dialogs — **Camera scan** opens `ReceiptCaptureActivity` (CameraX); **From gallery** uses the Photo Picker / `GetContent`. **Preview receipt** / **Remove receipt** use the same outlined `MaterialButton` styling as the scan row. `MainActivity` keeps a single `receiptDialogHostView` while either dialog is open so activity results route OCR to the active form.
- **List:** `item_transaction` **photo** `ImageButton` when `ReceiptRowUi.showReceiptThumbnail` is true; opens the same preview as the dialogs.
- **Preview:** `ReceiptPreviewActivity` — **fullscreen** (`Theme.EnvelopeMoney.ReceiptPreview`), sampled decode via `ReceiptBitmapLoader` (screen-based max dimension, cap 4096). **Pinch-zoom**, **drag** when zoomed, **double-tap** to refit. **Rotate left/right** uses **view** rotation in 90° steps until **Save rotation** (enabled when net rotation mod 360° ≠ 0); **MaterialAlertDialogBuilder** confirms **Replace / Cancel**, then `ReceiptRotatedJpegWriter.writeRotatedJpegOverwrite` decodes from the same URI, **Matrix**-rotates pixels, writes JPEG at quality **92** (`openOutputStream(uri, "w")`), and the activity **reloads** the bitmap and clears view rotation. Failed writes show a **Material** alert. **Back** / close when dirty: **Keep editing** / **Discard**. `ReceiptZoomImageView` handles gestures; `EXTRA_IMAGE_URI` opens this screen.
- **Gallery file:** Camera shots are saved upright (EXIF applied via `ReceiptExifBitmapLoader`) with `MediaStore` under **`Pictures/Mountain Money`** (`MediaStoreReceiptSaver`).
- **OCR slot:** `OcrEngine` + `PaddleOcrAdapter.createDefaultEngine()` — currently **on-device ML Kit Latin** text recognition (offline after model init). Swap in **PaddleOCR JNI** + models when packaged without changing `ReceiptFieldParser` tests.
- **Parsing:** `ReceiptFieldParser` (pure Java) + `ReceiptFieldParserTest` golden cases.
  - **Merchant (comment):** ML Kit lines sorted top-to-bottom; brand capped to **one–two words** after junk filters (phone, URL, order/points lines, survey boilerplate). ALL CAPS title-cased; comment prefilled only when empty.
  - **Total:** labeled **`$` totals** preferred bottom-up; restaurant tip composition unchanged; fallback scoring deprioritizes order/points lines without total labels.
- **Persistence:** Optional `Transaction.receiptImageUri` (Gson) for the saved JPEG URI.
- **Tests:** `ReceiptRowUiTest` for list thumbnail visibility rules (placeholder row excluded).

## Protocol

Implementations follow [AI_CHANGE_PROTOCOL.md](AI_CHANGE_PROTOCOL.md): update [DATA_SCHEMA.md](DATA_SCHEMA.md), [TASK_STATE.json](../state/TASK_STATE.json), README Change Log, and run tests on a Gradle-compatible JDK.

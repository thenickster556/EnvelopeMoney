# Receipt OCR (Mountain Money)

## Architecture

- **UI:** **Add** and **edit** transaction dialogs — **Camera scan** opens `ReceiptCaptureActivity` (CameraX); **From gallery** uses the Photo Picker / `GetContent`. **Preview receipt** / **Remove receipt** use the same outlined `MaterialButton` styling as the scan row. `MainActivity` keeps a single `receiptDialogHostView` while either dialog is open so activity results route OCR to the active form.
- **List:** `item_transaction` **photo** `ImageButton` when `ReceiptRowUi.showReceiptThumbnail` is true; opens the same preview as the dialogs.
- **Preview:** `dialog_receipt_preview.xml` + sampled `BitmapFactory` decode (max dimension cap) to avoid OOM; `MaterialAlertDialogBuilder` + `applyIconMaterialDialogActions` for themed dismiss control.
- **Gallery file:** Camera shots are saved with `MediaStore` under **`Pictures/Mountain Money`** (`MediaStoreReceiptSaver`).
- **OCR slot:** `OcrEngine` + `PaddleOcrAdapter.createDefaultEngine()` — currently **on-device ML Kit Latin** text recognition (offline after model init). Swap in **PaddleOCR JNI** + models when packaged without changing `ReceiptFieldParser` tests.
- **Parsing:** `ReceiptFieldParser` (pure Java) + `ReceiptFieldParserTest` golden cases.
- **Persistence:** Optional `Transaction.receiptImageUri` (Gson) for the saved JPEG URI.
- **Tests:** `ReceiptRowUiTest` for list thumbnail visibility rules (placeholder row excluded).

## Protocol

Implementations follow [AI_CHANGE_PROTOCOL.md](AI_CHANGE_PROTOCOL.md): update [DATA_SCHEMA.md](DATA_SCHEMA.md), [TASK_STATE.json](../state/TASK_STATE.json), README Change Log, and run tests on a Gradle-compatible JDK.

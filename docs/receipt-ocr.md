# Receipt OCR (Mountain Money)

## Architecture

- **UI:** New transaction dialog — **Camera scan** opens `ReceiptCaptureActivity` (CameraX); **From gallery** uses the Photo Picker / `GetContent`.
- **Gallery file:** Camera shots are saved with `MediaStore` under **`Pictures/Mountain Money`** (`MediaStoreReceiptSaver`).
- **OCR slot:** `OcrEngine` + `PaddleOcrAdapter.createDefaultEngine()` — currently **on-device ML Kit Latin** text recognition (offline after model init). Swap in **PaddleOCR JNI** + models when packaged without changing `ReceiptFieldParser` tests.
- **Parsing:** `ReceiptFieldParser` (pure Java) + `ReceiptFieldParserTest` golden cases.
- **Persistence:** Optional `Transaction.receiptImageUri` (Gson) for the saved JPEG URI.

## Protocol

Implementations follow [AI_CHANGE_PROTOCOL.md](AI_CHANGE_PROTOCOL.md): update [DATA_SCHEMA.md](DATA_SCHEMA.md), [TASK_STATE.json](../state/TASK_STATE.json), README Change Log, and run tests on a Gradle-compatible JDK.

package com.example.envelopemoney.receipt;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.ArrayList;
import java.util.List;

/**
 * On-device Latin script OCR (ML Kit). Fits the {@link OcrEngine} slot planned for PaddleOCR.
 */
public final class MlKitLatinOcrEngine implements OcrEngine {

    private final TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void recognizeAsync(Context context, Bitmap bitmap, OcrCallback callback) {
        if (bitmap == null) {
            mainHandler.post(() -> callback.onFailure(new IllegalArgumentException("null bitmap")));
            return;
        }
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        Task<Text> task = recognizer.process(image);
        task.addOnSuccessListener(text -> {
            List<OcrLine> lines = new ArrayList<>();
            for (Text.TextBlock block : text.getTextBlocks()) {
                for (Text.Line line : block.getLines()) {
                    String t = line.getText();
                    if (t != null && !t.trim().isEmpty()) {
                        lines.add(new OcrLine(t.trim(), 0.9f));
                    }
                }
            }
            OcrResult result = new OcrResult(lines);
            mainHandler.post(() -> callback.onSuccess(result));
        }).addOnFailureListener(e -> mainHandler.post(() -> callback.onFailure(e)));
    }
}

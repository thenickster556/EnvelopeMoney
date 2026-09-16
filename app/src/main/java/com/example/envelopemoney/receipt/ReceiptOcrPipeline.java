package com.example.envelopemoney.receipt;

import android.content.Context;
import android.graphics.Bitmap;
/**
 * Preprocess → OCR → parse. OCR callbacks are delivered on the main thread (see {@link MlKitLatinOcrEngine}).
 */
public final class ReceiptOcrPipeline {

    public interface PipelineCallback {
        void onResult(ReceiptDraft draft);

        void onError(Throwable t);
    }

    private final OcrEngine ocrEngine;

    public ReceiptOcrPipeline(OcrEngine ocrEngine) {
        this.ocrEngine = ocrEngine != null ? ocrEngine : PaddleOcrAdapter.createDefaultEngine();
    }

    public void runAsync(Context context, Bitmap bitmap, ReceiptCaptureMode mode, float[] amountWeights, PipelineCallback callback) {
        if (bitmap == null) {
            callback.onError(new IllegalArgumentException("bitmap null"));
            return;
        }
        final Bitmap source = bitmap;
        final Bitmap forOcr = ImagePreprocessor.forOcr(bitmap);
        ocrEngine.recognizeAsync(context, forOcr, new OcrEngine.OcrCallback() {
            @Override
            public void onSuccess(OcrResult result) {
                if (forOcr != source) {
                    forOcr.recycle();
                }
                callback.onResult(ReceiptFieldParser.parse(result, mode, amountWeights));
            }

            @Override
            public void onFailure(Exception error) {
                if (forOcr != source) {
                    forOcr.recycle();
                }
                callback.onError(error);
            }
        });
    }
}

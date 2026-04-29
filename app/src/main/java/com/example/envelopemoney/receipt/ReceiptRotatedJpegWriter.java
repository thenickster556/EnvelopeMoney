package com.example.envelopemoney.receipt;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Rewrites an existing receipt image URI in place as JPEG with a rotation applied to decoded pixels.
 * Quality matches {@link MediaStoreReceiptSaver} ({@value #JPEG_QUALITY}).
 */
public final class ReceiptRotatedJpegWriter {

    static final int JPEG_QUALITY = 92;
    static final int DECODE_MAX_DIM = 4096;

    private ReceiptRotatedJpegWriter() {
    }

    /**
     * Decodes from {@code uri}, rotates by {@code degrees} (typically a multiple of 90), compresses
     * as JPEG, and writes to the same {@code uri} (truncating existing content).
     */
    public static void writeRotatedJpegOverwrite(@NonNull Context context, @NonNull Uri uri, float degrees)
            throws IOException {
        float d = degrees % 360f;
        if (d < -0.01f) {
            d += 360f;
        }
        if (d < 0.01f || d > 359.99f) {
            d = 0f;
        }

        Bitmap src = ReceiptBitmapLoader.decodeSampled(context, uri, DECODE_MAX_DIM);
        if (src == null) {
            throw new IOException("decode failed");
        }
        Bitmap toEncode = null;
        try {
            if (d > 0.01f) {
                Matrix m = new Matrix();
                m.postRotate(d);
                toEncode = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
                src.recycle();
                src = null;
            } else {
                toEncode = src;
                src = null;
            }
            try (OutputStream out = context.getContentResolver().openOutputStream(uri, "w")) {
                if (out == null) {
                    throw new IOException("openOutputStream null");
                }
                if (!toEncode.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
                    throw new IOException("compress failed");
                }
            }
        } finally {
            if (src != null && !src.isRecycled()) {
                src.recycle();
            }
            if (toEncode != null && !toEncode.isRecycled()) {
                toEncode.recycle();
            }
        }
    }
}

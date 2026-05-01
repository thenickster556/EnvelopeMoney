package com.example.envelopemoney.receipt;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.IOException;
import java.io.InputStream;

/**
 * Photo picker URIs ({@code picker_get_content}) cannot be delegated to another activity with URI grants.
 * Normalize by copying once into {@link MediaStoreReceiptSaver}'s gallery album so preview/OCR use a stable URI.
 */
public final class ReceiptPickerUriNormalizer {

    private ReceiptPickerUriNormalizer() {
    }

    public static Uri normalize(Context context, Uri uri) throws IOException {
        if (uri == null) {
            throw new IOException("uri null");
        }
        String s = uri.toString();
        if (!s.contains("picker_get_content")) {
            return uri;
        }
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) {
                throw new IOException("openInputStream null");
            }
            Bitmap bmp = BitmapFactory.decodeStream(is);
            if (bmp == null) {
                throw new IOException("decode bitmap failed");
            }
            try {
                return MediaStoreReceiptSaver.saveJpeg(context, bmp);
            } finally {
                bmp.recycle();
            }
        } catch (SecurityException e) {
            throw new IOException("picker uri permission denied", e);
        }
    }
}

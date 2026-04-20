package com.example.envelopemoney.receipt;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.IOException;
import java.io.InputStream;

/**
 * Loads receipt images with subsampling to cap memory use.
 */
public final class ReceiptBitmapLoader {

    private ReceiptBitmapLoader() {
    }

    public static Bitmap decodeSampled(Context context, Uri uri, int maxDim) throws IOException {
        if (uri == null || maxDim < 1) {
            return null;
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) {
                return null;
            }
            BitmapFactory.decodeStream(is, null, opts);
        }
        opts.inSampleSize = 1;
        int h = opts.outHeight;
        int w = opts.outWidth;
        if (h > maxDim || w > maxDim) {
            int halfH = h / 2;
            int halfW = w / 2;
            while ((halfH / opts.inSampleSize) > maxDim || (halfW / opts.inSampleSize) > maxDim) {
                opts.inSampleSize *= 2;
            }
        }
        opts.inJustDecodeBounds = false;
        try (InputStream is2 = context.getContentResolver().openInputStream(uri)) {
            return is2 != null ? BitmapFactory.decodeStream(is2, null, opts) : null;
        }
    }
}

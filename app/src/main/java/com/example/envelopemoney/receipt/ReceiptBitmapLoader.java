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
        try {
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
            Bitmap decoded;
            try (InputStream is2 = context.getContentResolver().openInputStream(uri)) {
                decoded = is2 != null ? BitmapFactory.decodeStream(is2, null, opts) : null;
            }
            if (decoded == null) {
                return null;
            }
            int rotation = readExifRotation(context, uri);
            if (rotation == 0) {
                return decoded;
            }
            android.graphics.Matrix matrix = new android.graphics.Matrix();
            matrix.postRotate(rotation);
            Bitmap rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.getWidth(), decoded.getHeight(), matrix, true);
            if (rotated != decoded) {
                decoded.recycle();
            }
            return rotated;
        } catch (SecurityException e) {
            throw new IOException("uri permission denied", e);
        }
    }

    private static int readExifRotation(Context context, Uri uri) throws IOException {
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) {
                return 0;
            }
            return ReceiptExifBitmapLoader.exifToDegrees(
                    new androidx.exifinterface.media.ExifInterface(is).getAttributeInt(
                            androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                            androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL));
        }
    }
}

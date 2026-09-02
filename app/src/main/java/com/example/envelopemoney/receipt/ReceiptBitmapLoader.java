package com.example.envelopemoney.receipt;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.IOException;

/**
 * Loads receipt images with subsampling to cap memory use.
 */
public final class ReceiptBitmapLoader {

    private ReceiptBitmapLoader() {
    }

    public static Bitmap decodeSampled(Context context, Uri uri, int maxDim) throws IOException {
        if (context == null || uri == null || maxDim < 1) {
            return null;
        }
        byte[] data = ReceiptUriStreams.readAllBytes(context, uri);
        if (data.length == 0) {
            return null;
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, opts);
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
        Bitmap decoded = BitmapFactory.decodeByteArray(data, 0, data.length, opts);
        if (decoded == null) {
            return null;
        }
        int rotation = ReceiptExifBitmapLoader.readExifRotationDegreesFromBytes(data);
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
    }
}

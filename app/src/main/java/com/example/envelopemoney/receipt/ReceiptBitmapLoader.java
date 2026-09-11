package com.example.envelopemoney.receipt;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;

/**
 * Loads receipt images with subsampling to cap memory use.
 */
public final class ReceiptBitmapLoader {

    private ReceiptBitmapLoader() {
    }

    /** Resolve once through the same rules used by repair, OCR and rotation. */
    @NonNull
    public static Uri requireResolvedUri(Context context, Uri uri) throws IOException {
        ReceiptReferenceResolver.Result result = AndroidReceiptSource.resolve(context, uri.toString(), null);
        if (result.status != ReceiptReferenceResolver.Status.RESOLVED) {
            throw new IOException("Receipt cannot be loaded: " + result.status);
        }
        return AndroidReceiptSource.providerUri(result.reference);
    }

    @NonNull
    public static InputStream openInputStream(@NonNull Context context, @NonNull Uri uri)
            throws IOException {
        return openResolvedStream(context, requireResolvedUri(context, uri));
    }

    private static InputStream openResolvedStream(Context context, Uri uri) throws IOException {
        try {
            InputStream stream = context.getContentResolver().openInputStream(uri);
            if (stream == null) throw new IOException("Receipt stream is unavailable");
            return stream;
        } catch (SecurityException denied) {
            throw new IOException("Receipt permission denied", denied);
        }
    }
    public static Bitmap decodeSampled(Context context, Uri uri, int maxDim) throws IOException {
        if (context == null || uri == null || maxDim < 1) {
            return null;
        }
        uri = requireResolvedUri(context, uri);
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        try (InputStream is = openResolvedStream(context, uri)) {
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
        try (InputStream is2 = openResolvedStream(context, uri)) {
            decoded = BitmapFactory.decodeStream(is2, null, opts);
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
    }

    private static int readExifRotation(Context context, Uri uri) throws IOException {
        try (InputStream is = openResolvedStream(context, uri)) {
            return ReceiptExifBitmapLoader.exifToDegrees(
                    new androidx.exifinterface.media.ExifInterface(is).getAttributeInt(
                            androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                            androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL));
        }
    }
}

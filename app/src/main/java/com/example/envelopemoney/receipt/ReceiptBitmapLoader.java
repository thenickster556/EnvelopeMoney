package com.example.envelopemoney.receipt;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Loads receipt images with subsampling to cap memory use.
 * Reads the URI once: some providers allow only a single {@code openInputStream}.
 */
public final class ReceiptBitmapLoader {

    private ReceiptBitmapLoader() {
    }

    @NonNull
    public static InputStream openInputStream(@NonNull Context context, @NonNull Uri uri)
            throws IOException {
        return openRaw(context, uri);
    }

    public static Bitmap decodeSampled(Context context, Uri uri, int maxDim) throws IOException {
        if (context == null || uri == null || maxDim < 1) {
            return null;
        }
        byte[] data = readAllBytes(context, uri);
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

    public static boolean isReadable(Context context, Uri uri) {
        if (context == null || uri == null) {
            return false;
        }
        try {
            byte[] data = readAllBytes(context, uri);
            if (data.length == 0) {
                return false;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, opts);
            return opts.outWidth > 0 && opts.outHeight > 0;
        } catch (IOException | RuntimeException unavailable) {
            return false;
        }
    }

    @NonNull
    static byte[] readAllBytes(@NonNull Context context, @NonNull Uri uri) throws IOException {
        try (InputStream in = openRaw(context, uri)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    @NonNull
    private static InputStream openRaw(@NonNull Context context, @NonNull Uri uri) throws IOException {
        Uri withoutFragment = uri.getFragment() == null ? uri : uri.buildUpon().fragment(null).build();
        if ("file".equalsIgnoreCase(withoutFragment.getScheme())) {
            String path = withoutFragment.getPath();
            if (path != null) {
                File file = new File(path);
                if (file.isFile()) {
                    return new FileInputStream(file);
                }
            }
        }
        try {
            InputStream stream = context.getContentResolver().openInputStream(withoutFragment);
            if (stream == null) {
                throw new IOException("Receipt stream is unavailable");
            }
            return stream;
        } catch (SecurityException denied) {
            throw new IOException("uri permission denied", denied);
        }
    }
}

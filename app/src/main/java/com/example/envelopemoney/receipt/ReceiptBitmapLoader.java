package com.example.envelopemoney.receipt;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Loads receipt images with subsampling to cap memory use.
 * {@code content://} is read once (some providers allow only a single {@code openInputStream}).
 * {@code file://} uses a two-pass {@code decodeFile} so the whole JPEG is not slurped into RAM.
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
        File file = localFile(uri);
        if (file != null) {
            return decodeSampledFromFile(file, maxDim);
        }
        byte[] data = readAllBytes(context, uri);
        if (data.length == 0) {
            return null;
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, opts);
        opts.inSampleSize = sampleSize(opts.outWidth, opts.outHeight, maxDim);
        opts.inJustDecodeBounds = false;
        Bitmap decoded = BitmapFactory.decodeByteArray(data, 0, data.length, opts);
        if (decoded == null) {
            return null;
        }
        return rotateIfNeeded(decoded, ReceiptExifBitmapLoader.readExifRotationDegreesFromBytes(data));
    }

    /**
     * Power-of-two subsample so both sides fit inside {@code maxDim}.
     * Images already inside the cap stay sample 1. A 512px square with max 64 needs 16
     * (the half-size loop in the platform sample would otherwise leave a 128px bitmap).
     */
    static int sampleSize(int width, int height, int maxDim) {
        if (maxDim < 1 || width < 1 || height < 1) {
            return 1;
        }
        if (width <= maxDim && height <= maxDim) {
            return 1;
        }
        int sample = 1;
        while ((width / sample) >= maxDim || (height / sample) >= maxDim) {
            sample *= 2;
            if (sample >= 1024) {
                return sample;
            }
        }
        return sample;
    }

    public static boolean isReadable(Context context, Uri uri) {
        if (context == null || uri == null) {
            return false;
        }
        try {
            File file = localFile(uri);
            if (file != null) {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
                return opts.outWidth > 0 && opts.outHeight > 0;
            }
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

    private static Bitmap decodeSampledFromFile(File file, int maxDim) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
        opts.inSampleSize = sampleSize(opts.outWidth, opts.outHeight, maxDim);
        opts.inJustDecodeBounds = false;
        Bitmap decoded = BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
        if (decoded == null) {
            return null;
        }
        return rotateIfNeeded(decoded,
                ReceiptExifBitmapLoader.readExifRotationDegreesFromFile(file.getAbsolutePath()));
    }

    private static Bitmap rotateIfNeeded(Bitmap decoded, int rotation) {
        if (rotation == 0) {
            return decoded;
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(rotation);
        Bitmap rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.getWidth(), decoded.getHeight(),
                matrix, true);
        if (rotated != decoded) {
            decoded.recycle();
        }
        return rotated;
    }

    private static File localFile(Uri uri) {
        Uri withoutFragment = uri.getFragment() == null ? uri : uri.buildUpon().fragment(null).build();
        if (!"file".equalsIgnoreCase(withoutFragment.getScheme())) {
            return null;
        }
        String path = withoutFragment.getPath();
        if (path == null) {
            return null;
        }
        File file = new File(path);
        return file.isFile() ? file : null;
    }

    @NonNull
    private static InputStream openRaw(@NonNull Context context, @NonNull Uri uri) throws IOException {
        Uri withoutFragment = uri.getFragment() == null ? uri : uri.buildUpon().fragment(null).build();
        File file = localFile(withoutFragment);
        if (file != null) {
            return new FileInputStream(file);
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

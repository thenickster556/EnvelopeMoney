package com.example.envelopemoney.receipt;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.exifinterface.media.ExifInterface;

import java.io.IOException;
import java.io.InputStream;

/**
 * Decodes JPEG bitmaps applying EXIF orientation so saved pixels are upright.
 */
public final class ReceiptExifBitmapLoader {

    private ReceiptExifBitmapLoader() {
    }

    @Nullable
    public static Bitmap decodeUpright(Context context, Uri uri) throws IOException {
        if (context == null || uri == null) {
            return null;
        }
        Bitmap bitmap;
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) {
                return null;
            }
            bitmap = BitmapFactory.decodeStream(is);
        } catch (SecurityException e) {
            throw new IOException("uri permission denied", e);
        }
        if (bitmap == null) {
            return null;
        }
        int rotation = readExifRotationDegrees(context, uri);
        if (rotation == 0) {
            return bitmap;
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(rotation);
        Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (rotated != bitmap) {
            bitmap.recycle();
        }
        return rotated;
    }

    @Nullable
    public static Bitmap decodeUprightFromFile(String path) throws IOException {
        if (path == null) {
            return null;
        }
        Bitmap bitmap = BitmapFactory.decodeFile(path);
        if (bitmap == null) {
            return null;
        }
        int rotation = 0;
        try {
            ExifInterface exif = new ExifInterface(path);
            rotation = exifToDegrees(exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL));
        } catch (IOException ignored) {
        }
        if (rotation == 0) {
            return bitmap;
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(rotation);
        Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (rotated != bitmap) {
            bitmap.recycle();
        }
        return rotated;
    }

    static int readExifRotationDegrees(Context context, Uri uri) throws IOException {
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) {
                return 0;
            }
            ExifInterface exif = new ExifInterface(is);
            return exifToDegrees(exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL));
        } catch (SecurityException e) {
            throw new IOException("uri permission denied", e);
        }
    }

    static int exifToDegrees(int orientation) {
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                return 90;
            case ExifInterface.ORIENTATION_ROTATE_180:
                return 180;
            case ExifInterface.ORIENTATION_ROTATE_270:
                return 270;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                return 90;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                return 270;
            default:
                return 0;
        }
    }
}

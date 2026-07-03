package com.example.envelopemoney.receipt;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Writes receipt JPEGs to the device gallery under {@code Pictures/Mountain Money}.
 */
public final class MediaStoreReceiptSaver {

    public static final String ALBUM_RELATIVE = Environment.DIRECTORY_PICTURES + "/Mountain Money";

    private MediaStoreReceiptSaver() {
    }

    @NonNull
    public static Uri saveJpeg(Context context, Bitmap bitmap) throws IOException {
        if (bitmap == null) {
            throw new IllegalArgumentException("bitmap null");
        }
        String name = "MountainMoney_" + System.currentTimeMillis() + ".jpg";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, ALBUM_RELATIVE);
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri uri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                throw new IOException("MediaStore insert failed");
            }
            try (OutputStream out = context.getContentResolver().openOutputStream(uri)) {
                if (out == null) {
                    throw new IOException("openOutputStream null");
                }
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)) {
                    throw new IOException("compress failed");
                }
            }
            values.clear();
            values.put(MediaStore.MediaColumns.IS_PENDING, 0);
            context.getContentResolver().update(uri, values, null, null);
            return uri;
        }
        File pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File album = new File(pictures, "Mountain Money");
        if (!album.exists() && !album.mkdirs()) {
            throw new IOException("mkdir Mountain Money failed");
        }
        File file = new File(album, name);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 92, fos)) {
                throw new IOException("compress failed");
            }
        }
        android.media.MediaScannerConnection.scanFile(
                context,
                new String[]{file.getAbsolutePath()},
                new String[]{"image/jpeg"},
                null);
        return Uri.fromFile(file);
    }

    /**
     * Copies JPEG bytes from {@code input} into the Mountain Money album without re-encoding.
     */
    @NonNull
    public static Uri saveJpegStream(Context context, InputStream input) throws IOException {
        if (input == null) {
            throw new IllegalArgumentException("input null");
        }
        String name = "MountainMoney_" + System.currentTimeMillis() + ".jpg";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, ALBUM_RELATIVE);
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri uri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                throw new IOException("MediaStore insert failed");
            }
            try (OutputStream out = context.getContentResolver().openOutputStream(uri)) {
                if (out == null) {
                    throw new IOException("openOutputStream null");
                }
                copyStream(input, out);
            }
            values.clear();
            values.put(MediaStore.MediaColumns.IS_PENDING, 0);
            context.getContentResolver().update(uri, values, null, null);
            return uri;
        }
        File pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File album = new File(pictures, "Mountain Money");
        if (!album.exists() && !album.mkdirs()) {
            throw new IOException("mkdir Mountain Money failed");
        }
        File file = new File(album, name);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            copyStream(input, fos);
        }
        android.media.MediaScannerConnection.scanFile(
                context,
                new String[]{file.getAbsolutePath()},
                new String[]{"image/jpeg"},
                null);
        return Uri.fromFile(file);
    }

    private static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }
}

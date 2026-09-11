package com.example.envelopemoney.receipt;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Read-only Android adapter for exact-folder receipt discovery and sampled image verification. */
public final class AndroidReceiptSource implements ReceiptReferenceResolver.Source {
    private final Context context;
    public AndroidReceiptSource(Context context) { this.context = context.getApplicationContext(); }

    /** Android may require library access for files created before reinstall or by another app. */
    public static String readPermission() {
        if (Build.VERSION.SDK_INT >= 33) return Manifest.permission.READ_MEDIA_IMAGES;
        if (Build.VERSION.SDK_INT >= 23) return Manifest.permission.READ_EXTERNAL_STORAGE;
        return null;
    }

    public boolean needsReadPermission() {
        String permission = readPermission();
        return permission != null && ContextCompat.checkSelfPermission(context, permission)
                != PackageManager.PERMISSION_GRANTED;
    }

    @Override public boolean albumAccessRestricted() { return needsReadPermission(); }

    /** Startup repair must ask for library access whenever stored receipts exist and it is missing. */
    public boolean shouldRequestLibraryAccess(boolean hasStoredReceipts) {
        return hasStoredReceipts && needsReadPermission();
    }

    /** Convert only documented media-image document IDs; arbitrary picker IDs are not MediaStore IDs. */
    public static Uri providerUri(String reference) {
        Uri parsed = Uri.parse(reference);
        if (parsed.getScheme() == null || reference.matches("^[A-Za-z]:\\\\.*")) {
            parsed = Uri.parse(new File(reference).toURI().toString());
        }
        if ("com.android.providers.media.documents".equals(parsed.getAuthority())) {
            String identifier = parsed.getLastPathSegment();
            if (identifier != null && identifier.matches("image:[0-9]+")) {
                try {
                    return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            Long.parseLong(identifier.substring("image:".length())));
                } catch (NumberFormatException invalidIdentifier) {
                    // Preserve an unsupported identifier for the normal unavailable-reference path.
                }
            }
        }
        return parsed.buildUpon().fragment(null).build();
    }

    @Override public ReceiptReferenceResolver.Result inspect(String reference) {
        Uri uri = providerUri(reference);
        String name = queryFileName(uri);
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
                if (stream == null) return failure(unavailableStatus(), name);
                BitmapFactory.decodeStream(stream, null, options);
            }
            if (options.outWidth <= 0 || options.outHeight <= 0) return failure(ReceiptReferenceResolver.Status.CORRUPT, name);
            options.inJustDecodeBounds = false;
            options.inSampleSize = 1;
            while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 256) options.inSampleSize *= 2;
            try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
                Bitmap image = BitmapFactory.decodeStream(stream, null, options);
                if (image == null) return failure(ReceiptReferenceResolver.Status.CORRUPT, name);
                image.recycle();
            }
            return ReceiptReferenceResolver.Result.resolved(uri.toString(), name);
        } catch (SecurityException denied) {
            return failure(ReceiptReferenceResolver.Status.PERMISSION_REQUIRED, name);
        } catch (IOException | RuntimeException unavailable) {
            return failure(unavailableStatus(), name);
        }
    }

    /** Metadata can still be readable when the image stream or grant has stopped working. */
    private String queryFileName(Uri uri) {
        if (!"content".equals(uri.getScheme())) {
            return ReceiptReferenceResolver.fileNameFromReference(uri.toString());
        }
        try (Cursor cursor = context.getContentResolver().query(uri,
                new String[]{MediaStore.MediaColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        } catch (RuntimeException unavailableMetadata) {
            // A missing metadata row must not prevent checking the original stream or filename hint.
        }
        return null;
    }

    /**
     * Builds one exact-folder inventory. Accessible disk files supplement missing MediaStore rows;
     * the disk and indexed representations of one filename are not counted as two photographs.
     * Duplicate indexed names remain ambiguous. All candidates must pass inspect before persistence.
     */
    @Override public List<ReceiptReferenceResolver.Result> readAlbum() {
        List<ReceiptReferenceResolver.Result> pictures = new ArrayList<>();
        Set<String> indexedNames = new HashSet<>();
        boolean accessDenied = false;
        boolean scopedStorage = Build.VERSION.SDK_INT >= 29;
        String locationColumn = scopedStorage ? MediaStore.MediaColumns.RELATIVE_PATH : MediaStore.MediaColumns.DATA;
        File folder = albumDirectory();
        String albumQuery = scopedStorage ? MediaStoreReceiptSaver.ALBUM_RELATIVE + "%" : folder.getAbsolutePath() + File.separator + "%";
        try (Cursor cursor = context.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Images.Media._ID, MediaStore.MediaColumns.DISPLAY_NAME, locationColumn},
                locationColumn + " LIKE ?",
                new String[]{albumQuery}, null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String name = cursor.getString(1);
                    String location = cursor.getString(2);
                    if (name == null || location == null) continue;
                    // LIKE also returns nested directories; persist only the exact receipt folder.
                    if (!isExactAlbumLocation(location, scopedStorage, folder)) continue;
                    Uri uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(0));
                    pictures.add(ReceiptReferenceResolver.Result.resolved(uri.toString(), name));
                    indexedNames.add(name);
                }
            }
        } catch (SecurityException denied) {
            accessDenied = true;
        } catch (RuntimeException unavailableIndex) {
            // OEM provider failures do not prevent recovery from accessible files in the known folder.
        }
        try {
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && !indexedNames.contains(file.getName())) {
                        pictures.add(ReceiptReferenceResolver.Result.resolved(file.toURI().toString(), file.getName()));
                    }
                }
            }
        } catch (SecurityException denied) {
            accessDenied = true;
        }
        if (pictures.isEmpty() && (accessDenied || needsReadPermission())) {
            throw new SecurityException("Photo access required");
        }
        return pictures;
    }

    private static File albumDirectory() {
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Mountain Money");
    }

    /** MediaStore may store {@code Pictures/Mountain Money} with or without a trailing slash. */
    static boolean isExactAlbumLocation(String location, boolean scopedStorage, File folder) {
        if (location == null) return false;
        if (!scopedStorage) {
            return folder.equals(new File(location).getParentFile());
        }
        String normalized = location.replace('\\', '/').trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return MediaStoreReceiptSaver.ALBUM_RELATIVE.replace('\\', '/').equals(normalized);
    }

    public static ReceiptReferenceResolver.Result resolve(Context context, String reference, String fileName) {
        return new ReceiptReferenceResolver(new AndroidReceiptSource(context)).resolve(reference, fileName);
    }

    private ReceiptReferenceResolver.Status unavailableStatus() {
        return needsReadPermission() ? ReceiptReferenceResolver.Status.PERMISSION_REQUIRED
                : ReceiptReferenceResolver.Status.MISSING;
    }

    private static ReceiptReferenceResolver.Result failure(ReceiptReferenceResolver.Status status, String name) {
        return ReceiptReferenceResolver.Result.failure(status, name);
    }
}
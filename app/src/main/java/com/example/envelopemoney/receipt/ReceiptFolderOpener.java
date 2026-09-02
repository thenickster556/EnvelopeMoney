package com.example.envelopemoney.receipt;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * Locates receipt JPEGs in {@code Pictures/Mountain Money} by {@code MountainMoney_*.jpg} filename
 * so preview does not depend on a stale picker or MediaStore id.
 */
public final class ReceiptFolderOpener {

    static final String DISPLAY_NAME_PREFIX = "MountainMoney_";

    private ReceiptFolderOpener() {
    }

    @NonNull
    public static File albumDirectory() {
        File pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        if (pictures == null) {
            pictures = new File("/storage/emulated/0/Pictures");
        }
        return new File(pictures, "Mountain Money");
    }

    @NonNull
    public static File albumFile(@NonNull String displayName) {
        return new File(albumDirectory(), displayName);
    }

    @NonNull
    public static Uri folderFileUri(@NonNull String displayName) {
        return Uri.fromFile(albumFile(displayName));
    }

    public static boolean isAlbumDisplayName(@Nullable String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.US);
        return name.startsWith(DISPLAY_NAME_PREFIX) && lower.endsWith(".jpg");
    }

    @Nullable
    public static String albumDisplayName(@Nullable Uri uri) {
        if (uri == null) {
            return null;
        }
        String last = uri.getLastPathSegment();
        String decoded = decodeSegment(last);
        if (isAlbumDisplayName(decoded)) {
            return decoded;
        }
        String path = uri.getPath();
        if (path != null) {
            int slash = path.lastIndexOf('/');
            String fromPath = slash >= 0 ? path.substring(slash + 1) : path;
            fromPath = decodeSegment(fromPath);
            if (isAlbumDisplayName(fromPath)) {
                return fromPath;
            }
        }
        return null;
    }

    public static boolean isEphemeralPickerUri(@Nullable Uri uri) {
        if (uri == null) {
            return false;
        }
        String raw = uri.toString().toLowerCase(Locale.US);
        if (raw.contains("picker") || raw.contains("photopicker")) {
            return true;
        }
        String authority = uri.getAuthority();
        if (authority == null) {
            return false;
        }
        String auth = authority.toLowerCase(Locale.US);
        return auth.contains("documents");
    }

    /**
     * Persist the album folder file URI. Never keep a picker grant when an album URI exists.
     */
    @Nullable
    static Uri persistUriAfterImport(@Nullable Uri saved, @Nullable Uri originalUri) {
        if (saved != null && !isEphemeralPickerUri(saved)) {
            return saved;
        }
        if (originalUri != null && !isEphemeralPickerUri(originalUri)) {
            return originalUri;
        }
        return saved != null ? saved : originalUri;
    }

    @NonNull
    public static InputStream open(@NonNull Context context, @NonNull Uri stored) throws IOException {
        String name = albumDisplayName(stored);
        if (name != null) {
            InputStream fromName = openByDisplayName(context, name);
            if (fromName != null) {
                return fromName;
            }
        }
        InputStream fromId = openByMediaStoreIdIfAlbum(context, stored);
        if (fromId != null) {
            return fromId;
        }
        try {
            InputStream fromStored = context.getContentResolver().openInputStream(stored);
            if (fromStored != null) {
                return fromStored;
            }
        } catch (SecurityException e) {
            InputStream fileFallback = openFileScheme(stored);
            if (fileFallback != null) {
                return fileFallback;
            }
            throw new IOException("uri permission denied", e);
        } catch (FileNotFoundException e) {
            InputStream fileFallback = openFileScheme(stored);
            if (fileFallback != null) {
                return fileFallback;
            }
            throw e;
        }
        InputStream fileFallback = openFileScheme(stored);
        if (fileFallback != null) {
            return fileFallback;
        }
        throw new IOException("openInputStream null");
    }

    @NonNull
    public static Uri resolveForWrite(@NonNull Context context, @NonNull Uri stored) {
        String name = albumDisplayName(stored);
        if (name != null) {
            Uri media = queryByDisplayName(context, name);
            if (media != null) {
                return media;
            }
            File file = albumFile(name);
            if (file.isFile()) {
                return Uri.fromFile(file);
            }
        }
        return stored;
    }

    @Nullable
    static Uri queryByDisplayName(@Nullable Context context, @Nullable String displayName) {
        if (context == null || !isAlbumDisplayName(displayName)) {
            return null;
        }
        Uri found = queryImages(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, displayName);
        if (found != null) {
            return found;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return queryImages(context,
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                    displayName);
        }
        return null;
    }

    @Nullable
    private static InputStream openByDisplayName(Context context, String name) {
        InputStream fromFile = openFile(albumFile(name));
        if (fromFile != null) {
            return fromFile;
        }
        File[] listed = albumDirectory().listFiles();
        if (listed != null) {
            for (File file : listed) {
                if (name.equals(file.getName())) {
                    InputStream in = openFile(file);
                    if (in != null) {
                        return in;
                    }
                }
            }
        }
        Uri media = queryByDisplayName(context, name);
        if (media != null) {
            try {
                return context.getContentResolver().openInputStream(media);
            } catch (SecurityException | FileNotFoundException ignored) {
                return null;
            }
        }
        return null;
    }

    @Nullable
    private static InputStream openByMediaStoreIdIfAlbum(Context context, Uri stored) {
        if (stored == null || !"content".equalsIgnoreCase(stored.getScheme())) {
            return null;
        }
        String last = stored.getLastPathSegment();
        if (last == null || !last.matches("\\d+")) {
            return null;
        }
        String displayName = queryColumn(context, stored, MediaStore.MediaColumns.DISPLAY_NAME);
        String relative = queryColumn(context, stored,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                        ? MediaStore.MediaColumns.RELATIVE_PATH
                        : MediaStore.MediaColumns.DATA);
        if (!ReceiptPickerUriNormalizer.matchesAppOwnedMediaMetadata(displayName, relative)) {
            return null;
        }
        if (isAlbumDisplayName(displayName)) {
            return openByDisplayName(context, displayName);
        }
        try {
            return context.getContentResolver().openInputStream(stored);
        } catch (SecurityException | FileNotFoundException ignored) {
            return null;
        }
    }

    @Nullable
    private static Uri queryImages(Context context, Uri collection, String displayName) {
        String[] projection = new String[]{MediaStore.Images.Media._ID};
        try (Cursor cursor = context.getContentResolver().query(
                collection,
                projection,
                MediaStore.MediaColumns.DISPLAY_NAME + "=?",
                new String[]{displayName},
                MediaStore.Images.Media._ID + " DESC")) {
            if (cursor != null && cursor.moveToFirst()) {
                long id = cursor.getLong(0);
                return ContentUris.withAppendedId(collection, id);
            }
        } catch (RuntimeException ignored) {
            return null;
        }
        return null;
    }

    @Nullable
    private static String queryColumn(Context context, Uri uri, String column) {
        try (Cursor cursor = context.getContentResolver().query(
                uri, new String[]{column}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(column);
                if (index >= 0 && !cursor.isNull(index)) {
                    return cursor.getString(index);
                }
            }
        } catch (RuntimeException ignored) {
            return null;
        }
        return null;
    }

    @Nullable
    private static InputStream openFileScheme(@Nullable Uri uri) {
        if (uri == null || !"file".equalsIgnoreCase(uri.getScheme())) {
            return null;
        }
        String path = uri.getPath();
        if (path == null || path.isEmpty()) {
            return null;
        }
        return openFile(new File(path));
    }

    @Nullable
    private static InputStream openFile(@Nullable File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        try {
            return new FileInputStream(file);
        } catch (FileNotFoundException | SecurityException e) {
            return null;
        }
    }

    @Nullable
    private static String decodeSegment(@Nullable String segment) {
        if (segment == null || segment.isEmpty()) {
            return segment;
        }
        try {
            String decoded = Uri.decode(segment);
            return decoded != null ? decoded : segment;
        } catch (RuntimeException e) {
            return segment;
        }
    }
}

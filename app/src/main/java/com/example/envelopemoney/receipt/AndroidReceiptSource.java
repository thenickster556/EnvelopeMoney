package com.example.envelopemoney.receipt;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.exifinterface.media.ExifInterface;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Read-only Android adapter for exact-folder receipt discovery and sampled image verification. */
public final class AndroidReceiptSource implements ReceiptReferenceResolver.Source {
    private static final String TAG = "EnvelopeMoney";
    /** compileSdk 33 has no constant for the Android 14 partial photo-access permission. */
    private static final String READ_MEDIA_VISUAL_USER_SELECTED = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED";

    private final Context context;
    public AndroidReceiptSource(Context context) { this.context = context.getApplicationContext(); }

    /** Android may require library access for files created before reinstall or by another app. */
    public static String readPermission() {
        if (Build.VERSION.SDK_INT >= 33) return Manifest.permission.READ_MEDIA_IMAGES;
        if (Build.VERSION.SDK_INT >= 23) return Manifest.permission.READ_EXTERNAL_STORAGE;
        return null;
    }

    private boolean fullLibraryAccessGranted() {
        String permission = readPermission();
        return permission != null && ContextCompat.checkSelfPermission(context, permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Android 14 "Select photos" access lists chosen photos without granting the whole library.
     * The permission exists only on Android 14+, so older platforms simply report it denied.
     */
    private boolean partialVisualAccessGranted() {
        return ContextCompat.checkSelfPermission(context, READ_MEDIA_VISUAL_USER_SELECTED)
                == PackageManager.PERMISSION_GRANTED;
    }

    public boolean needsReadPermission() {
        if (readPermission() == null) return false;
        if (fullLibraryAccessGranted()) return false;
        return !partialVisualAccessGranted();
    }

    /**
     * A user-selected partial list can omit folder files, so date matching stays off; identity
     * (filename) matches remain valid because listed photos are readable.
     */
    @Override public boolean albumAccessRestricted() {
        return readPermission() != null && !fullLibraryAccessGranted();
    }

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
            // The header decode proves the stream is a readable image; a second sampled pixel
            // decode here doubled every search pass with no extra corruption signal. The
            // fullscreen preview performs the real decode and already handles decode failures.
            if (options.outWidth <= 0 || options.outHeight <= 0) return failure(ReceiptReferenceResolver.Status.CORRUPT, name);
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
        long startedAt = System.currentTimeMillis();
        List<ReceiptReferenceResolver.Result> pictures = new ArrayList<>();
        Set<String> indexedNames = new HashSet<>();
        boolean accessDenied = false;
        int rowsSeen = 0;
        int[] exifProbes = new int[1];
        try {
            rowsSeen += queryAlbumRows(pictures, indexedNames, exifProbes);
            if (rowsSeen == 0 && !needsReadPermission()) {
                // Files copied over USB or a PC can sit in the folder before MediaStore indexes them.
                Log.i(TAG, "receipt album empty in MediaStore; requesting rescan of the Mountain Money folder");
                rescanAlbumFolder();
                rowsSeen += queryAlbumRows(pictures, indexedNames, exifProbes);
            }
        } catch (SecurityException denied) {
            accessDenied = true;
        }
        int diskSupplement = 0;
        try {
            File[] files = albumDirectory().listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && !indexedNames.contains(file.getName())) {
                        pictures.add(ReceiptReferenceResolver.Result.resolved(
                                file.toURI().toString(), file.getName(), diskCaptureTimeMs(file)));
                        diskSupplement++;
                    }
                }
            }
        } catch (SecurityException denied) {
            accessDenied = true;
        }
        Log.i(TAG, "receipt album: " + pictures.size() + " pictures (mediastore=" + rowsSeen
                + ", disk=" + diskSupplement + ", exif=" + exifProbes[0]
                + ", restricted=" + albumAccessRestricted()
                + ", " + (System.currentTimeMillis() - startedAt) + "ms)");
        if (pictures.isEmpty() && (accessDenied || needsReadPermission())) {
            throw new SecurityException("Photo access required");
        }
        return pictures;
    }

    /** One MediaStore pass over the exact album folder; rows outside it are skipped. */
    private int queryAlbumRows(List<ReceiptReferenceResolver.Result> pictures, Set<String> indexedNames,
                               int[] exifProbes) {
        boolean scopedStorage = Build.VERSION.SDK_INT >= 29;
        String locationColumn = scopedStorage ? MediaStore.MediaColumns.RELATIVE_PATH : MediaStore.MediaColumns.DATA;
        File folder = albumDirectory();
        String albumQuery = scopedStorage ? MediaStoreReceiptSaver.ALBUM_RELATIVE + "%" : folder.getAbsolutePath() + File.separator + "%";
        int kept = 0;
        try (Cursor cursor = context.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Images.Media._ID, MediaStore.MediaColumns.DISPLAY_NAME, locationColumn,
                        MediaStore.Images.Media.DATE_TAKEN, MediaStore.Images.Media.DATE_ADDED},
                locationColumn + " LIKE ?",
                new String[]{albumQuery}, null)) {
            if (cursor == null) return 0;
            int idCol = cursor.getColumnIndex(MediaStore.Images.Media._ID);
            int nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
            int locationCol = cursor.getColumnIndex(locationColumn);
            int takenCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN);
            int addedCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED);
            while (cursor.moveToNext()) {
                String name = nameCol >= 0 ? cursor.getString(nameCol) : null;
                String location = locationCol >= 0 ? cursor.getString(locationCol) : null;
                if (name == null || location == null || idCol < 0) continue;
                // LIKE also returns nested directories; persist only the exact receipt folder.
                if (!isExactAlbumLocation(location, scopedStorage, folder)) continue;
                Uri uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        cursor.getLong(idCol));
                long taken = takenCol >= 0 && !cursor.isNull(takenCol) ? cursor.getLong(takenCol) : 0L;
                long added = addedCol >= 0 && !cursor.isNull(addedCol) ? cursor.getLong(addedCol) : 0L;
                long exifOriginalMs = 0L;
                // Copied/renamed JPEGs often have a wrong DATE_TAKEN (import day). Probe EXIF
                // whenever the name has no epoch so the true capture day stays in the ±1 window.
                if (ReceiptAlbumMatcher.captureTimeMs(name) == null) {
                    Long probed = exifCaptureTimeMs(context, uri);
                    if (probed != null && probed > 0) {
                        exifOriginalMs = probed;
                        exifProbes[0]++;
                    }
                }
                pictures.add(ReceiptReferenceResolver.Result.resolved(uri.toString(), name,
                        captureTimeMs(name, taken, exifOriginalMs, added)));
                indexedNames.add(name);
                kept++;
            }
        } catch (SecurityException denied) {
            // Propagated to readAlbum so an empty denied album reports PERMISSION_REQUIRED.
            throw denied;
        } catch (RuntimeException unavailableIndex) {
            // OEM provider failures do not prevent recovery from accessible files in the known folder.
            Log.w(TAG, "receipt album MediaStore query unavailable", unavailableIndex);
        }
        return kept;
    }

    private void rescanAlbumFolder() {
        try {
            MediaScannerConnection.scanFile(context,
                    new String[]{albumDirectory().getAbsolutePath()}, null, null);
        } catch (RuntimeException scanUnavailable) {
            Log.w(TAG, "receipt album rescan unavailable", scanUnavailable);
        }
    }

    private static File albumDirectory() {
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Mountain Money");
    }

    /** MediaStore may store {@code Pictures/Mountain Money} in any casing, with or without a trailing slash. */
    static boolean isExactAlbumLocation(String location, boolean scopedStorage, File folder) {
        if (location == null) return false;
        if (!scopedStorage) {
            return folder.equals(new File(location).getParentFile());
        }
        String normalized = location.replace('\\', '/').trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        String album = MediaStoreReceiptSaver.ALBUM_RELATIVE.replace('\\', '/');
        return album.toLowerCase(Locale.ROOT).equals(normalized.toLowerCase(Locale.ROOT));
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

    /**
     * Filename epoch first, then the JPEG's EXIF {@code DateTimeOriginal} (the real capture instant
     * for copied files), then MediaStore {@code DATE_TAKEN}, then {@code DATE_ADDED} (seconds unless
     * the value is already milliseconds) — the scan/copy day as a last resort.
     */
    static long captureTimeMs(String fileName, long dateTakenMs, long exifOriginalMs, long dateAddedRaw) {
        Long fromName = ReceiptAlbumMatcher.captureTimeMs(fileName);
        if (fromName != null && fromName > 0) return fromName;
        if (exifOriginalMs > 0) return exifOriginalMs;
        if (dateTakenMs > 0) return dateTakenMs;
        if (dateAddedRaw > 1_000_000_000_000L) return dateAddedRaw;
        if (dateAddedRaw > 0) return dateAddedRaw * 1000L;
        return 0L;
    }

    /**
     * Header-only EXIF probe: {@code DateTimeOriginal} parsed in the default timezone. Copied and
     * renamed JPEGs carry their true capture instant there while MediaStore only knows the scan
     * day; screenshots and stripped exports return null and keep falling back to DATE_ADDED.
     */
    @Nullable
    static Long exifCaptureTimeMs(Context context, Uri uri) {
        if (context == null || uri == null) return null;
        try (InputStream stream = ReceiptBitmapLoader.openInputStream(context, uri)) {
            if (stream == null) return null;
            ExifInterface exif = new ExifInterface(stream);
            String original = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL);
            if (original == null) return null;
            SimpleDateFormat format = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US);
            format.setLenient(false);
            Date parsed = format.parse(original.trim());
            return parsed != null ? parsed.getTime() : null;
        } catch (IOException | ParseException | RuntimeException unavailable) {
            return null;
        }
    }

    static long captureTimeMs(File file) {
        if (file == null) return 0L;
        Long fromName = ReceiptAlbumMatcher.captureTimeMs(file.getName());
        if (fromName != null && fromName > 0) return fromName;
        return file.lastModified();
    }

    /** Disk-supplement capture time: name epoch, then an EXIF probe, then last-modified. */
    long diskCaptureTimeMs(File file) {
        Long fromName = ReceiptAlbumMatcher.captureTimeMs(file.getName());
        if (fromName != null && fromName > 0) return fromName;
        Long fromExif = exifCaptureTimeMs(context, Uri.fromFile(file));
        if (fromExif != null && fromExif > 0) return fromExif;
        return file.lastModified();
    }
}
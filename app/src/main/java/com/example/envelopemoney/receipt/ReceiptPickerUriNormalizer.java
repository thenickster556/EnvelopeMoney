package com.example.envelopemoney.receipt;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * External and photo-picker URIs are imported into {@link MediaStoreReceiptSaver}'s Mountain Money
 * album so preview, OCR, and persisted {@code receiptImageUri} use a stable app-owned URI (same as camera).
 * When the platform allows, the original picker file is deleted after import (move semantics).
 * If delete is denied, the new copy is removed and the original URI is kept (no duplicate).
 *
 * <p>App-owned detection cannot rely on MediaStore ID strings alone (they do not contain
 * "Mountain Money"). Prefer {@link #isAppOwnedReceiptUri(Context, Uri)} which also checks
 * {@code DISPLAY_NAME} / {@code RELATIVE_PATH}.
 */
public final class ReceiptPickerUriNormalizer {

    private static final String MOUNTAIN_MONEY_ALBUM_MARKER = "mountain money";
    /** Prefix used by {@link MediaStoreReceiptSaver} for saved JPEG display names. */
    static final String APP_OWNED_DISPLAY_NAME_PREFIX = "MountainMoney_";

    public static final class ImportResult {
        @NonNull
        public final Uri uri;
        public final boolean sourceDeleted;

        ImportResult(@NonNull Uri uri, boolean sourceDeleted) {
            this.uri = uri;
            this.sourceDeleted = sourceDeleted;
        }
    }

    private ReceiptPickerUriNormalizer() {
    }

    /**
     * True when the URI string itself encodes the Mountain Money album path
     * (typical for {@code file://} pre-Q paths). Insufficient for bare MediaStore IDs.
     */
    public static boolean isAppOwnedReceiptUri(@Nullable String uriString) {
        if (uriString == null || uriString.trim().isEmpty()) {
            return false;
        }
        String lower = uriString.toLowerCase(Locale.US);
        // Path may be plain ("Mountain Money") or URI-encoded ("Mountain%20Money").
        return lower.contains(MOUNTAIN_MONEY_ALBUM_MARKER)
                || lower.contains("mountain%20money");
    }

    /**
     * True when MediaStore metadata identifies a Mountain Money receipt JPEG.
     *
     * @param displayName  {@link MediaStore.MediaColumns#DISPLAY_NAME}, may be null
     * @param relativePath {@link MediaStore.MediaColumns#RELATIVE_PATH}, may be null
     */
    public static boolean matchesAppOwnedMediaMetadata(@Nullable String displayName,
                                                       @Nullable String relativePath) {
        if (displayName != null && displayName.startsWith(APP_OWNED_DISPLAY_NAME_PREFIX)) {
            return true;
        }
        if (relativePath != null && !relativePath.trim().isEmpty()) {
            return relativePath.toLowerCase(Locale.US).contains(MOUNTAIN_MONEY_ALBUM_MARKER);
        }
        return false;
    }

    /**
     * True when the URI already points at a JPEG under Pictures/Mountain Money.
     * Checks path markers, then MediaStore {@code DISPLAY_NAME} / {@code RELATIVE_PATH}.
     */
    public static boolean isAppOwnedReceiptUri(@Nullable Context context, @Nullable Uri uri) {
        if (uri == null) {
            return false;
        }
        if (isAppOwnedReceiptUri(uri.toString())) {
            return true;
        }
        if (context == null) {
            return false;
        }
        return matchesAppOwnedMediaMetadata(
                queryMediaColumn(context, uri, MediaStore.MediaColumns.DISPLAY_NAME),
                queryRelativePath(context, uri));
    }

    /**
     * True when a gallery/picker URI should be decoded and imported into the app album before use.
     * String-only: path markers. Prefer {@link #shouldImportToAppGallery(Context, Uri)} for MediaStore IDs.
     */
    public static boolean shouldImportToAppGallery(@Nullable String uriString) {
        return !isAppOwnedReceiptUri(uriString);
    }

    /** True when the URI is not already an app-owned Mountain Money receipt. */
    public static boolean shouldImportToAppGallery(@Nullable Context context, @Nullable Uri uri) {
        return !isAppOwnedReceiptUri(context, uri);
    }

    /** @deprecated use {@link #shouldImportToAppGallery(String)} */
    @Deprecated
    public static boolean shouldCopyToAppGallery(@Nullable String uriString) {
        return shouldImportToAppGallery(uriString);
    }

    static boolean canStreamCopyBytesInPlace(byte[] data) throws IOException {
        if (data == null || data.length < 2) {
            return false;
        }
        if ((data[0] & 0xFF) != 0xFF || (data[1] & 0xFF) != 0xD8) {
            return false;
        }
        return ReceiptExifBitmapLoader.readExifRotationDegreesFromBytes(data) == 0;
    }

    /**
     * True when JPEG bytes can be streamed into the app album without a decode/rotate pass.
     */
    static boolean canStreamCopyInPlace(Context context, Uri uri) throws IOException {
        if (context == null || uri == null) {
            return false;
        }
        String mime = context.getContentResolver().getType(uri);
        if (mime != null) {
            mime = mime.toLowerCase(Locale.US);
            if (!mime.equals("image/jpeg") && !mime.equals("image/jpg")) {
                return false;
            }
        }
        return ReceiptExifBitmapLoader.readExifRotationDegrees(context, uri) == 0;
    }

    /**
     * Try to delete the picker source after saving to the app album. If delete fails, remove the
     * new copy and keep the original URI so only one file remains.
     * Never treats an app-owned Mountain Money URI as a deletable picker source.
     */
    @NonNull
    static ImportResult finishImportMoveOrRollback(Context context, Uri saved, Uri originalUri) {
        if (originalUri == null) {
            return new ImportResult(saved, false);
        }
        if (isAppOwnedReceiptUri(context, originalUri)) {
            // Original is already app-owned; keep saved if different, else keep original.
            if (saved != null && !originalUri.equals(saved)) {
                return new ImportResult(saved, false);
            }
            return new ImportResult(originalUri, false);
        }
        if (ReceiptSourceDeleter.tryDeleteSource(context, originalUri)) {
            return new ImportResult(saved, true);
        }
        ReceiptSourceDeleter.tryDeleteSource(context, saved);
        return new ImportResult(originalUri, false);
    }

    /**
     * Import bytes already read from a picker URI (safe to call off the main thread).
     */
    @NonNull
    public static ImportResult normalizeImportFromBytes(Context context, byte[] data, Uri originalUri)
            throws IOException {
        if (data == null || data.length == 0) {
            throw new IOException("empty image bytes");
        }
        if (originalUri != null && !shouldImportToAppGallery(context, originalUri)) {
            return new ImportResult(originalUri, false);
        }
        if (canStreamCopyBytesInPlace(data)) {
            try (InputStream in = new ByteArrayInputStream(data)) {
                Uri saved = MediaStoreReceiptSaver.saveJpegStream(context, in);
                return finishImportMoveOrRollback(context, saved, originalUri);
            }
        }
        Bitmap bmp = ReceiptExifBitmapLoader.decodeUprightFromBytes(data);
        if (bmp == null) {
            throw new IOException("decode bitmap failed");
        }
        try {
            Uri saved = MediaStoreReceiptSaver.saveJpeg(context, bmp);
            return finishImportMoveOrRollback(context, saved, originalUri);
        } finally {
            bmp.recycle();
        }
    }

    @NonNull
    public static ImportResult normalizeImport(Context context, Uri uri) throws IOException {
        if (uri == null) {
            throw new IOException("uri null");
        }
        if (!shouldImportToAppGallery(context, uri)) {
            return new ImportResult(uri, false);
        }
        Uri original = uri;
        try {
            if (canStreamCopyInPlace(context, uri)) {
                try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                    if (in == null) {
                        throw new IOException("openInputStream null");
                    }
                    Uri saved = MediaStoreReceiptSaver.saveJpegStream(context, in);
                    return finishImportMoveOrRollback(context, saved, original);
                }
            }
            Bitmap bmp = ReceiptExifBitmapLoader.decodeUpright(context, uri);
            if (bmp == null) {
                throw new IOException("decode bitmap failed");
            }
            try {
                Uri saved = MediaStoreReceiptSaver.saveJpeg(context, bmp);
                return finishImportMoveOrRollback(context, saved, original);
            } finally {
                bmp.recycle();
            }
        } catch (SecurityException e) {
            throw new IOException("picker uri permission denied", e);
        }
    }

    /**
     * Resolves to an app-owned URI when import is required. Prefer not calling this from preview;
     * open the stored URI directly instead.
     */
    public static Uri normalize(Context context, Uri uri) throws IOException {
        return normalizeImport(context, uri).uri;
    }

    @Nullable
    private static String queryRelativePath(Context context, Uri uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return queryMediaColumn(context, uri, MediaStore.MediaColumns.RELATIVE_PATH);
        }
        return queryMediaColumn(context, uri, MediaStore.MediaColumns.DATA);
    }

    @Nullable
    private static String queryMediaColumn(Context context, Uri uri, String column) {
        if (context == null || uri == null || column == null) {
            return null;
        }
        try (Cursor cursor = context.getContentResolver().query(
                uri, new String[]{column}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(column);
                if (index >= 0 && !cursor.isNull(index)) {
                    return cursor.getString(index);
                }
            }
        } catch (SecurityException ignored) {
            return null;
        } catch (RuntimeException ignored) {
            return null;
        }
        return null;
    }
}

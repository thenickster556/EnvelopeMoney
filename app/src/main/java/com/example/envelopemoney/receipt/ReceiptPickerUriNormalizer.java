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
 * If delete of the picker source is denied, the album copy is still persisted (never a picker grant).
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
        /** Source survived deletion but qualifies for a system delete-consent request (Android 11+). */
        public final boolean deleteNeedsConsent;

        ImportResult(@NonNull Uri uri, boolean sourceDeleted) {
            this(uri, sourceDeleted, false);
        }

        ImportResult(@NonNull Uri uri, boolean sourceDeleted, boolean deleteNeedsConsent) {
            this.uri = uri;
            this.sourceDeleted = sourceDeleted;
            this.deleteNeedsConsent = deleteNeedsConsent;
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
        if (albumDisplayName(Uri.parse(uriString)) != null) {
            return true;
        }
        String lower = uriString.toLowerCase(Locale.US);
        // Path may be plain ("Mountain Money") or URI-encoded ("Mountain%20Money").
        return lower.contains(MOUNTAIN_MONEY_ALBUM_MARKER)
                || lower.contains("mountain%20money");
    }

    public static boolean isAlbumDisplayName(@Nullable String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        return name.startsWith(APP_OWNED_DISPLAY_NAME_PREFIX)
                && name.toLowerCase(Locale.US).endsWith(".jpg");
    }

    /**
     * {@code MountainMoney_*.jpg} from a URI fragment, last path segment, or file path.
     */
    @Nullable
    public static String albumDisplayName(@Nullable Uri uri) {
        if (uri == null) {
            return null;
        }
        if (isAlbumDisplayName(uri.getFragment())) {
            return uri.getFragment();
        }
        String last = decodeSegment(uri.getLastPathSegment());
        if (isAlbumDisplayName(last)) {
            return last;
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

    /**
     * Always keep the album MediaStore URI. Never persist a picker grant.
     */
    @Nullable
    static Uri persistUriAfterImport(@Nullable Uri saved, @Nullable Uri originalUri) {
        return saved != null ? saved : originalUri;
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
     * Persist the album copy. Best-effort delete of a non–app-owned picker source; when the
     * platform forbids a silent delete but allows a user-consent request (Android 11+
     * MediaStore image), the result flags it so the caller can finish the move.
     * Never deletes a Mountain Money file, and never rolls back to a picker URI.
     */
    @NonNull
    static ImportResult finishImportMoveOrRollback(Context context, Uri saved, Uri originalUri) {
        Uri persist = persistUriAfterImport(saved, originalUri);
        if (persist == null) {
            throw new IllegalArgumentException("saved uri null");
        }
        boolean deleted = false;
        if (originalUri != null
                && !originalUri.equals(persist)
                && !isAppOwnedReceiptUri(context, originalUri)) {
            deleted = ReceiptSourceDeleter.tryDeleteSource(context, originalUri);
        }
        boolean needsConsent = !deleted
                && ReceiptSourceDeleter.needsSystemDeleteConsent(originalUri,
                        Build.VERSION.SDK_INT);
        return new ImportResult(persist, deleted, needsConsent);
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
                stripFragment(uri), new String[]{column}, null, null, null)) {
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

    @NonNull
    static Uri stripFragment(@NonNull Uri uri) {
        if (uri.getFragment() == null) {
            return uri;
        }
        return uri.buildUpon().fragment(null).build();
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

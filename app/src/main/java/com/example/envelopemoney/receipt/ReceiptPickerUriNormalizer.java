package com.example.envelopemoney.receipt;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.Locale;

/**
 * External and photo-picker URIs are imported into {@link MediaStoreReceiptSaver}'s Mountain Money
 * album so preview, OCR, and persisted {@code receiptImageUri} use a stable app-owned URI (same as camera).
 * When the platform allows, the original picker file is deleted after import (move semantics).
 */
public final class ReceiptPickerUriNormalizer {

    private static final String MOUNTAIN_MONEY_ALBUM_MARKER = "mountain money";

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
     * True when the URI already points at a JPEG saved under Pictures/Mountain Money.
     */
    public static boolean isAppOwnedReceiptUri(@Nullable String uriString) {
        if (uriString == null || uriString.trim().isEmpty()) {
            return false;
        }
        String lower = uriString.toLowerCase(Locale.US);
        return lower.contains(MOUNTAIN_MONEY_ALBUM_MARKER);
    }

    /**
     * True when a gallery/picker URI should be decoded and imported into the app album before use.
     */
    public static boolean shouldImportToAppGallery(@Nullable String uriString) {
        return !isAppOwnedReceiptUri(uriString);
    }

    /** @deprecated use {@link #shouldImportToAppGallery(String)} */
    public static boolean shouldCopyToAppGallery(@Nullable String uriString) {
        return shouldImportToAppGallery(uriString);
    }

    @NonNull
    public static ImportResult normalizeImport(Context context, Uri uri) throws IOException {
        if (uri == null) {
            throw new IOException("uri null");
        }
        if (!shouldImportToAppGallery(uri.toString())) {
            return new ImportResult(uri, false);
        }
        Uri original = uri;
        try {
            Bitmap bmp = ReceiptExifBitmapLoader.decodeUpright(context, uri);
            if (bmp == null) {
                throw new IOException("decode bitmap failed");
            }
            try {
                Uri saved = MediaStoreReceiptSaver.saveJpeg(context, bmp);
                boolean deleted = ReceiptSourceDeleter.tryDeleteSource(context, original);
                return new ImportResult(saved, deleted);
            } finally {
                bmp.recycle();
            }
        } catch (SecurityException e) {
            throw new IOException("picker uri permission denied", e);
        }
    }

    public static Uri normalize(Context context, Uri uri) throws IOException {
        return normalizeImport(context, uri).uri;
    }
}

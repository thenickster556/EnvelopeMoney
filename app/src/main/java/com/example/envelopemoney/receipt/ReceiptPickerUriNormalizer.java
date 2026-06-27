package com.example.envelopemoney.receipt;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.Locale;

/**
 * External and photo-picker URIs are copied into {@link MediaStoreReceiptSaver}'s Mountain Money
 * album so preview, OCR, and persisted {@code receiptImageUri} use a stable app-owned URI (same as camera).
 */
public final class ReceiptPickerUriNormalizer {

    private static final String MOUNTAIN_MONEY_ALBUM_MARKER = "mountain money";

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
     * True when a gallery/picker URI should be decoded and copied into the app album before use.
     */
    public static boolean shouldCopyToAppGallery(@Nullable String uriString) {
        return !isAppOwnedReceiptUri(uriString);
    }

    public static Uri normalize(Context context, Uri uri) throws IOException {
        if (uri == null) {
            throw new IOException("uri null");
        }
        if (!shouldCopyToAppGallery(uri.toString())) {
            return uri;
        }
        try {
            Bitmap bmp = ReceiptExifBitmapLoader.decodeUpright(context, uri);
            if (bmp == null) {
                throw new IOException("decode bitmap failed");
            }
            try {
                return MediaStoreReceiptSaver.saveJpeg(context, bmp);
            } finally {
                bmp.recycle();
            }
        } catch (SecurityException e) {
            throw new IOException("picker uri permission denied", e);
        }
    }
}

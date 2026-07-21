package com.example.envelopemoney.receipt;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.annotation.Nullable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * Best-effort delete of a gallery/picker source after import into the Mountain Money album.
 */
public final class ReceiptSourceDeleter {

    private ReceiptSourceDeleter() {
    }

    /**
     * True when deleting {@code sourceUriString} is worth attempting after import to {@code appOwnedUriString}.
     * String-path ownership only; prefer {@link #shouldAttemptDelete(Context, Uri, Uri)} for MediaStore IDs.
     */
    public static boolean shouldAttemptDelete(@Nullable String sourceUriString,
                                              @Nullable String appOwnedUriString) {
        if (sourceUriString == null || sourceUriString.trim().isEmpty()) {
            return false;
        }
        if (appOwnedUriString != null && sourceUriString.equals(appOwnedUriString)) {
            return false;
        }
        return !ReceiptPickerUriNormalizer.isAppOwnedReceiptUri(sourceUriString);
    }

    /**
     * True when deleting {@code sourceUri} is safe after import. Never deletes app-owned Mountain Money files.
     */
    public static boolean shouldAttemptDelete(@Nullable Context context,
                                              @Nullable Uri sourceUri,
                                              @Nullable Uri appOwnedUri) {
        if (sourceUri == null) {
            return false;
        }
        if (appOwnedUri != null && sourceUri.equals(appOwnedUri)) {
            return false;
        }
        if (ReceiptPickerUriNormalizer.isAppOwnedReceiptUri(context, sourceUri)) {
            return false;
        }
        return shouldAttemptDelete(sourceUri.toString(),
                appOwnedUri != null ? appOwnedUri.toString() : null);
    }

    /**
     * Heuristic: MediaStore image URIs are more likely to be deletable than ephemeral picker grants.
     */
    public static boolean isMediaStoreImagesUri(@Nullable String uriString) {
        if (uriString == null || uriString.trim().isEmpty()) {
            return false;
        }
        String lower = uriString.toLowerCase(Locale.US);
        return lower.contains("content://media/")
                && (lower.contains("/images/") || lower.contains("media.documents"));
    }

    /**
     * @return true when the source was removed
     */
    public static boolean tryDeleteSource(Context context, Uri sourceUri) {
        if (context == null || sourceUri == null) {
            return false;
        }
        if (!shouldAttemptDelete(context, sourceUri, null)) {
            return false;
        }
        if (DocumentsContract.isDocumentUri(context, sourceUri)) {
            try {
                return DocumentsContract.deleteDocument(context.getContentResolver(), sourceUri);
            } catch (FileNotFoundException ignored) {
                return false;
            } catch (SecurityException ignored) {
                return false;
            } catch (RuntimeException ignored) {
                return false;
            }
        }
        if ("file".equalsIgnoreCase(sourceUri.getScheme())) {
            String path = sourceUri.getPath();
            if (path != null) {
                return new java.io.File(path).delete();
            }
            return false;
        }
        try {
            int rows = context.getContentResolver().delete(sourceUri, null, null);
            return rows > 0;
        } catch (SecurityException ignored) {
            return false;
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}

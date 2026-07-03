package com.example.envelopemoney.receipt;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * Best-effort delete of a gallery/picker source after import into the Mountain Money album.
 */
public final class ReceiptSourceDeleter {

    private ReceiptSourceDeleter() {
    }

    /**
     * True when deleting {@code sourceUriString} is worth attempting after import to {@code appOwnedUriString}.
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
     * @return true when {@link Context#getContentResolver()} reports rows deleted
     */
    public static boolean tryDeleteSource(Context context, Uri sourceUri) {
        if (context == null || sourceUri == null) {
            return false;
        }
        if (!shouldAttemptDelete(sourceUri.toString(), null)) {
            return false;
        }
        try {
            int rows = context.getContentResolver().delete(sourceUri, null, null);
            return rows > 0;
        } catch (SecurityException ignored) {
            return false;
        } catch (RuntimeException e) {
            // RecoverableSecurityException (API 29+) and other delete denials
            return false;
        }
    }
}

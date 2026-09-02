package com.example.envelopemoney.receipt;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Reads receipt image bytes. Delegates locate-and-open to {@link ReceiptFolderOpener}.
 */
public final class ReceiptUriStreams {

    private ReceiptUriStreams() {
    }

    @NonNull
    public static InputStream openInputStream(@NonNull Context context, @NonNull Uri uri)
            throws IOException {
        return ReceiptFolderOpener.open(context, uri);
    }

    @NonNull
    public static byte[] readAllBytes(@NonNull Context context, @NonNull Uri uri) throws IOException {
        try (InputStream in = openInputStream(context, uri)) {
            return readFully(in);
        }
    }

    @NonNull
    private static byte[] readFully(@NonNull InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}

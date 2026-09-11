package com.example.envelopemoney.receipt;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.net.URI;
import java.net.URISyntaxException;

/** Resolves stored receipt references without changing or deleting gallery files. */
public final class ReceiptReferenceResolver {
    public enum Status { RESOLVED, PERMISSION_REQUIRED, MISSING, AMBIGUOUS, CORRUPT }

    /** Immutable resolution outcome. Only RESOLVED results may replace a stored reference. */
    public static final class Result {
        public final Status status;
        public final String reference;
        public final String fileName;
        private Result(Status status, String reference, String fileName) {
            this.status = status; this.reference = reference; this.fileName = fileName;
        }
        public static Result resolved(String reference, String fileName) {
            return new Result(Status.RESOLVED, reference, fileName);
        }
        public static Result failure(Status status) { return new Result(status, null, null); }
        public static Result failure(Status status, String fileName) { return new Result(status, null, fileName); }
    }

    /** Platform boundary: inspect must verify image decoding; readAlbum must restrict the folder. */
    public interface Source {
        Result inspect(String reference);
        List<Result> readAlbum();
        /**
         * True when {@link #readAlbum()} may omit photos until the user grants library access.
         * Owned pictures can still appear; missing matches must not be treated as a full search.
         */
        default boolean albumAccessRestricted() { return false; }
    }

    private final Source source;
    private Map<String, Result> albumByName;
    private final Set<String> ambiguousNames = new HashSet<>();
    private boolean albumAccessDenied;

    public ReceiptReferenceResolver(Source source) { this.source = source; }

    /**
     * Verifies the original first, then a unique album match. One resolver is used per background
     * pass so the album is indexed once. A new pass retries access after a permission change.
     * No date or age participates in recovery, including receipts created earlier today.
     */
    public Result resolve(String reference, String fileName) {
        if (reference == null || reference.trim().isEmpty()) return Result.failure(Status.MISSING);
        String expectedName = validFileName(fileName) ? fileName : fileNameFromReference(reference);
        Result original = source.inspect(reference);
        if (expectedName == null && validFileName(original.fileName)) expectedName = original.fileName;
        if (original.status == Status.RESOLVED) {
            if (expectedName == null || expectedName.equals(original.fileName)) return original;
            // A reindexed MediaStore ID can now refer to another photo. Do not silently relink it.
            original = Result.failure(Status.MISSING);
        }
        if (albumAccessDenied) return Result.failure(Status.PERMISSION_REQUIRED, expectedName);
        try {
            if (albumByName == null) {
                albumByName = new HashMap<>();
                for (Result picture : source.readAlbum()) {
                    Result previous = albumByName.put(picture.fileName, picture);
                    if (previous != null && !previous.reference.equals(picture.reference)) {
                        ambiguousNames.add(picture.fileName);
                    }
                }
            }
        } catch (SecurityException denied) {
            albumAccessDenied = true;
            return Result.failure(Status.PERMISSION_REQUIRED, expectedName);
        }
        if (expectedName == null) {
            if (original.status == Status.MISSING && source.albumAccessRestricted()) {
                return Result.failure(Status.PERMISSION_REQUIRED, expectedName);
            }
            return original;
        }
        if (ambiguousNames.contains(expectedName)) return Result.failure(Status.AMBIGUOUS);
        Result candidate = albumByName.get(expectedName);
        if (candidate == null) {
            if (source.albumAccessRestricted()) {
                return Result.failure(Status.PERMISSION_REQUIRED, expectedName);
            }
            return original;
        }
        Result verified = source.inspect(candidate.reference);
        if (verified.status == Status.RESOLVED && !expectedName.equals(verified.fileName)) {
            // The row changed between inventory and decoding. Keep the old association for a retry.
            return Result.failure(Status.MISSING, expectedName);
        }
        return verified;
    }

    /** Extracts a filename hint from old paths or URI fragments; numeric MediaStore IDs are not names. */
    public static String fileNameFromReference(String reference) {
        if (reference == null || reference.isEmpty()) return null;
        try {
            URI parsed = new URI(reference.replace(" ", "%20"));
            if (validFileName(parsed.getFragment())) return parsed.getFragment();
            String path = parsed.getPath();
            if (path == null) return null;
            String name = path.substring(path.lastIndexOf('/') + 1);
            return validFileName(name) ? name : null;
        } catch (URISyntaxException invalid) {
            return null;
        }
    }

    private static boolean validFileName(String name) {
        return name != null && name.contains(".") && !name.startsWith(".")
                && !name.contains("/") && !name.contains("\\") && !name.contains("\u0000");
    }
}

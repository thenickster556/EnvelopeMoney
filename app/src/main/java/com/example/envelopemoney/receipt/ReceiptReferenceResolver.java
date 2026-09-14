package com.example.envelopemoney.receipt;

import java.util.List;
import java.util.Map;
import java.util.Collections;
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
        /** Epoch millis when known (filename or MediaStore); 0 if unknown. */
        public final long captureTimeMs;
        /** Competing album files behind an AMBIGUOUS outcome, for a user picker; empty otherwise. */
        public final List<Result> alternatives;
        private Result(Status status, String reference, String fileName, long captureTimeMs,
                       List<Result> alternatives) {
            this.status = status;
            this.reference = reference;
            this.fileName = fileName;
            this.captureTimeMs = captureTimeMs;
            this.alternatives = alternatives != null ? alternatives : Collections.emptyList();
        }
        public static Result resolved(String reference, String fileName) {
            return resolved(reference, fileName, 0L);
        }
        public static Result resolved(String reference, String fileName, long captureTimeMs) {
            return new Result(Status.RESOLVED, reference, fileName, captureTimeMs, null);
        }
        public static Result failure(Status status) { return failure(status, null); }
        public static Result failure(Status status, String fileName) {
            return new Result(status, null, fileName, 0L, null);
        }
        public static Result failure(Status status, String fileName, List<Result> alternatives) {
            return new Result(status, null, fileName, 0L, alternatives);
        }
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
    private Map<String, Result> albumByNormalizedName;
    private final Set<String> ambiguousNames = new HashSet<>();
    private final Set<String> ambiguousNormalizedNames = new HashSet<>();
    private boolean albumAccessDenied;

    public ReceiptReferenceResolver(Source source) { this.source = source; }

    /**
     * Verifies the original first, then a unique album filename match. One resolver is used per
     * background pass so the album is indexed once. A new pass retries access after a permission
     * change. This single-row path does not guess from transaction dates; leftover same-day
     * matching is a one-pass {@link ReceiptAlbumMatcher} / {@link ReceiptReferenceRepair#resolveAll}
     * concern.
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
                albumByNormalizedName = new HashMap<>();
                for (Result picture : source.readAlbum()) {
                    Result previous = albumByName.put(picture.fileName, picture);
                    if (previous != null && !previous.reference.equals(picture.reference)) {
                        ambiguousNames.add(picture.fileName);
                    }
                    String normalized = ReceiptAlbumMatcher.normalizeFileName(picture.fileName);
                    if (normalized != null) {
                        Result previousNormalized = albumByNormalizedName.put(normalized, picture);
                        if (previousNormalized != null
                                && !previousNormalized.reference.equals(picture.reference)) {
                            ambiguousNormalizedNames.add(normalized);
                        }
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
            // A renamed copy (case, .jpeg, " (1)" suffix) still identifies its file.
            String normalized = ReceiptAlbumMatcher.normalizeFileName(expectedName);
            if (normalized != null && !ambiguousNormalizedNames.contains(normalized)) {
                candidate = albumByNormalizedName.get(normalized);
            }
        }
        if (candidate == null) {
            if (source.albumAccessRestricted()) {
                return Result.failure(Status.PERMISSION_REQUIRED, expectedName);
            }
            return original;
        }
        Result verified = source.inspect(candidate.reference);
        if (verified.status == Status.RESOLVED && candidate.fileName != null && verified.fileName != null
                && !candidate.fileName.equals(verified.fileName)) {
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

    static boolean validFileName(String name) {
        return name != null && name.contains(".") && !name.startsWith(".")
                && !name.contains("/") && !name.contains("\\") && !name.contains("\u0000");
    }
}

package com.example.envelopemoney.receipt;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * One-pass album assignment. Identity tiers win without date evidence: the exact filename, then a
 * normalized filename (case, .jpeg extension, URL encoding, " (1)" copy suffix), then a shared
 * epoch token such as the digits in MountainMoney_1726150469234. Leftover claims fall back to the
 * only unused album file captured on the transaction day, or one day either side when that day
 * holds no file. When unique bind fails, unused files in that ±1-day window become ranked
 * alternatives (closer capture time first). Anything short of a unique candidate stays ambiguous
 * or missing.
 */
public final class ReceiptAlbumMatcher {
    private ReceiptAlbumMatcher() {
    }

    public static final class Claim {
        public final String key;
        public final String fileName;
        public final String transactionDate;

        public Claim(String key, String fileName, String transactionDate) {
            this.key = key;
            this.fileName = fileName;
            this.transactionDate = transactionDate;
        }
    }

    public static Map<String, ReceiptReferenceResolver.Result> assign(
            List<Claim> claims, List<ReceiptReferenceResolver.Result> album, TimeZone zone) {
        return assign(claims, album, zone, Collections.emptySet(), true);
    }

    public static Map<String, ReceiptReferenceResolver.Result> assign(
            List<Claim> claims,
            List<ReceiptReferenceResolver.Result> album,
            TimeZone zone,
            Set<String> reservedReferences,
            boolean allowDateMatch) {
        Map<String, ReceiptReferenceResolver.Result> results = new HashMap<>();
        if (claims == null || claims.isEmpty()) {
            return results;
        }
        TimeZone tz = zone != null ? zone : TimeZone.getDefault();
        AlbumIndex index = new AlbumIndex(album);
        Set<String> bound = new HashSet<>();
        if (reservedReferences != null) {
            bound.addAll(reservedReferences);
        }
        List<Claim> leftover = new ArrayList<>();
        for (Claim claim : claims) {
            String name = ReceiptReferenceResolver.validFileName(claim.fileName) ? claim.fileName : null;
            List<ReceiptReferenceResolver.Result> candidates = index.identityCandidates(name, bound);
            if (candidates.isEmpty()) {
                leftover.add(claim);
                continue;
            }
            if (candidates.size() != 1) {
                results.put(claim.key, ReceiptReferenceResolver.Result.failure(
                        ReceiptReferenceResolver.Status.AMBIGUOUS, claim.fileName, candidates));
                continue;
            }
            ReceiptReferenceResolver.Result picture = candidates.get(0);
            bound.add(picture.reference);
            results.put(claim.key, picture);
        }
        if (!allowDateMatch) {
            for (Claim claim : leftover) {
                results.put(claim.key, ReceiptReferenceResolver.Result.failure(
                        ReceiptReferenceResolver.Status.MISSING, claim.fileName));
            }
            return results;
        }
        assignByCaptureDay(leftover, index, tz, bound, results);
        expandFailedUniqueMatches(claims, index, tz, bound, results);
        return results;
    }

    /** Immutable filename inventory of the album, queried per identity tier. */
    private static final class AlbumIndex {
        private final List<ReceiptReferenceResolver.Result> files = new ArrayList<>();

        AlbumIndex(List<ReceiptReferenceResolver.Result> album) {
            if (album == null) return;
            for (ReceiptReferenceResolver.Result picture : album) {
                if (picture != null) files.add(picture);
            }
        }

        /**
         * Unbound files sharing the claim's identity under the first tier that finds any: exact
         * filename, normalized filename, or epoch token. Later tiers never override earlier ones,
         * so an exact hit can not be displaced by a looser coincidence.
         */
        List<ReceiptReferenceResolver.Result> identityCandidates(String fileName, Set<String> bound) {
            if (fileName == null) return Collections.emptyList();
            List<ReceiptReferenceResolver.Result> exact = filesNamed(fileName, bound);
            if (!exact.isEmpty()) return exact;
            String normalized = normalizeFileName(fileName);
            if (normalized != null) {
                List<ReceiptReferenceResolver.Result> byNormalization = new ArrayList<>();
                for (ReceiptReferenceResolver.Result picture : files) {
                    if (bound.contains(picture.reference)) continue;
                    if (normalized.equals(normalizeFileName(picture.fileName))) byNormalization.add(picture);
                }
                if (!byNormalization.isEmpty()) return byNormalization;
            }
            String token = epochToken(fileName);
            if (token != null) {
                List<ReceiptReferenceResolver.Result> byToken = new ArrayList<>();
                for (ReceiptReferenceResolver.Result picture : files) {
                    if (bound.contains(picture.reference)) continue;
                    if (token.equals(epochToken(picture.fileName))) byToken.add(picture);
                }
                return byToken;
            }
            return Collections.emptyList();
        }

        private List<ReceiptReferenceResolver.Result> filesNamed(String fileName, Set<String> bound) {
            List<ReceiptReferenceResolver.Result> matches = new ArrayList<>();
            for (ReceiptReferenceResolver.Result picture : files) {
                if (bound.contains(picture.reference)) continue;
                if (fileName.equals(picture.fileName)) matches.add(picture);
            }
            return matches;
        }
    }

    /**
     * Same-day pass first, exactly as before: one claim and one unused file on a day bind, several
     * files make the day ambiguous, several claims keep the day unassigned. Lone claims whose day
     * holds no file get one adjacent-day retry so backdated entries and scan-time drift recover.
     */
    private static void assignByCaptureDay(List<Claim> leftover, AlbumIndex index, TimeZone tz,
                                           Set<String> bound, Map<String, ReceiptReferenceResolver.Result> results) {
        Map<String, List<Claim>> claimsByDay = new HashMap<>();
        for (Claim claim : leftover) {
            String day = normalizeDay(claim.transactionDate);
            if (day == null) {
                results.put(claim.key, ReceiptReferenceResolver.Result.failure(
                        ReceiptReferenceResolver.Status.MISSING, claim.fileName));
                continue;
            }
            List<Claim> same = claimsByDay.get(day);
            if (same == null) {
                same = new ArrayList<>();
                claimsByDay.put(day, same);
            }
            same.add(claim);
        }
        Map<String, List<ReceiptReferenceResolver.Result>> unusedByDay =
                unusedFilesByDay(index, tz, bound);
        List<Claim> loneClaimsWithoutSameDayFile = new ArrayList<>();
        for (Map.Entry<String, List<Claim>> dayClaims : claimsByDay.entrySet()) {
            List<ReceiptReferenceResolver.Result> dayFiles = unusedByDay.get(dayClaims.getKey());
            if (dayFiles == null) dayFiles = Collections.emptyList();
            if (dayClaims.getValue().size() == 1 && dayFiles.size() == 1) {
                ReceiptReferenceResolver.Result picture = dayFiles.get(0);
                bound.add(picture.reference);
                results.put(dayClaims.getValue().get(0).key, picture);
                continue;
            }
            ReceiptReferenceResolver.Status status = dayFiles.size() > 1
                    ? ReceiptReferenceResolver.Status.AMBIGUOUS
                    : ReceiptReferenceResolver.Status.MISSING;
            if (dayClaims.getValue().size() == 1 && dayFiles.isEmpty()) {
                loneClaimsWithoutSameDayFile.add(dayClaims.getValue().get(0));
                continue;
            }
            for (Claim claim : dayClaims.getValue()) {
                results.put(claim.key, ReceiptReferenceResolver.Result.failure(status, claim.fileName,
                        status == ReceiptReferenceResolver.Status.AMBIGUOUS ? dayFiles : null));
            }
        }
        assignByAdjacentDay(loneClaimsWithoutSameDayFile, unusedByDay, tz, bound, results);
    }

    /**
     * Adjacent-day retry for claims whose own day held no file at all. The single unused file one
     * day either side binds, unless several files or another lone claim contest it; contesting
     * claims are both marked ambiguous so iteration order cannot pick a winner.
     */
    private static void assignByAdjacentDay(List<Claim> loneClaims,
                                            Map<String, List<ReceiptReferenceResolver.Result>> unusedByDay,
                                            TimeZone tz, Set<String> bound,
                                            Map<String, ReceiptReferenceResolver.Result> results) {
        for (Claim claim : loneClaims) {
            if (results.containsKey(claim.key)) continue;
            List<ReceiptReferenceResolver.Result> pool = new ArrayList<>();
            for (String adjacent : adjacentDays(normalizeDay(claim.transactionDate))) {
                List<ReceiptReferenceResolver.Result> dayFiles = unusedByDay.get(adjacent);
                if (dayFiles == null) continue;
                for (ReceiptReferenceResolver.Result picture : dayFiles) {
                    if (!bound.contains(picture.reference)) pool.add(picture);
                }
            }
            if (pool.isEmpty()) {
                results.put(claim.key, ReceiptReferenceResolver.Result.failure(
                        ReceiptReferenceResolver.Status.MISSING, claim.fileName));
                continue;
            }
            if (pool.size() > 1) {
                results.put(claim.key, ReceiptReferenceResolver.Result.failure(
                        ReceiptReferenceResolver.Status.AMBIGUOUS, claim.fileName, pool));
                continue;
            }
            ReceiptReferenceResolver.Result picture = pool.get(0);
            if (adjacentCandidateIsContested(picture, claim, loneClaims, unusedByDay, bound, tz, results)) {
                results.put(claim.key, ReceiptReferenceResolver.Result.failure(
                        ReceiptReferenceResolver.Status.AMBIGUOUS, claim.fileName,
                        Collections.singletonList(picture)));
                continue;
            }
            bound.add(picture.reference);
            results.put(claim.key, picture);
        }
    }

    private static boolean adjacentCandidateIsContested(ReceiptReferenceResolver.Result picture, Claim claim,
                                                        List<Claim> loneClaims,
                                                        Map<String, List<ReceiptReferenceResolver.Result>> unusedByDay,
                                                        Set<String> bound, TimeZone tz,
                                                        Map<String, ReceiptReferenceResolver.Result> results) {
        String fileDay = captureDayOf(picture, tz);
        for (Claim other : loneClaims) {
            if (other == claim || results.containsKey(other.key)) continue;
            for (String adjacent : adjacentDays(normalizeDay(other.transactionDate))) {
                if (!adjacent.equals(fileDay)) continue;
                List<ReceiptReferenceResolver.Result> dayFiles = unusedByDay.get(adjacent);
                if (dayFiles != null && dayFiles.contains(picture)) {
                    results.put(other.key, ReceiptReferenceResolver.Result.failure(
                            ReceiptReferenceResolver.Status.AMBIGUOUS, other.fileName,
                            Collections.singletonList(picture)));
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * After unique auto-bind, leftover failures (and identity collisions) offer unused files whose
     * capture day is the transaction day ±1, ranked most-to-least likely. Identity candidates that
     * are still unused stay in the list even when they fall outside that window.
     */
    private static void expandFailedUniqueMatches(List<Claim> claims, AlbumIndex index, TimeZone tz,
                                                  Set<String> bound,
                                                  Map<String, ReceiptReferenceResolver.Result> results) {
        Map<String, List<ReceiptReferenceResolver.Result>> unusedByDay =
                unusedFilesByDay(index, tz, bound);
        for (Claim claim : claims) {
            ReceiptReferenceResolver.Result current = results.get(claim.key);
            if (current == null) continue;
            if (current.status == ReceiptReferenceResolver.Status.RESOLVED) continue;
            List<ReceiptReferenceResolver.Result> pool = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (ReceiptReferenceResolver.Result alternative : current.alternatives) {
                if (alternative == null || alternative.reference == null) continue;
                if (bound.contains(alternative.reference)) continue;
                if (seen.add(alternative.reference)) pool.add(alternative);
            }
            String day = normalizeDay(claim.transactionDate);
            if (day != null) {
                for (ReceiptReferenceResolver.Result picture : windowUnused(day, unusedByDay, bound)) {
                    if (seen.add(picture.reference)) pool.add(picture);
                }
            }
            if (pool.isEmpty()) {
                results.put(claim.key, ReceiptReferenceResolver.Result.failure(
                        ReceiptReferenceResolver.Status.MISSING, claim.fileName));
            } else {
                results.put(claim.key, ReceiptReferenceResolver.Result.failure(
                        ReceiptReferenceResolver.Status.AMBIGUOUS, claim.fileName,
                        rankByLikelihood(claim, pool, tz)));
            }
        }
    }

    private static Map<String, List<ReceiptReferenceResolver.Result>> unusedFilesByDay(
            AlbumIndex index, TimeZone tz, Set<String> bound) {
        Map<String, List<ReceiptReferenceResolver.Result>> unusedByDay = new HashMap<>();
        for (ReceiptReferenceResolver.Result picture : index.files) {
            if (bound.contains(picture.reference)) continue;
            String day = captureDayOf(picture, tz);
            if (day == null) continue;
            List<ReceiptReferenceResolver.Result> same = unusedByDay.get(day);
            if (same == null) {
                same = new ArrayList<>();
                unusedByDay.put(day, same);
            }
            same.add(picture);
        }
        return unusedByDay;
    }

    /** Unused album files captured on {@code txDay} or one calendar day either side. */
    static List<ReceiptReferenceResolver.Result> windowUnused(String txDay,
            Map<String, List<ReceiptReferenceResolver.Result>> unusedByDay, Set<String> bound) {
        List<ReceiptReferenceResolver.Result> pool = new ArrayList<>();
        if (txDay == null || unusedByDay == null) return pool;
        Set<String> seen = new HashSet<>();
        List<String> days = new ArrayList<>();
        days.add(txDay);
        days.addAll(adjacentDays(txDay));
        for (String day : days) {
            List<ReceiptReferenceResolver.Result> dayFiles = unusedByDay.get(day);
            if (dayFiles == null) continue;
            for (ReceiptReferenceResolver.Result picture : dayFiles) {
                if (picture == null || picture.reference == null) continue;
                if (bound != null && bound.contains(picture.reference)) continue;
                if (seen.add(picture.reference)) pool.add(picture);
            }
        }
        return pool;
    }

    /**
     * Target instant for ranking: epoch digits from a stale MountainMoney name, otherwise noon of
     * the transaction date in {@code tz}. Zero when neither is usable.
     */
    static long targetCaptureMs(Claim claim, TimeZone tz) {
        if (claim == null) return 0L;
        Long fromName = captureTimeMs(claim.fileName);
        if (fromName != null) return fromName;
        String day = normalizeDay(claim.transactionDate);
        if (day == null) return 0L;
        return noonOfDay(day, tz);
    }

    /** Smaller |capture − target| first; equal distances break ties on the reference string. */
    static List<ReceiptReferenceResolver.Result> rankByLikelihood(Claim claim,
            List<ReceiptReferenceResolver.Result> pool, TimeZone tz) {
        if (pool == null || pool.isEmpty()) return Collections.emptyList();
        final long target = targetCaptureMs(claim, tz);
        List<ReceiptReferenceResolver.Result> ranked = new ArrayList<>(pool);
        Collections.sort(ranked, new Comparator<ReceiptReferenceResolver.Result>() {
            @Override
            public int compare(ReceiptReferenceResolver.Result left,
                               ReceiptReferenceResolver.Result right) {
                long leftDistance = Math.abs(captureInstant(left) - target);
                long rightDistance = Math.abs(captureInstant(right) - target);
                int byDistance = Long.compare(leftDistance, rightDistance);
                if (byDistance != 0) return byDistance;
                String leftRef = left.reference != null ? left.reference : "";
                String rightRef = right.reference != null ? right.reference : "";
                return leftRef.compareTo(rightRef);
            }
        });
        return ranked;
    }

    private static long captureInstant(ReceiptReferenceResolver.Result picture) {
        if (picture == null) return 0L;
        if (picture.captureTimeMs > 0) return picture.captureTimeMs;
        return valueOrZero(captureTimeMs(picture.fileName));
    }

    static long noonOfDay(String day, TimeZone tz) {
        if (day == null || tz == null) return 0L;
        String[] parts = day.split("-");
        if (parts.length != 3) return 0L;
        Calendar calendar = Calendar.getInstance(tz);
        calendar.clear();
        calendar.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1,
                Integer.parseInt(parts[2]), 12, 0, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    /** Filename epoch first (MountainMoney saves millis in the name), otherwise unusable. */
    static String captureDayOf(ReceiptReferenceResolver.Result picture, TimeZone tz) {
        long capture = picture.captureTimeMs > 0
                ? picture.captureTimeMs
                : valueOrZero(captureTimeMs(picture.fileName));
        return capture > 0 ? localDay(capture, tz) : null;
    }

    public static Long captureTimeMs(String fileName) {
        if (fileName == null || !fileName.startsWith("MountainMoney_")) return null;
        String rest = fileName.substring("MountainMoney_".length());
        if (rest.length() < 14 || !rest.toLowerCase(Locale.US).endsWith(".jpg")) return null;
        String digits = rest.substring(0, rest.length() - 4);
        if (digits.length() < 10 || digits.length() > 13) return null;
        for (int i = 0; i < digits.length(); i++) {
            if (!Character.isDigit(digits.charAt(i))) return null;
        }
        return Long.parseLong(digits);
    }

    /**
     * Comparable filename form: trimmed, lowercased, .jpeg folded to .jpg, percent-encoding
     * decoded, and Windows-style " (1)" copy suffixes dropped. Returns null when nothing usable
     * remains, which sends the claim to the date tiers instead.
     */
    static String normalizeFileName(String fileName) {
        if (fileName == null) return null;
        String trimmed = fileName.trim();
        if (trimmed.isEmpty()) return null;
        String lowered = decodePercent(trimmed.toLowerCase(Locale.ROOT)).trim();
        if (lowered.isEmpty()) return null;
        int extensionStart = lowered.lastIndexOf('.');
        String extension = "";
        String base = lowered;
        if (extensionStart >= 0) {
            extension = lowered.substring(extensionStart);
            base = lowered.substring(0, extensionStart);
        }
        if (".jpeg".equals(extension)) extension = ".jpg";
        base = base.replaceFirst("\\s*\\(\\d+\\)$", "").trim();
        if (base.isEmpty()) return null;
        return base + extension;
    }

    /** Decodes %XX sequences; unlike URLDecoder a plus sign stays a plus sign in a filename. */
    private static String decodePercent(String value) {
        if (!value.contains("%")) return value;
        StringBuilder decoded = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '%' && i + 2 < value.length()
                    && isHexDigit(value.charAt(i + 1)) && isHexDigit(value.charAt(i + 2))) {
                decoded.append((char) Integer.parseInt(value.substring(i + 1, i + 3), 16));
                i += 2;
            } else {
                decoded.append(character);
            }
        }
        return decoded.toString();
    }

    private static boolean isHexDigit(char character) {
        return (character >= '0' && character <= '9')
                || (character >= 'a' && character <= 'f')
                || (character >= 'A' && character <= 'F');
    }

    /** First digit run of at least ten characters in the base name; epoch millis are that long. */
    static String epochToken(String fileName) {
        if (fileName == null) return null;
        int extensionStart = fileName.lastIndexOf('.');
        if (extensionStart <= 0) return null;
        String base = fileName.substring(0, extensionStart);
        int runStart = -1;
        for (int i = 0; i <= base.length(); i++) {
            boolean digit = i < base.length() && Character.isDigit(base.charAt(i));
            if (digit) {
                if (runStart < 0) runStart = i;
                continue;
            }
            if (runStart >= 0 && i - runStart >= 10) return base.substring(runStart, i);
            runStart = -1;
        }
        return null;
    }

    static String localDay(long epochMs, TimeZone zone) {
        Calendar calendar = Calendar.getInstance(zone);
        calendar.setTimeInMillis(epochMs);
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH) + 1;
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        return String.format(Locale.US, "%04d-%02d-%02d", year, month, day);
    }

    /** The day before and after a yyyy-MM-dd day; empty for unparseable input. */
    private static List<String> adjacentDays(String day) {
        if (day == null) return Collections.emptyList();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setLenient(false);
        try {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(format.parse(day));
            List<String> adjacent = new ArrayList<>(2);
            calendar.add(Calendar.DAY_OF_MONTH, -1);
            adjacent.add(format.format(calendar.getTime()));
            calendar.add(Calendar.DAY_OF_MONTH, 2);
            adjacent.add(format.format(calendar.getTime()));
            return adjacent;
        } catch (ParseException invalidDay) {
            return Collections.emptyList();
        }
    }

    private static String normalizeDay(String date) {
        if (date == null) return null;
        String trimmed = date.trim();
        return trimmed.matches("\\d{4}-\\d{2}-\\d{2}") ? trimmed : null;
    }

    private static long valueOrZero(Long value) {
        return value != null ? value : 0L;
    }
}

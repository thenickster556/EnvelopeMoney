package com.example.envelopemoney.receipt;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * One-pass album assignment. Unique filenames win; leftover unnamed receipts may take the
 * only unused album file whose capture day matches the transaction date. Two leftovers on
 * the same day stay ambiguous.
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
            boolean allowSameDayMatch) {
        Map<String, ReceiptReferenceResolver.Result> results = new HashMap<>();
        if (claims == null || claims.isEmpty()) {
            return results;
        }
        TimeZone tz = zone != null ? zone : TimeZone.getDefault();
        Map<String, List<ReceiptReferenceResolver.Result>> byName = new HashMap<>();
        if (album != null) {
            for (ReceiptReferenceResolver.Result picture : album) {
                if (picture == null || picture.fileName == null) continue;
                List<ReceiptReferenceResolver.Result> same = byName.get(picture.fileName);
                if (same == null) {
                    same = new ArrayList<>();
                    byName.put(picture.fileName, same);
                }
                same.add(picture);
            }
        }
        Set<String> boundByName = new HashSet<>();
        if (reservedReferences != null) {
            boundByName.addAll(reservedReferences);
        }
        List<Claim> leftover = new ArrayList<>();
        for (Claim claim : claims) {
            String name = ReceiptReferenceResolver.validFileName(claim.fileName) ? claim.fileName : null;
            if (name == null) {
                leftover.add(claim);
                continue;
            }
            List<ReceiptReferenceResolver.Result> matches = byName.get(name);
            if (matches == null || matches.isEmpty()) {
                leftover.add(claim);
                continue;
            }
            if (matches.size() != 1) {
                results.put(claim.key, ReceiptReferenceResolver.Result.failure(
                        ReceiptReferenceResolver.Status.AMBIGUOUS, name));
                continue;
            }
            ReceiptReferenceResolver.Result picture = matches.get(0);
            boundByName.add(picture.reference);
            results.put(claim.key, picture);
        }
        if (!allowSameDayMatch) {
            for (Claim claim : leftover) {
                results.put(claim.key, ReceiptReferenceResolver.Result.failure(
                        ReceiptReferenceResolver.Status.MISSING, claim.fileName));
            }
            return results;
        }
        Map<String, List<Claim>> leftoverByDay = new HashMap<>();
        for (Claim claim : leftover) {
            String day = normalizeDay(claim.transactionDate);
            if (day == null) {
                results.put(claim.key, ReceiptReferenceResolver.Result.failure(
                        ReceiptReferenceResolver.Status.MISSING, claim.fileName));
                continue;
            }
            List<Claim> same = leftoverByDay.get(day);
            if (same == null) {
                same = new ArrayList<>();
                leftoverByDay.put(day, same);
            }
            same.add(claim);
        }
        Map<String, List<ReceiptReferenceResolver.Result>> unusedByDay = new HashMap<>();
        if (album != null) {
            for (ReceiptReferenceResolver.Result picture : album) {
                if (picture == null || boundByName.contains(picture.reference)) continue;
                long capture = picture.captureTimeMs > 0
                        ? picture.captureTimeMs
                        : valueOrZero(captureTimeMs(picture.fileName));
                if (capture <= 0) continue;
                String day = localDay(capture, tz);
                List<ReceiptReferenceResolver.Result> same = unusedByDay.get(day);
                if (same == null) {
                    same = new ArrayList<>();
                    unusedByDay.put(day, same);
                }
                same.add(picture);
            }
        }
        for (Map.Entry<String, List<Claim>> dayClaims : leftoverByDay.entrySet()) {
            List<ReceiptReferenceResolver.Result> dayFiles = unusedByDay.get(dayClaims.getKey());
            if (dayFiles == null) dayFiles = new ArrayList<>();
            if (dayClaims.getValue().size() == 1 && dayFiles.size() == 1) {
                ReceiptReferenceResolver.Result picture = dayFiles.get(0);
                boundByName.add(picture.reference);
                results.put(dayClaims.getValue().get(0).key, picture);
                continue;
            }
            ReceiptReferenceResolver.Status status = dayFiles.size() > 1
                    ? ReceiptReferenceResolver.Status.AMBIGUOUS
                    : ReceiptReferenceResolver.Status.MISSING;
            for (Claim claim : dayClaims.getValue()) {
                results.put(claim.key, ReceiptReferenceResolver.Result.failure(status, claim.fileName));
            }
        }
        return results;
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

    static String localDay(long epochMs, TimeZone zone) {
        Calendar calendar = Calendar.getInstance(zone);
        calendar.setTimeInMillis(epochMs);
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH) + 1;
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        return String.format(Locale.US, "%04d-%02d-%02d", year, month, day);
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

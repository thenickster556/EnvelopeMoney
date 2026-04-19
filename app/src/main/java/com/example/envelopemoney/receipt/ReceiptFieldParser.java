package com.example.envelopemoney.receipt;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Heuristic field extraction from OCR lines (retail / restaurant / gas). Pure Java — unit tested.
 */
public final class ReceiptFieldParser {

    private static final Pattern MONEY = Pattern.compile(
            "(?:^|[^\\d])(\\$)?\\s*(\\d{1,3}(?:,\\d{3})+|\\d+)\\s*([.,])\\s*(\\d{2})(?:\\s*$|[^\\d])");
    private static final Pattern ISO_DATE = Pattern.compile("(20\\d{2})-(\\d{2})-(\\d{2})");
    private static final Pattern US_DATE = Pattern.compile("(\\d{1,2})[/-](\\d{1,2})[/-](\\d{2,4})");
    private static final Pattern TOTAL_LABEL = Pattern.compile(
            "(?i)\\b(total|amount\\s*due|balance\\s*due|grand\\s*total)\\b");
    private static final Pattern SUBTOTAL_OR_TAX = Pattern.compile(
            "(?i)\\b(sub\\s*total|subtotal|tax|tip|gratuity|suggested)\\b");
    private static final Pattern GAS_GALLON = Pattern.compile("(?i)\\b(gal|gallon|/\\s*gal)\\b");

    private ReceiptFieldParser() {
    }

    public static ReceiptDraft parse(OcrResult ocr, ReceiptCaptureMode mode) {
        if (ocr == null) {
            return new ReceiptDraft(null, null, null, 0f, null);
        }
        List<String> lines = new ArrayList<>();
        for (OcrLine line : ocr.getLines()) {
            if (line.text != null && !line.text.trim().isEmpty()) {
                lines.add(line.text.trim());
            }
        }
        String full = ocr.getFullText();
        String sample = full.length() > 2000 ? full.substring(0, 2000) : full;

        String date = extractDate(full);
        ReceiptCaptureMode m = mode == null ? ReceiptCaptureMode.AUTO : mode;
        if (m == ReceiptCaptureMode.AUTO) {
            m = inferMode(lines);
        }

        String merchant = extractMerchant(lines);
        AmountPick pick = pickTotal(lines, m);

        Double amount = pick.amount;
        float conf = pick.confidence;
        if (merchant == null || merchant.isEmpty()) {
            if (m == ReceiptCaptureMode.GAS) {
                merchant = "Gas";
            } else {
                merchant = "Unknown merchant";
            }
        }

        return new ReceiptDraft(merchant, amount, date, conf, sample);
    }

    private static ReceiptCaptureMode inferMode(List<String> lines) {
        String joined = joinLower(lines);
        if (joined.contains("subtotal") || joined.contains("gratuity") || joined.contains("suggested tip")) {
            return ReceiptCaptureMode.RESTAURANT;
        }
        if (lines.size() <= 6 && countMoneyCandidates(lines) <= 4) {
            return ReceiptCaptureMode.GAS;
        }
        return ReceiptCaptureMode.RECEIPT;
    }

    private static String joinLower(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (String l : lines) {
            sb.append(l.toLowerCase(Locale.US)).append('\n');
        }
        return sb.toString();
    }

    private static int countMoneyCandidates(List<String> lines) {
        int n = 0;
        for (String line : lines) {
            if (MONEY.matcher(line).find()) {
                n++;
            }
        }
        return n;
    }

    @Nullable
    private static String extractDate(String full) {
        Matcher iso = ISO_DATE.matcher(full);
        if (iso.find()) {
            return iso.group(1) + "-" + iso.group(2) + "-" + iso.group(3);
        }
        Matcher us = US_DATE.matcher(full);
        if (us.find()) {
            int m = Integer.parseInt(us.group(1));
            int d = Integer.parseInt(us.group(2));
            int y = Integer.parseInt(us.group(3));
            if (y < 100) {
                y += 2000;
            }
            return String.format(Locale.US, "%04d-%02d-%02d", y, m, d);
        }
        return null;
    }

    @Nullable
    private static String extractMerchant(List<String> lines) {
        int limit = Math.min(lines.size(), 10);
        for (int i = 0; i < limit; i++) {
            String line = lines.get(i);
            if (line.length() < 3 || line.length() > 80) {
                continue;
            }
            if (TOTAL_LABEL.matcher(line).find() || SUBTOTAL_OR_TAX.matcher(line).find()) {
                continue;
            }
            if (ISO_DATE.matcher(line).find() || US_DATE.matcher(line).find()) {
                continue;
            }
            if (line.matches("(?i)^\\s*\\d+\\s*$")) {
                continue;
            }
            if (mostlyNumeric(line)) {
                continue;
            }
            return line;
        }
        return null;
    }

    private static boolean mostlyNumeric(String line) {
        int digits = 0;
        for (int i = 0; i < line.length(); i++) {
            if (Character.isDigit(line.charAt(i))) {
                digits++;
            }
        }
        return digits >= line.length() * 0.6;
    }

    private static AmountPick pickTotal(List<String> lines, ReceiptCaptureMode mode) {
        List<Double> candidates = new ArrayList<>();
        for (String line : lines) {
            if (mode == ReceiptCaptureMode.GAS && GAS_GALLON.matcher(line).find()) {
                continue;
            }
            if (mode == ReceiptCaptureMode.RESTAURANT && SUBTOTAL_OR_TAX.matcher(line).find()
                    && !TOTAL_LABEL.matcher(line).find()) {
                continue;
            }
            Matcher m = MONEY.matcher(line);
            while (m.find()) {
                Double v = parseMoneyGroup(m);
                if (v != null && v > 0 && v < 100_000) {
                    candidates.add(v);
                }
            }
        }

        if (candidates.isEmpty()) {
            return new AmountPick(null, 0.2f);
        }

        // Prefer amount on same line as TOTAL
        for (String line : lines) {
            if (TOTAL_LABEL.matcher(line).find()) {
                Matcher m = MONEY.matcher(line);
                double best = -1;
                while (m.find()) {
                    Double v = parseMoneyGroup(m);
                    if (v != null && v > best) {
                        best = v;
                    }
                }
                if (best > 0) {
                    return new AmountPick(best, 0.9f);
                }
            }
        }

        if (mode == ReceiptCaptureMode.GAS || mode == ReceiptCaptureMode.AUTO) {
            double max = Collections.max(candidates);
            return new AmountPick(max, 0.55f);
        }

        // Restaurant / receipt: prefer largest plausible final total (not perfect but 80/20)
        double max = Collections.max(candidates);
        return new AmountPick(max, 0.5f);
    }

    @Nullable
    private static Double parseMoneyGroup(Matcher m) {
        try {
            String intPart = m.group(2).replace(",", "");
            String frac = m.group(4);
            return Double.parseDouble(intPart + "." + frac);
        } catch (Exception e) {
            return null;
        }
    }

    private static final class AmountPick {
        @Nullable final Double amount;
        final float confidence;

        AmountPick(@Nullable Double amount, float confidence) {
            this.amount = amount;
            this.confidence = confidence;
        }
    }
}

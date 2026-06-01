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
    /**
     * Line looks like a final charge label; scanned from <i>bottom</i> of the receipt for restaurant
     * so we win “amount due” / last total (after tip) over an earlier pre-tip total.
     */
    private static final Pattern TOTAL_LINE_STRONG = Pattern.compile(
            "(?i)\\b(amount\\s*due|balance\\s*due|total\\s*due|grand\\s*total|pay\\s*this\\s*amount"
                    + "|total\\s*paid|amount\\s*paid|payment\\s*total|you\\s*paid|paid\\s*total)\\b");
    private static final Pattern TOTAL_LABEL = Pattern.compile(
            "(?i)\\b(total|amount\\s*due|balance\\s*due|grand\\s*total|total\\s*due|pay\\s*this\\s*amount"
                    + "|total\\s*paid|amount\\s*paid|payment\\s*total|you\\s*paid|paid\\s*total)\\b");
    private static final Pattern SUBTOTAL_OR_TAX = Pattern.compile(
            "(?i)\\b(sub\\s*total|subtotal|tax|tip|gratuity|suggested)\\b");
    private static final Pattern TIP_LINE = Pattern.compile("(?i)\\b(tip|gratuity)\\b");
    private static final Pattern SUGGESTED_TIP_LINE = Pattern.compile(
            "(?i)\\b(suggested|recommend|guide)\\b.*\\b(tip|gratuity)\\b|\\b(tip|gratuity)\\b.*\\d+\\s*%");
    private static final Pattern SUBTOTAL_LINE = Pattern.compile("(?i)\\b(sub\\s*total|subtotal)\\b");
    private static final Pattern TAX_LINE = Pattern.compile("(?i)\\btax\\b");
    private static final Pattern TAX_ID_LINE = Pattern.compile("(?i)\\btax\\s*id\\b");
    private static final Pattern GAS_GALLON = Pattern.compile("(?i)\\b(gal|gallon|/\\s*gal)\\b");
    private static final Pattern PHONE = Pattern.compile("\\d{3}[-.\\s]?\\d{3}[-.\\s]?\\d{4}");
    private static final Pattern HTTP_OR_WWW = Pattern.compile("(?i)https?://|www\\.");
    private static final Pattern EMAIL = Pattern.compile("@\\S+");
    private static final Pattern THANK_YOU = Pattern.compile("(?i)thank\\s+you");
    private static final Pattern GUEST_OR_TABLE = Pattern.compile("(?i)guest\\s*check|table\\s*#?|server\\s*[:#]");
    private static final Pattern STREET_START = Pattern.compile("^\\d{1,5}\\s+[A-Za-z]");
    private static final Pattern LINE_ZIP_ONLY = Pattern.compile("(?i)^\\d{5}(-\\d{4})?\\s*$");

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
            int mon = Integer.parseInt(us.group(1));
            int d = Integer.parseInt(us.group(2));
            int y = Integer.parseInt(us.group(3));
            if (y < 100) {
                y += 2000;
            }
            return String.format(Locale.US, "%04d-%02d-%02d", y, mon, d);
        }
        return null;
    }

    @Nullable
    private static String extractMerchant(List<String> lines) {
        int limit = Math.min(lines.size(), 10);
        int bestScore = Integer.MIN_VALUE;
        int bestIndex = -1;
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
            if (isJunkMerchantLine(line)) {
                continue;
            }
            int s = scoreMerchantLine(line);
            if (s > bestScore) {
                bestScore = s;
                bestIndex = i;
            }
        }
        if (bestIndex < 0) {
            return null;
        }
        return normalizeMerchantDisplay(lines.get(bestIndex));
    }

    private static boolean isJunkMerchantLine(String line) {
        String t = line.trim();
        if (HTTP_OR_WWW.matcher(t).find() || EMAIL.matcher(t).find()) {
            return true;
        }
        if (THANK_YOU.matcher(t).find() || GUEST_OR_TABLE.matcher(t).find()) {
            return true;
        }
        if (PHONE.matcher(t).find()) {
            return true;
        }
        if (LINE_ZIP_ONLY.matcher(t).find()) {
            return true;
        }
        if (STREET_START.matcher(t).find()) {
            return true;
        }
        return false;
    }

    private static int scoreMerchantLine(String line) {
        int letters = 0;
        int digits = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (Character.isLetter(c)) {
                letters++;
            } else if (Character.isDigit(c)) {
                digits++;
            }
        }
        if (letters < 2) {
            return -1;
        }
        int len = line.length();
        if (len > 60) {
            return -1;
        }
        int s = letters * 2 - digits;
        if (len >= 4 && len <= 40) {
            s += 5;
        }
        return s;
    }

    static String normalizeMerchantDisplay(String line) {
        if (line == null) {
            return null;
        }
        String t = line.trim().replaceAll("\\s+", " ");
        if (t.isEmpty()) {
            return t;
        }
        if (!looksAllCapsish(t)) {
            return t;
        }
        String[] words = t.split("\\s+");
        StringBuilder b = new StringBuilder();
        for (int w = 0; w < words.length; w++) {
            if (w > 0) {
                b.append(' ');
            }
            b.append(titleCaseToken(words[w]));
        }
        return b.toString();
    }

    private static boolean looksAllCapsish(String t) {
        int letters = 0;
        int uppers = 0;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (Character.isLetter(c)) {
                letters++;
                if (Character.isUpperCase(c)) {
                    uppers++;
                }
            }
        }
        if (letters < 2) {
            return false;
        }
        return (uppers * 1.0f / letters) > 0.7f;
    }

    private static String titleCaseToken(String w) {
        if (w.isEmpty()) {
            return w;
        }
        if (!w.chars().anyMatch(Character::isLetter)) {
            return w;
        }
        String low = w.toLowerCase(Locale.US);
        return Character.toUpperCase(low.charAt(0)) + low.substring(1);
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
        if (lines.isEmpty()) {
            return new AmountPick(null, 0.2f);
        }

        if (mode == ReceiptCaptureMode.RESTAURANT || mode == ReceiptCaptureMode.RECEIPT) {
            AmountPick labeled = pickLabeledTotalFromBottom(lines, mode);
            AmountPick withTip = pickTotalIncludingTip(lines, mode);
            if (labeled.amount != null && withTip.amount != null) {
                if (withTip.amount > labeled.amount + 0.009d) {
                    return withTip;
                }
                return labeled;
            }
            if (withTip.amount != null) {
                return withTip;
            }
            if (labeled.amount != null) {
                return labeled;
            }
        }

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

        if (mode == ReceiptCaptureMode.GAS || mode == ReceiptCaptureMode.AUTO) {
            double max = Collections.max(candidates);
            return new AmountPick(max, 0.55f);
        }

        // Restaurant / receipt: prefer largest remaining candidate
        double max = Collections.max(candidates);
        return new AmountPick(max, 0.5f);
    }

  /** Strong / weak total labels scanned from the bottom (final charge is usually last). */
    private static AmountPick pickLabeledTotalFromBottom(List<String> lines, ReceiptCaptureMode mode) {
        for (int i = lines.size() - 1; i >= 0; i--) {
            String line = lines.get(i);
            if (mode == ReceiptCaptureMode.RESTAURANT
                    && isSkippableRestaurantLineForTotalLabel(line)
                    && !TOTAL_LINE_STRONG.matcher(line).find()
                    && !TOTAL_LABEL.matcher(line).find()) {
                continue;
            }
            if (TOTAL_LINE_STRONG.matcher(line).find()) {
                Double v = maxMoneyOnLine(line);
                if (v != null && v > 0) {
                    return new AmountPick(v, 0.92f);
                }
            }
        }
        for (int i = lines.size() - 1; i >= 0; i--) {
            String line = lines.get(i);
            if (mode == ReceiptCaptureMode.RESTAURANT
                    && isSkippableRestaurantLineForTotalLabel(line)
                    && !TOTAL_LABEL.matcher(line).find()) {
                continue;
            }
            if (TOTAL_LABEL.matcher(line).find()) {
                Double v = maxMoneyOnLine(line);
                if (v != null && v > 0) {
                    return new AmountPick(v, 0.88f);
                }
            }
        }
        return new AmountPick(null, 0.2f);
    }

    /**
     * When OCR finds an explicit tip/gratuity amount, prefer the amount actually spent
     * (pre-tip total + tip, or subtotal + tax + tip) over an earlier pre-tip "Total" line.
     */
    @Nullable
    private static AmountPick pickTotalIncludingTip(List<String> lines, ReceiptCaptureMode mode) {
        if (mode != ReceiptCaptureMode.RESTAURANT && mode != ReceiptCaptureMode.RECEIPT) {
            return new AmountPick(null, 0.2f);
        }

        Double subtotal = null;
        Double tax = null;
        Double tip = null;
        int tipLineIndex = -1;
        Double lastTotalLabeled = null;
        int lastTotalLineIndex = -1;
        Double amountDue = null;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (isSuggestedTipLine(line)) {
                continue;
            }
            Double money = maxMoneyOnLine(line);
            if (money == null || money <= 0) {
                continue;
            }

            if (TOTAL_LINE_STRONG.matcher(line).find()) {
                amountDue = money;
            }
            if (SUBTOTAL_LINE.matcher(line).find()) {
                subtotal = money;
            }
            if (TAX_LINE.matcher(line).find() && !TAX_ID_LINE.matcher(line).find() && !TIP_LINE.matcher(line).find()) {
                tax = money;
            }
            if (isExplicitTipLine(line)) {
                tip = money;
                tipLineIndex = i;
            }
            if (TOTAL_LABEL.matcher(line).find() && !isExplicitTipLine(line)) {
                lastTotalLabeled = money;
                lastTotalLineIndex = i;
            }
        }

        if (amountDue != null) {
            return new AmountPick(amountDue, 0.93f);
        }

        if (lastTotalLabeled != null && tip != null && tipLineIndex > lastTotalLineIndex) {
            double combined = roundMoney(lastTotalLabeled + tip);
            return new AmountPick(combined, 0.91f);
        }

        if (subtotal != null && tip != null) {
            double taxAmount = tax != null ? tax : 0d;
            double foodAndTax = roundMoney(subtotal + taxAmount);
            double withTip = roundMoney(foodAndTax + tip);
            if (lastTotalLabeled != null && amountsClose(lastTotalLabeled, foodAndTax)) {
                return new AmountPick(withTip, 0.9f);
            }
            if (lastTotalLabeled == null || withTip >= lastTotalLabeled) {
                return new AmountPick(withTip, 0.89f);
            }
        }

        if (lastTotalLabeled != null && tip != null) {
            double combined = roundMoney(lastTotalLabeled + tip);
            if (combined > lastTotalLabeled + 0.009d) {
                return new AmountPick(combined, 0.87f);
            }
        }

        return new AmountPick(null, 0.2f);
    }

    private static boolean isSkippableRestaurantLineForTotalLabel(String line) {
        return SUBTOTAL_OR_TAX.matcher(line).find();
    }

    private static boolean isSuggestedTipLine(String line) {
        return SUGGESTED_TIP_LINE.matcher(line).find();
    }

    /** Tip/gratuity line with an amount (not a final "total" line). */
    private static boolean isExplicitTipLine(String line) {
        return TIP_LINE.matcher(line).find()
                && !TOTAL_LABEL.matcher(line).find()
                && !isSuggestedTipLine(line);
    }

    private static boolean amountsClose(double a, double b) {
        return Math.abs(a - b) < 0.02d;
    }

    private static double roundMoney(double value) {
        return Math.round(value * 100d) / 100d;
    }

    @Nullable
    private static Double maxMoneyOnLine(String line) {
        Matcher m = MONEY.matcher(line);
        double best = -1;
        while (m.find()) {
            Double v = parseMoneyGroup(m);
            if (v != null && v > best) {
                best = v;
            }
        }
        return best > 0 ? best : null;
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

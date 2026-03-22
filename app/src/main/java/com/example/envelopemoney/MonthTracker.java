package com.example.envelopemoney;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;

public class MonthTracker {
    private static final String CURRENT_MONTH_KEY = "current_month";
    private static final String MONTH_FORMAT = "yyyy-MM";
    private static final Pattern MONTH_PATTERN = Pattern.compile("^\\d{4}-\\d{2}$");

    public static String getStoredMonthOrNull(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        return normalizeMonth(prefs.getString(CURRENT_MONTH_KEY, null));
    }

    public static String getCurrentMonth(Context context) {
        String stored = getStoredMonthOrNull(context);
        if (stored != null) return stored;
        return getRealCurrentMonth();
    }

    public static void setCurrentMonth(Context context, String month) {
        SharedPreferences.Editor editor = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit();
        editor.putString(CURRENT_MONTH_KEY, normalizeMonth(month));
        editor.apply();
    }

    public static String formatMonth(Date date) {
        return new SimpleDateFormat(MONTH_FORMAT, Locale.getDefault()).format(date);
    }

    public static String getRealCurrentMonth() {
        return formatMonth(new Date());
    }

    public static String normalizeMonth(String month) {
        if (month == null) {
            return null;
        }
        String trimmed = month.trim();
        if (!MONTH_PATTERN.matcher(trimmed).matches()) {
            return null;
        }
        return trimmed;
    }

    public static boolean shouldRollover(String storedMonth, String actualMonth) {
        String normalizedActual = normalizeMonth(actualMonth);
        if (normalizedActual == null) {
            normalizedActual = getRealCurrentMonth();
        }
        String normalizedStored = normalizeMonth(storedMonth);
        return normalizedStored == null || !normalizedStored.equals(normalizedActual);
    }

    public static boolean isNewMonth(Context context) {
        return shouldRollover(getStoredMonthOrNull(context), getRealCurrentMonth());
    }
    public static boolean isFirstMonth(Context context) {
        return getStoredMonthOrNull(context) == null;
    }
}

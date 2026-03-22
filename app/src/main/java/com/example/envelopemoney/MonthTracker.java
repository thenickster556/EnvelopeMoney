package com.example.envelopemoney;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MonthTracker {
    private static final String CURRENT_MONTH_KEY = "current_month";
    private static final String MONTH_FORMAT = "yyyy-MM";

    public static String getCurrentMonth(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        String stored = prefs.getString(CURRENT_MONTH_KEY, "");
        if (!stored.isEmpty()) return stored;
        return new SimpleDateFormat(MONTH_FORMAT, Locale.getDefault()).format(new Date());
    }

    public static void setCurrentMonth(Context context, String month) {
        SharedPreferences.Editor editor = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit();
        editor.putString(CURRENT_MONTH_KEY, month);
        editor.apply();
    }

    public static String formatMonth(Date date) {
        return new SimpleDateFormat(MONTH_FORMAT, Locale.getDefault()).format(date);
    }

    public static boolean isNewMonth(Context context) {
        String current = formatMonth(new Date());
        return !current.equals(getCurrentMonth(context));
    }
    public static boolean isFirstMonth(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        return !prefs.contains(CURRENT_MONTH_KEY);
    }
}
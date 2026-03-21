package com.example.envelopemoney;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class PrefManager {
    private static final String PREFS_NAME = "envelope_prefs";
    private static final String ENVELOPES_KEY = "envelopes";
    private static final String ENVELOPES_COLLAPSED_KEY = "envelopes_collapsed";
    private static final String LAST_ADD_TRANSACTION_ENVELOPE_KEY = "last_add_transaction_envelope";
    private static final String LAST_ADD_TRANSFER_DESTINATION_PREFIX = "last_add_transfer_destination_";
    private static final String LAST_TRANSFER_TOTALS_OPTION_KEY = "last_transfer_totals_option";
    private String name;
    private double limit;
    private double remaining;
    @SerializedName("amount")
    private double amount;
    @SerializedName("date")
    private String date;
    @SerializedName("comment")
    private String comment;


    public static void saveEnvelopes(Context context, List<Envelope> envelopes) {
        Gson gson = new Gson();
        String json = gson.toJson(envelopes);
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.putString(ENVELOPES_KEY, json);
        editor.apply();
    }

    public static List<Envelope> getEnvelopes(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(ENVELOPES_KEY, null);
        if (json == null) return createDefaultEnvelopes(context);

        Type type = new TypeToken<ArrayList<Envelope>>(){}.getType();
        return new Gson().fromJson(json, (java.lang.reflect.Type) type);
    }

    public static void setEnvelopesCollapsed(Context context, boolean collapsed) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.putBoolean(ENVELOPES_COLLAPSED_KEY, collapsed);
        editor.apply();
    }

    public static boolean isEnvelopesCollapsed(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(ENVELOPES_COLLAPSED_KEY, false);
    }

    public static void setLastAddTransactionEnvelope(Context context, String envelopeName) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.putString(LAST_ADD_TRANSACTION_ENVELOPE_KEY, envelopeName);
        editor.apply();
    }

    public static String getLastAddTransactionEnvelope(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(LAST_ADD_TRANSACTION_ENVELOPE_KEY, null);
    }

    public static void setLastAddTransferDestination(Context context, String sourceEnvelopeName, String destinationEnvelopeName) {
        if (sourceEnvelopeName == null || sourceEnvelopeName.isEmpty()) {
            return;
        }
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.putString(LAST_ADD_TRANSFER_DESTINATION_PREFIX + sourceEnvelopeName, destinationEnvelopeName);
        editor.apply();
    }

    public static String getLastAddTransferDestination(Context context, String sourceEnvelopeName) {
        if (sourceEnvelopeName == null || sourceEnvelopeName.isEmpty()) {
            return null;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(LAST_ADD_TRANSFER_DESTINATION_PREFIX + sourceEnvelopeName, null);
    }

    public static void setLastTransferTotalsOptionKey(Context context, String optionKey) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.putString(LAST_TRANSFER_TOTALS_OPTION_KEY, optionKey);
        editor.apply();
    }

    public static String getLastTransferTotalsOptionKey(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(LAST_TRANSFER_TOTALS_OPTION_KEY, null);
    }

    public static void clearLastTransferTotalsOptionKey(Context context) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.remove(LAST_TRANSFER_TOTALS_OPTION_KEY);
        editor.apply();
    }

    private static List<Envelope> createDefaultEnvelopes(Context context) {
        List<Envelope> defaultEnvelopes = new ArrayList<>();
        defaultEnvelopes.add(new Envelope("Gas", 90));
        defaultEnvelopes.add(new Envelope("Personal", 250));
        defaultEnvelopes.add(new Envelope("Vacation", 300));
        defaultEnvelopes.add(new Envelope("Outreach", 86.5));
        saveEnvelopes(context, defaultEnvelopes);
        return defaultEnvelopes;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setLimit(double limit) {
        this.limit = limit;
    }

    // Add this method to handle limit changes properly
    public void adjustLimit(double newLimit) {
        double spent = this.limit - this.remaining;
        this.limit = newLimit;
        this.remaining = Math.max(newLimit - spent, 0);
    }
}

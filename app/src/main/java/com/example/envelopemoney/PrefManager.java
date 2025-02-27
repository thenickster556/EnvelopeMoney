package com.example.envelopemoney;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class PrefManager {
    private static final String PREFS_NAME = "envelope_prefs";
    private static final String ENVELOPES_KEY = "envelopes";
    private String name;
    private double limit;
    private double remaining;

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

    private static List<Envelope> createDefaultEnvelopes(Context context) {
        List<Envelope> defaultEnvelopes = new ArrayList<>();
        defaultEnvelopes.add(new Envelope("Emergency Fund", 500));
        defaultEnvelopes.add(new Envelope("Education Fund", 300));
        defaultEnvelopes.add(new Envelope("Vacation Fund", 200));
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

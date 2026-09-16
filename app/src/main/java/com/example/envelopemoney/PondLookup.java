package com.example.envelopemoney;

import androidx.annotation.Nullable;

import java.util.List;

/**
 * Resolves pond names against the live pond list using trimmed equality.
 */
public final class PondLookup {

    private PondLookup() {
    }

    @Nullable
    public static Envelope findByName(@Nullable List<Envelope> envelopes, @Nullable String name) {
        if (envelopes == null || name == null) {
            return null;
        }
        String normalized = normalize(name);
        if (normalized.isEmpty()) {
            return null;
        }
        for (Envelope envelope : envelopes) {
            if (envelope == null) {
                continue;
            }
            String envName = envelope.getName();
            if (envName != null && normalize(envName).equals(normalized)) {
                return envelope;
            }
        }
        return null;
    }

    @Nullable
    public static String canonicalName(@Nullable List<Envelope> envelopes, @Nullable String name) {
        Envelope envelope = findByName(envelopes, name);
        return envelope != null ? envelope.getName() : null;
    }

    private static String normalize(String value) {
        return value.trim();
    }
}

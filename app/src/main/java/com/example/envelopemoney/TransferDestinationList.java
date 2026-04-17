package com.example.envelopemoney;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the transfer-destination pond list: every pond except the source (you cannot transfer to the same pond).
 */
public final class TransferDestinationList {

    private TransferDestinationList() {
    }

    /**
     * @param envelopes         current pond list (order preserved)
     * @param sourceEnvelopeName selected source pond name (transfer origin)
     * @return names of destination ponds, excluding {@code sourceEnvelopeName} (trimmed equality)
     */
    public static List<String> excludingSource(List<Envelope> envelopes, String sourceEnvelopeName) {
        String srcNorm = normalize(sourceEnvelopeName);
        List<String> out = new ArrayList<>();
        if (envelopes == null) {
            return out;
        }
        for (Envelope env : envelopes) {
            if (env == null) {
                continue;
            }
            String name = env.getName();
            if (name == null) {
                continue;
            }
            if (!normalize(name).equals(srcNorm)) {
                out.add(name);
            }
        }
        return out;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim();
    }
}

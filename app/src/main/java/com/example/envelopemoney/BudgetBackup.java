package com.example.envelopemoney;

import com.example.envelopemoney.receipt.ReceiptReferenceResolver;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Ledger-only budget file shared with web/domain/budgetBackup.js.
 * Photos, passwords, and hashes are rejected. A bad file must not be applied.
 */
public final class BudgetBackup {
    public static final String KIND = "mountain-money-budget";
    public static final int VERSION = 1;
    public static final int MAX_CHARS = 2_000_000;

    private static final Gson GSON = new Gson();
    private static final Set<String> FORBIDDEN_KEYS = new HashSet<>(Arrays.asList(
            "password", "passwordhash", "photobase64", "imagebase64", "jpegbase64"));

    private BudgetBackup() {
    }

    public static final class ParseResult {
        public final boolean ok;
        public final String error;
        public final String currentMonth;
        public final JsonArray envelopes;
        public final List<Integer> billsDays;
        public final List<Integer> paydays;
        public final boolean billsFilterActive;
        public final String billsFilterSavedStartDisplay;
        public final String billsFilterSavedEndDisplay;
        public final boolean learningPresent;
        public final List<String> comments;
        public final float[] ocrWeights;

        private ParseResult(boolean ok, String error, String currentMonth, JsonArray envelopes,
                            List<Integer> billsDays, List<Integer> paydays, boolean billsFilterActive,
                            String billsFilterSavedStartDisplay, String billsFilterSavedEndDisplay,
                            boolean learningPresent, List<String> comments, float[] ocrWeights) {
            this.ok = ok;
            this.error = error;
            this.currentMonth = currentMonth;
            this.envelopes = envelopes;
            this.billsDays = billsDays;
            this.paydays = paydays;
            this.billsFilterActive = billsFilterActive;
            this.billsFilterSavedStartDisplay = billsFilterSavedStartDisplay;
            this.billsFilterSavedEndDisplay = billsFilterSavedEndDisplay;
            this.learningPresent = learningPresent;
            this.comments = comments;
            this.ocrWeights = ocrWeights;
        }
    }

    public static ParseResult parse(String text) {
        if (text == null) return failure("not-json");
        if (text.length() > MAX_CHARS) return failure("too-large");
        JsonElement rootElement;
        try {
            rootElement = GSON.fromJson(text, JsonElement.class);
        } catch (RuntimeException invalid) {
            return failure("not-json");
        }
        if (rootElement == null || !rootElement.isJsonObject()) return failure("not-json");
        if (containsForbidden(rootElement)) return failure("forbidden");
        JsonObject root = rootElement.getAsJsonObject();
        if (!KIND.equals(stringOrNull(root, "kind"))) return failure("wrong-kind");
        JsonElement versionElement = root.get("version");
        if (versionElement == null || !versionElement.isJsonPrimitive()
                || !versionElement.getAsJsonPrimitive().isNumber()) {
            return failure("bad-version");
        }
        double version = versionElement.getAsDouble();
        if (version > VERSION) return failure("newer-version");
        if (version != VERSION) return failure("bad-version");
        JsonElement envelopesElement = root.get("envelopes");
        if (envelopesElement == null || !envelopesElement.isJsonArray()) return failure("missing-envelopes");

        JsonArray envelopes = envelopesElement.getAsJsonArray().deepCopy();
        fillReceiptFileNames(envelopes);
        boolean learningPresent = false;
        List<String> comments = new ArrayList<>();
        float[] weights = null;
        JsonElement learning = root.get("learning");
        if (learning != null && learning.isJsonObject()) {
            learningPresent = true;
            comments = commentList(learning.getAsJsonObject().get("comments"));
            weights = weightList(learning.getAsJsonObject().get("ocrWeights"));
        }
        return new ParseResult(true, null, stringOrNull(root, "currentMonth"), envelopes,
                intList(root.get("billsDays")), intList(root.get("paydays")),
                bool(root.get("billsFilterActive")),
                stringOrNull(root, "billsFilterSavedStartDisplay"),
                stringOrNull(root, "billsFilterSavedEndDisplay"),
                learningPresent, comments, weights);
    }

    public static String buildSnapshot(String currentMonth, JsonArray envelopes, List<Integer> billsDays,
                                   List<Integer> paydays, boolean billsFilterActive, String savedStart,
                                   String savedEnd, List<String> comments, float[] weights) {
        ParseResult snapshot = new ParseResult(true, null, currentMonth,
                envelopes == null ? new JsonArray() : envelopes,
                billsDays == null ? new ArrayList<Integer>() : billsDays,
                paydays == null ? new ArrayList<Integer>() : paydays,
                billsFilterActive, savedStart, savedEnd, true,
                comments == null ? new ArrayList<String>() : comments, weights);
        return build(snapshot);
    }

    public static String build(ParseResult snapshot) {
        if (snapshot == null || !snapshot.ok) {
            throw new IllegalArgumentException("backup snapshot is not valid");
        }
        JsonArray envelopes = snapshot.envelopes == null
                ? new JsonArray() : snapshot.envelopes.deepCopy();
        fillReceiptFileNames(envelopes);
        JsonObject document = new JsonObject();
        document.addProperty("kind", KIND);
        document.addProperty("version", VERSION);
        addNullableString(document, "currentMonth", snapshot.currentMonth);
        document.add("billsDays", GSON.toJsonTree(snapshot.billsDays));
        document.add("paydays", GSON.toJsonTree(snapshot.paydays));
        document.addProperty("billsFilterActive", snapshot.billsFilterActive);
        addNullableString(document, "billsFilterSavedStartDisplay", snapshot.billsFilterSavedStartDisplay);
        addNullableString(document, "billsFilterSavedEndDisplay", snapshot.billsFilterSavedEndDisplay);
        document.add("envelopes", envelopes);
        if (snapshot.learningPresent) {
            JsonObject learning = new JsonObject();
            learning.add("comments", GSON.toJsonTree(snapshot.comments));
            if (snapshot.ocrWeights == null) {
                learning.add("ocrWeights", JsonNull.INSTANCE);
            } else {
                JsonArray weights = new JsonArray();
                for (float weight : snapshot.ocrWeights) weights.add(weight);
                learning.add("ocrWeights", weights);
            }
            document.add("learning", learning);
        }
        return GSON.toJson(document);
    }

    /** Parse, write, and parse again so a filename filled from a URI fragment survives. */
    public static JsonObject summarize(JsonObject document) {
        ParseResult first = parse(GSON.toJson(document));
        ParseResult parsed = first.ok ? parse(build(first)) : first;
        return summary(parsed);
    }

    private static ParseResult failure(String error) {
        return new ParseResult(false, error, null, new JsonArray(), new ArrayList<Integer>(),
                new ArrayList<Integer>(), false, null, null, false, new ArrayList<String>(), null);
    }

    private static JsonObject summary(ParseResult parsed) {
        JsonObject envelope = null;
        JsonObject tx = null;
        if (parsed.ok && parsed.envelopes != null && parsed.envelopes.size() > 0
                && parsed.envelopes.get(0).isJsonObject()) {
            envelope = parsed.envelopes.get(0).getAsJsonObject();
            JsonElement txs = envelope.get("transactions");
            if (txs != null && txs.isJsonArray() && txs.getAsJsonArray().size() > 0
                    && txs.getAsJsonArray().get(0).isJsonObject()) {
                tx = txs.getAsJsonArray().get(0).getAsJsonObject();
            }
        }
        JsonObject summary = new JsonObject();
        summary.addProperty("ok", parsed.ok);
        if (parsed.error == null) summary.add("error", JsonNull.INSTANCE);
        else summary.addProperty("error", parsed.error);
        addNullableString(summary, "currentMonth", parsed.ok ? parsed.currentMonth : null);
        addNullableString(summary, "envelopeName", envelope == null ? null : stringOrNull(envelope, "name"));
        if (tx != null && tx.has("amount") && tx.get("amount").isJsonPrimitive()
                && tx.get("amount").getAsJsonPrimitive().isNumber()) {
            summary.addProperty("amount", tx.get("amount").getAsDouble());
        } else {
            summary.add("amount", JsonNull.INSTANCE);
        }
        addNullableString(summary, "transferId", tx == null ? null : stringOrNull(tx, "transferId"));
        addNullableString(summary, "splitPurchaseGroupId",
                tx == null ? null : stringOrNull(tx, "splitPurchaseGroupId"));
        addNullableString(summary, "receiptImageFileName",
                tx == null ? null : stringOrNull(tx, "receiptImageFileName"));
        summary.addProperty("learningPresent", parsed.ok && parsed.learningPresent);
        addNullableString(summary, "comment",
                parsed.ok && parsed.comments != null && !parsed.comments.isEmpty()
                        ? parsed.comments.get(0) : null);
        if (parsed.ok && parsed.ocrWeights != null && parsed.ocrWeights.length > 0) {
            summary.addProperty("weight0", parsed.ocrWeights[0]);
        } else {
            summary.add("weight0", JsonNull.INSTANCE);
        }
        if (parsed.ok && parsed.billsDays != null && !parsed.billsDays.isEmpty()) {
            summary.addProperty("billsDay0", parsed.billsDays.get(0));
        } else {
            summary.add("billsDay0", JsonNull.INSTANCE);
        }
        return summary;
    }

    private static void fillReceiptFileNames(JsonArray envelopes) {
        if (envelopes == null) return;
        for (JsonElement envelopeElement : envelopes) {
            if (!envelopeElement.isJsonObject()) continue;
            JsonObject envelope = envelopeElement.getAsJsonObject();
            fillTransactions(envelope.get("transactions"));
            JsonElement monthly = envelope.get("monthlyData");
            if (monthly == null || !monthly.isJsonObject()) continue;
            for (Map.Entry<String, JsonElement> month : monthly.getAsJsonObject().entrySet()) {
                if (month.getValue() != null && month.getValue().isJsonObject()) {
                    fillTransactions(month.getValue().getAsJsonObject().get("transactions"));
                }
            }
        }
    }

    private static void fillTransactions(JsonElement transactions) {
        if (transactions == null || !transactions.isJsonArray()) return;
        for (JsonElement txElement : transactions.getAsJsonArray()) {
            if (!txElement.isJsonObject()) continue;
            JsonObject tx = txElement.getAsJsonObject();
            String existing = stringOrNull(tx, "receiptImageFileName");
            if (existing != null && !existing.trim().isEmpty()) continue;
            String name = ReceiptReferenceResolver.fileNameFromReference(stringOrNull(tx, "receiptImageUri"));
            if (name != null) tx.addProperty("receiptImageFileName", name);
        }
    }

    private static boolean containsForbidden(JsonElement element) {
        if (element == null || element.isJsonNull()) return false;
        if (element.isJsonPrimitive()) {
            return element.getAsJsonPrimitive().isString()
                    && element.getAsString().toLowerCase(Locale.US).startsWith("data:image");
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                if (containsForbidden(child)) return true;
            }
            return false;
        }
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                if (FORBIDDEN_KEYS.contains(entry.getKey().toLowerCase(Locale.US))) return true;
                if (containsForbidden(entry.getValue())) return true;
            }
        }
        return false;
    }

    private static List<Integer> intList(JsonElement element) {
        List<Integer> days = new ArrayList<>();
        if (element == null || !element.isJsonArray()) return days;
        for (JsonElement item : element.getAsJsonArray()) {
            if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isNumber()) {
                double value = item.getAsDouble();
                if (value == Math.rint(value)) days.add((int) value);
            }
        }
        return days;
    }

    private static List<String> commentList(JsonElement element) {
        List<String> comments = new ArrayList<>();
        if (element == null || !element.isJsonArray()) return comments;
        for (JsonElement item : element.getAsJsonArray()) {
            if (comments.size() >= 50) break;
            if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) continue;
            String text = item.getAsString().trim();
            if (!text.isEmpty()) comments.add(text);
        }
        return comments;
    }

    private static float[] weightList(JsonElement element) {
        if (element == null || !element.isJsonArray() || element.getAsJsonArray().size() != 5) return null;
        float[] weights = new float[5];
        for (int index = 0; index < 5; index++) {
            JsonElement item = element.getAsJsonArray().get(index);
            if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isNumber()) return null;
            weights[index] = item.getAsFloat();
        }
        return weights;
    }

    private static boolean bool(JsonElement element) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()
                && element.getAsBoolean();
    }

    private static String stringOrNull(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            return null;
        }
        return value.getAsString();
    }

    private static void addNullableString(JsonObject object, String key, String value) {
        if (value == null) object.add(key, JsonNull.INSTANCE);
        else object.addProperty(key, value);
    }
}

package com.example.envelopemoney.parity;

import com.example.envelopemoney.BudgetBackup;
import com.example.envelopemoney.MoneyMath;
import com.example.envelopemoney.receipt.OcrLine;
import com.example.envelopemoney.receipt.OcrResult;
import com.example.envelopemoney.receipt.ReceiptCaptureMode;
import com.example.envelopemoney.receipt.ReceiptDraft;
import com.example.envelopemoney.receipt.ReceiptFieldParser;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.Test;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Android half of the cross-platform parity tether: every case in {@code shared/fixtures/} must
 * produce the same numbers here as the web port does in {@code web/test/sharedParity.test.js}.
 * Seeded from cases already green in both suites; a fixture naming an unknown helper or function
 * fails loudly so a typo can never pass silently. See the parity matrix in docs/WEB_DEMO.md.
 */
public class SharedParityFixturesTest {
    private static final Gson GSON = new Gson();

    @Test
    public void sharedFixtureCasesMatchAndroidBehavior() throws IOException {
        List<Path> fixtures = fixtureFiles();
        assertTrue("No parity fixtures found in shared/fixtures — the tether cannot be empty",
                !fixtures.isEmpty());
        for (Path fixture : fixtures) {
            runFixture(fixture);
        }
    }

    private static List<Path> fixtureFiles() throws IOException {
        // Gradle runs unit tests with the module directory as the working directory.
        List<Path> candidates = Arrays.asList(
                Paths.get("../shared/fixtures"), Paths.get("shared/fixtures"));
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) {
                try (Stream<Path> files = Files.list(candidate)) {
                    return files.filter(path -> path.toString().endsWith(".fixtures.json"))
                            .sorted().collect(Collectors.toList());
                }
            }
        }
        fail("shared/fixtures directory not found relative to " + new File(".").getAbsolutePath());
        return Collections.emptyList();
    }

    private static void runFixture(Path fixture) throws IOException {
        JsonObject root;
        try (FileReader reader = new FileReader(fixture.toFile())) {
            root = GSON.fromJson(reader, JsonObject.class);
        }
        String helper = stringOrNull(root, "helper");
        JsonArray cases = root.getAsJsonArray("cases");
        assertNotNull("fixture without cases: " + fixture, cases);
        for (JsonElement element : cases) {
            JsonObject parityCase = element.getAsJsonObject();
            String name = stringOrNull(parityCase, "name");
            String function = stringOrNull(parityCase, "function");
            assertNotNull("case without a name in " + fixture, name);
            JsonElement args = parityCase.get("args");
            JsonElement expected = parityCase.get("expected");
            String where = fixture.getFileName() + "/" + name;
            if ("MoneyMath".equals(helper)) {
                assertMoneyMath(where, function, args.getAsJsonArray(), expected);
            } else if ("ReceiptFieldParser".equals(helper)) {
                assertReceiptFieldParser(where, function, args.getAsJsonObject(), expected);
            } else if ("BudgetBackup".equals(helper)) {
                assertBudgetBackup(where, function, args.getAsJsonObject(), expected);
            } else {
                fail("Unknown parity helper '" + helper + "' in " + where);
            }
        }
    }

    private static void assertMoneyMath(String where, String function, JsonArray args, JsonElement expected) {
        if ("roundToCents".equals(function)) {
            double actual = MoneyMath.roundToCents(args.get(0).getAsDouble());
            assertEquals(where, expected.getAsDouble(), actual, 0.000001);
        } else if ("splitIntegerPercentsFirstCeiling".equals(function)) {
            int[] actual = MoneyMath.splitIntegerPercentsFirstCeiling(args.get(0).getAsInt());
            assertArrayEquals(where, ints(expected.getAsJsonArray()), actual);
        } else if ("splitTotalByPercents".equals(function)) {
            int[] percents = ints(args.get(1).getAsJsonArray());
            double[] actual = MoneyMath.splitTotalByPercents(args.get(0).getAsDouble(), percents);
            double[] expectedAmounts = doubles(expected.getAsJsonArray());
            assertEquals(where, expectedAmounts.length, actual.length);
            for (int index = 0; index < expectedAmounts.length; index++) {
                assertEquals(where, expectedAmounts[index], actual[index], 0.000001);
            }
        } else {
            fail("Unknown MoneyMath function '" + function + "' in " + where);
        }
    }

    private static void assertBudgetBackup(String where, String function, JsonObject namedArgs,
                                            JsonElement expected) {
        if (!"summarize".equals(function)) {
            fail("Unknown BudgetBackup function '" + function + "' in " + where);
        }
        JsonObject actual = BudgetBackup.summarize(namedArgs.getAsJsonObject("document"));
        JsonObject expectedFields = expected.getAsJsonObject();
        for (java.util.Map.Entry<String, JsonElement> entry : expectedFields.entrySet()) {
            JsonElement got = actual.get(entry.getKey());
            JsonElement want = entry.getValue();
            String field = where + " " + entry.getKey();
            if (want == null || want.isJsonNull()) {
                assertTrue(field, got == null || got.isJsonNull());
            } else if (want.getAsJsonPrimitive().isBoolean()) {
                assertEquals(field, want.getAsBoolean(), got.getAsBoolean());
            } else if (want.getAsJsonPrimitive().isNumber()) {
                assertEquals(field, want.getAsDouble(), got.getAsDouble(), 0.000001);
            } else {
                assertEquals(field, want.getAsString(), got.getAsString());
            }
        }
    }

    private static void assertReceiptFieldParser(String where, String function, JsonObject namedArgs,
                                                  JsonElement expected) {
        if (!"parse".equals(function)) {
            fail("Unknown ReceiptFieldParser function '" + function + "' in " + where);
        }
        List<OcrLine> lines = new ArrayList<>();
        for (JsonElement line : namedArgs.getAsJsonArray("lines")) {
            lines.add(new OcrLine(line.getAsString(), 0.9f));
        }
        ReceiptCaptureMode mode = ReceiptCaptureMode.valueOf(namedArgs.get("mode").getAsString());
        ReceiptDraft draft = ReceiptFieldParser.parse(new OcrResult(lines), mode);
        JsonObject expectedFields = expected.getAsJsonObject();
        // Only listed fields are compared so parser additions do not break parity.
        if (expectedFields.has("totalAmount")) {
            assertEquals(where, expectedFields.get("totalAmount").getAsDouble(),
                    draft.totalAmount == null ? 0d : draft.totalAmount, 0.000001);
        }
        if (expectedFields.has("merchantForComment")) {
            assertEquals(where, stringOrNull(expectedFields, "merchantForComment"),
                    draft.merchantForComment);
        }
        if (expectedFields.has("dateYyyyMmDd")) {
            assertEquals(where, stringOrNull(expectedFields, "dateYyyyMmDd"), draft.dateYyyyMmDd);
        }
    }

    private static int[] ints(JsonArray values) {
        int[] parsed = new int[values.size()];
        for (int index = 0; index < values.size(); index++) {
            parsed[index] = values.get(index).getAsInt();
        }
        return parsed;
    }

    private static double[] doubles(JsonArray values) {
        double[] parsed = new double[values.size()];
        for (int index = 0; index < values.size(); index++) {
            parsed[index] = values.get(index).getAsDouble();
        }
        return parsed;
    }

    private static String stringOrNull(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }
}

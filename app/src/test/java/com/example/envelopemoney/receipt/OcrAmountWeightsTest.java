package com.example.envelopemoney.receipt;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class OcrAmountWeightsTest {

    @Test
    public void defaultsMatchParserConstants() {
        float[] d = OcrAmountWeights.defaults();
        assertEquals(5, d.length);
        assertEquals(50f, d[OcrAmountWeights.DOLLAR_SIGN], 0.001f);
        assertEquals(80f, d[OcrAmountWeights.STRONG_TOTAL_LABEL], 0.001f);
        assertEquals(60f, d[OcrAmountWeights.TOTAL_LABEL], 0.001f);
        assertEquals(25f, d[OcrAmountWeights.BOTTOM_HALF], 0.001f);
        assertEquals(-100f, d[OcrAmountWeights.ORDER_OR_POINTS_PENALTY], 0.001f);
    }

    @Test
    public void littleEndianBlobRoundTrip() {
        float[] original = new float[]{50f, 80f, 60f, 25f, -100f};
        byte[] blob = OcrAmountWeights.toLittleEndianBlob(original);
        assertEquals(20, blob.length);
        assertArrayEquals(original, OcrAmountWeights.fromLittleEndianBlob(blob), 0.001f);
    }

    @Test
    public void corruptBlobReturnsDefaults() {
        float[] defaults = OcrAmountWeights.defaults();
        assertArrayEquals(defaults, OcrAmountWeights.fromLittleEndianBlob(null), 0.001f);
        assertArrayEquals(defaults, OcrAmountWeights.fromLittleEndianBlob(new byte[]{1, 2, 3}), 0.001f);
        assertArrayEquals(defaults, OcrAmountWeights.fromLittleEndianBlob(new byte[19]), 0.001f);
    }
}

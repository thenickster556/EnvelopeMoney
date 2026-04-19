package com.example.envelopemoney.receipt;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ReceiptFieldParserTest {

    @Test
    public void retail_prefersTotalLabeledLine() {
        OcrResult ocr = new OcrResult(Arrays.asList(
                new OcrLine("ACME GROCERY", 0.9f),
                new OcrLine("SUBTOTAL 10.00", 0.9f),
                new OcrLine("TAX 0.80", 0.9f),
                new OcrLine("TOTAL $12.34", 0.9f)
        ));
        ReceiptDraft d = ReceiptFieldParser.parse(ocr, ReceiptCaptureMode.RECEIPT);
        assertEquals("ACME GROCERY", d.merchantForComment);
        assertNotNull(d.totalAmount);
        assertEquals(12.34, d.totalAmount, 0.001);
    }

    @Test
    public void gas_skipsGallonLine() {
        OcrResult ocr = new OcrResult(Arrays.asList(
                new OcrLine("12.345 gal", 0.9f),
                new OcrLine("$45.67", 0.9f)
        ));
        ReceiptDraft d = ReceiptFieldParser.parse(ocr, ReceiptCaptureMode.GAS);
        assertEquals(45.67, d.totalAmount, 0.001);
    }

    @Test
    public void restaurant_usesLargestWhenNoTotalLabel() {
        OcrResult ocr = new OcrResult(Arrays.asList(
                new OcrLine("Bistro", 0.9f),
                new OcrLine("Subtotal 20.00", 0.9f),
                new OcrLine("Tax 1.50", 0.9f),
                new OcrLine("21.50", 0.9f)
        ));
        ReceiptDraft d = ReceiptFieldParser.parse(ocr, ReceiptCaptureMode.RESTAURANT);
        assertEquals("Bistro", d.merchantForComment);
        assertNotNull(d.totalAmount);
        assertEquals(21.50, d.totalAmount, 0.001);
    }

    @Test
    public void commentIsMerchantOnly_noAmountAppended() {
        OcrResult ocr = new OcrResult(Arrays.asList(
                new OcrLine("Joe's Diner", 0.9f),
                new OcrLine("TOTAL 9.99", 0.9f)
        ));
        ReceiptDraft d = ReceiptFieldParser.parse(ocr, ReceiptCaptureMode.RECEIPT);
        assertEquals("Joe's Diner", d.merchantForComment);
        assertEquals(9.99, d.totalAmount, 0.001);
    }
}

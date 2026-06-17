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
        assertEquals("Acme Grocery", d.merchantForComment);
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

    @Test
    public void merchant_skipsPhoneAndPicksStoreLine() {
        OcrResult ocr = new OcrResult(Arrays.asList(
                new OcrLine("800-555-1234", 0.9f),
                new OcrLine("BEST FOODS MARKET", 0.9f),
                new OcrLine("TOTAL 5.00", 0.9f)
        ));
        ReceiptDraft d = ReceiptFieldParser.parse(ocr, ReceiptCaptureMode.RECEIPT);
        assertEquals("Best Foods Market", d.merchantForComment);
    }

    @Test
    public void merchant_skipsStreetLine() {
        OcrResult ocr = new OcrResult(Arrays.asList(
                new OcrLine("123 Main St", 0.9f),
                new OcrLine("CORNER BISTRO", 0.9f),
                new OcrLine("TOTAL 10.00", 0.9f)
        ));
        ReceiptDraft d = ReceiptFieldParser.parse(ocr, ReceiptCaptureMode.RESTAURANT);
        assertEquals("Corner Bistro", d.merchantForComment);
    }

    @Test
    public void restaurant_prefersAmountDueOverEarlierTotal() {
        OcrResult ocr = new OcrResult(Arrays.asList(
                new OcrLine("BISTRO", 0.9f),
                new OcrLine("Subtotal 40.00", 0.9f),
                new OcrLine("Tax 2.00", 0.9f),
                new OcrLine("Tip 8.00", 0.9f),
                new OcrLine("Total 50.00", 0.9f),
                new OcrLine("Amount Due 58.00", 0.9f)
        ));
        ReceiptDraft d = ReceiptFieldParser.parse(ocr, ReceiptCaptureMode.RESTAURANT);
        assertEquals("Bistro", d.merchantForComment);
        assertEquals(58.00, d.totalAmount, 0.001);
    }

    @Test
    public void restaurant_usesLastTotalLabeledLineWhenMultiple() {
        OcrResult ocr = new OcrResult(Arrays.asList(
                new OcrLine("CAFE", 0.9f),
                new OcrLine("Total 45.00", 0.9f),
                new OcrLine("Tip suggested", 0.9f),
                new OcrLine("Total 52.30", 0.9f)
        ));
        ReceiptDraft d = ReceiptFieldParser.parse(ocr, ReceiptCaptureMode.RESTAURANT);
        assertEquals("Cafe", d.merchantForComment);
        assertEquals(52.30, d.totalAmount, 0.001);
    }

    @Test
    public void normalizeMerchantDisplay_titleCaseAllCaps() {
        assertEquals("Joe's Diner", ReceiptFieldParser.normalizeMerchantDisplay("JOE'S DINER"));
    }

    @Test
    public void merchant_skipsHttpUrlLine() {
        OcrResult ocr = new OcrResult(Arrays.asList(
                new OcrLine("https://example.com/receipt", 0.9f),
                new OcrLine("FRESH MARKET", 0.9f),
                new OcrLine("TOTAL $4.00", 0.9f)
        ));
        ReceiptDraft d = ReceiptFieldParser.parse(ocr, ReceiptCaptureMode.RECEIPT);
        assertEquals("Fresh Market", d.merchantForComment);
    }

    @Test
    public void receipt_prefersBottomStrongPayThisAmount() {
        OcrResult ocr = new OcrResult(Arrays.asList(
                new OcrLine("MART", 0.9f),
                new OcrLine("Total $5.00", 0.9f),
                new OcrLine("Pay this amount $7.50", 0.9f)
        ));
        ReceiptDraft d = ReceiptFieldParser.parse(ocr, ReceiptCaptureMode.RECEIPT);
        assertEquals("Mart", d.merchantForComment);
        assertEquals(7.50, d.totalAmount, 0.001);
    }

    @Test
    public void restaurant_composesTotalPlusTipWhenTipAfterPreTipTotal() {
        OcrResult ocr = new OcrResult(Arrays.asList(
                new OcrLine("BISTRO", 0.9f),
                new OcrLine("Subtotal 40.00", 0.9f),
                new OcrLine("Tax 2.00", 0.9f),
                new OcrLine("Total 42.00", 0.9f),
                new OcrLine("Tip 8.00", 0.9f)
        ));
        ReceiptDraft d = ReceiptFieldParser.parse(ocr, ReceiptCaptureMode.RESTAURANT);
        assertEquals(50.00, d.totalAmount, 0.001);
    }

    @Test
    public void restaurant_composesSubtotalTaxAndTipWhenNoFinalTotal() {
        OcrResult ocr = new OcrResult(Arrays.asList(
                new OcrLine("CAFE", 0.9f),
                new OcrLine("Subtotal 20.00", 0.9f),
                new OcrLine("Tax 1.50", 0.9f),
                new OcrLine("Gratuity 3.00", 0.9f)
        ));
        ReceiptDraft d = ReceiptFieldParser.parse(ocr, ReceiptCaptureMode.RESTAURANT);
        assertEquals(24.50, d.totalAmount, 0.001);
    }

    @Test
    public void restaurant_ignoresSuggestedTipPercentLine() {
        OcrResult ocr = new OcrResult(Arrays.asList(
                new OcrLine("DINER", 0.9f),
                new OcrLine("Subtotal 10.00", 0.9f),
                new OcrLine("Tax 0.80", 0.9f),
                new OcrLine("Total 10.80", 0.9f),
                new OcrLine("Suggested tip 20%", 0.9f),
                new OcrLine("Tip 2.00", 0.9f)
        ));
        ReceiptDraft d = ReceiptFieldParser.parse(ocr, ReceiptCaptureMode.RESTAURANT);
        assertEquals(12.80, d.totalAmount, 0.001);
    }

    @Test
    public void receipt_prefersTotalPaidOverEarlierTotal() {
        OcrResult ocr = new OcrResult(Arrays.asList(
                new OcrLine("GRILL", 0.9f),
                new OcrLine("Total 30.00", 0.9f),
                new OcrLine("Tip 6.00", 0.9f),
                new OcrLine("Total paid 36.00", 0.9f)
        ));
        ReceiptDraft d = ReceiptFieldParser.parse(ocr, ReceiptCaptureMode.RECEIPT);
        assertEquals(36.00, d.totalAmount, 0.001);
    }

    @Test
    public void brand_tallTopLine_beatsAddress() {
        OcrResult ocr = new OcrResult(Arrays.asList(
                new OcrLine("123 Main St", 0.9f, 18),
                new OcrLine("WALMART", 0.9f, 72),
                new OcrLine("TOTAL $12.34", 0.9f, 20)
        ));
        ReceiptDraft d = ReceiptFieldParser.parse(ocr, ReceiptCaptureMode.RECEIPT);
        assertEquals("Walmart", d.merchantForComment);
    }

    @Test
    public void brand_firstLineCheckers() {
        OcrResult ocr = new OcrResult(Arrays.asList(
                new OcrLine("CHECKERS", 0.9f, 60),
                new OcrLine("123 Main St", 0.9f, 18),
                new OcrLine("TOTAL 9.99", 0.9f, 20)
        ));
        ReceiptDraft d = ReceiptFieldParser.parse(ocr, ReceiptCaptureMode.RESTAURANT);
        assertEquals("Checkers", d.merchantForComment);
    }

    @Test
    public void normalizeBrandDisplay_stripsWelcomeTo() {
        assertEquals("Checkers", ReceiptFieldParser.normalizeBrandDisplay("Welcome to Checkers"));
    }

    @Test
    public void normalizeBrandDisplay_stripsStoreNumber() {
        assertEquals("Walmart", ReceiptFieldParser.normalizeBrandDisplay("WALMART #1234"));
    }

    @Test
    public void brand_skipsTransactionBoilerplate() {
        OcrResult ocr = new OcrResult(Arrays.asList(
                new OcrLine("AUTH 123456", 0.9f, 40),
                new OcrLine("CHECKERS", 0.9f, 55),
                new OcrLine("TOTAL 15.00", 0.9f, 22)
        ));
        ReceiptDraft d = ReceiptFieldParser.parse(ocr, ReceiptCaptureMode.RECEIPT);
        assertEquals("Checkers", d.merchantForComment);
    }

    @Test
    public void guessMerchantFromTopLine_usesFirstWord() {
        assertEquals("Walmart", ReceiptFieldParser.guessMerchantFromTopLine(Arrays.asList(
                new OcrLine("WALMART SUPERCENTER #4821", 0.9f, 50)
        )));
    }

    @Test
    public void fallback_firstWordWhenNoScoredBrand() {
        OcrResult ocr = new OcrResult(Arrays.asList(
                new OcrLine("QT", 0.9f, 10),
                new OcrLine("12.345 gal", 0.9f, 12),
                new OcrLine("$45.67", 0.9f, 14)
        ));
        ReceiptDraft d = ReceiptFieldParser.parse(ocr, ReceiptCaptureMode.GAS);
        assertEquals("Qt", d.merchantForComment);
    }

    @Test
    public void noUnknownMerchantFallback() {
        OcrResult ocr = new OcrResult(Arrays.asList(
                new OcrLine("X", 0.9f),
                new OcrLine("TOTAL 1.00", 0.9f)
        ));
        ReceiptDraft d = ReceiptFieldParser.parse(ocr, ReceiptCaptureMode.RECEIPT);
        assertEquals("X", d.merchantForComment);
    }

    @Test
    public void merchant_trimsLongSentenceToBrandWords() {
        OcrResult ocr = new OcrResult(Arrays.asList(
                new OcrLine("FRESH MARKET DOWNTOWN LOCATION", 0.9f, 50),
                new OcrLine("TOTAL $12.34", 0.9f)
        ));
        ReceiptDraft d = ReceiptFieldParser.parse(ocr, ReceiptCaptureMode.RECEIPT);
        assertEquals("Fresh Market", d.merchantForComment);
    }

    @Test
    public void total_prefersDollarTotalOverOrderNumber() {
        OcrResult ocr = new OcrResult(Arrays.asList(
                new OcrLine("MART", 0.9f),
                new OcrLine("Order #48291", 0.9f),
                new OcrLine("Points 120", 0.9f),
                new OcrLine("Total $24.31", 0.9f)
        ));
        ReceiptDraft d = ReceiptFieldParser.parse(ocr, ReceiptCaptureMode.RECEIPT);
        assertEquals(24.31, d.totalAmount, 0.001);
    }
}

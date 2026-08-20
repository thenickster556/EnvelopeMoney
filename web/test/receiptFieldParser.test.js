import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  parse,
  ocrLine,
  ocrResult,
  ReceiptCaptureMode,
  normalizeMerchantDisplay,
  normalizeBrandDisplay,
  guessMerchantFromTopLine,
} from '../domain/receiptFieldParser.js';
import { learn } from '../domain/ocrAmountLearner.js';
import { fromDefaults } from '../domain/ocrAmountWeights.js';

function lines(...rows) {
  return ocrResult(rows.map((row) => {
    if (typeof row === 'string') return ocrLine(row);
    return ocrLine(row[0], 0.9, row[1] || 0);
  }));
}

test('retail prefers total labeled line', () => {
  const d = parse(lines('ACME GROCERY', 'SUBTOTAL 10.00', 'TAX 0.80', 'TOTAL $12.34'), ReceiptCaptureMode.RECEIPT);
  assert.equal(d.merchantForComment, 'Acme Grocery');
  assert.equal(d.totalAmount, 12.34);
});

test('gas skips gallon line', () => {
  const d = parse(lines('12.345 gal', '$45.67'), ReceiptCaptureMode.GAS);
  assert.equal(d.totalAmount, 45.67);
});

test('restaurant uses largest when no total label', () => {
  const d = parse(lines('Bistro', 'Subtotal 20.00', 'Tax 1.50', '21.50'), ReceiptCaptureMode.RESTAURANT);
  assert.equal(d.merchantForComment, 'Bistro');
  assert.equal(d.totalAmount, 21.50);
});

test('comment is merchant only', () => {
  const d = parse(lines("Joe's Diner", 'TOTAL 9.99'), ReceiptCaptureMode.RECEIPT);
  assert.equal(d.merchantForComment, "Joe's Diner");
  assert.equal(d.totalAmount, 9.99);
});

test('merchant skips phone and picks store line', () => {
  const d = parse(lines('800-555-1234', 'BEST FOODS MARKET', 'TOTAL 5.00'), ReceiptCaptureMode.RECEIPT);
  assert.equal(d.merchantForComment, 'Best Foods Market');
});

test('merchant skips street line', () => {
  const d = parse(lines('123 Main St', 'CORNER BISTRO', 'TOTAL 10.00'), ReceiptCaptureMode.RESTAURANT);
  assert.equal(d.merchantForComment, 'Corner Bistro');
});

test('restaurant prefers amount due over earlier total', () => {
  const d = parse(lines('BISTRO', 'Subtotal 40.00', 'Tax 2.00', 'Tip 8.00', 'Total 50.00', 'Amount Due 58.00'), ReceiptCaptureMode.RESTAURANT);
  assert.equal(d.merchantForComment, 'Bistro');
  assert.equal(d.totalAmount, 58.00);
});

test('restaurant uses last total labeled line when multiple', () => {
  const d = parse(lines('CAFE', 'Total 45.00', 'Tip suggested', 'Total 52.30'), ReceiptCaptureMode.RESTAURANT);
  assert.equal(d.merchantForComment, 'Cafe');
  assert.equal(d.totalAmount, 52.30);
});

test('normalizeMerchantDisplay title case all caps', () => {
  assert.equal(normalizeMerchantDisplay("JOE'S DINER"), "Joe's Diner");
});

test('merchant skips http url line', () => {
  const d = parse(lines('https://example.com/receipt', 'FRESH MARKET', 'TOTAL $4.00'), ReceiptCaptureMode.RECEIPT);
  assert.equal(d.merchantForComment, 'Fresh Market');
});

test('receipt prefers bottom strong pay this amount', () => {
  const d = parse(lines('MART', 'Total $5.00', 'Pay this amount $7.50'), ReceiptCaptureMode.RECEIPT);
  assert.equal(d.merchantForComment, 'Mart');
  assert.equal(d.totalAmount, 7.50);
});

test('restaurant composes total plus tip', () => {
  const d = parse(lines('BISTRO', 'Subtotal 40.00', 'Tax 2.00', 'Total 42.00', 'Tip 8.00'), ReceiptCaptureMode.RESTAURANT);
  assert.equal(d.totalAmount, 50.00);
});

test('restaurant composes subtotal tax and tip', () => {
  const d = parse(lines('CAFE', 'Subtotal 20.00', 'Tax 1.50', 'Gratuity 3.00'), ReceiptCaptureMode.RESTAURANT);
  assert.equal(d.totalAmount, 24.50);
});

test('restaurant ignores suggested tip percent', () => {
  const d = parse(lines('DINER', 'Subtotal 10.00', 'Tax 0.80', 'Total 10.80', 'Suggested tip 20%', 'Tip 2.00'), ReceiptCaptureMode.RESTAURANT);
  assert.equal(d.totalAmount, 12.80);
});

test('receipt prefers total paid', () => {
  const d = parse(lines('GRILL', 'Total 30.00', 'Tip 6.00', 'Total paid 36.00'), ReceiptCaptureMode.RECEIPT);
  assert.equal(d.totalAmount, 36.00);
});

test('brand tall top line beats address', () => {
  const d = parse(ocrResult([
    ocrLine('123 Main St', 0.9, 18),
    ocrLine('WALMART', 0.9, 72),
    ocrLine('TOTAL $12.34', 0.9, 20),
  ]), ReceiptCaptureMode.RECEIPT);
  assert.equal(d.merchantForComment, 'Walmart');
});

test('brand first line checkers', () => {
  const d = parse(ocrResult([
    ocrLine('CHECKERS', 0.9, 60),
    ocrLine('123 Main St', 0.9, 18),
    ocrLine('TOTAL 9.99', 0.9, 20),
  ]), ReceiptCaptureMode.RESTAURANT);
  assert.equal(d.merchantForComment, 'Checkers');
});

test('normalizeBrandDisplay strips welcome to', () => {
  assert.equal(normalizeBrandDisplay('Welcome to Checkers'), 'Checkers');
});

test('normalizeBrandDisplay strips store number', () => {
  assert.equal(normalizeBrandDisplay('WALMART #1234'), 'Walmart');
});

test('brand skips transaction boilerplate', () => {
  const d = parse(ocrResult([
    ocrLine('AUTH 123456', 0.9, 40),
    ocrLine('CHECKERS', 0.9, 55),
    ocrLine('TOTAL 15.00', 0.9, 22),
  ]), ReceiptCaptureMode.RECEIPT);
  assert.equal(d.merchantForComment, 'Checkers');
});

test('guessMerchantFromTopLine uses first word', () => {
  assert.equal(guessMerchantFromTopLine([ocrLine('WALMART SUPERCENTER #4821', 0.9, 50)]), 'Walmart');
});

test('fallback first word when no scored brand', () => {
  const d = parse(ocrResult([
    ocrLine('QT', 0.9, 10),
    ocrLine('12.345 gal', 0.9, 12),
    ocrLine('$45.67', 0.9, 14),
  ]), ReceiptCaptureMode.GAS);
  assert.equal(d.merchantForComment, 'Qt');
});

test('no unknown merchant fallback', () => {
  const d = parse(lines('X', 'TOTAL 1.00'), ReceiptCaptureMode.RECEIPT);
  assert.equal(d.merchantForComment, 'X');
});

test('merchant trims long sentence to brand words', () => {
  const d = parse(ocrResult([
    ocrLine('FRESH MARKET DOWNTOWN LOCATION', 0.9, 50),
    ocrLine('TOTAL $12.34', 0.9),
  ]), ReceiptCaptureMode.RECEIPT);
  assert.equal(d.merchantForComment, 'Fresh Market');
});

test('total prefers dollar total over order number', () => {
  const d = parse(lines('MART', 'Order #48291', 'Points 120', 'Total $24.31'), ReceiptCaptureMode.RECEIPT);
  assert.equal(d.totalAmount, 24.31);
});

test('fallback two totals default weights prefer dollar line', () => {
  const d = parse(lines('Store', '$45.12', 'misc', '42.00'), ReceiptCaptureMode.RECEIPT, null);
  assert.equal(d.totalAmount, 45.12);
});

test('fallback two totals learned weights prefer corrected amount', () => {
  const learned = learn(['Store', '$45.12', 'misc', '42.00'], 45.12, 42.00, fromDefaults(), ReceiptCaptureMode.RECEIPT);
  const d = parse(lines('Store', '$45.12', 'misc', '42.00'), ReceiptCaptureMode.RECEIPT, learned);
  assert.equal(d.totalAmount, 42.00);
});

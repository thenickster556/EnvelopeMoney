import { copyOrDefault } from './ocrAmountWeights.js';

const MONEY = /(?:^|[^\d])(\$)?\s*(\d{1,3}(?:,\d{3})+|\d+)\s*([.,])\s*(\d{2})(?:\s*$|[^\d])/g;
const ISO_DATE = /(20\d{2})-(\d{2})-(\d{2})/;
const US_DATE = /(\d{1,2})[/-](\d{1,2})[/-](\d{2,4})/;
const TOTAL_LINE_STRONG = /\b(amount\s*due|balance\s*due|total\s*due|grand\s*total|pay\s*this\s*amount|total\s*paid|amount\s*paid|payment\s*total|you\s*paid|paid\s*total)\b/i;
const TOTAL_LABEL = /\b(total|amount\s*due|balance\s*due|grand\s*total|total\s*due|pay\s*this\s*amount|total\s*paid|amount\s*paid|payment\s*total|you\s*paid|paid\s*total)\b/i;
const SUBTOTAL_OR_TAX = /\b(sub\s*total|subtotal|tax|tip|gratuity|suggested)\b/i;
const TIP_LINE = /\b(tip|gratuity)\b/i;
const SUGGESTED_TIP_LINE = /\b(suggested|recommend|guide)\b.*\b(tip|gratuity)\b|\b(tip|gratuity)\b.*\d+\s*%/i;
const SUBTOTAL_LINE = /\b(sub\s*total|subtotal)\b/i;
const TAX_LINE = /\btax\b/i;
const TAX_ID_LINE = /\btax\s*id\b/i;
const GAS_GALLON = /\b(gal|gallon|\/\s*gal)\b/i;
const PHONE = /\d{3}[-.\s]?\d{3}[-.\s]?\d{4}/;
const HTTP_OR_WWW = /https?:\/\/|www\./i;
const EMAIL = /@\S+/;
const THANK_YOU = /thank\s+you/i;
const GUEST_OR_TABLE = /guest\s*check|table\s*#?|server\s*[:#]/i;
const STREET_START = /^\d{1,5}\s+[A-Za-z]/;
const LINE_ZIP_ONLY = /^\d{5}(-\d{4})?\s*$/i;
const TRANSACTION_BOILERPLATE = /\b(transaction|auth|approval|approved|terminal|cashier|invoice|receipt\s*#|customer\s+copy|merchant\s+copy|trans\s|mid\b|batch\s|ref\s*#)/i;
const CARD_BRAND = /\b(visa|mastercard|amex|american\s+express|debit|credit\s+card)\b/i;
const WELCOME_PREFIX = /^welcome\s+to\s+/i;
const THANKS_SHOPPING_PREFIX = /^thank\s+you\s+for\s+shopping\s+at\s+/i;
const TRAILING_STORE_ID = /\s+(store\s*#?|#|no\.?)\s*\d+\s*$/i;
const ORDER_OR_POINTS_LINE = /\b(order\s*#|order\s+number|points\s*\d|rewards\s*\d|invoice\s*#|trans\s*#|ref\s*#)\b/i;
const SURVEY_OR_POLICY_LINE = /\b(survey|return\s+policy|tell\s+us|save\s+\d+%|visit\s+us\s+at)\b/i;
const DIGITS_ONLY = /^\s*\d+\s*$/;

const BRAND_SCAN_TOP_LINES = 6;
const MAX_BRAND_WORDS = 2;
const MAX_BRAND_DISPLAY_LENGTH = 40;

export const ReceiptCaptureMode = {
  AUTO: 'AUTO',
  RECEIPT: 'RECEIPT',
  RESTAURANT: 'RESTAURANT',
  GAS: 'GAS',
};

export function ocrLine(text, confidence = 0.9, lineHeightPx = 0) {
  return { text: text || '', confidence, lineHeightPx: Math.max(0, lineHeightPx) };
}

export function ocrResult(lines) {
  const list = lines ? [...lines] : [];
  return {
    lines: list,
    fullText: list.map((l) => l.text).join('\n'),
  };
}

export function parse(ocr, mode, weights) {
  if (!ocr) {
    return { merchantForComment: null, totalAmount: null, dateYyyyMmDd: null, amountConfidence: 0, rawOcrSample: null, sourceLines: [] };
  }
  const lines = [];
  for (const line of ocr.lines || []) {
    if (line.text && line.text.trim()) lines.push(line.text.trim());
  }
  const full = ocr.fullText || lines.join('\n');
  const sample = full.length > 2000 ? full.slice(0, 2000) : full;
  const date = extractDate(full);
  let m = mode == null ? ReceiptCaptureMode.AUTO : mode;
  if (m === ReceiptCaptureMode.AUTO) m = inferMode(lines);
  let merchant = extractMerchant(ocr.lines || []);
  const pick = pickTotal(lines, m, weights);
  if (!merchant) merchant = guessMerchantFromTopLine(ocr.lines || []);
  if (!merchant && m === ReceiptCaptureMode.GAS) merchant = 'Gas';
  return {
    merchantForComment: merchant,
    totalAmount: pick.amount,
    dateYyyyMmDd: date,
    amountConfidence: pick.confidence,
    rawOcrSample: sample,
    sourceLines: lines,
  };
}

function inferMode(lines) {
  const joined = joinLower(lines);
  if (joined.includes('subtotal') || joined.includes('gratuity') || joined.includes('suggested tip')) {
    return ReceiptCaptureMode.RESTAURANT;
  }
  if (lines.length <= 6 && countMoneyCandidates(lines) <= 4) {
    return ReceiptCaptureMode.GAS;
  }
  return ReceiptCaptureMode.RECEIPT;
}

function joinLower(lines) {
  return lines.map((l) => l.toLowerCase()).join('\n') + '\n';
}

function countMoneyCandidates(lines) {
  let n = 0;
  for (const line of lines) {
    MONEY.lastIndex = 0;
    if (MONEY.test(line)) n++;
  }
  return n;
}

function extractDate(full) {
  const iso = ISO_DATE.exec(full);
  if (iso) return `${iso[1]}-${iso[2]}-${iso[3]}`;
  const us = US_DATE.exec(full);
  if (us) {
    const mon = Number(us[1]);
    const d = Number(us[2]);
    let y = Number(us[3]);
    if (y < 100) y += 2000;
    return `${String(y).padStart(4, '0')}-${String(mon).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
  }
  return null;
}

function extractMerchant(ocrLines) {
  if (!ocrLines || ocrLines.length === 0) return null;
  const limit = Math.min(ocrLines.length, BRAND_SCAN_TOP_LINES);
  let maxHeightInTop = 0;
  for (let i = 0; i < limit; i++) {
    maxHeightInTop = Math.max(maxHeightInTop, ocrLines[i].lineHeightPx || 0);
  }
  let bestScore = Number.MIN_SAFE_INTEGER;
  let bestIndex = -1;
  for (let i = 0; i < limit; i++) {
    const line = (ocrLines[i].text || '').trim();
    if (!isViableBrandCandidate(line)) continue;
    const score = scoreBrandLine(line, i, ocrLines[i].lineHeightPx || 0, maxHeightInTop);
    if (score > bestScore || (score === bestScore && bestIndex >= 0 && i < bestIndex)) {
      bestScore = score;
      bestIndex = i;
    }
  }
  if (bestIndex < 0) return guessMerchantFromTopLine(ocrLines);
  return normalizeBrandDisplay(ocrLines[bestIndex].text);
}

export function guessMerchantFromTopLine(ocrLines) {
  if (!ocrLines || ocrLines.length === 0) return null;
  for (const lineObj of ocrLines) {
    const line = lineObj.text ? lineObj.text.trim() : '';
    if (!line) continue;
    const word = firstSignificantWord(line);
    if (!word) return normalizeBrandDisplay(line);
    return normalizeMerchantDisplay(word);
  }
  return null;
}

function firstSignificantWord(line) {
  let cleaned = line.trim();
  cleaned = cleaned.replace(WELCOME_PREFIX, '');
  cleaned = cleaned.replace(THANKS_SHOPPING_PREFIX, '');
  cleaned = cleaned.trim();
  if (!cleaned) return null;
  const tokens = cleaned.split(/\s+/);
  for (const token of tokens) {
    const lettersOnly = token.replace(/[^\p{L}0-9'&.-]/gu, '');
    if (lettersOnly.length < 2) continue;
    if (![...lettersOnly].some(isLetter)) continue;
    if (isSkipTopFallbackWord(lettersOnly)) continue;
    return lettersOnly;
  }
  for (const token of tokens) {
    const lettersOnly = token.replace(/[^\p{L}0-9'&.-]/gu, '');
    if (lettersOnly) return lettersOnly;
  }
  return null;
}

function isLetter(ch) {
  return /\p{L}/u.test(ch);
}

function isSkipTopFallbackWord(word) {
  const lower = word.toLowerCase();
  return ['welcome', 'to', 'the', 'thank', 'you', 'customer', 'merchant', 'copy', 'receipt'].includes(lower);
}

function isViableBrandCandidate(line) {
  if (line.length < 3 || line.length > 80) return false;
  if (countWords(line) > 8) return false;
  if (SURVEY_OR_POLICY_LINE.test(line)) return false;
  if (TOTAL_LABEL.test(line) || SUBTOTAL_OR_TAX.test(line)) return false;
  if (ISO_DATE.test(line) || US_DATE.test(line)) return false;
  if (DIGITS_ONLY.test(line)) return false;
  if (mostlyNumeric(line)) return false;
  MONEY.lastIndex = 0;
  if (MONEY.test(line)) return false;
  if (GAS_GALLON.test(line)) return false;
  return !isJunkMerchantLine(line);
}

function isJunkMerchantLine(line) {
  const t = line.trim();
  if (HTTP_OR_WWW.test(t) || EMAIL.test(t)) return true;
  if (THANK_YOU.test(t) || GUEST_OR_TABLE.test(t)) return true;
  if (PHONE.test(t)) return true;
  if (LINE_ZIP_ONLY.test(t)) return true;
  if (STREET_START.test(t)) return true;
  if (TRANSACTION_BOILERPLATE.test(t)) return true;
  if (CARD_BRAND.test(t)) return true;
  return false;
}

function scoreBrandLine(line, lineIndex, lineHeightPx, maxHeightInTop) {
  let letters = 0;
  let digits = 0;
  for (const c of line) {
    if (isLetter(c)) letters++;
    else if (c >= '0' && c <= '9') digits++;
  }
  if (letters < 2) return -1;
  const len = line.length;
  if (len > 60) return -1;
  let score = letters * 2 - digits * 3;
  if (countWords(line) > 4) score -= 20;
  if (lineIndex === 0) score += 30;
  else if (lineIndex === 1) score += 20;
  else if (lineIndex === 2) score += 10;
  if (lineHeightPx > 0 && maxHeightInTop > 0) {
    score += Math.trunc((lineHeightPx * 50) / maxHeightInTop);
  }
  if (looksAllCapsish(line)) score += 8;
  if (len >= 4 && len <= MAX_BRAND_DISPLAY_LENGTH) score += 5;
  if (TRAILING_STORE_ID.test(line)) score -= 4;
  return score;
}

export function normalizeBrandDisplay(line) {
  if (line == null) return null;
  let cleaned = line.trim().replace(/\s+/g, ' ');
  cleaned = cleaned.replace(WELCOME_PREFIX, '');
  cleaned = cleaned.replace(THANKS_SHOPPING_PREFIX, '');
  cleaned = cleaned.replace(TRAILING_STORE_ID, '');
  cleaned = cleaned.trim();
  cleaned = trimToBrandWords(cleaned);
  if (cleaned.length > MAX_BRAND_DISPLAY_LENGTH) {
    cleaned = cleaned.slice(0, MAX_BRAND_DISPLAY_LENGTH).trim();
  }
  return normalizeMerchantDisplay(cleaned);
}

export function trimToBrandWords(cleaned) {
  if (!cleaned) return cleaned;
  const words = cleaned.split(/\s+/);
  if (words.length <= MAX_BRAND_WORDS) return cleaned;
  if (words.length === 3 && looksAllCapsish(cleaned) && cleaned.length <= 28) return cleaned;
  return words.slice(0, MAX_BRAND_WORDS).join(' ');
}

function countWords(line) {
  if (!line || !line.trim()) return 0;
  return line.trim().split(/\s+/).length;
}

export function normalizeMerchantDisplay(line) {
  if (line == null) return null;
  const t = line.trim().replace(/\s+/g, ' ');
  if (!t) return t;
  if (!looksAllCapsish(t)) return t;
  return t.split(/\s+/).map(titleCaseToken).join(' ');
}

function looksAllCapsish(t) {
  let letters = 0;
  let uppers = 0;
  for (const c of t) {
    if (isLetter(c)) {
      letters++;
      if (c === c.toUpperCase() && c !== c.toLowerCase()) uppers++;
    }
  }
  if (letters < 2) return false;
  return uppers / letters > 0.7;
}

function titleCaseToken(w) {
  if (!w) return w;
  if (![...w].some(isLetter)) return w;
  const low = w.toLowerCase();
  return low.charAt(0).toUpperCase() + low.slice(1);
}

function mostlyNumeric(line) {
  let digits = 0;
  for (const c of line) {
    if (c >= '0' && c <= '9') digits++;
  }
  return digits >= line.length * 0.6;
}

function pickTotal(lines, mode, weights) {
  if (lines.length === 0) return { amount: null, confidence: 0.2 };
  if (mode === ReceiptCaptureMode.RESTAURANT || mode === ReceiptCaptureMode.RECEIPT) {
    const labeled = pickLabeledTotalFromBottom(lines, mode);
    const withTip = pickTotalIncludingTip(lines, mode);
    if (labeled.amount != null && withTip.amount != null) {
      if (withTip.amount > labeled.amount + 0.009) return withTip;
      return labeled;
    }
    if (withTip.amount != null) return withTip;
    if (labeled.amount != null) return labeled;
  }

  const candidates = listMoneyCandidates(lines, mode);
  if (candidates.length === 0) return { amount: null, confidence: 0.2 };
  let bestIndex = 0;
  let bestScore = -Infinity;
  for (let i = 0; i < candidates.length; i++) {
    const score = scoreCandidate(candidates[i], mode, weights);
    if (score > bestScore) {
      bestScore = score;
      bestIndex = i;
    }
  }
  const conf = bestScore >= 40 ? 0.72 : 0.55;
  return { amount: candidates[bestIndex].amount, confidence: conf };
}

export function listMoneyCandidates(lines, mode) {
  const candidates = [];
  if (!lines || lines.length === 0) return candidates;
  const resolved = mode == null ? ReceiptCaptureMode.RECEIPT : mode;
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    if (resolved === ReceiptCaptureMode.GAS && GAS_GALLON.test(line)) continue;
    if (shouldSkipFallbackMoneyLine(line)) continue;
    if (resolved === ReceiptCaptureMode.RESTAURANT && SUBTOTAL_OR_TAX.test(line) && !TOTAL_LABEL.test(line)) {
      continue;
    }
    MONEY.lastIndex = 0;
    let match;
    while ((match = MONEY.exec(line)) !== null) {
      const v = parseMoneyGroup(match);
      if (v != null && v > 0 && v < 100000) {
        candidates.push({
          amount: v,
          dollarSign: !!match[1],
          strongTotalLabel: TOTAL_LINE_STRONG.test(line),
          totalLabel: TOTAL_LABEL.test(line),
          bottomHalf: i >= lines.length / 2,
          orderOrPoints: ORDER_OR_POINTS_LINE.test(line),
          lineIndex: i,
          line,
        });
      }
    }
  }
  return candidates;
}

function scoreCandidate(candidate, mode, weights) {
  const w = copyOrDefault(weights);
  let score = candidate.lineIndex;
  if (candidate.dollarSign) score += w[0];
  if (candidate.strongTotalLabel) score += w[1];
  else if (candidate.totalLabel) score += w[2];
  if (candidate.bottomHalf) score += w[3];
  if (candidate.orderOrPoints) score += w[4];
  if (mode === ReceiptCaptureMode.RESTAURANT && isExplicitTipLine(candidate.line)) score -= 10;
  return score;
}

function shouldSkipFallbackMoneyLine(line) {
  if (ORDER_OR_POINTS_LINE.test(line) && !TOTAL_LABEL.test(line)) return true;
  if (PHONE.test(line) && !TOTAL_LABEL.test(line)) return true;
  return ISO_DATE.test(line) || US_DATE.test(line);
}

function pickLabeledTotalFromBottom(lines, mode) {
  for (let i = lines.length - 1; i >= 0; i--) {
    const line = lines[i];
    if (mode === ReceiptCaptureMode.RESTAURANT
      && isSkippableRestaurantLineForTotalLabel(line)
      && !TOTAL_LINE_STRONG.test(line)
      && !TOTAL_LABEL.test(line)) {
      continue;
    }
    if (TOTAL_LINE_STRONG.test(line)) {
      const v = maxMoneyOnLine(line);
      if (v != null && v > 0) return { amount: v, confidence: 0.92 };
    }
  }
  for (let i = lines.length - 1; i >= 0; i--) {
    const line = lines[i];
    if (mode === ReceiptCaptureMode.RESTAURANT
      && isSkippableRestaurantLineForTotalLabel(line)
      && !TOTAL_LABEL.test(line)) {
      continue;
    }
    if (TOTAL_LABEL.test(line)) {
      const v = maxMoneyOnLine(line);
      if (v != null && v > 0) return { amount: v, confidence: 0.88 };
    }
  }
  return { amount: null, confidence: 0.2 };
}

function pickTotalIncludingTip(lines, mode) {
  if (mode !== ReceiptCaptureMode.RESTAURANT && mode !== ReceiptCaptureMode.RECEIPT) {
    return { amount: null, confidence: 0.2 };
  }
  let subtotal = null;
  let tax = null;
  let tip = null;
  let tipLineIndex = -1;
  let lastTotalLabeled = null;
  let lastTotalLineIndex = -1;
  let amountDue = null;
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    if (isSuggestedTipLine(line)) continue;
    const money = maxMoneyOnLine(line);
    if (money == null || money <= 0) continue;
    if (TOTAL_LINE_STRONG.test(line)) amountDue = money;
    if (SUBTOTAL_LINE.test(line)) subtotal = money;
    if (TAX_LINE.test(line) && !TAX_ID_LINE.test(line) && !TIP_LINE.test(line)) tax = money;
    if (isExplicitTipLine(line)) {
      tip = money;
      tipLineIndex = i;
    }
    if (TOTAL_LABEL.test(line) && !isExplicitTipLine(line)) {
      lastTotalLabeled = money;
      lastTotalLineIndex = i;
    }
  }
  if (amountDue != null) return { amount: amountDue, confidence: 0.93 };
  if (lastTotalLabeled != null && tip != null && tipLineIndex > lastTotalLineIndex) {
    return { amount: roundMoney(lastTotalLabeled + tip), confidence: 0.91 };
  }
  if (subtotal != null && tip != null) {
    const taxAmount = tax != null ? tax : 0;
    const foodAndTax = roundMoney(subtotal + taxAmount);
    const withTip = roundMoney(foodAndTax + tip);
    if (lastTotalLabeled != null && amountsClose(lastTotalLabeled, foodAndTax)) {
      return { amount: withTip, confidence: 0.9 };
    }
    if (lastTotalLabeled == null || withTip >= lastTotalLabeled) {
      return { amount: withTip, confidence: 0.89 };
    }
  }
  if (lastTotalLabeled != null && tip != null) {
    const combined = roundMoney(lastTotalLabeled + tip);
    if (combined > lastTotalLabeled + 0.009) {
      return { amount: combined, confidence: 0.87 };
    }
  }
  return { amount: null, confidence: 0.2 };
}

function isSkippableRestaurantLineForTotalLabel(line) {
  return SUBTOTAL_OR_TAX.test(line);
}

function isSuggestedTipLine(line) {
  return SUGGESTED_TIP_LINE.test(line);
}

function isExplicitTipLine(line) {
  return TIP_LINE.test(line) && !TOTAL_LABEL.test(line) && !isSuggestedTipLine(line);
}

function amountsClose(a, b) {
  return Math.abs(a - b) < 0.02;
}

function roundMoney(value) {
  return Math.round(value * 100) / 100;
}

function maxMoneyOnLine(line) {
  MONEY.lastIndex = 0;
  let best = -1;
  let match;
  while ((match = MONEY.exec(line)) !== null) {
    const v = parseMoneyGroup(match);
    if (v != null && v > best) best = v;
  }
  return best > 0 ? best : null;
}

function parseMoneyGroup(m) {
  try {
    const intPart = m[2].replace(/,/g, '');
    const frac = m[4];
    return Number(`${intPart}.${frac}`);
  } catch {
    return null;
  }
}

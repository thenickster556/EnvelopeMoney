function normalize(value) {
  return value == null ? '' : String(value).trim();
}

function safe(value) {
  const n = Number(value);
  if (!Number.isFinite(n)) return 0;
  return n;
}

export function allocatedTotal(slices) {
  let total = 0;
  if (!slices) return total;
  for (const s of slices) {
    if (!s) continue;
    total += safe(s.amount);
  }
  return total;
}

export function validate(purchaseTotal, slices) {
  if (!slices || slices.length < 2) {
    return { valid: false, message: 'Add at least two split slices' };
  }
  const ponds = new Set();
  let sum = 0;
  for (const s of slices) {
    if (!s) {
      return { valid: false, message: 'Split slice is missing' };
    }
    const pond = normalize(s.pondName);
    if (pond === '') {
      return { valid: false, message: 'Choose a pond for every slice' };
    }
    const key = pond.toLowerCase();
    if (ponds.has(key)) {
      return { valid: false, message: 'Each pond can only appear once in a split' };
    }
    ponds.add(key);
    if (s.amount <= 0) {
      return { valid: false, message: 'Every slice must be greater than $0.00' };
    }
    sum += s.amount;
  }
  if (Math.abs(sum - purchaseTotal) > 0.01) {
    const off = sum - purchaseTotal;
    return { valid: false, message: `Slices must sum to the purchase total (off by $${off.toFixed(2)})` };
  }
  return { valid: true, message: null };
}

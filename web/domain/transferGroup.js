function normalize(value) {
  return value == null ? '' : String(value).trim();
}

function safe(value) {
  const n = Number(value);
  if (!Number.isFinite(n)) return 0;
  return n;
}

export function allocatedTotal(allocations) {
  let total = 0;
  if (!allocations) return total;
  for (const allocation of allocations) {
    if (!allocation) continue;
    total += safe(allocation.amount);
  }
  return total;
}

export function spentInSource(sourceAmount, allocations) {
  return sourceAmount - allocatedTotal(allocations);
}

export function validate(sourceAmount, sourceEnvelopeName, allocations) {
  if (!allocations || allocations.length === 0) {
    return { valid: false, message: 'Add at least one transfer bucket' };
  }
  const destinations = new Set();
  let allocated = 0;
  for (const allocation of allocations) {
    if (!allocation) {
      return { valid: false, message: 'Transfer bucket is missing' };
    }
    const destination = normalize(allocation.toEnvelope);
    if (destination === '') {
      return { valid: false, message: 'Choose a destination for every transfer bucket' };
    }
    if (destination === normalize(sourceEnvelopeName)) {
      return { valid: false, message: 'Transfer destination must be a different envelope' };
    }
    const key = destination.toLowerCase();
    if (destinations.has(key)) {
      return { valid: false, message: 'Each destination can only appear once per transfer' };
    }
    destinations.add(key);
    if (allocation.amount <= 0) {
      return { valid: false, message: 'Every transfer bucket must be greater than $0.00' };
    }
    allocated += allocation.amount;
  }
  if (allocated - sourceAmount > 0.0001) {
    const over = allocated - sourceAmount;
    return { valid: false, message: `Transfers exceed the total by $${over.toFixed(2)}` };
  }
  return { valid: true, message: null };
}

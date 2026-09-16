import { test } from 'node:test';
import assert from 'node:assert/strict';
import { buildDemoProfile, demoMonths, shouldSeedDemoProfile, DEMO_POND_SPECS } from '../domain/demoSeed.js';
import { getTransactions } from '../domain/envelopeModel.js';
import { addMonthsYearMonth } from '../domain/dates.js';

test('shouldSeedDemoProfile only when ponds are missing', () => {
  assert.equal(shouldSeedDemoProfile(null), true);
  assert.equal(shouldSeedDemoProfile({ envelopes: [] }), true);
  assert.equal(shouldSeedDemoProfile({ envelopes: [{ name: 'Groceries' }] }), false);
});

test('buildDemoProfile has five ponds and 13 months of mixed activity', () => {
  const now = new Date(2026, 7, 19);
  const profile = buildDemoProfile(now);
  assert.deepEqual(profile.envelopes.map((e) => e.name), DEMO_POND_SPECS.map((s) => s.name));
  assert.equal(profile.envelopes.length, 5);
  assert.deepEqual(profile.billsDays, [1, 15]);
  assert.deepEqual(profile.paydays, [1, 15]);
  assert.equal(profile.currentMonth, '2026-08');

  const months = demoMonths(now);
  assert.equal(months.length, 13);
  assert.equal(months[0], '2025-08');
  assert.equal(months[12], '2026-08');

  for (const month of months) {
    const all = profile.envelopes.flatMap((e) => getTransactions(e).filter((t) => t.month === month));
    assert.ok(all.some((t) => !t.transferId && !t.splitPurchaseGroupId && t.amount > 0), `spending in ${month}`);
    assert.ok(all.some((t) => t.transferId && !t.transferBucketId), `transfer source in ${month}`);
    const dests = new Set(all.filter((t) => t.transferBucketId).map((t) => t.envelopeName));
    assert.ok(dests.has('Savings') && dests.has('Bills'), `two transfer buckets in ${month}`);
    const splitIds = new Set(all.filter((t) => t.splitPurchaseGroupId).map((t) => t.splitPurchaseGroupId));
    assert.equal(splitIds.size, 1, `one split group in ${month}`);
    assert.ok(all.some((t) => t.recurring && t.comment === 'Rent'), `rent in ${month}`);
  }

  const templates = profile.envelopes.flatMap((e) => getTransactions(e).filter((t) => t.recurringTemplate));
  assert.equal(templates.length, 1);

  const future = addMonthsYearMonth('2026-08', 1);
  const futureRows = profile.envelopes.flatMap((e) => getTransactions(e).filter((t) => t.month === future));
  assert.equal(futureRows.length, 0);
});

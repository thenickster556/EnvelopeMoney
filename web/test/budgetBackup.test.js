import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  MAX_BACKUP_CHARS,
  parseBudgetBackup,
  applyBudgetToProfile,
} from '../domain/budgetBackup.js';

test('backup rejects text over the size cap before trusting it', () => {
  const parsed = parseBudgetBackup('x'.repeat(MAX_BACKUP_CHARS + 1));
  assert.equal(parsed.ok, false);
  assert.equal(parsed.error, 'too-large');
});

test('backup rejects text that is not JSON', () => {
  const parsed = parseBudgetBackup('not json');
  assert.equal(parsed.ok, false);
  assert.equal(parsed.error, 'not-json');
});

test('applyBudgetToProfile replaces the ledger and shows that month', () => {
  const parsed = parseBudgetBackup(JSON.stringify({
    kind: 'mountain-money-budget',
    version: 1,
    currentMonth: '2026-09',
    billsDays: [10],
    paydays: [],
    billsFilterActive: true,
    billsFilterSavedStartDisplay: 'Sep 1, 2026',
    billsFilterSavedEndDisplay: 'Sep 9, 2026',
    envelopes: [{ name: 'Bills', transactions: [] }],
  }));
  assert.equal(parsed.ok, true);
  const next = applyBudgetToProfile({
    userId: 'keep-me-out-of-logic',
    currentMonth: '2020-01',
    displayedMonth: '2020-01',
    envelopes: [{ name: 'Old' }],
    dateFilterStartDisplay: 'Jan 1, 2020',
    dateFilterEndDisplay: 'Jan 31, 2020',
  }, parsed);
  assert.equal(next.currentMonth, '2026-09');
  assert.equal(next.displayedMonth, '2026-09');
  assert.equal(next.envelopes[0].name, 'Bills');
  assert.equal(next.billsFilterActive, true);
  assert.equal(next.billsDays[0], 10);
  assert.match(next.dateFilterStartDisplay, /Sep/);
  assert.equal(next.userId, 'keep-me-out-of-logic');
});

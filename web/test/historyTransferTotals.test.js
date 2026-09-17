import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  includeAmountInHistoryTotal,
  spendingTotal,
  addInboundToPanel,
  formatToSummary,
} from '../domain/historyTransferTotals.js';

test('inbound mirror is excluded from history total', () => {
  assert.equal(includeAmountInHistoryTotal(true, false), false);
  assert.equal(includeAmountInHistoryTotal(true, true), true);
  assert.equal(includeAmountInHistoryTotal(false, false), true);
});

test('spending total ignores inbound and keeps outgoing out when transfers visible', () => {
  assert.equal(spendingTotal(70, 40, true), 30);
  assert.equal(spendingTotal(70, 40, false), 70);
});

test('inbound panel stays negative when destination is selected', () => {
  let panel = 0;
  panel = addInboundToPanel(panel, 40);
  panel = addInboundToPanel(panel, 10);
  assert.equal(panel, -50);
  assert.equal(formatToSummary('Savings', panel), 'To Savings: -$50.00');
});

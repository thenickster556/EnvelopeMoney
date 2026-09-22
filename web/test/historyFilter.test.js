import { test } from 'node:test';
import assert from 'node:assert/strict';
import { rowVisible, historyShowingLabel, transferSides } from '../domain/historyFilter.js';

const envelopes = [
  {
    name: 'Groceries',
    selected: true,
    transfers: [{ id: 't1', bucketId: 'b1', toEnvelope: 'Savings', amount: 40 }],
    transactions: [
      { envelopeName: 'Groceries', amount: 50, transferId: 't1', transferBucketId: null, month: '2026-09', date: '2026-09-02' },
      { envelopeName: 'Groceries', amount: 12.5, transferId: null, month: '2026-09', date: '2026-09-07' },
    ],
  },
  {
    name: 'Gas',
    selected: false,
    transfers: [],
    transactions: [
      { envelopeName: 'Gas', amount: 28, transferId: null, month: '2026-09', date: '2026-09-01' },
    ],
  },
  {
    name: 'Savings',
    selected: false,
    transfers: [],
    transactions: [
      { envelopeName: 'Savings', amount: -40, transferId: 't1', transferBucketId: 'b1', month: '2026-09', date: '2026-09-02' },
    ],
  },
];

test('no ponds checked hides every row', () => {
  const selected = new Set();
  const sides = transferSides(envelopes, 't1', selected);
  assert.equal(rowVisible({
    transferId: null,
    inRange: true,
    pondSelected: false,
    transfersVisible: true,
    ...sides,
  }), false);
  assert.equal(historyShowingLabel([], ['Groceries', 'Gas']), 'Showing: no ponds');
});

test('one pond shows only that pond spending', () => {
  assert.equal(rowVisible({
    transferId: null,
    inRange: true,
    pondSelected: true,
    transfersVisible: false,
    sourceSelected: false,
    anyDestinationSelected: false,
  }), true);
  assert.equal(rowVisible({
    transferId: null,
    inRange: true,
    pondSelected: false,
    transfersVisible: false,
    sourceSelected: false,
    anyDestinationSelected: false,
  }), false);
  assert.equal(historyShowingLabel(['Gas'], ['Groceries', 'Gas', 'Fun']), 'Showing: Gas');
});

test('split slice follows its own pond only', () => {
  assert.equal(rowVisible({
    transferId: null,
    inRange: true,
    pondSelected: true,
    transfersVisible: true,
    sourceSelected: false,
    anyDestinationSelected: false,
  }), true);
  assert.equal(rowVisible({
    transferId: null,
    inRange: true,
    pondSelected: false,
    transfersVisible: true,
    sourceSelected: false,
    anyDestinationSelected: false,
  }), false);
});

test('transfers stay hidden when the toggle is off', () => {
  assert.equal(rowVisible({
    transferId: 't1',
    inRange: true,
    pondSelected: true,
    transfersVisible: false,
    sourceSelected: true,
    anyDestinationSelected: true,
  }), false);
});

test('transfer row shows when the other pond is checked and transfers are on', () => {
  const selected = new Set(['Groceries']);
  const sides = transferSides(envelopes, 't1', selected);
  assert.equal(sides.sourceSelected, true);
  assert.equal(sides.anyDestinationSelected, false);
  assert.equal(rowVisible({
    transferId: 't1',
    inRange: true,
    pondSelected: false,
    transfersVisible: true,
    ...sides,
  }), true);
});

test('date outside the range is excluded', () => {
  assert.equal(rowVisible({
    transferId: null,
    inRange: false,
    pondSelected: true,
    transfersVisible: true,
    sourceSelected: false,
    anyDestinationSelected: false,
  }), false);
});

test('all ponds selected uses the all-ponds label', () => {
  assert.equal(
    historyShowingLabel(['Groceries', 'Gas'], ['Groceries', 'Gas']),
    'Showing: all ponds',
  );
});

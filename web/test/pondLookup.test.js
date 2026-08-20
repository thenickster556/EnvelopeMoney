import { test } from 'node:test';
import assert from 'node:assert/strict';
import { findByName, canonicalName } from '../domain/pondLookup.js';
import { createEnvelope } from '../domain/envelopeModel.js';

test('findByName exact match', () => {
  const gas = createEnvelope('Gas', 0);
  assert.equal(findByName([gas], 'Gas'), gas);
});

test('findByName trimmed input and stored name', () => {
  const gas = createEnvelope(' Gas ', 0);
  assert.equal(findByName([gas], 'Gas'), gas);
  assert.equal(findByName([createEnvelope('Gas', 0)], ' Gas ').name, 'Gas');
});

test('findByName missing or deleted', () => {
  assert.equal(findByName([createEnvelope('A', 0), createEnvelope('B', 0)], 'Deleted'), null);
  assert.equal(findByName(null, 'A'), null);
  assert.equal(findByName([createEnvelope('A', 0)], null), null);
  assert.equal(findByName([createEnvelope('A', 0)], '   '), null);
});

test('canonicalName returns stored name', () => {
  const gas = createEnvelope(' Gas ', 0);
  assert.equal(canonicalName([gas], 'Gas'), ' Gas ');
});

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { remember, suggestions } from '../domain/commentHistory.js';

test('remember trims and skips empty', () => {
  const existing = ['Fill-up'];
  assert.deepEqual(remember(existing, '   '), existing);
  assert.deepEqual(remember(existing, null), existing);
  assert.deepEqual(remember(existing, '  Market run  '), ['Market run', 'Fill-up']);
});

test('remember merges case insensitive keeping latest casing', () => {
  assert.deepEqual(remember(['fill-up', 'Groceries'], 'Fill-up'), ['Fill-up', 'Groceries']);
});

test('remember moves to front and caps at fifty', () => {
  const existing = [];
  for (let i = 0; i < 50; i++) existing.push(`Note ${i}`);
  const next = remember(existing, 'Newest');
  assert.equal(next.length, 50);
  assert.equal(next[0], 'Newest');
  assert.equal(next[1], 'Note 0');
  assert.equal(next[49], 'Note 48');
});

test('suggestions prefix then contains recency', () => {
  const comments = ['Fill-up', 'Fun night', 'Farmer market', 'Coffee'];
  const matches = suggestions(comments, 'f');
  assert.deepEqual(matches, ['Fill-up', 'Fun night', 'Farmer market', 'Coffee']);
  assert.deepEqual(matches.slice(0, 3), ['Fill-up', 'Fun night', 'Farmer market']);
});

test('suggestions empty query or no match', () => {
  const comments = ['Fill-up', 'Coffee'];
  assert.deepEqual(suggestions(comments, ''), []);
  assert.deepEqual(suggestions(comments, '   '), []);
  assert.deepEqual(suggestions(comments, 'zzz'), []);
});

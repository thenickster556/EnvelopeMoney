import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  joinPath,
  normalizePath,
  fileName,
  receiptsMonthFolder,
  mountainMoneyFileName,
  uniqueFileName,
  shouldHideEntry,
  relativePathFromSelectedFile,
} from '../public/js/storage/pathUtils.js';

test('joinPath and normalizePath keep a logical tree', () => {
  assert.equal(joinPath('receipts', '2026-09', 'walmart.jpg'), 'receipts/2026-09/walmart.jpg');
  assert.equal(normalizePath('\\receipts\\2026-09\\walmart.jpg'), 'receipts/2026-09/walmart.jpg');
  assert.equal(fileName('receipts/2026-09/walmart.jpg'), 'walmart.jpg');
});

test('receiptsMonthFolder and mountainMoneyFileName match the web naming plan', () => {
  assert.equal(receiptsMonthFolder('2026-09'), 'receipts/2026-09');
  assert.equal(mountainMoneyFileName(1710000000000), 'MountainMoney_1710000000000.jpg');
});

test('uniqueFileName appends a copy suffix when the name is taken', async () => {
  const taken = new Set(['MountainMoney_1.jpg']);
  const name = await uniqueFileName('MountainMoney_1.jpg', (candidate) => taken.has(candidate));
  assert.equal(name, 'MountainMoney_1 (1).jpg');
});

test('shouldHideEntry skips dotfiles and thumbnail junk', () => {
  assert.equal(shouldHideEntry('.DS_Store'), true);
  assert.equal(shouldHideEntry('Thumbs.db'), true);
  assert.equal(shouldHideEntry('walmart.jpg'), false);
});

test('relativePathFromSelectedFile strips the chosen folder name and does not flatten', () => {
  assert.equal(
    relativePathFromSelectedFile({ name: 'walmart.jpg', webkitRelativePath: 'MountainMoney/receipts/walmart.jpg' }),
    'receipts/walmart.jpg',
  );
  assert.equal(
    relativePathFromSelectedFile({ name: 'statement.pdf', webkitRelativePath: 'MountainMoney/attachments/statement.pdf' }),
    'attachments/statement.pdf',
  );
  assert.equal(
    relativePathFromSelectedFile({ name: 'solo.jpg', webkitRelativePath: '' }),
    'receipts/solo.jpg',
  );
});

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createMemoryWorkspace } from '../public/js/storage/memoryWorkspace.js';
import { createLocalFileStorage } from '../public/js/storage/localFileStorage.js';
import { createReceiptStorage } from '../public/js/receipts/receiptStorage.js';
import { createGridFsReceiptStorage } from '../public/js/receipts/gridFsReceiptStorage.js';
import { createLocalBrowserReceiptStorage } from '../public/js/receipts/localBrowserReceiptStorage.js';
import { parseFileReference } from '../domain/fileReference.js';

test('server mode uploads to GridFS and never writes the local workspace', async () => {
  const posted = [];
  const workspace = createMemoryWorkspace();
  const localFiles = createLocalFileStorage({ workspace, capabilities: { fileInput: true, opfs: true } });
  const receipts = createReceiptStorage({
    getMode: () => 'server',
    gridFs: createGridFsReceiptStorage({
      post: async (_path, _form) => {
        posted.push('upload');
        return { id: '64b1c2d3e4f5a6b7c8d9e0f1', uri: '/api/receipts/64b1c2d3e4f5a6b7c8d9e0f1' };
      },
      put: async () => ({ uri: '/api/receipts/64b1c2d3e4f5a6b7c8d9e0f1' }),
      getBlob: async () => new Blob(['server']),
    }),
    local: createLocalBrowserReceiptStorage({
      localFileStorage: localFiles,
      now: () => new Date('2026-09-16T12:00:00Z'),
      epochMs: () => 1710000000000,
    }),
  });
  const uri = await receipts.save(new Blob(['jpeg'], { type: 'image/jpeg' }));
  assert.equal(uri, '/api/receipts/64b1c2d3e4f5a6b7c8d9e0f1');
  assert.deepEqual(posted, ['upload']);
  assert.equal(await workspace.exists('receipts/2026-09/MountainMoney_1710000000000.jpg'), false);
});

test('local mode writes a local:// reference and does not call GridFS upload', async () => {
  const posted = [];
  const workspace = createMemoryWorkspace();
  const localFiles = createLocalFileStorage({ workspace, capabilities: { fileInput: true, opfs: true } });
  const receipts = createReceiptStorage({
    getMode: () => 'local',
    gridFs: createGridFsReceiptStorage({
      post: async () => {
        posted.push('upload');
        return { uri: '/api/receipts/should-not-happen' };
      },
      put: async () => ({ uri: '/api/receipts/should-not-happen' }),
      getBlob: async () => new Blob(['server']),
    }),
    local: createLocalBrowserReceiptStorage({
      localFileStorage: localFiles,
      now: () => new Date('2026-09-16T12:00:00Z'),
      epochMs: () => 1710000000000,
    }),
  });
  const uri = await receipts.save(new Blob(['jpeg'], { type: 'image/jpeg' }));
  assert.equal(uri, 'local://receipts/2026-09/MountainMoney_1710000000000.jpg');
  assert.deepEqual(posted, []);
  assert.equal(await workspace.exists('receipts/2026-09/MountainMoney_1710000000000.jpg'), true);
});

test('resolver loads GridFS and local refs independently', async () => {
  const workspace = createMemoryWorkspace();
  await workspace.writeBlob('receipts/phone.jpg', new Blob(['phone']));
  const localFiles = createLocalFileStorage({ workspace, capabilities: { fileInput: true, opfs: true } });
  const receipts = createReceiptStorage({
    getMode: () => 'local',
    gridFs: createGridFsReceiptStorage({
      post: async () => ({ uri: '/api/receipts/64b1c2d3e4f5a6b7c8d9e0f1' }),
      put: async () => ({ uri: '/api/receipts/64b1c2d3e4f5a6b7c8d9e0f1' }),
      getBlob: async (uri) => {
        assert.equal(uri, '/api/receipts/64b1c2d3e4f5a6b7c8d9e0f1');
        return new Blob(['grid']);
      },
    }),
    local: createLocalBrowserReceiptStorage({ localFileStorage: localFiles }),
  });
  const grid = await receipts.loadBlob('/api/receipts/64b1c2d3e4f5a6b7c8d9e0f1');
  const local = await receipts.loadBlob('local://receipts/phone.jpg');
  assert.equal(await grid.text(), 'grid');
  assert.equal(await local.text(), 'phone');
  assert.equal(parseFileReference('local://receipts/phone.jpg').storage, 'local');
});

test('missing local file throws a broken-reference error and does not invent a wipe', async () => {
  const workspace = createMemoryWorkspace();
  const localFiles = createLocalFileStorage({ workspace, capabilities: { fileInput: true, opfs: true } });
  const receipts = createReceiptStorage({
    getMode: () => 'local',
    gridFs: createGridFsReceiptStorage({
      post: async () => ({ uri: '/api/receipts/x' }),
      put: async () => ({ uri: '/api/receipts/x' }),
      getBlob: async () => new Blob(['x']),
    }),
    local: createLocalBrowserReceiptStorage({ localFileStorage: localFiles }),
  });
  await assert.rejects(
    () => receipts.loadBlob('local://receipts/missing.jpg'),
    (err) => err.code === 'broken-reference' || err.code === 'missing',
  );
  assert.equal(await workspace.exists('receipts/missing.jpg'), false);
});

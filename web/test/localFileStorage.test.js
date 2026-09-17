import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createMemoryWorkspace } from '../public/js/storage/memoryWorkspace.js';
import { createLocalFileStorage } from '../public/js/storage/localFileStorage.js';
import { StorageError, StorageErrorCode } from '../public/js/storage/storageErrors.js';
import { relativePathFromSelectedFile } from '../public/js/storage/pathUtils.js';
import { createDirectDirectoryProvider } from '../public/js/storage/providers/directDirectoryProvider.js';
import { importSelectedFiles } from '../public/js/storage/providers/directoryImportProvider.js';
import { createStoreZip, listStoreZipPaths } from '../public/js/storage/storeZip.js';
import { loadReceiptStorageMode, saveReceiptStorageMode } from '../public/js/storage/receiptStorageMode.js';

function cancelledPicker() {
  const err = new Error('The user aborted a request.');
  err.name = 'AbortError';
  return err;
}

test('memory workspace preserves directory hierarchy and skips hidden files', async () => {
  const workspace = createMemoryWorkspace();
  await workspace.writeBlob('receipts/walmart.jpg', new Blob(['img']));
  await workspace.writeBlob('attachments/statement.pdf', new Blob(['pdf']));
  await workspace.writeBlob('.DS_Store', new Blob(['junk']));
  const root = await workspace.list('');
  const names = root.map((entry) => entry.name).sort();
  assert.deepEqual(names, ['attachments', 'receipts']);
  const receipts = await workspace.list('receipts');
  assert.equal(receipts.length, 1);
  assert.equal(receipts[0].path, 'receipts/walmart.jpg');
  assert.equal(await workspace.exists('attachments/statement.pdf'), true);
});

test('LocalFileStorage lists nested files through the active workspace', async () => {
  const workspace = createMemoryWorkspace();
  await workspace.writeBlob('receipts/walmart.jpg', new Blob(['img']));
  const storage = createLocalFileStorage({
    capabilities: { directoryPicker: false, webkitDirectory: true, fileInput: true, opfs: true, secureContext: true },
    workspace,
  });
  assert.equal(storage.hasWorkspace(), true);
  const listed = await storage.list('receipts');
  assert.equal(listed[0].path, 'receipts/walmart.jpg');
  const text = await storage.readText('receipts/walmart.jpg');
  assert.equal(text, 'img');
});

test('LocalFileStorage maps picker cancel to a silent picker-cancelled error', async () => {
  const storage = createLocalFileStorage({
    capabilities: { directoryPicker: true, webkitDirectory: true, fileInput: true, opfs: true, secureContext: true },
    chooseDirectory: async () => {
      throw cancelledPicker();
    },
  });
  await assert.rejects(() => storage.chooseFolder(), (err) => {
    assert.equal(err instanceof StorageError, true);
    assert.equal(err.code, StorageErrorCode.PICKER_CANCELLED);
    return true;
  });
});

test('LocalFileStorage reports permission denied without wiping files', async () => {
  const workspace = createMemoryWorkspace();
  await workspace.writeBlob('receipts/keep.jpg', new Blob(['keep']));
  const storage = createLocalFileStorage({
    capabilities: { directoryPicker: true, fileInput: true, opfs: true, secureContext: true },
    workspace,
    ensurePermission: async () => {
      throw new StorageError(StorageErrorCode.PERMISSION_DENIED, 'Permission denied');
    },
  });
  await assert.rejects(() => storage.writeBlob('receipts/new.jpg', new Blob(['x'])), (err) => {
    assert.equal(err.code, StorageErrorCode.PERMISSION_DENIED);
    return true;
  });
  assert.equal(await storage.exists('receipts/keep.jpg'), true);
});

test('LocalFileStorage surfaces quota errors', async () => {
  const workspace = createMemoryWorkspace({ quotaBytes: 4 });
  const storage = createLocalFileStorage({
    capabilities: { fileInput: true, opfs: true, secureContext: true },
    workspace,
  });
  await assert.rejects(
    () => storage.writeBlob('receipts/big.jpg', new Blob(['hello-world'])),
    (err) => err.code === StorageErrorCode.QUOTA,
  );
});

test('directory import keeps receipts and attachments as separate folders', async () => {
  const files = [
    { name: 'walmart.jpg', webkitRelativePath: 'MountainMoney/receipts/walmart.jpg', blob: new Blob(['a']) },
    { name: 'statement.pdf', webkitRelativePath: 'MountainMoney/attachments/statement.pdf', blob: new Blob(['b']) },
  ];
  const paths = files.map((file) => relativePathFromSelectedFile(file));
  assert.deepEqual(paths.sort(), ['attachments/statement.pdf', 'receipts/walmart.jpg']);
  const workspace = createMemoryWorkspace();
  await importSelectedFiles(files, workspace);
  const root = await workspace.list('');
  assert.deepEqual(root.map((entry) => entry.name).sort(), ['attachments', 'receipts']);
});

test('direct directory provider writes through a fake handle and re-checks permission', async () => {
  const workspace = createMemoryWorkspace();
  let permissionChecks = 0;
  const provider = createDirectDirectoryProvider({
    showDirectoryPicker: async () => ({ name: 'Mountain Money' }),
    attachHandle: async () => workspace,
    handleStore: {
      save: async () => {},
      load: async () => ({ name: 'Mountain Money' }),
      clear: async () => {},
    },
    queryPermission: async () => {
      permissionChecks += 1;
      return 'granted';
    },
  });
  await provider.chooseFolder();
  await provider.writeBlob('receipts/2026-09/shot.jpg', new Blob(['jpeg']));
  assert.equal(await workspace.exists('receipts/2026-09/shot.jpg'), true);
  assert.ok(permissionChecks >= 1);
});

test('export builds a store-only zip that keeps nested paths', async () => {
  const zip = await createStoreZip([
    { path: 'receipts/walmart.jpg', bytes: new Uint8Array([1, 2, 3]) },
    { path: 'attachments/statement.pdf', bytes: new Uint8Array([4, 5]) },
  ]);
  const paths = listStoreZipPaths(zip);
  assert.deepEqual(paths.sort(), ['attachments/statement.pdf', 'receipts/walmart.jpg']);
});

test('receipt storage mode is per-browser and defaults to server', () => {
  const memory = new Map();
  const storage = {
    getItem: (key) => (memory.has(key) ? memory.get(key) : null),
    setItem: (key, value) => { memory.set(key, String(value)); },
  };
  assert.equal(loadReceiptStorageMode(storage), 'server');
  saveReceiptStorageMode('local', storage);
  assert.equal(loadReceiptStorageMode(storage), 'local');
  saveReceiptStorageMode('server', storage);
  assert.equal(loadReceiptStorageMode(storage), 'server');
});

test('honest save label is folder only when the workspace is writable', () => {
  const writable = createLocalFileStorage({
    capabilities: { directoryPicker: true, secureContext: true, fileInput: true, opfs: true },
    workspace: createMemoryWorkspace({ writable: true, folderLabel: 'Mountain Money' }),
  });
  const browserCopy = createLocalFileStorage({
    capabilities: { directoryPicker: false, secureContext: true, fileInput: true, opfs: true },
    workspace: createMemoryWorkspace({ writable: true, writesToOriginalFolder: false }),
  });
  assert.equal(writable.saveDestination(), 'folder');
  assert.equal(browserCopy.saveDestination(), 'browser');
});

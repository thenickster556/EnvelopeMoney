import { test } from 'node:test';
import assert from 'node:assert/strict';
import { replaceKeptId } from '../server/gridFsReplace.js';

function memoryStore() {
  const files = new Map();
  return {
    files,
    failUploadFor: null,
    skipWriteFor: null,
    async uploadTemp() {
      files.set('temp', Buffer.from('rotated'));
      return 'temp';
    },
    async deleteId(id) {
      files.delete(String(id));
    },
    async uploadWithId(id) {
      if (this.failUploadFor === String(id)) {
        this.failUploadFor = null;
        throw new Error('upload failed');
      }
      if (this.skipWriteFor === String(id)) {
        this.skipWriteFor = null;
        return;
      }
      files.set(String(id), Buffer.from('rotated'));
    },
    async originalExists(id) {
      return files.has(String(id));
    },
  };
}

test('replaceKeptId keeps the original id and drops temp', async () => {
  const store = memoryStore();
  store.files.set('orig', Buffer.from('old'));
  await replaceKeptId({
    originalId: 'orig',
    uploadTemp: () => store.uploadTemp(),
    deleteId: (id) => store.deleteId(id),
    uploadWithId: (id) => store.uploadWithId(id),
    originalExists: (id) => store.originalExists(id),
  });
  assert.equal(store.files.has('orig'), true);
  assert.equal(store.files.has('temp'), false);
  assert.equal(store.files.get('orig').toString(), 'rotated');
});

test('replaceKeptId restores original then throws when replacement upload fails', async () => {
  const store = memoryStore();
  store.files.set('orig', Buffer.from('old'));
  store.failUploadFor = 'orig';
  await assert.rejects(() => replaceKeptId({
    originalId: 'orig',
    uploadTemp: () => store.uploadTemp(),
    deleteId: (id) => store.deleteId(id),
    uploadWithId: (id) => store.uploadWithId(id),
    originalExists: (id) => store.originalExists(id),
  }));
  assert.equal(store.files.has('orig'), true);
  assert.equal(store.files.get('orig').toString(), 'rotated');
});

test('replaceKeptId does not succeed when the original id is empty after upload', async () => {
  const store = memoryStore();
  store.files.set('orig', Buffer.from('old'));
  store.skipWriteFor = 'orig';
  await assert.rejects(() => replaceKeptId({
    originalId: 'orig',
    uploadTemp: () => store.uploadTemp(),
    deleteId: (id) => store.deleteId(id),
    uploadWithId: (id) => store.uploadWithId(id),
    originalExists: (id) => store.originalExists(id),
  }));
  assert.equal(store.files.has('orig'), true);
});

import { mapStorageError, StorageError, StorageErrorCode } from './storageErrors.js';

export function createHandleStore(options = {}) {
  const dbName = options.dbName || 'mountain-money-local-files';
  const storeName = options.storeName || 'handles';
  const indexedDB = options.indexedDB
    || (typeof globalThis !== 'undefined' ? globalThis.indexedDB : null);

  function openDb() {
    return new Promise((resolve, reject) => {
      if (!indexedDB) {
        reject(new StorageError(StorageErrorCode.UNSUPPORTED, 'Folder reconnect is unavailable'));
        return;
      }
      const request = indexedDB.open(dbName, 1);
      request.onupgradeneeded = () => {
        const db = request.result;
        if (!db.objectStoreNames.contains(storeName)) {
          db.createObjectStore(storeName);
        }
      };
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
  }

  return {
    async save(key, handle) {
      const db = await openDb();
      try {
        await new Promise((resolve, reject) => {
          const tx = db.transaction(storeName, 'readwrite');
          tx.objectStore(storeName).put(handle, key);
          tx.oncomplete = () => resolve();
          tx.onerror = () => reject(tx.error);
        });
      } catch (err) {
        throw mapStorageError(err);
      } finally {
        db.close();
      }
    },
    async load(key) {
      try {
        const db = await openDb();
        try {
          return await new Promise((resolve, reject) => {
            const tx = db.transaction(storeName, 'readonly');
            const request = tx.objectStore(storeName).get(key);
            request.onsuccess = () => resolve(request.result || null);
            request.onerror = () => reject(request.error);
          });
        } finally {
          db.close();
        }
      } catch {
        return null;
      }
    },
    async clear(key) {
      try {
        const db = await openDb();
        try {
          await new Promise((resolve, reject) => {
            const tx = db.transaction(storeName, 'readwrite');
            tx.objectStore(storeName).delete(key);
            tx.oncomplete = () => resolve();
            tx.onerror = () => reject(tx.error);
          });
        } finally {
          db.close();
        }
      } catch {
        /* ignore */
      }
    },
  };
}

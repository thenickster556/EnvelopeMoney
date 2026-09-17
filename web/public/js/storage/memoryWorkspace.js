import { StorageError, StorageErrorCode } from './storageErrors.js';
import { fileName, normalizePath, parentPath, shouldHideEntry } from './pathUtils.js';

async function toBytes(data) {
  if (data instanceof Uint8Array) return data;
  if (typeof data === 'string') return new TextEncoder().encode(data);
  if (data && typeof data.arrayBuffer === 'function') {
    return new Uint8Array(await data.arrayBuffer());
  }
  throw new StorageError(StorageErrorCode.UNSUPPORTED, 'Unsupported file data');
}

/**
 * In-memory workspace used by tests and as a last-resort working copy.
 */
export function createMemoryWorkspace(options = {}) {
  const files = new Map();
  const quotaBytes = options.quotaBytes == null ? Infinity : Number(options.quotaBytes);
  const writable = options.writable !== false;
  const writesToOriginalFolder = options.writesToOriginalFolder !== false;

  function usedBytes() {
    let total = 0;
    for (const bytes of files.values()) total += bytes.length;
    return total;
  }

  function list(path = '') {
    const prefix = normalizePath(path);
    const dirs = new Set();
    const fileEntries = [];
    for (const filePath of files.keys()) {
      if (shouldHideEntry(fileName(filePath))) continue;
      if (prefix) {
        if (filePath === prefix) continue;
        if (!filePath.startsWith(`${prefix}/`)) continue;
        const rest = filePath.slice(prefix.length + 1);
        const slash = rest.indexOf('/');
        if (slash === -1) {
          fileEntries.push({ name: rest, path: filePath, kind: 'file' });
        } else {
          const dirName = rest.slice(0, slash);
          if (!shouldHideEntry(dirName)) dirs.add(dirName);
        }
      } else {
        const slash = filePath.indexOf('/');
        if (slash === -1) {
          fileEntries.push({ name: filePath, path: filePath, kind: 'file' });
        } else {
          const dirName = filePath.slice(0, slash);
          if (!shouldHideEntry(dirName)) dirs.add(dirName);
        }
      }
    }
    const dirEntries = [...dirs].map((name) => ({
      name,
      path: prefix ? `${prefix}/${name}` : name,
      kind: 'directory',
    }));
    return [...dirEntries, ...fileEntries].sort((a, b) => a.name.localeCompare(b.name));
  }

  async function writeBlob(path, data) {
    if (!writable) {
      throw new StorageError(StorageErrorCode.NOT_WRITABLE, 'Write permission unavailable');
    }
    const key = normalizePath(path);
    const bytes = await toBytes(data);
    const previous = files.get(key);
    const nextUsed = usedBytes() - (previous ? previous.length : 0) + bytes.length;
    if (nextUsed > quotaBytes) {
      throw new StorageError(StorageErrorCode.QUOTA, 'Storage is full');
    }
    files.set(key, bytes);
  }

  return {
    name: 'memory',
    writable,
    writesToOriginalFolder,
    folderLabel: options.folderLabel || null,
    hasWorkspace() {
      return true;
    },
    async list(path) {
      return list(path);
    },
    async exists(path) {
      return files.has(normalizePath(path));
    },
    async readBlob(path) {
      const bytes = files.get(normalizePath(path));
      if (!bytes) throw new StorageError(StorageErrorCode.MISSING, 'Selected file missing');
      return new Blob([bytes]);
    },
    async readText(path) {
      const bytes = files.get(normalizePath(path));
      if (!bytes) throw new StorageError(StorageErrorCode.MISSING, 'Selected file missing');
      return new TextDecoder().decode(bytes);
    },
    async readJSON(path) {
      return JSON.parse(await this.readText(path));
    },
    writeBlob,
    async writeText(path, data) {
      await writeBlob(path, String(data));
    },
    async writeJSON(path, data) {
      await writeBlob(path, JSON.stringify(data));
    },
    async delete(path) {
      files.delete(normalizePath(path));
    },
    async getObjectURL(path, cache) {
      const blob = await this.readBlob(path);
      if (cache && cache.fromBlob) return cache.fromBlob(blob);
      return URL.createObjectURL(blob);
    },
    async disconnect() {
      files.clear();
    },
    parentPath,
  };
}

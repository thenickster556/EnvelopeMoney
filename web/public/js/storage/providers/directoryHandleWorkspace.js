import { mapStorageError, StorageError, StorageErrorCode } from '../storageErrors.js';
import { fileName, normalizePath, shouldHideEntry } from '../pathUtils.js';

async function getDirectoryFromPath(root, path, create) {
  const parts = normalizePath(path).split('/').filter(Boolean);
  let dir = root;
  for (const part of parts) {
    dir = await dir.getDirectoryHandle(part, { create: !!create });
  }
  return dir;
}

async function splitFilePath(root, path, create) {
  const normalized = normalizePath(path);
  const parts = normalized.split('/').filter(Boolean);
  const name = parts.pop();
  let dir = root;
  for (const part of parts) {
    dir = await dir.getDirectoryHandle(part, { create: !!create });
  }
  return { dir, name };
}

/**
 * Wrap a FileSystemDirectoryHandle so LocalFileStorage never talks to the API directly.
 */
export function createDirectoryHandleWorkspace(rootHandle, options = {}) {
  const writable = options.writable !== false;
  const writesToOriginalFolder = options.writesToOriginalFolder !== false;

  async function list(path = '') {
    const dir = normalizePath(path)
      ? await getDirectoryFromPath(rootHandle, path, false)
      : rootHandle;
    const entries = [];
    for await (const entry of dir.values()) {
      if (shouldHideEntry(entry.name)) continue;
      const childPath = normalizePath(path) ? `${normalizePath(path)}/${entry.name}` : entry.name;
      entries.push({
        name: entry.name,
        path: childPath,
        kind: entry.kind === 'directory' ? 'directory' : 'file',
      });
    }
    return entries.sort((a, b) => a.name.localeCompare(b.name));
  }

  return {
    name: options.name || 'directory',
    writable,
    writesToOriginalFolder,
    folderLabel: options.folderLabel || rootHandle.name || null,
    handle: rootHandle,
    hasWorkspace() {
      return true;
    },
    list,
    async exists(path) {
      try {
        const { dir, name } = await splitFilePath(rootHandle, path, false);
        await dir.getFileHandle(name);
        return true;
      } catch {
        return false;
      }
    },
    async readBlob(path) {
      try {
        const { dir, name } = await splitFilePath(rootHandle, path, false);
        const handle = await dir.getFileHandle(name);
        return await handle.getFile();
      } catch (err) {
        throw mapStorageError(err);
      }
    },
    async readText(path) {
      const blob = await this.readBlob(path);
      return blob.text();
    },
    async readJSON(path) {
      return JSON.parse(await this.readText(path));
    },
    async writeBlob(path, data) {
      if (!writable) {
        throw new StorageError(StorageErrorCode.NOT_WRITABLE, 'Write permission unavailable');
      }
      try {
        const { dir, name } = await splitFilePath(rootHandle, path, true);
        const handle = await dir.getFileHandle(name, { create: true });
        const stream = await handle.createWritable();
        await stream.write(data);
        await stream.close();
      } catch (err) {
        throw mapStorageError(err);
      }
    },
    async writeText(path, data) {
      await this.writeBlob(path, data);
    },
    async writeJSON(path, data) {
      await this.writeBlob(path, JSON.stringify(data));
    },
    async delete(path) {
      try {
        const { dir, name } = await splitFilePath(rootHandle, path, false);
        await dir.removeEntry(name);
      } catch (err) {
        throw mapStorageError(err);
      }
    },
    async disconnect() {
      /* handle lifetime is owned by the provider */
    },
    fileName,
  };
}

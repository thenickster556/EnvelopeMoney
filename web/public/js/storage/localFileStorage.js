import { mapStorageError, StorageError, StorageErrorCode } from './storageErrors.js';
import { createStoreZip } from './storeZip.js';
import { importSelectedFiles } from './providers/directoryImportProvider.js';
import { createOpfsWorkingStore } from './providers/opfsWorkingStore.js';

function workspaceFrom(options) {
  return options.workspace || null;
}

async function collectFiles(workspace, path = '') {
  const entries = await workspace.list(path);
  const files = [];
  for (const entry of entries) {
    if (entry.kind === 'directory') {
      files.push(...await collectFiles(workspace, entry.path));
    } else {
      files.push(entry.path);
    }
  }
  return files;
}

function downloadZip(blob, fileName, downloadBlob) {
  if (downloadBlob) return downloadBlob(blob, fileName);
  if (typeof document === 'undefined') return undefined;
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  link.click();
  URL.revokeObjectURL(url);
  return undefined;
}

/**
 * UI talks to this facade only. Providers stay behind it.
 */
export function createLocalFileStorage(options = {}) {
  let workspace = workspaceFrom(options);
  const capabilities = options.capabilities || {};
  const objectUrls = options.objectUrls || null;

  async function requireWorkspace() {
    if (!workspace) {
      throw new StorageError(StorageErrorCode.UNSUPPORTED, 'Choose a folder or files first');
    }
    return workspace;
  }

  async function withPermission(action) {
    try {
      if (options.ensurePermission) await options.ensurePermission();
      return await action();
    } catch (err) {
      throw mapStorageError(err);
    }
  }

  async function ensureWorkingCopy() {
    if (workspace) return workspace;
    if (options.getWorkingStore) {
      workspace = await options.getWorkingStore();
      return workspace;
    }
    workspace = await createOpfsWorkingStore({
      getDirectory: options.getOpfsDirectory,
      allowMemoryFallback: options.allowMemoryFallback === true,
    });
    return workspace;
  }

  return {
    isSupported() {
      return !!(capabilities.directoryPicker || capabilities.webkitDirectory
        || capabilities.fileInput || capabilities.opfs);
    },
    attachWorkspace(next) {
      workspace = next;
    },
    async connectWorkingCopy() {
      return ensureWorkingCopy();
    },
    async chooseFolder() {
      try {
        if (options.chooseDirectory) {
          const next = await options.chooseDirectory();
          if (next && typeof next.list === 'function') workspace = next;
          return;
        }
        if (options.directProvider && capabilities.directoryPicker) {
          workspace = await options.directProvider.chooseFolder();
          if (workspace && typeof workspace.list !== 'function' && options.directProvider.workspace) {
            workspace = options.directProvider.workspace();
          }
          return;
        }
        if (options.pickDirectoryFiles) {
          const files = await options.pickDirectoryFiles();
          await ensureWorkingCopy();
          await importSelectedFiles(files, workspace);
          return;
        }
        throw new StorageError(StorageErrorCode.UNSUPPORTED, 'Choose Folder is not available');
      } catch (err) {
        throw mapStorageError(err);
      }
    },
    async chooseFiles() {
      try {
        const files = options.pickFiles ? await options.pickFiles() : [];
        await ensureWorkingCopy();
        await importSelectedFiles(files, workspace);
      } catch (err) {
        throw mapStorageError(err);
      }
    },
    hasWorkspace() {
      return !!workspace;
    },
    saveDestination() {
      if (workspace && workspace.writable && workspace.writesToOriginalFolder !== false) {
        return 'folder';
      }
      return 'browser';
    },
    folderLabel() {
      return workspace && workspace.folderLabel ? workspace.folderLabel : '';
    },
    async list(path) {
      return (await requireWorkspace()).list(path);
    },
    async listAllPaths() {
      return collectFiles(await requireWorkspace());
    },
    async exists(path) {
      return (await requireWorkspace()).exists(path);
    },
    async readText(path) {
      const current = await requireWorkspace();
      if (current.readText) return current.readText(path);
      const blob = await current.readBlob(path);
      return blob.text();
    },
    async readJSON(path) {
      const text = await this.readText(path);
      return JSON.parse(text);
    },
    async readBlob(path) {
      return (await requireWorkspace()).readBlob(path);
    },
    async writeText(path, data) {
      return withPermission(async () => {
        const current = await requireWorkspace();
        if (current.writeText) return current.writeText(path, data);
        return current.writeBlob(path, data);
      });
    },
    async writeJSON(path, data) {
      return this.writeText(path, JSON.stringify(data));
    },
    async writeBlob(path, data) {
      return withPermission(async () => (await requireWorkspace()).writeBlob(path, data));
    },
    async delete(path) {
      return withPermission(async () => (await requireWorkspace()).delete(path));
    },
    async getObjectURL(path) {
      const blob = await this.readBlob(path);
      if (objectUrls) return objectUrls.fromBlob(blob);
      if (typeof URL !== 'undefined' && URL.createObjectURL) return URL.createObjectURL(blob);
      throw new StorageError(StorageErrorCode.UNSUPPORTED, 'Preview URL unavailable');
    },
    async exportWorkspace() {
      const current = await requireWorkspace();
      const paths = await collectFiles(current);
      const entries = [];
      for (const path of paths) {
        const blob = await current.readBlob(path);
        const bytes = new Uint8Array(await blob.arrayBuffer());
        entries.push({ path, bytes });
      }
      const zip = await createStoreZip(entries);
      const blob = new Blob([zip], { type: 'application/zip' });
      await downloadZip(blob, 'mountain-money-files.zip', options.downloadBlob);
    },
    async disconnect() {
      if (options.directProvider && options.directProvider.disconnect) {
        await options.directProvider.disconnect();
      }
      workspace = null;
    },
  };
}

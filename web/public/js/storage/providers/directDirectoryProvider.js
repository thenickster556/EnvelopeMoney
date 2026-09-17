import { mapStorageError, StorageError, StorageErrorCode } from '../storageErrors.js';
import { createDirectoryHandleWorkspace } from './directoryHandleWorkspace.js';

async function permissionState(handle, queryPermission) {
  if (queryPermission) return queryPermission(handle);
  if (handle && typeof handle.queryPermission === 'function') {
    return handle.queryPermission({ mode: 'readwrite' });
  }
  return 'granted';
}

async function requestPermissionState(handle, requestPermission) {
  if (requestPermission) return requestPermission(handle);
  if (handle && typeof handle.requestPermission === 'function') {
    return handle.requestPermission({ mode: 'readwrite' });
  }
  return 'granted';
}

/**
 * Provider A: live directory access via showDirectoryPicker.
 */
export function createDirectDirectoryProvider(options = {}) {
  let workspace = null;
  let handle = null;
  const handleStore = options.handleStore;

  async function attach(nextHandle) {
    handle = nextHandle;
    if (options.attachHandle) {
      workspace = await options.attachHandle(nextHandle);
    } else {
      workspace = createDirectoryHandleWorkspace(nextHandle, {
        name: 'direct',
        writesToOriginalFolder: true,
        folderLabel: nextHandle && nextHandle.name,
      });
    }
    if (handleStore && nextHandle) {
      await handleStore.save('workspace', nextHandle);
    }
    return workspace;
  }

  async function ensureWritePermission() {
    if (!handle && !options.queryPermission) return;
    const state = await permissionState(handle, options.queryPermission);
    if (state === 'granted') return;
    const requested = await requestPermissionState(handle, options.requestPermission);
    if (requested !== 'granted') {
      throw new StorageError(StorageErrorCode.PERMISSION_DENIED, 'Permission denied');
    }
  }

  return {
    name: 'direct',
    async chooseFolder() {
      try {
        const picker = options.showDirectoryPicker
          || (typeof globalThis.showDirectoryPicker === 'function'
            ? globalThis.showDirectoryPicker.bind(globalThis)
            : null);
        if (!picker) {
          throw new StorageError(StorageErrorCode.UNSUPPORTED, 'Choose Folder is not available');
        }
        const nextHandle = await picker({ mode: 'readwrite' });
        return attach(nextHandle);
      } catch (err) {
        throw mapStorageError(err);
      }
    },
    attach,
    hasWorkspace() {
      return !!workspace;
    },
    workspace() {
      return workspace;
    },
    async list(path) {
      return workspace.list(path);
    },
    async exists(path) {
      return workspace.exists(path);
    },
    async readBlob(path) {
      return workspace.readBlob(path);
    },
    async writeBlob(path, data) {
      await ensureWritePermission();
      return workspace.writeBlob(path, data);
    },
    async disconnect() {
      workspace = null;
      handle = null;
      if (handleStore) await handleStore.clear('workspace');
    },
  };
}

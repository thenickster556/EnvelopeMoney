import { mapStorageError, StorageError, StorageErrorCode } from '../storageErrors.js';
import { createDirectoryHandleWorkspace } from './directoryHandleWorkspace.js';
import { createMemoryWorkspace } from '../memoryWorkspace.js';

/**
 * Working copy when the browser cannot write back to the user's original folder.
 * Never claim this is the original folder.
 */
export async function createOpfsWorkingStore(options = {}) {
  const getDirectory = options.getDirectory
    || (typeof navigator !== 'undefined'
      && navigator.storage
      && typeof navigator.storage.getDirectory === 'function'
      ? () => navigator.storage.getDirectory()
      : null);
  if (!getDirectory) {
    if (options.allowMemoryFallback) {
      return createMemoryWorkspace({
        writable: true,
        writesToOriginalFolder: false,
        folderLabel: null,
      });
    }
    throw new StorageError(StorageErrorCode.UNSUPPORTED, 'Browser-local storage unavailable');
  }
  try {
    const root = await getDirectory();
    const appDir = await root.getDirectoryHandle(options.rootName || 'mountain-money', { create: true });
    return createDirectoryHandleWorkspace(appDir, {
      name: 'opfs',
      writable: true,
      writesToOriginalFolder: false,
      folderLabel: null,
    });
  } catch (err) {
    throw mapStorageError(err);
  }
}

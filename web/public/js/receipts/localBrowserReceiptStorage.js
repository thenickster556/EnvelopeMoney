import { parseFileReference, serializeFileReference } from '../../../domain/fileReference.js';
import { StorageError, StorageErrorCode } from '../storage/storageErrors.js';
import {
  joinPath,
  mountainMoneyFileName,
  receiptsMonthFolder,
  uniqueFileName,
} from '../storage/pathUtils.js';

function yearMonth(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  return `${year}-${month}`;
}

export function createLocalBrowserReceiptStorage(options) {
  const localFileStorage = options.localFileStorage;
  const now = options.now || (() => new Date());
  const epochMs = options.epochMs || (() => Date.now());

  return {
    async save(blob) {
      const folder = receiptsMonthFolder(yearMonth(now()));
      const wanted = mountainMoneyFileName(epochMs());
      const name = await uniqueFileName(wanted, async (candidate) => (
        localFileStorage.exists(joinPath(folder, candidate))
      ));
      const path = joinPath(folder, name);
      await localFileStorage.writeBlob(path, blob);
      return serializeFileReference({ storage: 'local', path });
    },
    async replace(uri, blob) {
      const ref = parseFileReference(uri);
      if (ref.storage !== 'local' || !ref.path) {
        throw new StorageError(StorageErrorCode.BROKEN_REFERENCE, 'Could not open this image.');
      }
      await localFileStorage.writeBlob(ref.path, blob);
      return uri;
    },
    async loadBlob(uri) {
      const ref = parseFileReference(uri);
      if (ref.storage !== 'local' || !ref.path) {
        throw new StorageError(StorageErrorCode.BROKEN_REFERENCE, 'Could not open this image.');
      }
      const exists = await localFileStorage.exists(ref.path);
      if (!exists) {
        throw new StorageError(StorageErrorCode.BROKEN_REFERENCE, 'This picture is on the device where it was saved.');
      }
      return localFileStorage.readBlob(ref.path);
    },
  };
}

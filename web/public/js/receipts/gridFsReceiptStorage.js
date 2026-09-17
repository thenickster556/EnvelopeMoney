import { parseFileReference } from '../../../domain/fileReference.js';
import { StorageError, StorageErrorCode } from '../storage/storageErrors.js';

export function createGridFsReceiptStorage(options) {
  const post = options.post;
  const put = options.put;
  const getBlob = options.getBlob;

  return {
    async save(blob, extra = {}) {
      const result = await post('/api/receipts', {
        image: blob,
        fileName: extra.fileName || 'receipt.jpg',
      });
      return result.uri;
    },
    async replace(uri, blob) {
      const ref = parseFileReference(uri);
      if (ref.storage !== 'gridfs' || !ref.id) {
        throw new StorageError(StorageErrorCode.BROKEN_REFERENCE, 'Could not open this image.');
      }
      const result = await put(`/api/receipts/${ref.id}`, { image: blob, fileName: 'receipt.jpg' });
      return (result && result.uri) || uri;
    },
    async loadBlob(uri) {
      return getBlob(uri);
    },
  };
}

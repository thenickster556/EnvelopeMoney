import { parseFileReference } from '../../../domain/fileReference.js';

/**
 * Chooses GridFS or local-browser storage from the file reference, not from a global assumption.
 */
export function createReceiptStorage(options) {
  const getMode = options.getMode || (() => 'server');
  const gridFs = options.gridFs;
  const local = options.local;

  function backendFor(uri) {
    const ref = parseFileReference(uri);
    if (ref.storage === 'local') return local;
    return gridFs;
  }

  return {
    async save(blob, extra) {
      if (getMode() === 'local') return local.save(blob, extra);
      return gridFs.save(blob, extra);
    },
    async loadBlob(uri) {
      return backendFor(uri).loadBlob(uri);
    },
    async replace(uri, blob) {
      return backendFor(uri).replace(uri, blob);
    },
    async getObjectURL(uri, cache) {
      const blob = await this.loadBlob(uri);
      if (cache && cache.fromBlob) return cache.fromBlob(blob);
      if (typeof URL !== 'undefined' && URL.createObjectURL) return URL.createObjectURL(blob);
      return '';
    },
  };
}

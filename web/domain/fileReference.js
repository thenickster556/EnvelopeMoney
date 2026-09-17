/**
 * Logical file pointers stored on Transaction.receiptImageUri.
 * GridFS keeps /api/receipts/:id. Local device files use local://relative/path.
 */

const LOCAL_PREFIX = 'local://';
const GRIDFS_PREFIX = '/api/receipts/';
const GRIDFS_ID = /^[a-fA-F0-9]{24}$/;

export function parseFileReference(uri) {
  if (uri == null || uri === '') {
    return { storage: 'unknown', raw: uri == null ? uri : '' };
  }
  const value = String(uri);
  if (value.startsWith(LOCAL_PREFIX)) {
    return { storage: 'local', path: value.slice(LOCAL_PREFIX.length) };
  }
  if (value.startsWith(GRIDFS_PREFIX)) {
    const id = value.slice(GRIDFS_PREFIX.length);
    if (GRIDFS_ID.test(id)) {
      return { storage: 'gridfs', id };
    }
  }
  return { storage: 'unknown', raw: value };
}

export function serializeFileReference(ref) {
  if (!ref || typeof ref !== 'object') return '';
  if (ref.storage === 'local') return `${LOCAL_PREFIX}${ref.path || ''}`;
  if (ref.storage === 'gridfs') return `${GRIDFS_PREFIX}${ref.id || ''}`;
  return ref.raw ? String(ref.raw) : '';
}

export function isLocalFileReference(uri) {
  return parseFileReference(uri).storage === 'local';
}

export function isGridFsFileReference(uri) {
  return parseFileReference(uri).storage === 'gridfs';
}

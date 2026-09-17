export const StorageErrorCode = {
  PICKER_CANCELLED: 'picker-cancelled',
  PERMISSION_DENIED: 'permission-denied',
  UNSUPPORTED: 'unsupported',
  NOT_WRITABLE: 'not-writable',
  MISSING: 'missing',
  QUOTA: 'quota',
  BROKEN_REFERENCE: 'broken-reference',
  OFFLINE: 'offline',
};

export class StorageError extends Error {
  constructor(code, message) {
    super(message || code);
    this.name = 'StorageError';
    this.code = code;
  }
}

export function isPickerCancelled(err) {
  return !!(err && (err.code === StorageErrorCode.PICKER_CANCELLED || err.name === 'AbortError'));
}

export function mapStorageError(err) {
  if (!err) return new StorageError(StorageErrorCode.UNSUPPORTED, 'Storage failed');
  if (err instanceof StorageError) return err;
  if (err.name === 'AbortError' || err.code === 20) {
    return new StorageError(StorageErrorCode.PICKER_CANCELLED, 'Picker cancelled');
  }
  if (err.name === 'NotAllowedError' || err.name === 'SecurityError') {
    return new StorageError(StorageErrorCode.PERMISSION_DENIED, 'Permission denied');
  }
  if (err.name === 'QuotaExceededError' || err.code === 22) {
    return new StorageError(StorageErrorCode.QUOTA, 'Storage is full');
  }
  if (err.name === 'NotFoundError') {
    return new StorageError(StorageErrorCode.MISSING, 'Selected file missing');
  }
  return new StorageError(StorageErrorCode.UNSUPPORTED, err.message || 'Storage failed');
}

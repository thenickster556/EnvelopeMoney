export const RECEIPT_STORAGE_MODE_KEY = 'mm.receiptStorageMode';

export function loadReceiptStorageMode(storage) {
  try {
    return storage && storage.getItem(RECEIPT_STORAGE_MODE_KEY) === 'local' ? 'local' : 'server';
  } catch {
    return 'server';
  }
}

export function saveReceiptStorageMode(mode, storage) {
  try {
    if (!storage) return;
    storage.setItem(RECEIPT_STORAGE_MODE_KEY, mode === 'local' ? 'local' : 'server');
  } catch {
    /* private mode may block localStorage */
  }
}

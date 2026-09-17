const HIDDEN_NAMES = new Set(['.ds_store', 'thumbs.db', 'desktop.ini']);

export function normalizePath(path) {
  return String(path || '')
    .replace(/\\/g, '/')
    .replace(/^\.\/+/, '')
    .replace(/^\/+/, '')
    .replace(/\/+$/, '');
}

export function joinPath(...parts) {
  return normalizePath(parts.filter((part) => part != null && part !== '').join('/'));
}

export function fileName(path) {
  const normalized = normalizePath(path);
  const slash = normalized.lastIndexOf('/');
  return slash === -1 ? normalized : normalized.slice(slash + 1);
}

export function parentPath(path) {
  const normalized = normalizePath(path);
  const slash = normalized.lastIndexOf('/');
  return slash === -1 ? '' : normalized.slice(0, slash);
}

export function receiptsMonthFolder(yyyyMm) {
  return joinPath('receipts', String(yyyyMm || ''));
}

export function mountainMoneyFileName(epochMs) {
  return `MountainMoney_${epochMs}.jpg`;
}

export async function uniqueFileName(originalName, existsFn) {
  if (!(await existsFn(originalName))) return originalName;
  const dot = originalName.lastIndexOf('.');
  const base = dot === -1 ? originalName : originalName.slice(0, dot);
  const ext = dot === -1 ? '' : originalName.slice(dot);
  let n = 1;
  while (await existsFn(`${base} (${n})${ext}`)) {
    n += 1;
  }
  return `${base} (${n})${ext}`;
}

export function shouldHideEntry(name) {
  const value = String(name || '');
  if (!value) return true;
  if (value.startsWith('.')) return true;
  return HIDDEN_NAMES.has(value.toLowerCase());
}

/**
 * Keep the folder tree from a directory file input. Strip the selected root
 * folder name so MountainMoney/receipts/a.jpg stays receipts/a.jpg.
 */
export function relativePathFromSelectedFile(file) {
  const name = file && file.name ? String(file.name) : 'file';
  const relative = file && file.webkitRelativePath ? String(file.webkitRelativePath) : '';
  if (!relative) return joinPath('receipts', name);
  const parts = normalizePath(relative).split('/').filter(Boolean);
  if (parts.length <= 1) return joinPath('receipts', parts[0] || name);
  return joinPath(...parts.slice(1));
}

import { detectCapabilities, primaryLocalFilesAction } from './storage/capabilities.js';
import { isPickerCancelled } from './storage/storageErrors.js';

export function pickFromInput(input) {
  return new Promise((resolve) => {
    if (!input) {
      resolve([]);
      return;
    }
    const onChange = () => {
      resolve(Array.from(input.files || []));
      input.value = '';
    };
    input.addEventListener('change', onChange, { once: true });
    input.click();
  });
}

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, (c) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
  }[c]));
}

export function createLocalFilesUi(options) {
  const S = options.S;
  const localFileStorage = options.localFileStorage;
  const capabilities = options.capabilities || detectCapabilities();
  const getMode = options.getMode;
  const setMode = options.setMode;
  const openSheet = options.openSheet;
  const closeSheet = options.closeSheet;
  const toast = options.toast;
  const onWorkspaceChange = options.onWorkspaceChange || (() => {});
  const reconnectFolder = options.reconnectFolder;
  const getNeedsReconnect = options.getNeedsReconnect || (() => false);

  function saveStatusText() {
    if (getMode() !== 'local') return '';
    if (!localFileStorage.hasWorkspace()) return S.chooseFolderOrFiles;
    if (localFileStorage.saveDestination() === 'folder') return S.savedToFolder;
    return S.savedInBrowser;
  }

  async function runAction(fn) {
    try {
      await fn();
      onWorkspaceChange();
      draw();
    } catch (err) {
      if (isPickerCancelled(err)) {
        draw();
        return;
      }
      toast(err && err.message ? err.message : S.folderUnsupported);
      draw();
    }
  }

  function draw() {
    const mode = getMode();
    const primary = primaryLocalFilesAction(capabilities);
    const canFolder = !!(capabilities.directoryPicker || capabilities.webkitDirectory);
    const folderPrimary = primary === 'choose-folder';
    const label = localFileStorage.folderLabel();
    const hasWorkspace = localFileStorage.hasWorkspace();
    const dest = localFileStorage.saveDestination();
    const needsReconnect = getNeedsReconnect();
    const folderLine = hasWorkspace && label
      ? `Folder: ${escapeHtml(label)}`
      : (hasWorkspace ? S.savedInBrowser : S.noFolderYet);
    const writeLine = hasWorkspace
      ? (dest === 'folder' ? S.writableOnDevice : S.savedInBrowser)
      : '';
    openSheet(`
      <h3>${S.localFiles}</h3>
      <p class="muted">${S.localFilesLead}</p>
      <div class="tabs" id="lfModeTabs">
        <button type="button" class="tab ${mode === 'server' ? 'on' : ''}" data-mode="server">${S.receiptsOnServer}</button>
        <button type="button" class="tab ${mode === 'local' ? 'on' : ''}" data-mode="local">${S.receiptsOnDevice}</button>
      </div>
      <p class="local-files-status">${folderLine}</p>
      ${writeLine ? `<p class="muted">${escapeHtml(writeLine)}</p>` : ''}
      ${!capabilities.secureContext ? `<p class="muted">${S.insecureContextNote}</p>` : ''}
      <div class="local-files-actions">
        ${canFolder ? `<button type="button" class="${folderPrimary ? 'btn-primary' : 'btn-secondary'}" id="lfChooseFolder">${S.chooseFolder}</button>` : ''}
        <button type="button" class="${folderPrimary ? 'btn-secondary' : 'btn-primary'}" id="lfChooseFiles">${S.chooseFiles}</button>
        ${hasWorkspace && canFolder ? `<button type="button" class="btn-secondary" id="lfChangeFolder">${S.changeFolder}</button>` : ''}
        ${needsReconnect ? `<button type="button" class="btn-primary" id="lfReconnect">${S.reconnectFolder}</button>` : ''}
        <button type="button" class="btn-secondary" id="lfImport">${S.importFiles}</button>
        <button type="button" class="btn-secondary" id="lfExport" ${hasWorkspace ? '' : 'disabled'}>${S.exportFiles}</button>
      </div>
      <p class="muted">${S.localFilesPrivacy}</p>
      <div class="sheet-actions">
        <button type="button" class="btn-secondary" id="sheetClose">${S.close}</button>
      </div>`);
    document.querySelectorAll('#lfModeTabs [data-mode]').forEach((btn) => {
      btn.onclick = async () => {
        setMode(btn.dataset.mode);
        if (btn.dataset.mode === 'local' && !localFileStorage.hasWorkspace()) {
          try {
            await localFileStorage.connectWorkingCopy();
          } catch {
            /* user can still Choose Files */
          }
        }
        onWorkspaceChange();
        draw();
      };
    });
    const chooseFolder = document.getElementById('lfChooseFolder');
    if (chooseFolder) chooseFolder.onclick = () => runAction(() => localFileStorage.chooseFolder());
    const changeFolder = document.getElementById('lfChangeFolder');
    if (changeFolder) changeFolder.onclick = () => runAction(() => localFileStorage.chooseFolder());
    document.getElementById('lfChooseFiles').onclick = () => runAction(() => localFileStorage.chooseFiles());
    const reconnect = document.getElementById('lfReconnect');
    if (reconnect) reconnect.onclick = () => runAction(() => reconnectFolder());
    document.getElementById('lfImport').onclick = () => runAction(async () => {
      if (canFolder) await localFileStorage.chooseFolder();
      else await localFileStorage.chooseFiles();
    });
    document.getElementById('lfExport').onclick = () => runAction(() => localFileStorage.exportWorkspace());
    document.getElementById('sheetClose').onclick = closeSheet;
  }

  async function ensureLocalWorkspace() {
    if (getMode() !== 'local') return true;
    if (localFileStorage.hasWorkspace()) return true;
    try {
      await localFileStorage.connectWorkingCopy();
    } catch {
      /* fall through */
    }
    if (localFileStorage.hasWorkspace()) return true;
    toast(S.chooseFolderOrFiles);
    return false;
  }

  return {
    open: draw,
    saveStatusText,
    ensureLocalWorkspace,
  };
}

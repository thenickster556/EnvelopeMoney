/**
 * Browser capability detection. Callers inject globalThis so tests never sniff OS.
 */
export function detectCapabilities(globalObj = globalThis) {
  const secureContext = globalObj.isSecureContext === true;
  const nav = globalObj.navigator || {};
  const storage = nav.storage || {};
  const directoryPickerApiPresent = typeof globalObj.showDirectoryPicker === 'function';
  let webkitDirectory = false;
  let fileInput = false;
  try {
    const input = globalObj.document && typeof globalObj.document.createElement === 'function'
      ? globalObj.document.createElement('input')
      : null;
    if (input) {
      fileInput = true;
      webkitDirectory = 'webkitdirectory' in input;
    }
  } catch {
    fileInput = false;
  }
  return {
    secureContext,
    directoryPickerApiPresent,
    directoryPicker: secureContext && directoryPickerApiPresent,
    webkitDirectory,
    fileInput,
    opfs: secureContext && typeof storage.getDirectory === 'function',
  };
}

export function primaryLocalFilesAction(capabilities) {
  if (capabilities && capabilities.directoryPicker) return 'choose-folder';
  return 'choose-files';
}

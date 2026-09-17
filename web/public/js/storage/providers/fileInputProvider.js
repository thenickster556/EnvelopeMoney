import { importSelectedFiles } from './directoryImportProvider.js';

/**
 * Provider C: ordinary multi-file input. Still usable when folders are unavailable.
 */
export function createFileInputProvider(options = {}) {
  return {
    name: 'files',
    async chooseFiles() {
      const files = options.pickFiles ? await options.pickFiles() : [];
      if (!files || files.length === 0) return [];
      if (options.workspace) {
        await importSelectedFiles(files, options.workspace);
      }
      return files;
    },
  };
}

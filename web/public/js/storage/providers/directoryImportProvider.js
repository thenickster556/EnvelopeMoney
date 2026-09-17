import { relativePathFromSelectedFile } from '../pathUtils.js';

/**
 * Provider B: directory file input. Copies the original tree into a workspace.
 */
export async function importSelectedFiles(files, workspace) {
  const list = Array.from(files || []);
  for (const file of list) {
    const path = relativePathFromSelectedFile(file);
    const data = file.blob || file;
    await workspace.writeBlob(path, data);
  }
  return list.map((file) => relativePathFromSelectedFile(file));
}

export function createDirectoryImportProvider(options = {}) {
  return {
    name: 'import',
    async chooseFolder() {
      const files = options.pickDirectoryFiles
        ? await options.pickDirectoryFiles()
        : [];
      if (!files || files.length === 0) return [];
      if (options.workspace) {
        await importSelectedFiles(files, options.workspace);
      }
      return files;
    },
    importSelectedFiles,
  };
}

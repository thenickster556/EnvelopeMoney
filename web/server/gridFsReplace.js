import { Readable } from 'node:stream';
import { pipeline } from 'node:stream/promises';

/**
 * Replace a stored file while keeping the original id. Uploads a temp copy first so a
 * failed rewrite can restore the original id instead of leaving it empty.
 */
export async function replaceKeptId({
  originalId,
  uploadTemp,
  deleteId,
  uploadWithId,
  originalExists,
}) {
  const tempId = await uploadTemp();
  try {
    await deleteId(originalId);
    await uploadWithId(originalId);
    const exists = await originalExists(originalId);
    if (!exists) {
      throw new Error('replacement missing');
    }
    await deleteId(tempId);
  } catch (err) {
    try {
      try {
        await deleteId(originalId);
      } catch {
        /* original may already be gone */
      }
      await uploadWithId(originalId);
    } catch {
      /* restore failed; caller still sees the original error */
    }
    throw err;
  }
}

function isMissingGridFsFile(err) {
  if (!err) return false;
  return err.code === 'ENOENT' || err.codeName === 'FileNotFound' || err.name === 'FileNotFoundError';
}

async function deleteIgnoringMissing(bucket, id) {
  try {
    await bucket.delete(id);
  } catch (err) {
    if (!isMissingGridFsFile(err)) throw err;
  }
}

/**
 * GridFS rotate/replace: temp upload, delete original, write the same id, drop temp.
 */
export async function replaceGridFsFile(bucket, originalId, buffer, options) {
  const filename = options.filename;
  const uploadOptions = {
    contentType: options.contentType,
    metadata: options.metadata,
  };
  await replaceKeptId({
    originalId,
    uploadTemp: async () => {
      const stream = bucket.openUploadStream(filename, uploadOptions);
      await pipeline(Readable.from(buffer), stream);
      return stream.id;
    },
    deleteId: (id) => deleteIgnoringMissing(bucket, id),
    uploadWithId: async (id) => {
      const stream = bucket.openUploadStreamWithId(id, filename, uploadOptions);
      await pipeline(Readable.from(buffer), stream);
    },
    originalExists: options.originalExists,
  });
}

/** Receipt OCR prep that does not touch Tesseract. The original photo is what gets read. */

/**
 * The accurate path is the original camera or gallery file. Resizing or
 * re-encoding it as a smaller JPEG drops small receipt text.
 */
export function ocrSourceImage(file) {
  return file ?? null;
}

/**
 * Prefill only blank amount and comment. A printed receipt date replaces the
 * dialog date so the user can cross-check the photo. A comment the user already
 * typed is left alone.
 */
export function fillEmptyOcrFields(current, draft) {
  const fields = current || {};
  const found = draft || {};
  const amountBlank = String(fields.amount ?? '').trim() === '';
  const commentBlank = String(fields.comment ?? '').trim() === '';
  return {
    amount: amountBlank && found.totalAmount != null ? found.totalAmount : fields.amount,
    date: found.dateYyyyMmDd ? found.dateYyyyMmDd : (fields.date || ''),
    comment: commentBlank && found.merchantForComment
      ? found.merchantForComment
      : (fields.comment || ''),
  };
}

/** One worker for the page. createWorker runs on the first recognize call only. */
export function createOcrSession(createWorker) {
  let pending = null;
  return {
    recognize(image) {
      if (!pending) pending = Promise.resolve().then(() => createWorker());
      return pending.then((worker) => worker.recognize(image));
    },
  };
}

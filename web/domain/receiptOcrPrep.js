/** Receipt OCR prep that does not touch Tesseract. Longest edge matches Android's 1280px cap. */

export const OCR_MAX_EDGE = 1280;

export function scaledSize(width, height, maxEdge = OCR_MAX_EDGE) {
  const w = Number(width) || 0;
  const h = Number(height) || 0;
  if (w <= 0 || h <= 0) return { width: 0, height: 0 };
  const edge = Math.max(w, h);
  if (edge <= maxEdge) return { width: w, height: h };
  const scale = maxEdge / edge;
  return {
    width: Math.max(1, Math.round(w * scale)),
    height: Math.max(1, Math.round(h * scale)),
  };
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

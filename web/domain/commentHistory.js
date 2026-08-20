/**
 * Recency-ordered comment memory for typeahead. Empty query yields no suggestions.
 * Cap is 50 unique comments, case-insensitive merge keeps the latest casing.
 */
export const MAX_COMMENTS = 50;

export function remember(existing, comment) {
  const next = Array.isArray(existing) ? [...existing] : [];
  if (comment == null) return next;
  const trimmed = String(comment).trim();
  if (!trimmed) return next;
  for (let i = next.length - 1; i >= 0; i--) {
    if (next[i].toLowerCase() === trimmed.toLowerCase()) next.splice(i, 1);
  }
  next.unshift(trimmed);
  if (next.length > MAX_COMMENTS) next.length = MAX_COMMENTS;
  return next;
}

export function suggestions(comments, query) {
  if (query == null) return [];
  const needle = String(query).trim().toLowerCase();
  if (!needle) return [];
  const prefix = [];
  const contains = [];
  for (const comment of (Array.isArray(comments) ? comments : [])) {
    const lower = comment.toLowerCase();
    if (lower.startsWith(needle)) prefix.push(comment);
    else if (lower.includes(needle)) contains.push(comment);
  }
  return prefix.concat(contains);
}

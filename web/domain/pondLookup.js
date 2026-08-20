function normalize(value) {
  return value == null ? '' : String(value).trim();
}

export function findByName(envelopes, name) {
  if (!envelopes || name == null) return null;
  const normalized = normalize(name);
  if (normalized === '') return null;
  for (const envelope of envelopes) {
    if (!envelope) continue;
    const envName = envelope.name;
    if (envName != null && normalize(envName) === normalized) {
      return envelope;
    }
  }
  return null;
}

export function canonicalName(envelopes, name) {
  const envelope = findByName(envelopes, name);
  return envelope != null ? envelope.name : null;
}

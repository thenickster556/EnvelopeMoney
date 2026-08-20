function normalize(s) {
  return s == null ? '' : String(s).trim();
}

export function excludingSource(envelopes, sourceEnvelopeName) {
  const srcNorm = normalize(sourceEnvelopeName);
  const out = [];
  if (!envelopes) return out;
  for (const env of envelopes) {
    if (!env) continue;
    const name = env.name;
    if (name == null) continue;
    if (normalize(name) !== srcNorm) out.push(name);
  }
  return out;
}

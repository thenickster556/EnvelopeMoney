/**
 * History list rules for the web demo. None selected shows nothing.
 * Transfer rows stay hidden until the transfers toggle is on, then a row
 * shows when its own pond, the source pond, or a destination pond is checked.
 */

export function rowVisible({
  transferId,
  inRange,
  pondSelected,
  transfersVisible,
  sourceSelected,
  anyDestinationSelected,
}) {
  if (!inRange) return false;
  const isTransfer = transferId != null && String(transferId) !== '';
  if (isTransfer) {
    if (!transfersVisible) return false;
    return !!(pondSelected || sourceSelected || anyDestinationSelected);
  }
  return !!pondSelected;
}

export function historyShowingLabel(selectedNames, allNames) {
  const selected = selectedNames || [];
  const all = allNames || [];
  if (selected.length === 0) return 'Showing: no ponds';
  if (all.length > 0 && selected.length === all.length) return 'Showing: all ponds';
  return `Showing: ${selected.join(' · ')}`;
}

/** Source and destination pond names for a transfer group, from stored buckets or rows. */
export function transferSides(envelopes, transferId, selectedNames) {
  const selected = selectedNames instanceof Set ? selectedNames : new Set(selectedNames || []);
  let sourceName = null;
  const destinations = [];
  for (const envelope of envelopes || []) {
    if (!envelope) continue;
    for (const bucket of envelope.transfers || []) {
      if (!bucket || bucket.id !== transferId) continue;
      sourceName = envelope.name;
      if (bucket.toEnvelope) destinations.push(bucket.toEnvelope);
    }
  }
  if (!sourceName) {
    for (const envelope of envelopes || []) {
      for (const tx of envelope?.transactions || []) {
        if (!tx || tx.transferId !== transferId) continue;
        if (tx.transferBucketId) destinations.push(envelope.name);
        else sourceName = envelope.name;
      }
    }
  }
  return {
    sourceSelected: !!(sourceName && selected.has(sourceName)),
    anyDestinationSelected: destinations.some((name) => selected.has(name)),
  };
}

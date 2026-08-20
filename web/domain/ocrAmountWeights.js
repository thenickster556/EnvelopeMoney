export const FEATURE_COUNT = 5;
export const DOLLAR_SIGN = 0;
export const STRONG_TOTAL_LABEL = 1;
export const TOTAL_LABEL = 2;
export const BOTTOM_HALF = 3;
export const ORDER_OR_POINTS_PENALTY = 4;

export const DEFAULTS = Object.freeze([50, 80, 60, 25, -100]);
export const STEP = 25;
export const CLAMP_MIN = 0;
export const CLAMP_MAX = 120;
export const PENALTY_MIN = -150;
export const PENALTY_MAX = 0;

export function fromDefaults() {
  return DEFAULTS.slice();
}

export function copyOrDefault(weights) {
  if (!weights || weights.length !== FEATURE_COUNT) return fromDefaults();
  return weights.slice();
}

export function encodeWeights(weights) {
  const w = copyOrDefault(weights);
  const buf = new ArrayBuffer(FEATURE_COUNT * 4);
  const view = new DataView(buf);
  for (let i = 0; i < FEATURE_COUNT; i++) view.setFloat32(i * 4, w[i], true);
  return new Uint8Array(buf);
}

export function decodeWeights(blob) {
  if (!blob || blob.length !== FEATURE_COUNT * 4) return fromDefaults();
  try {
    const view = new DataView(blob.buffer, blob.byteOffset, blob.byteLength);
    const out = [];
    for (let i = 0; i < FEATURE_COUNT; i++) out.push(view.getFloat32(i * 4, true));
    return out;
  } catch {
    return fromDefaults();
  }
}

export function clampWeights(weights) {
  const w = copyOrDefault(weights);
  for (let i = 0; i < ORDER_OR_POINTS_PENALTY; i++) {
    w[i] = Math.min(CLAMP_MAX, Math.max(CLAMP_MIN, w[i]));
  }
  w[ORDER_OR_POINTS_PENALTY] = Math.min(PENALTY_MAX, Math.max(PENALTY_MIN, w[ORDER_OR_POINTS_PENALTY]));
  return w;
}

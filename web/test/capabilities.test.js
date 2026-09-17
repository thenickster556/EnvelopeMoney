import { test } from 'node:test';
import assert from 'node:assert/strict';
import { detectCapabilities } from '../public/js/storage/capabilities.js';

test('detectCapabilities requires a secure context for directory picker', () => {
  const caps = detectCapabilities({
    isSecureContext: false,
    showDirectoryPicker: async () => {},
    navigator: { storage: { getDirectory: async () => ({}) } },
    document: {
      createElement: () => ({ webkitdirectory: true }),
    },
  });
  assert.equal(caps.secureContext, false);
  assert.equal(caps.directoryPicker, false);
  assert.equal(caps.opfs, false);
  assert.equal(caps.webkitDirectory, true);
  assert.equal(caps.fileInput, true);
});

test('detectCapabilities enables directory picker and OPFS in a secure context', () => {
  const caps = detectCapabilities({
    isSecureContext: true,
    showDirectoryPicker: async () => {},
    navigator: { storage: { getDirectory: async () => ({}) } },
    document: {
      createElement: () => ({}),
    },
  });
  assert.equal(caps.directoryPicker, true);
  assert.equal(caps.opfs, true);
  assert.equal(caps.webkitDirectory, false);
});

test('detectCapabilities does not sniff user-agent', () => {
  const caps = detectCapabilities({
    isSecureContext: true,
    navigator: {
      userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)',
      storage: {},
    },
    document: {
      createElement: () => ({ webkitdirectory: true }),
    },
  });
  assert.equal(caps.directoryPicker, false);
  assert.equal(caps.webkitDirectory, true);
});

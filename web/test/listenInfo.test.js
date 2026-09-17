import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  resolveHost,
  listLanIPv4,
  formatListenLines,
  sessionCookieOptions,
} from '../server/listenInfo.js';

test('resolveHost defaults to 0.0.0.0 and stays configurable', () => {
  assert.equal(resolveHost({}), '0.0.0.0');
  assert.equal(resolveHost({ HOST: '127.0.0.1' }), '127.0.0.1');
});

test('listLanIPv4 skips internal addresses and IPv6', () => {
  const ips = listLanIPv4({
    lo: [{ address: '127.0.0.1', family: 'IPv4', internal: true }],
    eth0: [
      { address: '192.168.1.42', family: 'IPv4', internal: false },
      { address: 'fe80::1', family: 'IPv6', internal: false },
    ],
  });
  assert.deepEqual(ips, ['192.168.1.42']);
});

test('formatListenLines prints Local and Network URLs without exposing Mongo', () => {
  const lines = formatListenLines({
    protocol: 'http',
    port: 3000,
    lanAddresses: ['192.168.1.42'],
    mongoUri: 'mongodb://127.0.0.1:27017',
  });
  assert.ok(lines.some((line) => line.includes('http://localhost:3000')));
  assert.ok(lines.some((line) => line.includes('http://192.168.1.42:3000')));
  assert.ok(lines.some((line) => line.includes('mongodb://127.0.0.1:27017')));
  assert.ok(lines.some((line) => /not exposed to LAN/i.test(line)));
});

test('sessionCookieOptions sets secure only for HTTPS', () => {
  assert.equal(sessionCookieOptions(false).secure, false);
  assert.equal(sessionCookieOptions(true).secure, true);
  assert.equal(sessionCookieOptions(false).httpOnly, true);
  assert.equal(sessionCookieOptions(false).sameSite, 'lax');
});

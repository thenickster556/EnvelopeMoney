export function resolveHost(env = process.env) {
  return env.HOST || '0.0.0.0';
}

export function listLanIPv4(networkInterfaces) {
  const result = [];
  for (const addresses of Object.values(networkInterfaces || {})) {
    for (const addr of addresses || []) {
      if (addr.internal) continue;
      if (addr.family === 'IPv4' || addr.family === 4) {
        result.push(addr.address);
      }
    }
  }
  return result;
}

export function formatListenLines({
  protocol = 'http',
  port,
  lanAddresses = [],
  mongoUri = 'mongodb://127.0.0.1:27017',
}) {
  const lines = [
    'Mountain Money web demo',
    `  Local:   ${protocol}://localhost:${port}`,
  ];
  for (const ip of lanAddresses) {
    lines.push(`  Network: ${protocol}://${ip}:${port}`);
  }
  if (lanAddresses.length === 0) {
    lines.push('  Network: (no LAN IPv4 address found)');
  }
  lines.push(`  Mongo:   ${mongoUri} (not exposed to LAN)`);
  return lines;
}

export function sessionCookieOptions(useHttps) {
  return {
    httpOnly: true,
    sameSite: 'lax',
    maxAge: 7 * 24 * 60 * 60 * 1000,
    secure: !!useHttps,
  };
}

export function readHttpsOptions(env = process.env, readFileSync) {
  const keyPath = env.HTTPS_KEY;
  const certPath = env.HTTPS_CERT;
  if (!keyPath && !certPath) return null;
  if (!keyPath || !certPath) {
    throw new Error('Set both HTTPS_KEY and HTTPS_CERT to enable local HTTPS.');
  }
  if (!readFileSync) {
    throw new Error('readFileSync is required to load HTTPS certificates.');
  }
  return {
    key: readFileSync(keyPath),
    cert: readFileSync(certPath),
  };
}

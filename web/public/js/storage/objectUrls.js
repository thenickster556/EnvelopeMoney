export function createObjectUrlCache(options = {}) {
  const create = options.createObjectURL
    || (typeof URL !== 'undefined' && URL.createObjectURL ? URL.createObjectURL.bind(URL) : null);
  const revoke = options.revokeObjectURL
    || (typeof URL !== 'undefined' && URL.revokeObjectURL ? URL.revokeObjectURL.bind(URL) : () => {});
  const urls = new Set();

  return {
    fromBlob(blob) {
      if (!create) return '';
      const url = create(blob);
      urls.add(url);
      return url;
    },
    revoke(url) {
      if (!url || !urls.has(url)) return;
      revoke(url);
      urls.delete(url);
    },
    revokeAll() {
      for (const url of urls) revoke(url);
      urls.clear();
    },
  };
}

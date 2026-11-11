const { pathToFileURL } = require('node:url');

/** Only the packaged entry document and its hash routes may access the desktop bridge. */
function isAllowedNavigation(url, entryPath) {
  if (typeof url !== 'string' || typeof entryPath !== 'string') return false;
  try {
    const parsed = new URL(url);
    if (parsed.search) return false;
    parsed.hash = '';
    return parsed.href === pathToFileURL(entryPath).href;
  } catch { return false; }
}

module.exports = { isAllowedNavigation };

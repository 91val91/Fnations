'use strict';
// Règles de bibliothèques/arguments des fichiers de version Mojang.
const os = require('node:os');

function currentOs() {
  const p = process.platform;
  return p === 'win32' ? 'windows' : p === 'darwin' ? 'osx' : 'linux';
}

function currentArch() {
  const a = process.arch;
  return a === 'ia32' ? 'x86' : a; // x64, arm64...
}

/**
 * Évalue une liste de règles. ctx = { os: 'windows'|'osx'|'linux', arch, osVersion, features: {...} }
 * Sans règle : autorisé. Sinon la dernière règle applicable l'emporte.
 */
function allowed(rules, ctx = {}) {
  if (!rules || rules.length === 0) return true;
  const c = { os: currentOs(), arch: currentArch(), osVersion: os.release(), features: {}, ...ctx };
  let result = false;
  for (const r of rules) {
    let match = true;
    if (r.os) {
      if (r.os.name && r.os.name !== c.os) match = false;
      if (r.os.arch && r.os.arch !== c.arch) match = false;
      if (r.os.version) {
        try { if (!new RegExp(r.os.version).test(c.osVersion)) match = false; } catch { match = false; }
      }
    }
    if (r.features) {
      for (const [k, v] of Object.entries(r.features)) if (!!c.features[k] !== v) match = false;
    }
    if (match) result = r.action === 'allow';
  }
  return result;
}

module.exports = { allowed, currentOs, currentArch };

'use strict';
// Synchronisation du modpack du serveur (mods, configurations) à partir d'un manifeste JSON.
const fsp = require('node:fs/promises');
const path = require('node:path');
const { getJson, downloadAll } = require('./http');

/** Chemin relatif sûr (pas de chemin absolu ni de "..") à l'intérieur du dossier de jeu. */
function safeJoin(base, rel) {
  if (typeof rel !== 'string' || !rel || path.isAbsolute(rel) || rel.includes('\0')) throw new Error(`Chemin invalide dans le manifeste : ${rel}`);
  const full = path.resolve(base, rel);
  const root = path.resolve(base) + path.sep;
  if (!full.startsWith(root)) throw new Error(`Chemin hors du dossier de jeu : ${rel}`);
  return full;
}

async function fetchManifest(url) {
  if (url.startsWith('file://')) return JSON.parse(await fsp.readFile(new URL(url), 'utf8'));
  return getJson(url, { headers: { 'Cache-Control': 'no-cache' } });
}

/**
 * Télécharge les fichiers du manifeste dans gameDir et supprime les mods qui n'y figurent pas
 * (sauf si manifest.allowUserMods est vrai). Retourne { downloaded, removed }.
 */
async function syncModpack(manifest, gameDir, progress = () => {}) {
  const files = Array.isArray(manifest.files) ? manifest.files : [];
  const tasks = files.map(f => {
    if (!/^https:\/\//.test(f.url) && !/^http:\/\/(localhost|127\.0\.0\.1)[:/]/.test(f.url)) throw new Error(`URL non sécurisée dans le manifeste : ${f.url}`);
    return { url: f.url, file: safeJoin(gameDir, f.path), sha1: f.sha1, size: f.size };
  });
  await fsp.mkdir(path.join(gameDir, 'mods'), { recursive: true });
  await downloadAll(tasks, (n, t, b, bt) => progress('Mods du serveur', n, t, b, bt), 6);
  const removed = [];
  if (!manifest.allowUserMods) {
    const wanted = new Set(files.filter(f => f.path.replace(/\\/g, '/').startsWith('mods/')).map(f => path.basename(f.path)));
    for (const name of await fsp.readdir(path.join(gameDir, 'mods'))) {
      if (name.endsWith('.jar') && !wanted.has(name)) {
        await fsp.rm(path.join(gameDir, 'mods', name), { force: true });
        removed.push(name);
      }
    }
  }
  return { downloaded: tasks.length, removed };
}

module.exports = { fetchManifest, syncModpack, safeJoin };

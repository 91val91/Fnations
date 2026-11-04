'use strict';
// Téléchargements HTTP : JSON, fichiers avec vérification SHA-1, reprises et parallélisme.
const fs = require('node:fs');
const fsp = require('node:fs/promises');
const path = require('node:path');
const crypto = require('node:crypto');
const { Readable } = require('node:stream');
const { pipeline } = require('node:stream/promises');

const UA = 'FNations-Launcher/0.1 (+https://github.com/)';

async function fetchRetry(url, opts = {}, tries = 4) {
  let last;
  for (let i = 0; i < tries; i++) {
    try {
      const res = await fetch(url, { ...opts, headers: { 'User-Agent': UA, ...(opts.headers || {}) } });
      if (res.status >= 500 || res.status === 429) throw new Error(`HTTP ${res.status} sur ${url}`);
      return res;
    } catch (e) {
      last = e;
      await new Promise(r => setTimeout(r, 500 * 2 ** i));
    }
  }
  throw last;
}

async function getJson(url, opts = {}) {
  const res = await fetchRetry(url, opts);
  if (!res.ok) throw new Error(`HTTP ${res.status} sur ${url}`);
  return res.json();
}

function sha1File(file) {
  return new Promise((resolve, reject) => {
    const h = crypto.createHash('sha1');
    fs.createReadStream(file).on('error', reject).on('data', d => h.update(d)).on('end', () => resolve(h.digest('hex')));
  });
}

async function isValid(file, sha1, size) {
  try {
    const st = await fsp.stat(file);
    if (size && st.size !== size) return false;
    if (!sha1) return st.size > 0;
    return (await sha1File(file)) === sha1.toLowerCase();
  } catch {
    return false;
  }
}

/** Télécharge url -> file si absent ou corrompu. Retourne true si téléchargé. */
async function download(url, file, sha1, size) {
  if (await isValid(file, sha1, size)) return false;
  await fsp.mkdir(path.dirname(file), { recursive: true });
  const tmp = file + '.part';
  for (let attempt = 0; attempt < 3; attempt++) {
    const res = await fetchRetry(url);
    if (!res.ok) throw new Error(`HTTP ${res.status} sur ${url}`);
    await pipeline(Readable.fromWeb(res.body), fs.createWriteStream(tmp));
    if (!sha1 || (await sha1File(tmp)) === sha1.toLowerCase()) {
      await fsp.rename(tmp, file);
      return true;
    }
  }
  await fsp.rm(tmp, { force: true });
  throw new Error(`Somme de contrôle invalide pour ${url}`);
}

/**
 * Télécharge une liste de tâches {url, file, sha1, size} en parallèle.
 * onProgress(done, total, bytesDone, bytesTotal)
 */
async function downloadAll(tasks, onProgress, concurrency = 12) {
  let done = 0, bytes = 0;
  const totalBytes = tasks.reduce((a, t) => a + (t.size || 0), 0);
  let i = 0;
  const worker = async () => {
    while (i < tasks.length) {
      const t = tasks[i++];
      await download(t.url, t.file, t.sha1, t.size);
      done++;
      bytes += t.size || 0;
      if (onProgress) onProgress(done, tasks.length, bytes, totalBytes);
    }
  };
  await Promise.all(Array.from({ length: Math.min(concurrency, tasks.length || 1) }, worker));
}

module.exports = { fetchRetry, getJson, download, downloadAll, sha1File, isValid, UA };

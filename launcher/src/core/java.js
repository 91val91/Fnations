'use strict';
// Environnement Java officiel de Mojang (aucune installation de Java requise chez le joueur).
const fsp = require('node:fs/promises');
const path = require('node:path');
const { getJson, downloadAll } = require('./http');

const ALL = 'https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json';

function platformKey(platform = process.platform, arch = process.arch) {
  if (platform === 'win32') return arch === 'arm64' ? 'windows-arm64' : arch === 'ia32' ? 'windows-x86' : 'windows-x64';
  if (platform === 'darwin') return arch === 'arm64' ? 'mac-os-arm64' : 'mac-os';
  return arch === 'ia32' ? 'linux-i386' : 'linux';
}

function javaExe(dir, platform = process.platform) {
  if (platform === 'win32') return path.join(dir, 'bin', 'javaw.exe');
  if (platform === 'darwin') return path.join(dir, 'jre.bundle', 'Contents', 'Home', 'bin', 'java');
  return path.join(dir, 'bin', 'java');
}

/** Installe (ou vérifie) le runtime Java demandé par la version, retourne le chemin de l'exécutable. */
async function installJava(root, component = 'java-runtime-delta', progress = () => {}) {
  const key = platformKey();
  const dir = path.join(root, 'runtime', component, key);
  const marker = path.join(dir, '.fnations-installed');
  const all = await getJson(ALL);
  const list = (all[key] || {})[component];
  if (!list || !list.length) throw new Error(`Java ${component} indisponible pour ${key}`);
  const entry = list[0];
  try {
    if ((await fsp.readFile(marker, 'utf8')).trim() === entry.manifest.sha1) return javaExe(dir);
  } catch {}
  const manifest = await getJson(entry.manifest.url);
  const files = [];
  const links = [];
  for (const [rel, f] of Object.entries(manifest.files)) {
    const target = path.join(dir, rel);
    if (f.type === 'directory') await fsp.mkdir(target, { recursive: true });
    else if (f.type === 'file') files.push({ url: f.downloads.raw.url, file: target, sha1: f.downloads.raw.sha1, size: f.downloads.raw.size, exec: f.executable });
    else if (f.type === 'link') links.push({ target, to: f.target });
  }
  await downloadAll(files, (n, t, b, bt) => progress('Java ' + entry.version.name, n, t, b, bt), 16);
  if (process.platform !== 'win32') {
    for (const f of files) if (f.exec) await fsp.chmod(f.file, 0o755);
    for (const l of links) {
      await fsp.rm(l.target, { force: true });
      await fsp.mkdir(path.dirname(l.target), { recursive: true });
      await fsp.symlink(l.to, l.target);
    }
  }
  await fsp.writeFile(marker, entry.manifest.sha1);
  return javaExe(dir);
}

module.exports = { installJava, platformKey, javaExe };

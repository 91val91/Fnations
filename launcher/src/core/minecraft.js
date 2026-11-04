'use strict';
// Installation de Minecraft + Fabric : fichiers de version, bibliothèques, assets, et construction de la commande de lancement.
const fsp = require('node:fs/promises');
const path = require('node:path');
const { getJson, download, downloadAll } = require('./http');
const { allowed } = require('./rules');
const { mavenPath, libKey } = require('./maven');

const MANIFEST = 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json';
const FABRIC_META = 'https://meta.fabricmc.net/v2';
const RESOURCES = 'https://resources.download.minecraft.net';

function dirs(root) {
  return {
    root,
    versions: path.join(root, 'versions'),
    libraries: path.join(root, 'libraries'),
    assets: path.join(root, 'assets'),
    runtime: path.join(root, 'runtime'),
    natives: path.join(root, 'natives'),
  };
}

async function readJsonIfValid(file) {
  try { return JSON.parse(await fsp.readFile(file, 'utf8')); } catch { return null; }
}

/** Fichier de version vanilla (téléchargé et vérifié). */
async function vanillaVersion(root, id) {
  const d = dirs(root);
  const file = path.join(d.versions, id, id + '.json');
  let entry;
  try {
    const manifest = await getJson(MANIFEST);
    entry = manifest.versions.find(v => v.id === id);
  } catch (e) {
    const cached = await readJsonIfValid(file);
    if (cached) return cached; // hors ligne : version déjà installée
    throw e;
  }
  if (!entry) throw new Error(`Version Minecraft inconnue : ${id}`);
  await download(entry.url, file, entry.sha1);
  return JSON.parse(await fsp.readFile(file, 'utf8'));
}

/** Profil Fabric (loader) pour une version de Minecraft. */
async function fabricProfile(root, mcVersion, loaderVersion) {
  const d = dirs(root);
  const id = `fabric-loader-${loaderVersion}-${mcVersion}`;
  const file = path.join(d.versions, id, id + '.json');
  try {
    const prof = await getJson(`${FABRIC_META}/versions/loader/${encodeURIComponent(mcVersion)}/${encodeURIComponent(loaderVersion)}/profile/json`);
    await fsp.mkdir(path.dirname(file), { recursive: true });
    await fsp.writeFile(file, JSON.stringify(prof, null, 1));
    return prof;
  } catch (e) {
    const cached = await readJsonIfValid(file);
    if (cached) return cached;
    throw e;
  }
}

/** Fusionne le profil Fabric avec la version vanilla dont il hérite. */
function merge(vanilla, fabric) {
  if (!fabric) return vanilla;
  const seen = new Set();
  const libraries = [];
  for (const lib of [...(fabric.libraries || []), ...(vanilla.libraries || [])]) {
    const k = libKey(lib.name);
    if (seen.has(k)) continue;
    seen.add(k);
    libraries.push(lib);
  }
  return {
    ...vanilla,
    id: fabric.id,
    vanillaId: vanilla.id,
    mainClass: fabric.mainClass || vanilla.mainClass,
    libraries,
    arguments: {
      game: [...((vanilla.arguments || {}).game || []), ...((fabric.arguments || {}).game || [])],
      jvm: [...((vanilla.arguments || {}).jvm || []), ...((fabric.arguments || {}).jvm || [])],
    },
  };
}

/** Liste des fichiers de bibliothèques nécessaires sur cet OS. */
function libraryFiles(root, version, ctx) {
  const d = dirs(root);
  const out = [];
  for (const lib of version.libraries || []) {
    if (!allowed(lib.rules, ctx)) continue;
    const art = lib.downloads && lib.downloads.artifact;
    if (art && art.url) {
      out.push({ url: art.url, file: path.join(d.libraries, art.path || mavenPath(lib.name)), sha1: art.sha1, size: art.size, name: lib.name });
    } else if (lib.url || !lib.downloads) {
      const rel = mavenPath(lib.name);
      const base = (lib.url || 'https://libraries.minecraft.net/').replace(/\/?$/, '/');
      out.push({ url: base + rel, file: path.join(d.libraries, rel), sha1: lib.sha1, size: lib.size, name: lib.name });
    }
  }
  return out;
}

async function installVersion(root, version, progress = () => {}) {
  const d = dirs(root);
  const vid = version.vanillaId || version.id;
  // Jar client
  progress('Client Minecraft', 0, 1);
  const client = version.downloads.client;
  await download(client.url, path.join(d.versions, vid, vid + '.jar'), client.sha1, client.size);
  // Bibliothèques
  const libs = libraryFiles(root, version);
  await downloadAll(libs, (n, t) => progress('Bibliothèques', n, t), 16);
  // Assets
  const ai = version.assetIndex;
  const indexFile = path.join(d.assets, 'indexes', ai.id + '.json');
  await download(ai.url, indexFile, ai.sha1, ai.size);
  const index = JSON.parse(await fsp.readFile(indexFile, 'utf8'));
  const objects = Object.values(index.objects || {});
  const tasks = [];
  const seen = new Set();
  for (const o of objects) {
    if (seen.has(o.hash)) continue;
    seen.add(o.hash);
    const sub = o.hash.slice(0, 2);
    tasks.push({ url: `${RESOURCES}/${sub}/${o.hash}`, file: path.join(d.assets, 'objects', sub, o.hash), sha1: o.hash, size: o.size });
  }
  await downloadAll(tasks, (n, t, b, bt) => progress('Ressources du jeu', n, t, b, bt), 24);
}

/** Construit la ligne de commande Java. */
function buildLaunch(root, version, opt) {
  const d = dirs(root);
  const vid = version.vanillaId || version.id;
  const features = {
    is_demo_user: false,
    has_custom_resolution: !!(opt.width && opt.height),
    is_quick_play_multiplayer: !!opt.server,
    has_quick_plays_support: false,
    is_quick_play_singleplayer: false,
    is_quick_play_realms: false,
  };
  const ctx = { features };
  const classpath = [...libraryFiles(root, version, ctx).map(l => l.file), path.join(d.versions, vid, vid + '.jar')];
  const nativesDir = path.join(d.natives, vid);
  const vars = {
    auth_player_name: opt.account.name,
    version_name: version.id,
    game_directory: opt.gameDir,
    assets_root: d.assets,
    game_assets: d.assets,
    assets_index_name: version.assetIndex.id,
    auth_uuid: opt.account.uuid,
    auth_access_token: opt.account.accessToken,
    auth_session: opt.account.accessToken,
    clientid: opt.clientId || '',
    auth_xuid: opt.account.xuid || '',
    user_type: opt.account.type || 'msa',
    user_properties: '{}',
    version_type: version.type || 'release',
    natives_directory: nativesDir,
    launcher_name: 'fnations-launcher',
    launcher_version: opt.launcherVersion || '0.1.0',
    classpath: classpath.join(path.delimiter),
    classpath_separator: path.delimiter,
    library_directory: d.libraries,
    resolution_width: String(opt.width || 1280),
    resolution_height: String(opt.height || 720),
    quickPlayMultiplayer: opt.server || '',
    quickPlayPath: path.join(opt.gameDir, 'quickPlay', 'log.json'),
  };
  const sub = s => s.replace(/\$\{([a-zA-Z_]+)\}/g, (m, k) => (k in vars ? vars[k] : m));
  const expand = list => {
    const out = [];
    for (const a of list || []) {
      if (typeof a === 'string') out.push(sub(a));
      else if (allowed(a.rules, ctx)) for (const v of [].concat(a.value)) out.push(sub(v));
    }
    return out;
  };
  const jvm = [`-Xmx${opt.memoryMb || 4096}M`, `-Xms${Math.min(opt.memoryMb || 4096, 1024)}M`, '-XX:+UseG1GC', '-XX:+UnlockExperimentalVMOptions',
    '-XX:G1NewSizePercent=20', '-XX:MaxGCPauseMillis=50', ...expand(version.arguments.jvm)];
  // Pas de configuration log4j XML : la sortie standard reste en texte lisible pour la console du launcher.
  for (const extra of opt.extraJvm || []) jvm.push(extra);
  const game = expand(version.arguments.game);
  return { args: [...jvm, version.mainClass, ...game], nativesDir, classpath };
}

module.exports = { vanillaVersion, fabricProfile, merge, libraryFiles, installVersion, buildLaunch, dirs };

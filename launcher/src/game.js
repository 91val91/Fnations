'use strict';
// Chaîne complète « Jouer » : modpack -> Minecraft + Fabric -> Java -> mods -> lancement. Indépendant d'Electron.
const fsp = require('node:fs/promises');
const path = require('node:path');
const mc = require('./core/minecraft');
const java = require('./core/java');
const modpack = require('./core/modpack');
const { launch } = require('./core/launch');

/**
 * @param opt { root, config, settings, session, progress(step, done, total, bytes, bytesTotal), log(line), launcherVersion }
 * @returns le processus du jeu
 */
async function prepareAndLaunch(opt) {
  const { root, config, settings, session } = opt;
  const progress = opt.progress || (() => {});
  const log = opt.log || (() => {});
  const gameDir = path.join(root, 'instance');
  await fsp.mkdir(gameDir, { recursive: true });
  // Premier lancement : jeu en français et pas d'écran d'accessibilité (sinon la connexion automatique attend un clic)
  const options = path.join(gameDir, 'options.txt');
  try { await fsp.access(options); } catch {
    await fsp.writeFile(options, 'onboardAccessibility:false\nlang:fr_fr\nskipMultiplayerWarning:true\njoinedFirstServer:true\n');
  }

  // 1. Manifeste du modpack (mis en cache pour jouer hors ligne)
  progress('Vérification du modpack', 0, 1);
  const cache = path.join(root, 'manifest-cache.json');
  let manifest = null;
  if (config.manifestUrl && !/example\.com/.test(config.manifestUrl)) {
    try {
      manifest = await modpack.fetchManifest(config.manifestUrl);
      await fsp.writeFile(cache, JSON.stringify(manifest));
    } catch (e) {
      log(`[launcher] Manifeste indisponible (${e.message}), utilisation du cache.`);
      try { manifest = JSON.parse(await fsp.readFile(cache, 'utf8')); } catch {}
    }
  }
  manifest = manifest || { files: [], allowUserMods: true };
  const mcVersion = manifest.minecraft || config.minecraftVersion || '1.21.4';
  const loader = (manifest.loader && manifest.loader.version) || config.fabricLoader;

  // 2. Minecraft + Fabric
  progress('Version de Minecraft', 0, 1);
  const vanilla = await mc.vanillaVersion(root, mcVersion);
  const fabric = loader ? await mc.fabricProfile(root, mcVersion, loader) : null;
  const version = mc.merge(vanilla, fabric);
  await mc.installVersion(root, version, progress);

  // 3. Java (runtime officiel Mojang, sauf chemin personnalisé)
  const javaPath = settings.javaPath || await java.installJava(root, (vanilla.javaVersion || {}).component || 'java-runtime-delta', progress);

  // 4. Mods et fichiers du serveur
  const res = await modpack.syncModpack(manifest, gameDir, progress);
  if (res.removed.length) log(`[launcher] Mods retirés (absents du modpack) : ${res.removed.join(', ')}`);

  // 5. Lancement
  const server = manifest.server || { address: config.serverAddress, port: config.serverPort };
  const target = settings.autoConnect && server.address && !/example\.com/.test(server.address) ? `${server.address}:${server.port || 25565}` : null;
  const { args } = mc.buildLaunch(root, version, {
    account: session, gameDir, memoryMb: settings.memoryMb, width: settings.width, height: settings.height,
    server: target, clientId: config.azureClientId, launcherVersion: opt.launcherVersion, extraJvm: opt.extraJvm,
  });
  progress('Lancement du jeu', 1, 1);
  log(`[launcher] Lancement de ${version.id}${target ? ' -> ' + target : ''}`);
  return launch(javaPath, args, gameDir, log);
}

module.exports = { prepareAndLaunch };

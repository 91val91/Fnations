'use strict';
// Tests d'intégration (réseau) : vrais fichiers de version Mojang / Fabric, synchronisation d'un modpack local, ping.
// Ignorés si FNATIONS_OFFLINE=1.
const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const http = require('node:http');
const crypto = require('node:crypto');
const mc = require('../src/core/minecraft');
const modpack = require('../src/core/modpack');
const { ping } = require('../src/core/ping');

const offline = process.env.FNATIONS_OFFLINE === '1';
const root = fs.mkdtempSync(path.join(os.tmpdir(), 'fn-launcher-'));

test('Minecraft 1.21.4 + Fabric : fusion et ligne de commande', { skip: offline }, async () => {
  const vanilla = await mc.vanillaVersion(root, '1.21.4');
  assert.equal(vanilla.javaVersion.majorVersion, 21);
  const fabric = await mc.fabricProfile(root, '1.21.4', '0.16.14');
  const v = mc.merge(vanilla, fabric);
  assert.equal(v.mainClass, 'net.fabricmc.loader.impl.launch.knot.KnotClient');
  for (const osName of ['windows', 'linux', 'osx']) {
    const libs = mc.libraryFiles(root, v, { os: osName, arch: 'x64' });
    assert.ok(libs.some(l => l.name.startsWith('net.fabricmc:fabric-loader')), 'fabric-loader présent');
    assert.ok(libs.some(l => /lwjgl.*natives-/.test(l.name)), 'natives LWJGL présentes pour ' + osName);
    const natives = libs.filter(l => /natives-/.test(l.name)).map(l => l.name);
    const others = ['windows', 'linux', 'macos'].filter(o => o !== (osName === 'osx' ? 'macos' : osName));
    assert.ok(!natives.some(n => others.some(o => n.includes('natives-' + o + ':') || n.endsWith('natives-' + o))), 'pas de natives d\'un autre OS pour ' + osName);
  }
  const { args } = mc.buildLaunch(root, v, {
    account: { name: 'Joueur', uuid: '0123456789abcdef0123456789abcdef', accessToken: 'TOKEN', xuid: '1' },
    gameDir: path.join(root, 'instance'), memoryMb: 3072, width: 1280, height: 720, server: 'play.example.com:25565',
  });
  const line = args.join(' ');
  assert.ok(!/\$\{[a-z_]+\}/.test(line), 'toutes les variables sont remplacées : ' + (line.match(/\$\{[a-z_]+\}/) || [''])[0]);
  assert.ok(args.includes('-Xmx3072M'));
  assert.ok(args.includes('net.fabricmc.loader.impl.launch.knot.KnotClient'));
  const qp = args.indexOf('--quickPlayMultiplayer');
  assert.ok(qp > 0 && args[qp + 1] === 'play.example.com:25565', 'connexion automatique au serveur');
  assert.equal(args[args.indexOf('--accessToken') + 1], 'TOKEN');
  assert.equal(args[args.indexOf('--userType') + 1], 'msa');
  assert.equal(args[args.indexOf('--width') + 1], '1280');
});

test('modpack : téléchargement vérifié et purge des mods en trop', async () => {
  const content = Buffer.from('fichier de mod ' + crypto.randomUUID());
  const sha1 = crypto.createHash('sha1').update(content).digest('hex');
  const server = http.createServer((req, res) => { res.writeHead(200); res.end(content); }).listen(0, '127.0.0.1');
  await new Promise(r => server.once('listening', r));
  const port = server.address().port;
  const gameDir = path.join(root, 'game');
  fs.mkdirSync(path.join(gameDir, 'mods'), { recursive: true });
  fs.writeFileSync(path.join(gameDir, 'mods', 'triche.jar'), 'x');
  const manifest = { files: [{ path: 'mods/fnations.jar', url: `http://127.0.0.1:${port}/fnations.jar`, sha1, size: content.length }] };
  const r = await modpack.syncModpack(manifest, gameDir);
  server.close();
  assert.deepEqual(r.removed, ['triche.jar']);
  assert.equal(fs.readFileSync(path.join(gameDir, 'mods', 'fnations.jar')).toString(), content.toString());
  await assert.rejects(modpack.syncModpack({ files: [{ path: 'mods/x.jar', url: 'http://evil.example/x.jar' }] }, gameDir), /non sécurisée/);
  await assert.rejects(modpack.syncModpack({ files: [{ path: '../x.jar', url: 'https://a.b/x.jar' }] }, gameDir), /Chemin/);
});

test('ping du serveur local (si un serveur tourne sur 25565)', async t => {
  try {
    const s = await ping('127.0.0.1', 25565, 2000);
    assert.ok(s.online);
    assert.ok(typeof s.players.max === 'number');
    t.diagnostic(`serveur en ligne : ${s.players.online}/${s.players.max}, ${s.version && s.version.name}, ${s.latency} ms`);
  } catch {
    t.skip('aucun serveur local');
  }
});

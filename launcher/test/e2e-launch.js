'use strict';
// Essai de bout en bout (manuel) : installe Minecraft 1.21.4 + Fabric + Java via le launcher, synchronise un modpack
// servi localement, puis lance le vrai jeu (session hors ligne) qui se connecte automatiquement au serveur local.
// Usage : xvfb-run node test/e2e-launch.js <dossier-racine> <dossier-des-mods>
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const crypto = require('node:crypto');
const { prepareAndLaunch } = require('../src/game');

const root = path.resolve(process.argv[2]);
const modsDir = path.resolve(process.argv[3]);
const files = fs.readdirSync(modsDir).filter(f => f.endsWith('.jar'));

const server = http.createServer((req, res) => {
  const name = decodeURIComponent(req.url.slice(1));
  if (!files.includes(name)) { res.writeHead(404); return res.end(); }
  res.writeHead(200);
  fs.createReadStream(path.join(modsDir, name)).pipe(res);
}).listen(0, '127.0.0.1', async () => {
  const port = server.address().port;
  const manifest = {
    name: 'FNations (test)', minecraft: '1.21.4', loader: { type: 'fabric', version: '0.16.14' },
    server: { address: '127.0.0.1', port: 25565 },
    files: files.map(f => {
      const buf = fs.readFileSync(path.join(modsDir, f));
      return { path: 'mods/' + f, url: `http://127.0.0.1:${port}/${encodeURIComponent(f)}`, sha1: crypto.createHash('sha1').update(buf).digest('hex'), size: buf.length };
    }),
  };
  const mf = path.join(root, 'test-manifest.json');
  fs.mkdirSync(root, { recursive: true });
  fs.writeFileSync(mf, JSON.stringify(manifest, null, 1));
  const started = Date.now();
  let last = '';
  const proc = await prepareAndLaunch({
    root,
    config: { manifestUrl: 'file://' + mf, minecraftVersion: '1.21.4', fabricLoader: '0.16.14', azureClientId: '' },
    settings: { memoryMb: 2048, width: 854, height: 480, autoConnect: true, javaPath: '' },
    session: { name: 'FnLauncher', uuid: crypto.randomUUID().replace(/-/g, ''), accessToken: '0', xuid: '', type: 'msa' },
    progress: (step, done, total) => { const s = `${step} ${done}/${total}`; if (s !== last && (done === total || done % 200 === 0)) { console.log('[progression]', s); last = s; } },
    log: line => { if (/FNations|fnations|Connecting to|Loading Minecraft|Fabric Loader|Exception|ERROR|joined/i.test(line)) console.log('[jeu]', line.slice(0, 220)); },
  });
  console.log(`[e2e] jeu lancé après ${((Date.now() - started) / 1000).toFixed(0)} s (pid ${proc.pid})`);
  setTimeout(() => { console.log('[e2e] arrêt du jeu'); proc.kill(); server.close(); }, Number(process.env.E2E_SECONDS || 150) * 1000);
  proc.on('exit', code => { console.log('[e2e] fin du processus', code); process.exit(0); });
});

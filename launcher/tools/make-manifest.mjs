#!/usr/bin/env node
// Génère le manifeste du modpack lu par le launcher.
// Usage : node tools/make-manifest.mjs <dossier-mods> <url-de-base> [adresse-serveur] [port] > manifest.json
//   ex. : node tools/make-manifest.mjs ../dist/mods https://cdn.mon-serveur.fr/fnations/mods play.mon-serveur.fr 25565
import { createHash } from 'node:crypto';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';

const [dir, baseUrl, address = 'play.example.com', port = '25565'] = process.argv.slice(2);
if (!dir || !baseUrl) {
  console.error('Usage : node tools/make-manifest.mjs <dossier-mods> <url-de-base> [adresse] [port]');
  process.exit(1);
}
const files = readdirSync(dir).filter(f => f.endsWith('.jar')).sort().map(f => {
  const buf = readFileSync(join(dir, f));
  return {
    path: 'mods/' + f,
    url: baseUrl.replace(/\/$/, '') + '/' + encodeURIComponent(f),
    sha1: createHash('sha1').update(buf).digest('hex'),
    size: statSync(join(dir, f)).size,
  };
});
const manifest = {
  name: 'FNations',
  minecraft: '1.21.4',
  loader: { type: 'fabric', version: '0.16.14' },
  server: { address, port: Number(port) },
  allowUserMods: false,
  files,
  news: [{ title: 'Bienvenue sur FNations', date: new Date().toLocaleDateString('fr-FR'), body: 'Fondez votre nation et partez à la conquête de l\'espace !' }],
};
process.stdout.write(JSON.stringify(manifest, null, 2) + '\n');

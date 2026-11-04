'use strict';
// Lancement du processus Java du jeu.
const { spawn } = require('node:child_process');
const fsp = require('node:fs/promises');

async function launch(java, args, gameDir, onLog = () => {}, { detached = false } = {}) {
  await fsp.mkdir(gameDir, { recursive: true });
  const proc = spawn(java, args, { cwd: gameDir, detached, windowsHide: true });
  const pump = stream => {
    let buf = '';
    stream.on('data', d => {
      buf += d.toString();
      let i;
      while ((i = buf.indexOf('\n')) >= 0) {
        onLog(buf.slice(0, i).replace(/\r$/, ''));
        buf = buf.slice(i + 1);
      }
    });
  };
  pump(proc.stdout);
  pump(proc.stderr);
  return proc;
}

/** Masque le jeton d'accès dans une ligne de commande avant de l'afficher. */
function redact(args, token) {
  return args.map(a => (token && a.includes(token) ? a.replace(token, '••••') : a));
}

module.exports = { launch, redact };

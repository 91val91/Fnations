'use strict';
// Server List Ping (protocole Minecraft) : état du serveur, joueurs en ligne, MOTD.
const net = require('node:net');

function varint(n) {
  const out = [];
  do {
    let b = n & 0x7f;
    n >>>= 7;
    if (n !== 0) b |= 0x80;
    out.push(b);
  } while (n !== 0);
  return Buffer.from(out);
}

function readVarint(buf, off) {
  let v = 0, shift = 0, b;
  do {
    if (off >= buf.length) return null;
    b = buf[off++];
    v |= (b & 0x7f) << shift;
    shift += 7;
  } while (b & 0x80);
  return [v, off];
}

function packet(id, payload) {
  const body = Buffer.concat([varint(id), payload]);
  return Buffer.concat([varint(body.length), body]);
}

function ping(host, port = 25565, timeout = 5000) {
  return new Promise((resolve, reject) => {
    const started = Date.now();
    const sock = net.createConnection({ host, port });
    let data = Buffer.alloc(0);
    const fail = e => { sock.destroy(); reject(e); };
    sock.setTimeout(timeout, () => fail(new Error('Délai dépassé')));
    sock.on('error', fail);
    sock.on('connect', () => {
      const h = Buffer.from(host, 'utf8');
      const p = Buffer.alloc(2);
      p.writeUInt16BE(port);
      sock.write(packet(0, Buffer.concat([varint(769), varint(h.length), h, p, varint(1)])));
      sock.write(packet(0, Buffer.alloc(0)));
    });
    sock.on('data', chunk => {
      data = Buffer.concat([data, chunk]);
      const len = readVarint(data, 0);
      if (!len || data.length < len[1] + len[0]) return;
      const id = readVarint(data, len[1]);
      const sl = readVarint(data, id[1]);
      const json = data.slice(sl[1], sl[1] + sl[0]).toString('utf8');
      sock.end();
      try {
        const st = JSON.parse(json);
        resolve({ online: true, latency: Date.now() - started, players: st.players || { online: 0, max: 0 }, version: st.version, motd: st.description, favicon: st.favicon });
      } catch (e) {
        reject(e);
      }
    });
  });
}

/** Texte brut d'un composant de MOTD. */
function motdText(d) {
  if (!d) return '';
  if (typeof d === 'string') return d.replace(/§./g, '');
  return (d.text || '') + (d.extra || []).map(motdText).join('');
}

module.exports = { ping, motdText, varint, readVarint };

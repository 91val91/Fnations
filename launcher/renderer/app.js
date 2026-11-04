'use strict';
// Interface du launcher (aucun accès Node : tout passe par window.fn exposé par preload.js).
const $ = id => document.getElementById(id);
let state = { info: null, running: false, logs: [] };

function show(view) {
  $('view-login').classList.toggle('hidden', view !== 'login');
  $('view-main').classList.toggle('hidden', view !== 'main');
}

function drawHead(dataUrl) {
  const c = $('head'), g = c.getContext('2d');
  g.imageSmoothingEnabled = false;
  g.clearRect(0, 0, 64, 64);
  if (!dataUrl) {
    g.fillStyle = '#c9a13c'; g.fillRect(0, 0, 64, 64);
    g.fillStyle = '#1a1406'; g.fillRect(14, 26, 10, 8); g.fillRect(40, 26, 10, 8);
    return;
  }
  const img = new Image();
  img.onload = () => { g.drawImage(img, 8, 8, 8, 8, 0, 0, 64, 64); g.drawImage(img, 40, 8, 8, 8, 0, 0, 64, 64); };
  img.src = dataUrl;
}

function renderAccounts(list) {
  const sel = list.find(a => a.selected) || list[0];
  if (!sel) { show('login'); return; }
  $('player-name').textContent = sel.name;
  drawHead(sel.skin);
  const box = $('accounts');
  box.textContent = '';
  for (const a of list) {
    if (a.uuid === sel.uuid) continue;
    const b = document.createElement('button');
    b.textContent = '↔ ' + a.name;
    b.onclick = async () => renderAccounts(await window.fn.selectAccount(a.uuid));
    box.appendChild(b);
  }
  show('main');
}

function setProgress(text, ratio) {
  $('progress-text').textContent = text;
  $('progress-bar').style.width = Math.max(0, Math.min(100, ratio * 100)).toFixed(1) + '%';
}

function fmtBytes(b) { return b > 1e9 ? (b / 1e9).toFixed(2) + ' Go' : b > 1e6 ? (b / 1e6).toFixed(1) + ' Mo' : Math.round(b / 1e3) + ' Ko'; }

async function refreshStatus() {
  const s = await window.fn.serverStatus();
  const el = $('status');
  el.classList.toggle('online', !!s.online);
  el.classList.toggle('offline', !s.online);
  $('status-text').textContent = s.online ? 'En ligne' : 'Hors ligne';
  $('players').textContent = s.online ? `${s.players.online} / ${s.players.max}` : '—';
  $('mc-version').textContent = s.online && s.version ? s.version : (state.info ? state.info.config.minecraftVersion : '—');
  $('latency').textContent = s.online ? s.latency + ' ms' : '—';
}

async function loadNews() {
  const news = await window.fn.news();
  if (!news.length) return;
  const box = $('news');
  box.textContent = '';
  for (const n of news) {
    const d = document.createElement('div');
    d.className = 'news-item';
    const t = document.createElement('b'); t.textContent = n.title || '';
    const date = document.createElement('span'); date.className = 'date'; date.textContent = n.date || '';
    const p = document.createElement('p'); p.textContent = n.body || '';
    d.append(t, date, p);
    box.appendChild(d);
  }
}

function openSettings() {
  const s = state.info.settings;
  $('ram').value = s.memoryMb;
  $('ram-label').textContent = (s.memoryMb / 1024).toFixed(1).replace('.0', '') + ' Go';
  $('res-w').value = s.width; $('res-h').value = s.height;
  $('auto-connect').checked = s.autoConnect; $('keep-open').checked = s.keepOpen;
  $('java').value = s.javaPath || '';
  $('settings').classList.remove('hidden');
}

async function login(fn) {
  $('login-error').textContent = '';
  const r = await fn();
  if (r.error) { $('login-error').textContent = r.error; return; }
  renderAccounts(r.accounts);
}

async function init() {
  state.info = await window.fn.info();
  $('version').textContent = 'Launcher v' + state.info.version;
  $('server-name').textContent = state.info.config.serverName || 'FNations';
  if (state.info.accounts.length) renderAccounts(state.info.accounts); else show('login');
  refreshStatus();
  loadNews();
  setInterval(refreshStatus, 30000);
}

// ---- Connexion
$('btn-ms').onclick = () => login(window.fn.login);
$('btn-add').onclick = () => { show('login'); };
$('btn-device').onclick = async () => {
  $('login-error').textContent = '';
  const r = await window.fn.loginDevice();
  if (r.error) { $('login-error').textContent = r.error; return; }
  $('device-code').textContent = r.userCode;
  $('device-link').textContent = r.uri.replace(/^https:\/\//, '');
  $('device-link').onclick = e => { e.preventDefault(); window.fn.open(r.uri); };
  $('device-box').classList.remove('hidden');
};
$('btn-device-cancel').onclick = () => { window.fn.cancelLogin(); $('device-box').classList.add('hidden'); };
window.fn.on('auth:device-done', r => {
  $('device-box').classList.add('hidden');
  if (r.error) $('login-error').textContent = r.error; else renderAccounts(r.accounts);
});
$('btn-logout').onclick = async () => {
  const sel = (await window.fn.info()).accounts.find(a => a.selected);
  if (!sel) return;
  const list = await window.fn.removeAccount(sel.uuid);
  if (list.length) renderAccounts(list); else show('login');
};

// ---- Jeu
$('btn-play').onclick = async () => {
  if (state.running) return;
  $('btn-play').disabled = true;
  $('btn-play').textContent = '…';
  setProgress('Préparation…', 0);
  const r = await window.fn.play();
  if (r.error) {
    setProgress('Erreur : ' + r.error, 0);
    $('btn-play').disabled = false;
    $('btn-play').textContent = 'JOUER';
  }
};
window.fn.on('game:progress', p => {
  const ratio = p.bytesTotal ? p.bytes / p.bytesTotal : (p.total ? p.done / p.total : 0);
  const extra = p.bytesTotal ? ` — ${fmtBytes(p.bytes)} / ${fmtBytes(p.bytesTotal)}` : p.total > 1 ? ` — ${p.done}/${p.total}` : '';
  setProgress(p.step + extra, ratio);
});
window.fn.on('game:log', line => {
  state.logs.push(line);
  if (state.logs.length > 600) state.logs.shift();
  const c = $('console');
  c.textContent = state.logs.join('\n');
  c.scrollTop = c.scrollHeight;
});
window.fn.on('game:state', s => {
  state.running = s.running;
  $('btn-play').disabled = s.running;
  $('btn-play').textContent = s.running ? 'EN JEU' : 'JOUER';
  setProgress(s.running ? 'Le jeu est lancé — bon jeu !' : (s.code ? `Le jeu s'est arrêté (code ${s.code}). Voir le journal.` : 'Prêt à jouer'), s.running ? 1 : 0);
});
$('btn-console').onclick = () => $('console').classList.toggle('hidden');

// ---- Barre latérale
$('btn-settings').onclick = openSettings;
$('btn-folder').onclick = () => window.fn.open('gameDir');
$('btn-site').onclick = () => state.info.config.website && window.fn.open(state.info.config.website);
$('btn-discord').onclick = () => state.info.config.discord && window.fn.open(state.info.config.discord);
$('ram').oninput = e => { $('ram-label').textContent = (e.target.value / 1024).toFixed(1).replace('.0', '') + ' Go'; };
$('set-cancel').onclick = () => $('settings').classList.add('hidden');
$('set-save').onclick = async () => {
  state.info.settings = await window.fn.saveSettings({
    memoryMb: $('ram').value, width: $('res-w').value, height: $('res-h').value,
    autoConnect: $('auto-connect').checked, keepOpen: $('keep-open').checked, javaPath: $('java').value,
  });
  $('settings').classList.add('hidden');
};
$('w-min').onclick = () => window.fn.window('minimize');
$('w-close').onclick = () => window.fn.window('close');

init();

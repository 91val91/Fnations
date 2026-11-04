'use strict';
// Processus principal Electron du launcher FNations.
const { app, BrowserWindow, ipcMain, safeStorage, shell, nativeTheme } = require('electron');
const path = require('node:path');
const crypto = require('node:crypto');
const ms = require('./src/auth/microsoft');
const { Accounts } = require('./src/auth/accounts');
const { loadConfig, loadSettings, saveSettings } = require('./src/config');
const { ping, motdText } = require('./src/core/ping');
const modpack = require('./src/core/modpack');
const { prepareAndLaunch } = require('./src/game');

let win, accounts, config, settings, game = null, deviceAbort = null;

const userDir = () => app.getPath('userData');
const gameRoot = () => path.join(app.getPath('appData'), config.gameDirName || '.fnations');
const send = (ch, data) => { if (win && !win.isDestroyed()) win.webContents.send(ch, data); };

function createWindow() {
  nativeTheme.themeSource = 'dark';
  win = new BrowserWindow({
    width: 1080, height: 660, minWidth: 900, minHeight: 560, frame: false, backgroundColor: '#101317', show: false,
    icon: path.join(__dirname, 'build', 'icon.png'),
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true, nodeIntegration: false, sandbox: true, spellcheck: false },
  });
  win.once('ready-to-show', () => win.show());
  win.webContents.setWindowOpenHandler(({ url }) => { if (/^https:\/\//.test(url)) shell.openExternal(url); return { action: 'deny' }; });
  win.webContents.on('will-navigate', e => e.preventDefault());
  win.loadFile(path.join(__dirname, 'renderer', 'index.html'));
}

// ------------------------------------------------------------ connexion Microsoft (navigateur intégré + PKCE)
function interactiveLogin() {
  return new Promise((resolve, reject) => {
    if (!config.azureClientId || config.azureClientId.startsWith('VOTRE')) {
      reject(new ms.AuthError("ID d'application Azure non configuré (config/launcher.json, voir README).", 'no_client_id'));
      return;
    }
    const { verifier, challenge } = ms.pkce();
    const state = crypto.randomUUID();
    const w = new BrowserWindow({
      width: 520, height: 700, parent: win, modal: true, autoHideMenuBar: true, title: 'Connexion Microsoft', backgroundColor: '#ffffff',
      webPreferences: { partition: 'persist:msa-login', contextIsolation: true, nodeIntegration: false, sandbox: true },
    });
    let finished = false;
    const handle = url => {
      let code;
      try { code = ms.parseRedirect(url || '', state); } catch (e) { finished = true; w.destroy(); reject(e); return true; }
      if (code === null) return false;
      finished = true;
      w.destroy();
      ms.exchangeCode(config.azureClientId, code, verifier).then(resolve, reject);
      return true;
    };
    w.webContents.on('will-redirect', (e, url) => { if (handle(e.url || url)) e.preventDefault(); });
    w.webContents.on('will-navigate', (e, url) => { if (handle(e.url || url)) e.preventDefault(); });
    w.webContents.on('did-redirect-navigation', (e, url) => handle(e.url || url));
    w.on('closed', () => { if (!finished) reject(new ms.AuthError('Connexion annulée.', 'cancelled')); });
    w.loadURL(ms.authorizeUrl(config.azureClientId, challenge, state));
  });
}

async function skinDataUrl(url) {
  if (!url || !/^https:\/\/textures\.minecraft\.net\//.test(url)) return null;
  try {
    const res = await fetch(url);
    if (!res.ok) return null;
    return 'data:image/png;base64,' + Buffer.from(await res.arrayBuffer()).toString('base64');
  } catch {
    return null;
  }
}

async function publicAccounts() {
  const list = accounts.list();
  for (const a of list) a.skin = await skinDataUrl(a.skinUrl);
  return list;
}

function errorPayload(e) { return { error: e.message || String(e), code: e.code || null }; }

// ------------------------------------------------------------ IPC
function ipc() {
  ipcMain.handle('app:info', async () => ({
    version: app.getVersion(),
    config: { serverName: config.serverName, serverAddress: config.serverAddress, serverPort: config.serverPort, website: config.website, discord: config.discord, minecraftVersion: config.minecraftVersion },
    settings, accounts: await publicAccounts(), running: !!game,
  }));

  ipcMain.handle('auth:login', async () => {
    try {
      const tokens = await interactiveLogin();
      const acc = await accounts.addFromMicrosoft(tokens);
      return { ok: true, account: acc.name, accounts: await publicAccounts() };
    } catch (e) {
      return errorPayload(e);
    }
  });

  ipcMain.handle('auth:device', async () => {
    try {
      if (deviceAbort) deviceAbort.abort();
      deviceAbort = new AbortController();
      const dc = await ms.startDeviceCode(config.azureClientId);
      const signal = deviceAbort.signal;
      ms.pollDeviceCode(config.azureClientId, dc, fetch, { signal })
        .then(t => accounts.addFromMicrosoft(t))
        .then(async () => send('auth:device-done', { ok: true, accounts: await publicAccounts() }))
        .catch(e => send('auth:device-done', errorPayload(e)));
      return { ok: true, userCode: dc.user_code, uri: dc.verification_uri, expiresIn: dc.expires_in };
    } catch (e) {
      return errorPayload(e);
    }
  });

  ipcMain.handle('auth:cancel', () => { if (deviceAbort) deviceAbort.abort(); return true; });
  ipcMain.handle('auth:select', async (_e, uuid) => { await accounts.select(String(uuid)); return publicAccounts(); });
  ipcMain.handle('auth:remove', async (_e, uuid) => { await accounts.remove(String(uuid)); return publicAccounts(); });

  ipcMain.handle('server:status', async () => {
    try {
      const s = await ping(config.serverAddress, config.serverPort || 25565);
      return { ok: true, online: true, players: s.players, version: s.version && s.version.name, motd: motdText(s.motd), latency: s.latency, favicon: s.favicon };
    } catch (e) {
      return { ok: true, online: false, error: e.message };
    }
  });

  ipcMain.handle('news', async () => {
    try {
      if (!config.manifestUrl || /example\.com/.test(config.manifestUrl)) return [];
      const m = await modpack.fetchManifest(config.manifestUrl);
      return Array.isArray(m.news) ? m.news.slice(0, 6) : [];
    } catch {
      return [];
    }
  });

  ipcMain.handle('settings:set', (_e, s) => { settings = saveSettings(userDir(), s || {}); return settings; });

  ipcMain.handle('game:play', async () => {
    if (game) return { error: 'Le jeu est déjà lancé.' };
    try {
      const session = await accounts.session();
      game = await prepareAndLaunch({
        root: gameRoot(), config, settings, session, launcherVersion: app.getVersion(),
        progress: (step, done, total, bytes, bytesTotal) => send('game:progress', { step, done, total, bytes, bytesTotal }),
        log: line => send('game:log', line.replace(session.accessToken, '••••')),
      });
      send('game:state', { running: true });
      if (!settings.keepOpen) win.hide();
      game.on('exit', code => {
        game = null;
        if (win && !win.isDestroyed()) win.show();
        send('game:state', { running: false, code });
      });
      return { ok: true };
    } catch (e) {
      game = null;
      return errorPayload(e);
    }
  });

  ipcMain.handle('shell:open', (_e, what) => {
    if (what === 'gameDir') return shell.openPath(gameRoot());
    if (typeof what === 'string' && /^https:\/\//.test(what)) return shell.openExternal(what);
    return null;
  });

  ipcMain.on('window', (_e, action) => {
    if (!win) return;
    if (action === 'minimize') win.minimize();
    else if (action === 'close') win.close();
  });
}

app.whenReady().then(async () => {
  config = loadConfig(__dirname, userDir());
  settings = loadSettings(userDir());
  const crypt = {
    available: () => safeStorage.isEncryptionAvailable(),
    encrypt: s => safeStorage.encryptString(s),
    decrypt: b => safeStorage.decryptString(Buffer.from(b)),
  };
  accounts = await new Accounts(path.join(userDir(), 'accounts.json'), crypt, config.azureClientId).load();
  ipc();
  createWindow();
});

app.on('window-all-closed', () => { if (!game) app.quit(); });

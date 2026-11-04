'use strict';
// Charge le vrai main.js avec un faux module « electron » pour vérifier le câblage IPC sans interface graphique.
const test = require('node:test');
const assert = require('node:assert');
const Module = require('node:module');
const os = require('node:os');
const fs = require('node:fs');
const path = require('node:path');

test('main.js : démarrage, IPC et gestion des erreurs de connexion', async () => {
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'fn-main-'));
  const handlers = {};
  let resolveReady;
  const ready = new Promise(r => { resolveReady = r; });
  class FakeWindow {
    constructor(opts) { this.opts = opts; this.webContents = { setWindowOpenHandler() {}, on() {}, send() {}, isDestroyed: () => false }; }
    once() {} on() {} loadFile(f) { this.file = f; } loadURL(u) { this.url = u; } isDestroyed() { return false; } show() {} hide() {} destroy() {}
  }
  const fake = {
    app: { whenReady: () => ready, getPath: k => path.join(tmp, k), getVersion: () => '0.1.0', on() {}, quit() {} },
    BrowserWindow: FakeWindow,
    ipcMain: { handle: (ch, fn) => { handlers[ch] = fn; }, on: (ch, fn) => { handlers[ch] = fn; } },
    safeStorage: { isEncryptionAvailable: () => false },
    shell: { openExternal() {}, openPath() {} },
    nativeTheme: {},
  };
  const orig = Module._load;
  Module._load = function (req, ...rest) { return req === 'electron' ? fake : orig.call(this, req, ...rest); };
  try {
    require('../main.js');
    resolveReady();
    await new Promise(r => setTimeout(r, 100));
    for (const ch of ['app:info', 'auth:login', 'auth:device', 'server:status', 'news', 'settings:set', 'game:play', 'shell:open']) {
      assert.equal(typeof handlers[ch], 'function', 'gestionnaire IPC ' + ch);
    }
    const info = await handlers['app:info']();
    assert.equal(info.config.serverName, 'FNations');
    assert.deepEqual(info.accounts, []);
    const login = await handlers['auth:login']();
    assert.equal(login.code, 'no_client_id', "sans ID Azure, message explicite plutôt qu'une fenêtre vide");
    const play = await handlers['game:play']();
    assert.equal(play.code, 'no_account');
    const s = await handlers['settings:set'](null, { memoryMb: '999999', width: 'x', autoConnect: 1 });
    assert.equal(s.memoryMb, 32768);
    assert.equal(s.width, 1280);
    assert.equal(s.autoConnect, true);
    assert.deepEqual(await handlers['news'](), []);
  } finally {
    Module._load = orig;
  }
});

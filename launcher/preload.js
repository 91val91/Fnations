'use strict';
// Pont sécurisé entre l'interface et le processus principal (aucun accès Node côté interface).
const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('fn', {
  info: () => ipcRenderer.invoke('app:info'),
  login: () => ipcRenderer.invoke('auth:login'),
  loginDevice: () => ipcRenderer.invoke('auth:device'),
  cancelLogin: () => ipcRenderer.invoke('auth:cancel'),
  selectAccount: uuid => ipcRenderer.invoke('auth:select', uuid),
  removeAccount: uuid => ipcRenderer.invoke('auth:remove', uuid),
  serverStatus: () => ipcRenderer.invoke('server:status'),
  news: () => ipcRenderer.invoke('news'),
  saveSettings: s => ipcRenderer.invoke('settings:set', s),
  play: () => ipcRenderer.invoke('game:play'),
  open: what => ipcRenderer.invoke('shell:open', what),
  window: action => ipcRenderer.send('window', action),
  on: (channel, cb) => {
    const allowed = ['game:progress', 'game:log', 'game:state', 'auth:device-done'];
    if (!allowed.includes(channel)) return;
    ipcRenderer.on(channel, (_e, data) => cb(data));
  },
});

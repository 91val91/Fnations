'use strict';
// Configuration : config/launcher.json (livré) + surcharge facultative fnations-launcher.json dans le dossier utilisateur.
const fs = require('node:fs');
const path = require('node:path');

function readJson(file) {
  try { return JSON.parse(fs.readFileSync(file, 'utf8')); } catch { return null; }
}

function loadConfig(appDir, userDir) {
  const base = readJson(path.join(appDir, 'config', 'launcher.json')) || {};
  const override = userDir ? readJson(path.join(userDir, 'fnations-launcher.json')) : null;
  const cfg = { ...base, ...(override || {}) };
  if (process.env.FNATIONS_AZURE_CLIENT_ID) cfg.azureClientId = process.env.FNATIONS_AZURE_CLIENT_ID;
  if (process.env.FNATIONS_MANIFEST_URL) cfg.manifestUrl = process.env.FNATIONS_MANIFEST_URL;
  return cfg;
}

const DEFAULT_SETTINGS = { memoryMb: 4096, width: 1280, height: 720, autoConnect: true, keepOpen: false, javaPath: '' };

function loadSettings(userDir) {
  return { ...DEFAULT_SETTINGS, ...(readJson(path.join(userDir, 'settings.json')) || {}) };
}

function saveSettings(userDir, s) {
  const clean = {
    memoryMb: Math.max(1024, Math.min(32768, parseInt(s.memoryMb, 10) || DEFAULT_SETTINGS.memoryMb)),
    width: Math.max(640, Math.min(7680, parseInt(s.width, 10) || 1280)),
    height: Math.max(480, Math.min(4320, parseInt(s.height, 10) || 720)),
    autoConnect: !!s.autoConnect,
    keepOpen: !!s.keepOpen,
    javaPath: typeof s.javaPath === 'string' ? s.javaPath.trim() : '',
  };
  fs.mkdirSync(userDir, { recursive: true });
  fs.writeFileSync(path.join(userDir, 'settings.json'), JSON.stringify(clean, null, 1));
  return clean;
}

module.exports = { loadConfig, loadSettings, saveSettings, DEFAULT_SETTINGS };

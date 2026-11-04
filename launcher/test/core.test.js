'use strict';
const test = require('node:test');
const assert = require('node:assert');
const path = require('node:path');
const { allowed } = require('../src/core/rules');
const { mavenPath, libKey } = require('../src/core/maven');
const { safeJoin } = require('../src/core/modpack');
const { varint, readVarint } = require('../src/core/ping');
const { platformKey, javaExe } = require('../src/core/java');

test('règles Mojang : OS et fonctionnalités', () => {
  const osx = [{ action: 'allow', os: { name: 'osx' } }];
  assert.equal(allowed(osx, { os: 'osx' }), true);
  assert.equal(allowed(osx, { os: 'windows' }), false);
  const notOsx = [{ action: 'allow' }, { action: 'disallow', os: { name: 'osx' } }];
  assert.equal(allowed(notOsx, { os: 'linux' }), true);
  assert.equal(allowed(notOsx, { os: 'osx' }), false);
  const feat = [{ action: 'allow', features: { is_quick_play_multiplayer: true } }];
  assert.equal(allowed(feat, { features: { is_quick_play_multiplayer: true } }), true);
  assert.equal(allowed(feat, { features: {} }), false);
  assert.equal(allowed([{ action: 'allow', os: { arch: 'x86' } }], { arch: 'x64' }), false);
  assert.equal(allowed(undefined), true);
});

test('chemins Maven', () => {
  assert.equal(mavenPath('org.ow2.asm:asm:9.7.1'), 'org/ow2/asm/asm/9.7.1/asm-9.7.1.jar');
  assert.equal(mavenPath('org.lwjgl:lwjgl:3.3.3:natives-windows'), 'org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-windows.jar');
  assert.equal(mavenPath('a.b:c:1@zip'), 'a/b/c/1/c-1.zip');
  assert.equal(libKey('org.lwjgl:lwjgl:3.3.3:natives-linux'), 'org.lwjgl:lwjgl:natives-linux');
});

test('manifeste : protection contre la traversée de répertoires', () => {
  const base = path.resolve('/tmp/jeu');
  assert.equal(safeJoin(base, 'mods/a.jar'), path.join(base, 'mods', 'a.jar'));
  assert.throws(() => safeJoin(base, '../evil.jar'));
  assert.throws(() => safeJoin(base, 'mods/../../evil.jar'));
  assert.throws(() => safeJoin(base, '/etc/passwd'));
  assert.throws(() => safeJoin(base, ''));
});

test('varint du protocole Minecraft', () => {
  for (const n of [0, 1, 127, 128, 255, 769, 25565, 2097151]) {
    const [v, off] = readVarint(varint(n), 0);
    assert.equal(v, n);
    assert.equal(off, varint(n).length);
  }
});

test('runtime Java : plateforme et exécutable', () => {
  assert.equal(platformKey('win32', 'x64'), 'windows-x64');
  assert.equal(platformKey('darwin', 'arm64'), 'mac-os-arm64');
  assert.equal(platformKey('linux', 'x64'), 'linux');
  assert.match(javaExe('/r', 'win32'), /javaw\.exe$/);
  assert.match(javaExe('/r', 'darwin'), /jre\.bundle/);
});

'use strict';
// Chaîne Microsoft -> Xbox -> XSTS -> Minecraft avec des réponses simulées (aucun appel réseau).
const test = require('node:test');
const assert = require('node:assert');
const os = require('node:os');
const path = require('node:path');
const ms = require('../src/auth/microsoft');
const { Accounts } = require('../src/auth/accounts');

const b64 = o => Buffer.from(JSON.stringify(o)).toString('base64url');
const XSTS_JWT = `x.${b64({ xui: [{ uhs: 'UHS1', xid: '2535400000000001' }] })}.sig`;

function mockFetch(overrides = {}) {
  const calls = [];
  const res = (status, body) => ({ ok: status >= 200 && status < 300, status, json: async () => body, text: async () => JSON.stringify(body) });
  const routes = {
    'login.microsoftonline.com/consumers/oauth2/v2.0/token': () => res(200, { access_token: 'MS_AT', refresh_token: 'MS_RT', expires_in: 3600 }),
    'login.microsoftonline.com/consumers/oauth2/v2.0/devicecode': () => res(200, { device_code: 'DC', user_code: 'ABCD-EFGH', verification_uri: 'https://www.microsoft.com/link', expires_in: 900, interval: 1 }),
    'user.auth.xboxlive.com/user/authenticate': () => res(200, { Token: 'XBL', DisplayClaims: { xui: [{ uhs: 'UHS1' }] } }),
    'xsts.auth.xboxlive.com/xsts/authorize': () => res(200, { Token: XSTS_JWT, DisplayClaims: { xui: [{ uhs: 'UHS1' }] } }),
    'api.minecraftservices.com/authentication/login_with_xbox': () => res(200, { access_token: 'MC_AT', expires_in: 86400 }),
    'api.minecraftservices.com/entitlements/mcstore': () => res(200, { items: [{ name: 'product_minecraft' }, { name: 'game_minecraft' }] }),
    'api.minecraftservices.com/minecraft/profile': () => res(200, { id: '0123456789abcdef0123456789abcdef', name: 'Joueur', skins: [{ state: 'ACTIVE', url: 'https://textures.minecraft.net/texture/abc' }] }),
    ...overrides,
  };
  const f = async (url, opts = {}) => {
    const key = url.replace(/^https:\/\//, '').split('?')[0];
    calls.push({ key, opts });
    const r = routes[key];
    if (!r) throw new Error('route inattendue ' + key);
    return r(opts, calls);
  };
  f.calls = calls;
  return f;
}

test('URL d\'autorisation PKCE et lecture de la redirection', () => {
  const { verifier, challenge } = ms.pkce();
  assert.ok(verifier.length >= 43);
  const url = new URL(ms.authorizeUrl('CLIENT', challenge, 'ST'));
  assert.equal(url.searchParams.get('code_challenge_method'), 'S256');
  assert.equal(url.searchParams.get('scope'), 'XboxLive.signin offline_access');
  assert.equal(ms.parseRedirect('https://example.com/autre', 'ST'), null);
  assert.equal(ms.parseRedirect(ms.REDIRECT + '?code=CODE&state=ST', 'ST'), 'CODE');
  assert.throws(() => ms.parseRedirect(ms.REDIRECT + '?code=CODE&state=AUTRE', 'ST'), /state/);
  assert.throws(() => ms.parseRedirect(ms.REDIRECT + '?error=access_denied&error_description=Refus', 'ST'), /Refus/);
});

test('connexion complète : Xbox, XSTS, Minecraft, licence et profil', async () => {
  const f = mockFetch();
  const tokens = await ms.exchangeCode('CLIENT', 'CODE', 'VERIF', f);
  assert.equal(tokens.refresh_token, 'MS_RT');
  const mc = await ms.minecraftLogin(tokens.access_token, f);
  assert.equal(mc.name, 'Joueur');
  assert.equal(mc.accessToken, 'MC_AT');
  assert.equal(mc.xuid, '2535400000000001');
  const xbl = f.calls.find(c => c.key.startsWith('user.auth'));
  assert.equal(JSON.parse(xbl.opts.body).Properties.RpsTicket, 'd=MS_AT');
  const login = f.calls.find(c => c.key.endsWith('login_with_xbox'));
  assert.equal(JSON.parse(login.opts.body).identityToken, `XBL3.0 x=UHS1;${XSTS_JWT}`);
});

test('erreurs XSTS traduites (pas de compte Xbox, compte enfant)', async () => {
  for (const [code, msg] of [[2148916233, /profil Xbox/], [2148916238, /enfant/]]) {
    const f = mockFetch({ 'xsts.auth.xboxlive.com/xsts/authorize': () => ({ ok: false, status: 401, json: async () => ({ XErr: code }), text: async () => JSON.stringify({ XErr: code }) }) });
    await assert.rejects(ms.minecraftLogin('MS_AT', f), msg);
  }
});

test('application Azure non approuvée par Mojang (403)', async () => {
  const f = mockFetch({ 'api.minecraftservices.com/authentication/login_with_xbox': () => ({ ok: false, status: 403, json: async () => ({}), text: async () => '{}' }) });
  await assert.rejects(ms.minecraftLogin('MS_AT', f), e => e.code === 'app_not_allowed');
});

test('compte sans Minecraft', async () => {
  const f = mockFetch({
    'api.minecraftservices.com/entitlements/mcstore': () => ({ ok: true, status: 200, json: async () => ({ items: [] }) }),
    'api.minecraftservices.com/minecraft/profile': () => ({ ok: false, status: 404, json: async () => ({}) }),
  });
  await assert.rejects(ms.minecraftLogin('MS_AT', f), /ne possède pas Minecraft/);
});

test('code d\'appareil : attente puis succès', async () => {
  let n = 0;
  const f = mockFetch({
    'login.microsoftonline.com/consumers/oauth2/v2.0/token': () => (++n < 3
      ? { ok: false, status: 400, json: async () => ({ error: 'authorization_pending' }) }
      : { ok: true, status: 200, json: async () => ({ access_token: 'MS_AT', refresh_token: 'MS_RT' }) }),
  });
  const dc = await ms.startDeviceCode('CLIENT', f);
  assert.equal(dc.user_code, 'ABCD-EFGH');
  const t = await ms.pollDeviceCode('CLIENT', dc, f, { sleep: async () => {} });
  assert.equal(t.access_token, 'MS_AT');
  assert.equal(n, 3);
});

test('comptes : stockage chiffré et rafraîchissement automatique', async () => {
  const file = path.join(os.tmpdir(), `fn-accounts-${process.pid}.json`);
  const crypto = { available: () => true, encrypt: s => Buffer.from('ENC:' + s), decrypt: b => Buffer.from(b).toString().slice(4) };
  const f = mockFetch();
  const acc = await new Accounts(file, crypto, 'CLIENT', f).load();
  await acc.addFromMicrosoft({ access_token: 'MS_AT', refresh_token: 'MS_RT' });
  const raw = require('node:fs').readFileSync(file, 'utf8');
  assert.ok(!raw.includes('MS_RT') && !raw.includes('MC_AT'), 'les jetons ne doivent pas être stockés en clair');
  let s = await acc.session();
  assert.equal(s.accessToken, 'MC_AT');
  // expiration : le jeton est renouvelé via le refresh token
  acc.data.accounts[s.uuid].mcExpiresAt = Date.now() - 1000;
  const before = f.calls.length;
  s = await acc.session();
  assert.equal(s.name, 'Joueur');
  assert.ok(f.calls.slice(before).some(c => c.key.endsWith('/token')), 'refresh attendu');
  await acc.remove(s.uuid);
  assert.equal(acc.list().length, 0);
});

'use strict';
// Connexion avec un vrai compte Microsoft : OAuth 2.0 (Microsoft identity platform) -> Xbox Live -> XSTS -> Minecraft.
// Aucune donnée n'est envoyée à un tiers : uniquement aux services officiels Microsoft / Xbox / Mojang.
const crypto = require('node:crypto');

const AUTHORITY = 'https://login.microsoftonline.com/consumers/oauth2/v2.0';
const REDIRECT = 'https://login.microsoftonline.com/common/oauth2/nativeclient';
const SCOPE = 'XboxLive.signin offline_access';

const XERR = {
  2148916227: 'Ce compte a été banni du Xbox Live.',
  2148916233: "Ce compte Microsoft n'a pas de profil Xbox. Créez-en un sur xbox.com puis réessayez.",
  2148916235: "Xbox Live n'est pas disponible dans votre pays.",
  2148916236: "Ce compte doit être vérifié comme adulte (Corée du Sud).",
  2148916237: "Ce compte doit être vérifié comme adulte (Corée du Sud).",
  2148916238: "Compte enfant : il doit être ajouté à une famille Microsoft par un adulte.",
};

class AuthError extends Error {
  constructor(message, code) {
    super(message);
    this.code = code;
  }
}

function base64url(buf) { return buf.toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, ''); }

/** Paire PKCE (RFC 7636). */
function pkce() {
  const verifier = base64url(crypto.randomBytes(32));
  const challenge = base64url(crypto.createHash('sha256').update(verifier).digest());
  return { verifier, challenge };
}

function authorizeUrl(clientId, challenge, state) {
  const q = new URLSearchParams({
    client_id: clientId, response_type: 'code', redirect_uri: REDIRECT, response_mode: 'query',
    scope: SCOPE, prompt: 'select_account', code_challenge: challenge, code_challenge_method: 'S256', state,
  });
  return `${AUTHORITY}/authorize?${q}`;
}

/** Lit le code (ou l'erreur) depuis l'URL de redirection. */
function parseRedirect(url, expectedState) {
  if (!url.startsWith(REDIRECT)) return null;
  const u = new URL(url);
  const err = u.searchParams.get('error');
  if (err) throw new AuthError(u.searchParams.get('error_description') || err, err);
  if (expectedState && u.searchParams.get('state') !== expectedState) throw new AuthError('Réponse de connexion invalide (state).', 'state');
  return u.searchParams.get('code');
}

async function form(fetchImpl, url, params) {
  const res = await fetchImpl(url, { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams(params).toString() });
  const json = await res.json().catch(() => ({}));
  return { ok: res.ok, status: res.status, json };
}

async function postJson(fetchImpl, url, body, headers = {}) {
  const res = await fetchImpl(url, { method: 'POST', headers: { 'Content-Type': 'application/json', Accept: 'application/json', ...headers }, body: JSON.stringify(body) });
  const text = await res.text();
  let json = {};
  try { json = text ? JSON.parse(text) : {}; } catch {}
  return { ok: res.ok, status: res.status, json };
}

/** Échange le code d'autorisation contre des jetons Microsoft. */
async function exchangeCode(clientId, code, verifier, fetchImpl = fetch) {
  const r = await form(fetchImpl, `${AUTHORITY}/token`, { client_id: clientId, grant_type: 'authorization_code', code, redirect_uri: REDIRECT, code_verifier: verifier, scope: SCOPE });
  if (!r.ok) throw new AuthError(r.json.error_description || 'Échec de la connexion Microsoft.', r.json.error);
  return r.json;
}

async function refreshMicrosoft(clientId, refreshToken, fetchImpl = fetch) {
  const r = await form(fetchImpl, `${AUTHORITY}/token`, { client_id: clientId, grant_type: 'refresh_token', refresh_token: refreshToken, scope: SCOPE });
  if (!r.ok) throw new AuthError('Session Microsoft expirée, reconnectez-vous.', r.json.error || 'refresh');
  return r.json;
}

/** Flux « code d'appareil » : alternative sans navigateur intégré. */
async function startDeviceCode(clientId, fetchImpl = fetch) {
  const r = await form(fetchImpl, `${AUTHORITY}/devicecode`, { client_id: clientId, scope: SCOPE });
  if (!r.ok) throw new AuthError(r.json.error_description || 'Impossible de démarrer la connexion par code.', r.json.error);
  return r.json; // { device_code, user_code, verification_uri, expires_in, interval, message }
}

async function pollDeviceCode(clientId, dc, fetchImpl = fetch, { signal, sleep = ms => new Promise(r => setTimeout(r, ms)) } = {}) {
  let interval = (dc.interval || 5) * 1000;
  const deadline = Date.now() + (dc.expires_in || 900) * 1000;
  while (Date.now() < deadline) {
    if (signal && signal.aborted) throw new AuthError('Connexion annulée.', 'cancelled');
    await sleep(interval);
    const r = await form(fetchImpl, `${AUTHORITY}/token`, { client_id: clientId, grant_type: 'urn:ietf:params:oauth:grant-type:device_code', device_code: dc.device_code });
    if (r.ok) return r.json;
    const e = r.json.error;
    if (e === 'authorization_pending') continue;
    if (e === 'slow_down') { interval += 5000; continue; }
    if (e === 'authorization_declined') throw new AuthError('Connexion refusée.', e);
    if (e === 'expired_token') break;
    throw new AuthError(r.json.error_description || 'Échec de la connexion.', e);
  }
  throw new AuthError('Le code a expiré, recommencez.', 'expired_token');
}

/** Microsoft -> Xbox Live -> XSTS -> Minecraft, puis vérification de la licence et du profil. */
async function minecraftLogin(msAccessToken, fetchImpl = fetch) {
  const xbl = await postJson(fetchImpl, 'https://user.auth.xboxlive.com/user/authenticate', {
    Properties: { AuthMethod: 'RPS', SiteName: 'user.auth.xboxlive.com', RpsTicket: 'd=' + msAccessToken },
    RelyingParty: 'http://auth.xboxlive.com', TokenType: 'JWT',
  });
  if (!xbl.ok) throw new AuthError('Authentification Xbox Live refusée.', 'xbl');
  const uhs = xbl.json.DisplayClaims.xui[0].uhs;
  const xsts = await postJson(fetchImpl, 'https://xsts.auth.xboxlive.com/xsts/authorize', {
    Properties: { SandboxId: 'RETAIL', UserTokens: [xbl.json.Token] },
    RelyingParty: 'rp://api.minecraftservices.com/', TokenType: 'JWT',
  });
  if (!xsts.ok) {
    const code = xsts.json.XErr;
    throw new AuthError(XERR[code] || `Autorisation Xbox refusée (${code || xsts.status}).`, String(code || 'xsts'));
  }
  const mc = await postJson(fetchImpl, 'https://api.minecraftservices.com/authentication/login_with_xbox', { identityToken: `XBL3.0 x=${uhs};${xsts.json.Token}` });
  if (!mc.ok) {
    if (mc.status === 403) throw new AuthError("Cette application n'est pas encore autorisée par Mojang à utiliser la connexion Minecraft (voir README : demande d'accès à l'API).", 'app_not_allowed');
    throw new AuthError('Connexion aux services Minecraft impossible.', 'mc_login');
  }
  const token = mc.json.access_token;
  const auth = { Authorization: 'Bearer ' + token };
  const ent = await fetchImpl('https://api.minecraftservices.com/entitlements/mcstore', { headers: auth });
  const entJson = ent.ok ? await ent.json() : { items: [] };
  const owns = (entJson.items || []).some(i => i.name === 'product_minecraft' || i.name === 'game_minecraft');
  const prof = await fetchImpl('https://api.minecraftservices.com/minecraft/profile', { headers: auth });
  if (prof.status === 404) throw new AuthError(owns ? "Créez d'abord votre profil (pseudo) sur minecraft.net." : "Ce compte ne possède pas Minecraft Java Edition.", 'no_profile');
  if (!prof.ok) throw new AuthError('Impossible de récupérer le profil Minecraft.', 'profile');
  const profile = await prof.json();
  const skin = (profile.skins || []).find(s => s.state === 'ACTIVE');
  const xuid = decodeXuid(xsts.json.Token);
  return {
    uuid: profile.id,
    name: profile.name,
    accessToken: token,
    expiresAt: Date.now() + (mc.json.expires_in || 86400) * 1000,
    skinUrl: skin ? skin.url : null,
    xuid,
    type: 'msa',
  };
}

/** xuid (identifiant Xbox) contenu dans le jeton XSTS, si présent. */
function decodeXuid(jwt) {
  try {
    const payload = JSON.parse(Buffer.from(jwt.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'), 'base64').toString('utf8'));
    const x = payload.xui && payload.xui[0];
    return (x && (x.xid || x.xuid)) || '';
  } catch {
    return '';
  }
}

module.exports = {
  AUTHORITY, REDIRECT, SCOPE, AuthError, pkce, authorizeUrl, parseRedirect, exchangeCode, refreshMicrosoft,
  startDeviceCode, pollDeviceCode, minecraftLogin, decodeXuid,
};

'use strict';
// Comptes enregistrés : le jeton de rafraîchissement Microsoft est chiffré par le système (DPAPI / Trousseau / libsecret).
const fsp = require('node:fs/promises');
const path = require('node:path');
const ms = require('./microsoft');

class Accounts {
  /**
   * @param file fichier de stockage
   * @param crypto { encrypt(string): Buffer|string, decrypt(Buffer|string): string, available(): boolean }
   * @param clientId ID d'application Azure
   * @param fetchImpl fetch (injectable pour les tests)
   */
  constructor(file, crypto, clientId, fetchImpl = fetch) {
    this.file = file;
    this.crypto = crypto;
    this.clientId = clientId;
    this.fetch = fetchImpl;
    this.data = { selected: null, accounts: {} };
  }

  async load() {
    try { this.data = JSON.parse(await fsp.readFile(this.file, 'utf8')); } catch { this.data = { selected: null, accounts: {} }; }
    return this;
  }

  async save() {
    await fsp.mkdir(path.dirname(this.file), { recursive: true });
    await fsp.writeFile(this.file, JSON.stringify(this.data, null, 1), { mode: 0o600 });
  }

  list() {
    return Object.values(this.data.accounts).map(a => ({ uuid: a.uuid, name: a.name, skinUrl: a.skinUrl, selected: a.uuid === this.data.selected }));
  }

  selected() { return this.data.accounts[this.data.selected] || null; }

  seal(token) {
    if (this.crypto && this.crypto.available()) return { enc: true, v: Buffer.from(this.crypto.encrypt(token)).toString('base64') };
    return { enc: false, v: token };
  }

  unseal(box) {
    if (!box) return null;
    return box.enc ? this.crypto.decrypt(Buffer.from(box.v, 'base64')) : box.v;
  }

  /** Enregistre un compte après une connexion Microsoft réussie. */
  async addFromMicrosoft(msTokens) {
    const mc = await ms.minecraftLogin(msTokens.access_token, this.fetch);
    this.data.accounts[mc.uuid] = {
      uuid: mc.uuid, name: mc.name, skinUrl: mc.skinUrl, xuid: mc.xuid,
      refresh: this.seal(msTokens.refresh_token), mc: this.seal(mc.accessToken), mcExpiresAt: mc.expiresAt,
    };
    this.data.selected = mc.uuid;
    await this.save();
    return mc;
  }

  /** Jeton Minecraft valide pour le compte sélectionné (rafraîchi si besoin). */
  async session() {
    const a = this.selected();
    if (!a) throw new ms.AuthError('Aucun compte connecté.', 'no_account');
    if (a.mcExpiresAt - Date.now() > 10 * 60 * 1000) {
      return { uuid: a.uuid, name: a.name, accessToken: this.unseal(a.mc), xuid: a.xuid, type: 'msa' };
    }
    const tokens = await ms.refreshMicrosoft(this.clientId, this.unseal(a.refresh), this.fetch);
    const mc = await ms.minecraftLogin(tokens.access_token, this.fetch);
    Object.assign(a, { name: mc.name, skinUrl: mc.skinUrl, xuid: mc.xuid, mc: this.seal(mc.accessToken), mcExpiresAt: mc.expiresAt });
    if (tokens.refresh_token) a.refresh = this.seal(tokens.refresh_token);
    await this.save();
    return { uuid: mc.uuid, name: mc.name, accessToken: mc.accessToken, xuid: mc.xuid, type: 'msa' };
  }

  async select(uuid) {
    if (!this.data.accounts[uuid]) return;
    this.data.selected = uuid;
    await this.save();
  }

  async remove(uuid) {
    delete this.data.accounts[uuid];
    if (this.data.selected === uuid) this.data.selected = Object.keys(this.data.accounts)[0] || null;
    await this.save();
  }
}

module.exports = { Accounts };

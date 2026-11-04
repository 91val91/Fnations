package fr.fnations.nation;

import fr.fnations.gui.ConfirmPanel;
import fr.fnations.gui.Icons;
import fr.fnations.gui.PagedPanel;
import fr.fnations.gui.Panel;
import fr.fnations.gui.Prompt;
import fr.fnations.server.FnServer;
import fr.fnations.server.Msg;
import fr.fnations.util.Txt;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import fr.fnations.util.Locs;

import java.util.UUID;

/** Actions sur les nations, partagées par la commande /nation et le GUI. */
public final class NationActions {
    private final FnServer plugin;
    private final NationManager nm;

    public NationActions(FnServer plugin) {
        this.plugin = plugin;
        this.nm = plugin.nations();
    }

    private Nation need(ServerPlayer p, Role min) {
        Nation n = nm.of(p);
        if (n == null) { Msg.err(p, "Vous n'appartenez à aucune nation."); return null; }
        if (!n.can(p.getUUID(), min)) { Msg.err(p, "Vous n'avez pas le rang nécessaire (" + min.display + "<red>)."); return null; }
        return n;
    }

    public void create(ServerPlayer p, String name) { create(p, name, true); }

    public void create(ServerPlayer p, String name, boolean announce) {
        if (nm.of(p) != null) { Msg.err(p, "Vous êtes déjà dans une nation."); return; }
        String err = nm.validateName(name);
        if (err != null) { Msg.err(p, err); return; }
        double cost = plugin.config().getDouble("nations.cout-creation", 5000);
        if (!plugin.bank().withdraw(p.getUUID(), cost, "Création de nation")) {
            Msg.err(p, "Il faut " + Txt.money(cost) + " en banque pour fonder une nation.");
            return;
        }
        Nation n = nm.create(p, name);
        String dim = Locs.dim(p.level());
        String key = Locs.chunkKey(dim, p.blockPosition().getX() >> 4, p.blockPosition().getZ() >> 4);
        if (nm.atChunk(key) == null && !plugin.config().getStringList("nations.mondes-interdits").contains(dim)
                && !plugin.rooms().intersectsChunk(dim, p.blockPosition().getX() >> 4, p.blockPosition().getZ() >> 4)) nm.claim(n, key);
        if (announce) Msg.broadcast(plugin.server(), ("<green>" + Txt.esc(p.getScoreboardName()) + " a fondé la nation " + n.colored() + "<green> !"));
        else Msg.ok(p, "Nation " + n.name() + " fondée !");
    }

    public void invite(ServerPlayer p, String target) {
        Nation n = need(p, Role.OFFICER);
        if (n == null) return;
        ServerPlayer t = plugin.playerByName(target);
        if (t == null) { Msg.err(p, "Joueur introuvable."); return; }
        if (nm.of(t) != null) { Msg.err(p, "Ce joueur a déjà une nation."); return; }
        n.invites().add(t.getUUID());
        Msg.ok(p, t.getScoreboardName() + " a été invité.");
        Msg.send(t, "<yellow>Vous êtes invité à rejoindre " + n.colored() + "<yellow>. Tapez <white>/nation rejoindre " + n.name());
    }

    public void join(ServerPlayer p, String name) {
        if (nm.of(p) != null) { Msg.err(p, "Vous êtes déjà dans une nation."); return; }
        Nation n = nm.get(name);
        if (n == null) { Msg.err(p, "Nation introuvable."); return; }
        if (!n.open() && !n.invites().contains(p.getUUID())) { Msg.err(p, "Vous n'êtes pas invité dans cette nation."); return; }
        int max = plugin.config().getInt("nations.membres-max", 50);
        if (n.members().size() >= max) { Msg.err(p, "Cette nation est complète."); return; }
        nm.addMember(n, p.getUUID());
        nm.broadcast(n, "<green>" + Txt.esc(p.getScoreboardName()) + " a rejoint la nation !");
    }

    public void leave(ServerPlayer p) {
        Nation n = need(p, Role.MEMBER);
        if (n == null) return;
        if (n.leader().equals(p.getUUID())) { Msg.err(p, "Le chef doit transférer la nation ou la dissoudre."); return; }
        nm.removeMember(n, p.getUUID());
        Msg.ok(p, "Vous avez quitté " + n.colored() + "<green>.");
        nm.broadcast(n, "<red>" + Txt.esc(p.getScoreboardName()) + " a quitté la nation.");
    }

    public void kick(ServerPlayer p, UUID target) {
        Nation n = need(p, Role.OFFICER);
        if (n == null) return;
        Role r = n.role(target);
        if (r == null) { Msg.err(p, "Ce joueur n'est pas dans votre nation."); return; }
        if (r.power >= n.role(p.getUUID()).power) { Msg.err(p, "Vous ne pouvez pas expulser ce membre."); return; }
        nm.removeMember(n, target);
        nm.broadcast(n, "<red>" + plugin.name(target) + " a été expulsé.");
        ServerPlayer op = plugin.player(target);
        if (op != null) Msg.err(op, "Vous avez été expulsé de " + n.name() + ".");
    }

    public void promote(ServerPlayer p, UUID target) {
        Nation n = need(p, Role.LEADER);
        if (n == null) return;
        if (n.role(target) == Role.MEMBER) {
            n.members().put(target, Role.OFFICER);
            nm.broadcast(n, "<yellow>" + plugin.name(target) + " est promu Ministre.");
        } else if (n.role(target) == Role.OFFICER) {
            Msg.err(p, "Utilisez « transférer » pour céder la direction.");
        }
    }

    public void demote(ServerPlayer p, UUID target) {
        Nation n = need(p, Role.LEADER);
        if (n == null) return;
        if (n.role(target) == Role.OFFICER) {
            n.members().put(target, Role.MEMBER);
            nm.broadcast(n, "<yellow>" + plugin.name(target) + " est rétrogradé Citoyen.");
        }
    }

    public void transfer(ServerPlayer p, UUID target) {
        Nation n = need(p, Role.LEADER);
        if (n == null) return;
        if (n.role(target) == null || target.equals(p.getUUID())) { Msg.err(p, "Membre invalide."); return; }
        n.setLeader(target);
        nm.broadcast(n, "<gold>" + plugin.name(target) + " est le nouveau Chef d'État !");
    }

    public void claim(ServerPlayer p) { claimChunk(p, p.blockPosition().getX() >> 4, p.blockPosition().getZ() >> 4); }

    private void claimChunk(ServerPlayer p, int cx, int cz) {
        Nation n = need(p, Role.OFFICER);
        if (n == null) return;
        String dim = Locs.dim(p.level());
        String key = Locs.chunkKey(dim, cx, cz);
        if (plugin.config().getStringList("nations.mondes-interdits").contains(dim)) {
            Msg.err(p, "On ne peut pas revendiquer de territoire dans ce monde."); return;
        }
        Nation owner = nm.atChunk(key);
        if (owner != null) { Msg.err(p, "Ce territoire appartient déjà à " + owner.colored() + "<red>."); return; }
        if (plugin.rooms().intersectsChunk(dim, cx, cz)) { Msg.err(p, "Ce chunk contient des chambres de l'hôtel."); return; }
        if (n.claims().size() >= nm.maxClaims(n)) { Msg.err(p, "Limite de territoire atteinte (" + nm.maxClaims(n) + "). Recrutez ou recherchez Expansion."); return; }
        if (plugin.config().getBoolean("nations.claims-adjacents", true) && !nm.adjacent(n, dim, cx, cz)) {
            Msg.err(p, "Le territoire doit être adjacent à votre nation."); return;
        }
        double cost = plugin.config().getDouble("nations.cout-claim", 500);
        if (!plugin.bank().nationWithdraw(n, cost, "Revendication de territoire")) {
            Msg.err(p, "Le trésor national n'a pas " + Txt.money(cost) + "."); return;
        }
        nm.claim(n, key);
        Msg.ok(p, "Chunk revendiqué pour " + n.colored() + "<green> (" + n.claims().size() + "/" + nm.maxClaims(n) + ").");
    }

    /** Revendication d'un chunk cliqué sur la carte (proche du joueur). */
    public void claimAt(ServerPlayer p, int cx, int cz) {
        int pcx = p.blockPosition().getX() >> 4, pcz = p.blockPosition().getZ() >> 4;
        if (Math.abs(cx - pcx) > 12 || Math.abs(cz - pcz) > 12) { Msg.err(p, "Ce chunk est trop loin de vous."); return; }
        claimChunk(p, cx, cz);
    }

    public void unclaimAt(ServerPlayer p, int cx, int cz) {
        Nation n = need(p, Role.OFFICER);
        if (n == null) return;
        String key = Locs.chunkKey(Locs.dim(p.level()), cx, cz);
        if (!n.claims().contains(key)) { Msg.err(p, "Ce chunk n'appartient pas à votre nation."); return; }
        nm.unclaim(n, key);
        Msg.ok(p, "Chunk " + cx + ", " + cz + " abandonné.");
    }

    public void unclaim(ServerPlayer p) {
        Nation n = need(p, Role.OFFICER);
        if (n == null) return;
        String key = Locs.chunkKey(p.level(), p.blockPosition());
        if (!n.claims().contains(key)) { Msg.err(p, "Ce chunk n'appartient pas à votre nation."); return; }
        nm.unclaim(n, key);
        Msg.ok(p, "Chunk abandonné.");
    }

    public void deposit(ServerPlayer p, double amount) {
        Nation n = need(p, Role.MEMBER);
        if (n == null) return;
        if (!plugin.bank().withdraw(p.getUUID(), amount, "Don au trésor de " + n.name())) { Msg.err(p, "Fonds insuffisants."); return; }
        plugin.bank().nationDeposit(n, amount, "Don de " + p.getScoreboardName());
        Msg.ok(p, Txt.money(amount) + " versés au trésor national.");
    }

    public void withdraw(ServerPlayer p, double amount) {
        Nation n = need(p, Role.OFFICER);
        if (n == null) return;
        if (!plugin.bank().nationWithdraw(n, amount, "Retrait par " + p.getScoreboardName())) { Msg.err(p, "Trésor insuffisant."); return; }
        plugin.bank().deposit(p.getUUID(), amount, "Retrait du trésor de " + n.name());
        Msg.ok(p, Txt.money(amount) + " retirés du trésor national.");
    }

    public void ally(ServerPlayer p, String other) {
        Nation n = need(p, Role.OFFICER);
        if (n == null) return;
        Nation o = nm.get(other);
        if (o == null || o == n) { Msg.err(p, "Nation invalide."); return; }
        if (plugin.wars().between(n, o) != null) { Msg.err(p, "Vous êtes en guerre contre cette nation !"); return; }
        if (n.allies().contains(o.id())) {
            n.allies().remove(o.id());
            o.allies().remove(n.id());
            nm.broadcast(n, "<red>Alliance rompue avec " + o.colored());
            nm.broadcast(o, "<red>" + n.colored() + " <red>a rompu votre alliance.");
            return;
        }
        if (n.allyRequests().contains(o.id())) {
            n.allyRequests().remove(o.id());
            n.allies().add(o.id());
            o.allies().add(n.id());
            Msg.broadcast(plugin.server(), (n.colored() + " <green>et " + o.colored() + " <green>sont désormais alliées !"));
            return;
        }
        o.allyRequests().add(n.id());
        Msg.ok(p, "Demande d'alliance envoyée à " + o.colored());
        nm.broadcast(o, "<yellow>" + n.colored() + " <yellow>propose une alliance. (/nation allie " + n.name() + ")");
    }

    public void setHome(ServerPlayer p) {
        Nation n = need(p, Role.OFFICER);
        if (n == null) return;
        if (nm.at(p.level(), p.blockPosition()) != n) { Msg.err(p, "La capitale doit être sur votre territoire."); return; }
        n.setHome(Locs.Spot.of(p.level(), p.position(), p.getYRot(), p.getXRot()));
        Msg.ok(p, "Capitale définie.");
    }

    public void home(ServerPlayer p) {
        Nation n = need(p, Role.MEMBER);
        if (n == null) return;
        if (n.home() == null) { Msg.err(p, "Aucune capitale définie."); return; }
        if (plugin.wars().isCapturing(p)) { Msg.err(p, "Impossible pendant une capture."); return; }
        var h = n.home();
        var level = Locs.level(plugin.server(), h.dim());
        if (level == null) { Msg.err(p, "La capitale est dans une dimension introuvable."); return; }
        p.teleportTo(level, h.x(), h.y(), h.z(), java.util.Set.of(), h.yaw(), h.pitch(), true);
        Msg.ok(p, "Téléportation à la capitale.");
    }

    public void setTax(ServerPlayer p, double v) {
        Nation n = need(p, Role.LEADER);
        if (n == null) return;
        double max = plugin.config().getDouble("nations.impot-max", 200);
        n.setTaxPerHour(Math.max(0, Math.min(max, v)));
        nm.broadcast(n, "<yellow>Nouvel impôt : " + Txt.money(n.taxPerHour()) + " par heure.");
    }

    public void setTag(ServerPlayer p, String tag) {
        Nation n = need(p, Role.LEADER);
        if (n == null) return;
        if (!tag.matches("[A-Za-z0-9]{2,5}")) { Msg.err(p, "Le tag doit faire 2 à 5 lettres/chiffres."); return; }
        n.setTag(tag.toUpperCase());
        Msg.ok(p, "Tag modifié : [" + n.tag() + "]");
    }

    public void disband(ServerPlayer p) {
        Nation n = need(p, Role.LEADER);
        if (n == null) return;
        plugin.bank().deposit(p.getUUID(), n.balance(), "Dissolution de " + n.name());
        nm.disband(n);
    }
}

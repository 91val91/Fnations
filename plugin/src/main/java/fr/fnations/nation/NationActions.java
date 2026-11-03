package fr.fnations.nation;

import fr.fnations.FNations;
import fr.fnations.util.Locs;
import fr.fnations.util.Txt;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Actions sur les nations, partagées par la commande /nation et le GUI. */
public final class NationActions {
    private final FNations plugin;
    private final NationManager nm;

    public NationActions(FNations plugin) {
        this.plugin = plugin;
        this.nm = plugin.nations();
    }

    private Nation need(Player p, Role min) {
        Nation n = nm.of(p);
        if (n == null) { Txt.err(p, "Vous n'appartenez à aucune nation."); return null; }
        if (!n.can(p.getUniqueId(), min)) { Txt.err(p, "Vous n'avez pas le rang nécessaire (" + min.display + "<red>)."); return null; }
        return n;
    }

    public void create(Player p, String name) {
        if (nm.of(p) != null) { Txt.err(p, "Vous êtes déjà dans une nation."); return; }
        String err = nm.validateName(name);
        if (err != null) { Txt.err(p, err); return; }
        double cost = plugin.getConfig().getDouble("nations.cout-creation", 5000);
        if (!plugin.bank().withdraw(p.getUniqueId(), cost, "Création de nation")) {
            Txt.err(p, "Il faut " + Txt.money(cost) + " en banque pour fonder une nation.");
            return;
        }
        Nation n = nm.create(p, name);
        Bukkit.broadcast(Txt.raw(Txt.PREFIX + "<green>" + Txt.esc(p.getName()) + " a fondé la nation " + n.colored() + "<green> !"));
    }

    public void invite(Player p, String target) {
        Nation n = need(p, Role.OFFICER);
        if (n == null) return;
        Player t = Bukkit.getPlayerExact(target);
        if (t == null) { Txt.err(p, "Joueur introuvable."); return; }
        if (nm.of(t) != null) { Txt.err(p, "Ce joueur a déjà une nation."); return; }
        n.invites().add(t.getUniqueId());
        Txt.ok(p, t.getName() + " a été invité.");
        Txt.send(t, "<yellow>Vous êtes invité à rejoindre " + n.colored() + "<yellow>. Tapez <white>/nation rejoindre " + n.name());
    }

    public void join(Player p, String name) {
        if (nm.of(p) != null) { Txt.err(p, "Vous êtes déjà dans une nation."); return; }
        Nation n = nm.get(name);
        if (n == null) { Txt.err(p, "Nation introuvable."); return; }
        if (!n.open() && !n.invites().contains(p.getUniqueId())) { Txt.err(p, "Vous n'êtes pas invité dans cette nation."); return; }
        int max = plugin.getConfig().getInt("nations.membres-max", 50);
        if (n.members().size() >= max) { Txt.err(p, "Cette nation est complète."); return; }
        nm.addMember(n, p.getUniqueId());
        nm.broadcast(n, "<green>" + Txt.esc(p.getName()) + " a rejoint la nation !");
    }

    public void leave(Player p) {
        Nation n = need(p, Role.MEMBER);
        if (n == null) return;
        if (n.leader().equals(p.getUniqueId())) { Txt.err(p, "Le chef doit transférer la nation ou la dissoudre."); return; }
        nm.removeMember(n, p.getUniqueId());
        Txt.ok(p, "Vous avez quitté " + n.colored() + "<green>.");
        nm.broadcast(n, "<red>" + Txt.esc(p.getName()) + " a quitté la nation.");
    }

    public void kick(Player p, UUID target) {
        Nation n = need(p, Role.OFFICER);
        if (n == null) return;
        Role r = n.role(target);
        if (r == null) { Txt.err(p, "Ce joueur n'est pas dans votre nation."); return; }
        if (r.power >= n.role(p.getUniqueId()).power) { Txt.err(p, "Vous ne pouvez pas expulser ce membre."); return; }
        nm.removeMember(n, target);
        OfflinePlayer op = Bukkit.getOfflinePlayer(target);
        nm.broadcast(n, "<red>" + op.getName() + " a été expulsé.");
        if (op.getPlayer() != null) Txt.err(op.getPlayer(), "Vous avez été expulsé de " + n.name() + ".");
    }

    public void promote(Player p, UUID target) {
        Nation n = need(p, Role.LEADER);
        if (n == null) return;
        if (n.role(target) == Role.MEMBER) {
            n.members().put(target, Role.OFFICER);
            nm.broadcast(n, "<yellow>" + Bukkit.getOfflinePlayer(target).getName() + " est promu Ministre.");
        } else if (n.role(target) == Role.OFFICER) {
            Txt.err(p, "Utilisez « transférer » pour céder la direction.");
        }
    }

    public void demote(Player p, UUID target) {
        Nation n = need(p, Role.LEADER);
        if (n == null) return;
        if (n.role(target) == Role.OFFICER) {
            n.members().put(target, Role.MEMBER);
            nm.broadcast(n, "<yellow>" + Bukkit.getOfflinePlayer(target).getName() + " est rétrogradé Citoyen.");
        }
    }

    public void transfer(Player p, UUID target) {
        Nation n = need(p, Role.LEADER);
        if (n == null) return;
        if (n.role(target) == null || target.equals(p.getUniqueId())) { Txt.err(p, "Membre invalide."); return; }
        n.setLeader(target);
        nm.broadcast(n, "<gold>" + Bukkit.getOfflinePlayer(target).getName() + " est le nouveau Chef d'État !");
    }

    public void claim(Player p) {
        Nation n = need(p, Role.OFFICER);
        if (n == null) return;
        if (plugin.getConfig().getStringList("nations.mondes-interdits").contains(p.getWorld().getName())) {
            Txt.err(p, "On ne peut pas revendiquer de territoire dans ce monde."); return;
        }
        Nation owner = nm.at(p.getLocation());
        if (owner != null) { Txt.err(p, "Ce territoire appartient déjà à " + owner.colored() + "<red>."); return; }
        if (plugin.rooms().intersectsChunk(p.getLocation())) { Txt.err(p, "Ce chunk contient des chambres de l'hôtel."); return; }
        if (n.claims().size() >= nm.maxClaims(n)) { Txt.err(p, "Limite de territoire atteinte (" + nm.maxClaims(n) + "). Recrutez ou recherchez Expansion."); return; }
        if (plugin.getConfig().getBoolean("nations.claims-adjacents", true) && !nm.adjacent(n, p.getLocation())) {
            Txt.err(p, "Le territoire doit être adjacent à votre nation."); return;
        }
        double cost = plugin.getConfig().getDouble("nations.cout-claim", 500);
        if (!plugin.bank().nationWithdraw(n, cost, "Revendication de territoire")) {
            Txt.err(p, "Le trésor national n'a pas " + Txt.money(cost) + "."); return;
        }
        nm.claim(n, p.getLocation());
        Txt.ok(p, "Chunk revendiqué pour " + n.colored() + "<green> (" + n.claims().size() + "/" + nm.maxClaims(n) + ").");
    }

    public void unclaim(Player p) {
        Nation n = need(p, Role.OFFICER);
        if (n == null) return;
        String key = Locs.chunkKey(p.getLocation());
        if (!n.claims().contains(key)) { Txt.err(p, "Ce chunk n'appartient pas à votre nation."); return; }
        nm.unclaim(n, key);
        Txt.ok(p, "Chunk abandonné.");
    }

    public void deposit(Player p, double amount) {
        Nation n = need(p, Role.MEMBER);
        if (n == null) return;
        if (!plugin.bank().withdraw(p.getUniqueId(), amount, "Don au trésor de " + n.name())) { Txt.err(p, "Fonds insuffisants."); return; }
        plugin.bank().nationDeposit(n, amount, "Don de " + p.getName());
        Txt.ok(p, Txt.money(amount) + " versés au trésor national.");
    }

    public void withdraw(Player p, double amount) {
        Nation n = need(p, Role.OFFICER);
        if (n == null) return;
        if (!plugin.bank().nationWithdraw(n, amount, "Retrait par " + p.getName())) { Txt.err(p, "Trésor insuffisant."); return; }
        plugin.bank().deposit(p.getUniqueId(), amount, "Retrait du trésor de " + n.name());
        Txt.ok(p, Txt.money(amount) + " retirés du trésor national.");
    }

    public void ally(Player p, String other) {
        Nation n = need(p, Role.OFFICER);
        if (n == null) return;
        Nation o = nm.get(other);
        if (o == null || o == n) { Txt.err(p, "Nation invalide."); return; }
        if (plugin.wars().between(n, o) != null) { Txt.err(p, "Vous êtes en guerre contre cette nation !"); return; }
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
            Bukkit.broadcast(Txt.raw(Txt.PREFIX + n.colored() + " <green>et " + o.colored() + " <green>sont désormais alliées !"));
            return;
        }
        o.allyRequests().add(n.id());
        Txt.ok(p, "Demande d'alliance envoyée à " + o.colored());
        nm.broadcast(o, "<yellow>" + n.colored() + " <yellow>propose une alliance. (/nation allie " + n.name() + ")");
    }

    public void setHome(Player p) {
        Nation n = need(p, Role.OFFICER);
        if (n == null) return;
        if (nm.at(p.getLocation()) != n) { Txt.err(p, "La capitale doit être sur votre territoire."); return; }
        n.setHome(p.getLocation());
        Txt.ok(p, "Capitale définie.");
    }

    public void home(Player p) {
        Nation n = need(p, Role.MEMBER);
        if (n == null) return;
        if (n.home() == null) { Txt.err(p, "Aucune capitale définie."); return; }
        if (plugin.wars().isCapturing(p)) { Txt.err(p, "Impossible pendant une capture."); return; }
        p.teleportAsync(n.home());
        Txt.ok(p, "Téléportation à la capitale.");
    }

    public void setTax(Player p, double v) {
        Nation n = need(p, Role.LEADER);
        if (n == null) return;
        double max = plugin.getConfig().getDouble("nations.impot-max", 200);
        n.setTaxPerHour(Math.max(0, Math.min(max, v)));
        nm.broadcast(n, "<yellow>Nouvel impôt : " + Txt.money(n.taxPerHour()) + " par heure.");
    }

    public void setTag(Player p, String tag) {
        Nation n = need(p, Role.LEADER);
        if (n == null) return;
        if (!tag.matches("[A-Za-z0-9]{2,5}")) { Txt.err(p, "Le tag doit faire 2 à 5 lettres/chiffres."); return; }
        n.setTag(tag.toUpperCase());
        Txt.ok(p, "Tag modifié : [" + n.tag() + "]");
    }

    public void disband(Player p) {
        Nation n = need(p, Role.LEADER);
        if (n == null) return;
        plugin.bank().deposit(p.getUniqueId(), n.balance(), "Dissolution de " + n.name());
        nm.disband(n);
    }
}

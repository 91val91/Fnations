package fr.fnations.war;

import fr.fnations.FNations;
import fr.fnations.nation.Nation;
import fr.fnations.nation.Role;
import fr.fnations.util.Locs;
import fr.fnations.util.Txt;
import fr.fnations.util.YamlStore;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Système de guerres : déclaration, préparation, score, captures, paix et réparations. */
public final class WarManager implements Listener {
    private final FNations plugin;
    private final YamlStore store;
    private final Map<String, War> wars = new HashMap<>();
    private final Map<UUID, Capture> captures = new HashMap<>();

    private static final class Capture {
        final War war;
        final String chunk;
        final String nation;
        int elapsed;
        final BossBar bar;

        Capture(War war, String chunk, String nation, BossBar bar) {
            this.war = war;
            this.chunk = chunk;
            this.nation = nation;
            this.bar = bar;
        }
    }

    public WarManager(FNations plugin) {
        this.plugin = plugin;
        this.store = new YamlStore(plugin, "data/wars.yml");
        load();
    }

    private FileConfiguration cfg() { return plugin.getConfig(); }

    // ------------------------------------------------------------ requêtes
    public List<War> all() { return new ArrayList<>(wars.values()); }

    public List<War> of(Nation n) {
        List<War> l = new ArrayList<>();
        if (n == null) return l;
        for (War w : wars.values()) if (w.involves(n.id())) l.add(w);
        return l;
    }

    public War between(Nation a, Nation b) {
        if (a == null || b == null || a == b) return null;
        for (War w : wars.values()) if (w.involves(a.id()) && w.involves(b.id())) return w;
        return null;
    }

    public boolean activeBetween(Nation a, Nation b) {
        War w = between(a, b);
        return w != null && w.state() == War.State.ACTIVE;
    }

    public boolean isCapturing(Player p) { return captures.containsKey(p.getUniqueId()); }

    public Nation nation(String id) { return plugin.nations().get(id); }

    // ------------------------------------------------------------ actions
    public void declare(Player p, Nation target) {
        Nation n = plugin.nations().of(p);
        if (n == null || !n.can(p.getUniqueId(), Role.OFFICER)) { Txt.err(p, "Seuls les Ministres d'une nation peuvent déclarer la guerre."); return; }
        if (target == null || target == n) { Txt.err(p, "Cible invalide."); return; }
        if (plugin.nations().allied(n, target)) { Txt.err(p, "Vous ne pouvez pas attaquer un allié. Rompez l'alliance d'abord."); return; }
        if (between(n, target) != null) { Txt.err(p, "Vous êtes déjà en guerre contre cette nation."); return; }
        String tech = cfg().getString("guerres.recherche-requise", "doctrine_militaire");
        if (!plugin.research().has(n, tech) && !p.hasPermission("fnations.admin.bypass")) {
            var t = plugin.research().get(tech);
            Txt.err(p, "Recherche requise : " + (t == null ? tech : t.name()));
            return;
        }
        int minOnline = cfg().getInt("guerres.defenseurs-en-ligne-min", 1);
        if (plugin.nations().online(target).size() < minOnline) { Txt.err(p, "Au moins " + minOnline + " défenseur(s) doivent être en ligne."); return; }
        double cost = cfg().getDouble("guerres.cout-declaration", 10000);
        if (!plugin.bank().nationWithdraw(n, cost, "Déclaration de guerre à " + target.name())) {
            Txt.err(p, "Le trésor national n'a pas " + Txt.money(cost) + "."); return;
        }
        long now = System.currentTimeMillis();
        long prep = cfg().getLong("guerres.preparation-minutes", 10) * 60_000L;
        War w = new War(UUID.randomUUID().toString().substring(0, 8), n.id(), target.id(), now, now + prep);
        wars.put(w.id(), w);
        n.allyRequests().remove(target.id());
        target.allyRequests().remove(n.id());
        Bukkit.broadcast(Txt.raw(Txt.PREFIX + "<dark_red><b>⚔ GUERRE !</b> " + n.colored() + " <red>a déclaré la guerre à " + target.colored()
                + "<red> ! Début des hostilités dans " + Txt.duration(prep / 1000) + "."));
        for (Player pl : Bukkit.getOnlinePlayers()) pl.playSound(pl.getLocation(), Sound.EVENT_RAID_HORN, 1f, 1f);
        for (Player pl : plugin.nations().online(n)) plugin.advancements().grant(pl, "first_war");
        for (Player pl : plugin.nations().online(target)) plugin.advancements().grant(pl, "first_war");
    }

    public void addScore(War w, Nation n, int pts, String reason) {
        if (w.state() != War.State.ACTIVE || pts <= 0) return;
        w.addScore(n.id(), pts);
        Nation o = nation(w.other(n.id()));
        plugin.nations().broadcast(n, "<green>+" + pts + " points de guerre <gray>(" + reason + ") <dark_gray>— " + w.score(n.id()) + "/" + goal());
        if (o != null) plugin.nations().broadcast(o, "<red>L'ennemi marque " + pts + " points <gray>(" + reason + ")");
        if (w.score(n.id()) >= goal()) end(w, n, "a atteint le score de victoire");
    }

    public int goal() { return cfg().getInt("guerres.score-victoire", 100); }

    /** Points pour un événement entre deux nations (missile, destruction de véhicule...). */
    public void reward(Nation attacker, Nation victim, int pts, String reason) {
        War w = between(attacker, victim);
        if (w != null) addScore(w, attacker, pts, reason);
    }

    public void proposePeace(Player p, War w) {
        Nation n = plugin.nations().of(p);
        if (n == null || !n.can(p.getUniqueId(), Role.OFFICER) || !w.involves(n.id())) return;
        if (w.peaceProposedBy() != null && !w.peaceProposedBy().equals(n.id())) {
            end(w, null, "Traité de paix signé");
            return;
        }
        w.setPeaceProposedBy(n.id());
        Nation o = nation(w.other(n.id()));
        Txt.ok(p, "Proposition de paix envoyée.");
        if (o != null) plugin.nations().broadcast(o, "<yellow>" + n.colored() + " <yellow>propose un traité de paix blanche. (GUI Guerres)");
    }

    public void surrender(Player p, War w) {
        Nation n = plugin.nations().of(p);
        if (n == null || !n.can(p.getUniqueId(), Role.LEADER) || !w.involves(n.id())) { Txt.err(p, "Seul le Chef d'État peut capituler."); return; }
        Nation o = nation(w.other(n.id()));
        end(w, o, n.name() + " a capitulé");
    }

    public void end(War w, Nation winner, String reason) {
        wars.remove(w.id());
        Nation a = nation(w.attacker()), d = nation(w.defender());
        captures.entrySet().removeIf(e -> {
            if (e.getValue().war != w) return false;
            Player pl = Bukkit.getPlayer(e.getKey());
            if (pl != null) pl.hideBossBar(e.getValue().bar);
            return true;
        });
        if (winner == null) {
            Bukkit.broadcast(Txt.raw(Txt.PREFIX + "<green>☮ Paix entre " + (a == null ? "?" : a.colored()) + " <green>et " + (d == null ? "?" : d.colored()) + "<green>. <gray>(" + reason + ")"));
            return;
        }
        Nation loser = winner == a ? d : a;
        winner.addWin();
        double rep = 0;
        if (loser != null) {
            loser.addLoss();
            rep = Math.floor(loser.balance() * cfg().getDouble("guerres.reparations-pourcent", 30) / 100.0);
            if (rep > 0 && plugin.bank().nationWithdraw(loser, rep, "Réparations de guerre")) plugin.bank().nationDeposit(winner, rep, "Réparations de guerre");
        }
        Bukkit.broadcast(Txt.raw(Txt.PREFIX + "<gold><b>🏆 VICTOIRE</b> de " + winner.colored() + " <gold>contre " + (loser == null ? "?" : loser.colored())
                + " <gray>(" + reason + ")" + (rep > 0 ? " <gold>— Réparations : " + Txt.money(rep) : "")));
        for (Player pl : plugin.nations().online(winner)) {
            pl.showTitle(Title.title(Txt.raw("<gold><b>VICTOIRE"), Txt.raw("<yellow>" + reason)));
            pl.playSound(pl.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            plugin.advancements().grant(pl, "war_won");
        }
        if (loser != null) for (Player pl : plugin.nations().online(loser)) pl.showTitle(Title.title(Txt.raw("<dark_red><b>DÉFAITE"), Txt.raw("<red>" + reason)));
    }

    public void onNationDisband(Nation n) {
        for (War w : of(n)) end(w, nation(w.other(n.id())), n.name() + " a été dissoute");
    }

    // ------------------------------------------------------------ captures
    public void startCapture(Player p) {
        Nation n = plugin.nations().of(p);
        Nation owner = plugin.nations().at(p.getLocation());
        if (n == null || owner == null || owner == n) { Txt.err(p, "Placez-vous sur un territoire ennemi."); return; }
        War w = between(n, owner);
        if (w == null || w.state() != War.State.ACTIVE) { Txt.err(p, "Vous n'êtes pas en guerre active contre " + owner.colored() + "<red>."); return; }
        if (captures.containsKey(p.getUniqueId())) { Txt.err(p, "Capture déjà en cours."); return; }
        String key = Locs.chunkKey(p.getLocation());
        if (owner.home() != null && Locs.chunkKey(owner.home()).equals(key) && owner.claims().size() > 1) {
            Txt.err(p, "La capitale ne peut être prise qu'en dernier."); return;
        }
        for (Capture c : captures.values()) if (c.chunk.equals(key)) { Txt.err(p, "Ce chunk est déjà en cours de capture."); return; }
        BossBar bar = BossBar.bossBar(Txt.raw("<red>Capture du territoire de " + owner.colored()), 0f, BossBar.Color.RED, BossBar.Overlay.NOTCHED_10);
        p.showBossBar(bar);
        captures.put(p.getUniqueId(), new Capture(w, key, n.id(), bar));
        plugin.nations().broadcast(owner, "<dark_red>⚠ " + p.getName() + " capture un de vos territoires !");
        Txt.ok(p, "Capture lancée ! Restez dans ce chunk " + cfg().getInt("guerres.capture-secondes", 60) + " secondes.");
    }

    private void tickCaptures() {
        int need = cfg().getInt("guerres.capture-secondes", 60);
        Iterator<Map.Entry<UUID, Capture>> it = captures.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            Capture c = e.getValue();
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null || p.isDead() || !Locs.chunkKey(p.getLocation()).equals(c.chunk) || !wars.containsKey(c.war.id())) {
                if (p != null) { p.hideBossBar(c.bar); Txt.err(p, "Capture interrompue."); }
                it.remove();
                continue;
            }
            c.elapsed++;
            c.bar.progress(Math.min(1f, c.elapsed / (float) need));
            if (c.elapsed < need) continue;
            p.hideBossBar(c.bar);
            it.remove();
            Nation n = nation(c.nation);
            Nation victim = plugin.nations().atChunk(c.chunk);
            if (n == null) continue;
            plugin.nations().transferClaim(c.chunk, n);
            if (victim != null && victim.home() != null && Locs.chunkKey(victim.home()).equals(c.chunk)) victim.setHome(null);
            Txt.ok(p, "Territoire capturé !");
            addScore(c.war, n, cfg().getInt("guerres.points-capture", 15), "territoire capturé");
            if (victim != null && victim.claims().isEmpty() && wars.containsKey(c.war.id())) end(c.war, n, victim.name() + " a perdu tout son territoire");
        }
    }

    // ------------------------------------------------------------ tick & événements
    public void tick() {
        long now = System.currentTimeMillis();
        long max = cfg().getLong("guerres.duree-max-heures", 72) * 3_600_000L;
        for (War w : all()) {
            long prepLeft = w.activeAt() - now;
            if (prepLeft > 0 && prepLeft <= 1000) {
                Nation a = nation(w.attacker()), d = nation(w.defender());
                for (Nation n : new Nation[]{a, d}) if (n != null) for (Player p : plugin.nations().online(n)) {
                    p.showTitle(Title.title(Txt.raw("<dark_red><b>⚔ LA GUERRE COMMENCE"), Txt.raw("<red>" + (a == null ? "" : a.name()) + " vs " + (d == null ? "" : d.name()))));
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 1f);
                }
            }
            if (now - w.declaredAt() > max) {
                Nation a = nation(w.attacker()), d = nation(w.defender());
                Nation winner = w.attackerScore() == w.defenderScore() ? null : w.attackerScore() > w.defenderScore() ? a : d;
                end(w, winner, "Durée maximale atteinte");
            }
        }
        tickCaptures();
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        if (killer == null) return;
        Nation k = plugin.nations().of(killer), v = plugin.nations().of(victim);
        War w = between(k, v);
        if (w == null || w.state() != War.State.ACTIVE) return;
        w.addKill(k.id());
        addScore(w, k, cfg().getInt("guerres.points-kill", 5), killer.getName() + " a abattu " + victim.getName());
        double bounty = cfg().getDouble("guerres.prime-kill", 100);
        if (bounty > 0) plugin.bank().deposit(killer.getUniqueId(), bounty, "Prime de guerre");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Capture c = captures.remove(e.getPlayer().getUniqueId());
        if (c != null) e.getPlayer().hideBossBar(c.bar);
    }

    // ------------------------------------------------------------ persistance
    private void load() {
        ConfigurationSection root = store.get().getConfigurationSection("wars");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            War w = new War(id, s.getString("attacker"), s.getString("defender"), s.getLong("declared"), s.getLong("active"));
            w.setScores(s.getInt("score-a"), s.getInt("score-d"));
            w.setKills(s.getInt("kills-a"), s.getInt("kills-d"));
            w.setPeaceProposedBy(s.getString("peace"));
            wars.put(id, w);
        }
    }

    public void save() {
        YamlConfiguration y = store.fresh();
        for (War w : wars.values()) {
            String p = "wars." + w.id() + ".";
            y.set(p + "attacker", w.attacker());
            y.set(p + "defender", w.defender());
            y.set(p + "declared", w.declaredAt());
            y.set(p + "active", w.activeAt());
            y.set(p + "score-a", w.attackerScore());
            y.set(p + "score-d", w.defenderScore());
            y.set(p + "kills-a", w.kills(w.attacker()));
            y.set(p + "kills-d", w.kills(w.defender()));
            y.set(p + "peace", w.peaceProposedBy());
        }
        store.save();
    }
}

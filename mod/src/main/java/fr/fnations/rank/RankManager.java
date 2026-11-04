package fr.fnations.rank;

import fr.fnations.server.FnServer;
import fr.fnations.server.Msg;
import fr.fnations.util.Store;
import fr.fnations.util.Txt;
import fr.fnations.util.Yaml;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Grades des joueurs : durée, préfixes (équipes du tableau des scores), avantages et récompenses périodiques. */
public final class RankManager {
    public record Entry(Rank rank, long expires) {
        public boolean active() { return rank != Rank.NONE && (expires == 0 || expires > System.currentTimeMillis()); }
    }

    public enum Reward {
        DAILY("Récompense quotidienne", 86_400_000L), WEEKLY("Récompense hebdomadaire", 7 * 86_400_000L), MONTHLY("Récompense mensuelle", 30 * 86_400_000L);

        public final String display;
        public final long period;

        Reward(String display, long period) {
            this.display = display;
            this.period = period;
        }
    }

    private final FnServer s;
    private final Store store;
    private final Map<UUID, Entry> ranks = new HashMap<>();
    private final Map<String, Long> claims = new HashMap<>();

    public RankManager(FnServer s) {
        this.s = s;
        this.store = new Store(s.dataDir(), "grades.yml");
        load();
    }

    public Rank rank(UUID u) {
        Entry e = ranks.get(u);
        return e != null && e.active() ? e.rank() : Rank.NONE;
    }

    public Entry entry(UUID u) { return ranks.get(u); }

    /** Donne un grade (days = 0 : permanent). Un grade inférieur au grade actif n'écrase pas ce dernier. */
    public void grant(UUID u, String name, Rank r, int days) {
        long now = System.currentTimeMillis();
        Entry cur = ranks.get(u);
        long dur = days <= 0 ? 0 : days * 86_400_000L;
        Entry next;
        if (cur != null && cur.active() && cur.rank() == r) {
            next = new Entry(r, cur.expires() == 0 || dur == 0 ? 0 : cur.expires() + dur);
        } else if (cur != null && cur.active() && cur.rank().level > r.level) {
            next = cur; // garde le grade supérieur
        } else {
            next = new Entry(r, dur == 0 ? 0 : now + dur);
        }
        ranks.put(u, next);
        ServerPlayer p = s.player(u);
        if (p != null) {
            apply(p);
            Msg.title(p, r.tag(), "<white>Merci pour votre soutien !");
            Msg.sound(p, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
        if (next.rank() == r) Msg.broadcast(s.server(), "<white>" + Txt.esc(name) + " <gray>est désormais " + r.tag() + "<gray> !");
    }

    public void remove(UUID u) {
        ranks.remove(u);
        ServerPlayer p = s.player(u);
        if (p != null) apply(p);
    }

    // ------------------------------------------------------------ avantages
    public double jobBonus(UUID u) { return rank(u).jobBonus; }

    public int hdvSlots(UUID u) { return rank(u).hdvSlots; }

    public double taxCut(UUID u) { return rank(u).taxCut; }

    /** Prochaine réclamation possible (0 = disponible maintenant, -1 = pas droit). */
    public long nextClaim(UUID u, Reward r) {
        if (!available(rank(u), r)) return -1;
        Long last = claims.get(u + ":" + r.name());
        long next = last == null ? 0 : last + r.period;
        return next <= System.currentTimeMillis() ? 0 : next;
    }

    public static boolean available(Rank rank, Reward r) {
        return switch (r) {
            case DAILY, WEEKLY -> rank.level >= 1;
            case MONTHLY -> rank.level >= 2;
        };
    }

    /** Contenu d'une récompense : [argent, lootbox, nombre]. */
    public static String[] content(Rank rank, Reward r) {
        return switch (r) {
            case DAILY -> new String[]{String.valueOf((int) rank.daily), "", "0"};
            case WEEKLY -> new String[]{"0", rank == Rank.ULTRA ? "legendaire" : "classic", "1"};
            case MONTHLY -> new String[]{"0", rank == Rank.ULTRA ? "ultra" : "legendaire", "1"};
        };
    }

    public boolean claim(ServerPlayer p, Reward r) {
        if (nextClaim(p.getUUID(), r) != 0) { Msg.err(p, "Récompense indisponible pour le moment."); return false; }
        Rank rank = rank(p.getUUID());
        String[] c = content(rank, r);
        double money = Double.parseDouble(c[0]);
        if (money > 0) s.bank().deposit(p.getUUID(), money, r.display + " " + rank.display);
        if (!c[1].isEmpty()) s.loot().give(p, c[1], Integer.parseInt(c[2]));
        claims.put(p.getUUID() + ":" + r.name(), System.currentTimeMillis());
        Msg.ok(p, r.display + " récupérée !");
        Msg.sound(p, SoundEvents.PLAYER_LEVELUP, 0.8f, 1.3f);
        return true;
    }

    // ------------------------------------------------------------ préfixes (équipes)
    private static String teamName(Rank r) { return "fn" + (4 - r.level) + "_" + r.id(); }

    private PlayerTeam team(Rank r) {
        Scoreboard sb = s.server().getScoreboard();
        PlayerTeam t = sb.getPlayerTeam(teamName(r));
        if (t == null) t = sb.addPlayerTeam(teamName(r));
        t.setPlayerPrefix(Txt.mm(r.tag() + " "));
        t.setColor(r.chat);
        return t;
    }

    /** Met à jour l'équipe (préfixe) du joueur selon son grade actif. */
    public void apply(ServerPlayer p) {
        Scoreboard sb = s.server().getScoreboard();
        Rank r = rank(p.getUUID());
        String name = p.getScoreboardName();
        PlayerTeam cur = sb.getPlayersTeam(name);
        if (r == Rank.NONE) {
            if (cur != null && cur.getName().startsWith("fn") && cur.getName().contains("_")) sb.removePlayerFromTeam(name, cur);
            return;
        }
        PlayerTeam t = team(r);
        if (cur != t) sb.addPlayerToTeam(name, t);
    }

    /** Chaque minute : fin des grades expirés. */
    public void tick() {
        long now = System.currentTimeMillis();
        for (var e : new HashMap<>(ranks).entrySet()) {
            Entry en = e.getValue();
            if (en.rank() != Rank.NONE && en.expires() != 0 && en.expires() <= now) {
                ranks.remove(e.getKey());
                ServerPlayer p = s.player(e.getKey());
                if (p != null) {
                    apply(p);
                    Msg.send(p, "<yellow>Votre grade " + en.rank().tag() + "<yellow> a expiré. Merci pour votre soutien !");
                }
            }
        }
    }

    // ------------------------------------------------------------ persistance
    private void load() {
        Yaml y = store.get();
        Yaml g = y.getSection("grades");
        if (g != null) for (String k : g.getKeys()) {
            try {
                Rank r = Rank.byId(g.getString(k + ".grade"));
                if (r != null) ranks.put(UUID.fromString(k), new Entry(r, g.getLong(k + ".expire")));
            } catch (IllegalArgumentException ignored) {
            }
        }
        Yaml c = y.getSection("recompenses");
        if (c != null) for (String k : c.getKeys()) claims.put(k.replace('_', ':'), c.getLong(k));
    }

    public void save() {
        Yaml y = store.fresh();
        ranks.forEach((u, e) -> {
            y.set("grades." + u + ".grade", e.rank().id());
            y.set("grades." + u + ".expire", e.expires());
        });
        claims.forEach((k, v) -> y.set("recompenses." + k.replace(':', '_'), v));
        store.save();
    }
}

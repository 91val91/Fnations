package fr.fnations.rating;

import fr.fnations.job.Job;
import fr.fnations.nation.Nation;
import fr.fnations.server.FnServer;
import fr.fnations.server.Msg;
import fr.fnations.util.Locs;
import fr.fnations.util.Store;
import fr.fnations.util.Txt;
import fr.fnations.util.Yaml;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Notation des constructions : un joueur soumet une construction (pour lui ou pour son pays), le jury la note
 * sur cinq critères (Build, Skill, Architecture, Respect du thème, R&D) de 0 à 10. Les moyennes donnent la note
 * de la construction, celle du joueur et celle du pays. La première note récompense l'auteur (XP Builder,
 * XP Ingénieur selon la R&D, argent).
 */
public final class RatingManager {
    /** Les cinq critères de notation. */
    public enum Criterion {
        BUILD("build", "Build", "hammer", "Quantité et finition des constructions"),
        SKILL("skill", "Skill", "star", "Technique : détails, profondeur, terraformation"),
        ARCHITECTURE("architecture", "Architecture", "home", "Style, proportions, organisation de la ville"),
        THEME("theme", "Respect du thème", "flag", "Fidélité au thème annoncé (pays, époque, style)"),
        RD("rd", "R&D", "flask", "Technologie : machines, énergie, recherches de la nation");

        public final String id, display, icon, hint;

        Criterion(String id, String display, String icon, String hint) {
            this.id = id;
            this.display = display;
            this.icon = icon;
            this.hint = hint;
        }
    }

    /** Note d'un juré : une valeur de 0 à 10 par critère. */
    public record Note(UUID juror, String jurorName, int[] values, String comment, long time) {
        public double global() {
            double t = 0;
            for (int v : values) t += v;
            return t / values.length;
        }
    }

    /** Construction soumise au jury. */
    public static final class Build {
        public final int id;
        public final String name, theme, description, authorName, nation;
        public final UUID author;
        public final Locs.Spot spot;
        public final long created;
        public final Map<UUID, Note> notes = new LinkedHashMap<>();
        public boolean rewarded, unread;

        Build(int id, String name, String theme, String description, UUID author, String authorName, String nation, Locs.Spot spot, long created) {
            this.id = id;
            this.name = name;
            this.theme = theme;
            this.description = description;
            this.author = author;
            this.authorName = authorName;
            this.nation = nation;
            this.spot = spot;
            this.created = created;
        }

        public boolean rated() { return !notes.isEmpty(); }

        /** Moyenne des jurés pour un critère (0 si pas encore notée). */
        public double avg(Criterion c) {
            if (notes.isEmpty()) return 0;
            double t = 0;
            for (Note n : notes.values()) t += n.values()[c.ordinal()];
            return t / notes.size();
        }

        public double global() {
            if (notes.isEmpty()) return 0;
            double t = 0;
            for (Criterion c : Criterion.values()) t += avg(c);
            return t / Criterion.values().length;
        }
    }

    /** Notes moyennes d'un pays (ou d'un joueur) : un par critère, la note globale et le nombre de constructions notées. */
    public record Score(double[] criteria, double global, int count) {}

    private final FnServer s;
    private final Store store;
    private final Map<Integer, Build> builds = new LinkedHashMap<>();
    private final Set<UUID> jury = new LinkedHashSet<>();
    private int nextId = 1;

    public RatingManager(FnServer s) {
        this.s = s;
        this.store = new Store(s.dataDir(), "notation.yml");
        load();
    }

    // ------------------------------------------------------------ jury
    /** Juré : opérateur (niveau notation.niveau-op-jury), nommé par commande ou listé dans config.yml. */
    public boolean isJury(ServerPlayer p) {
        if (p.hasPermissions(Math.max(1, s.config().getInt("notation.niveau-op-jury", 2)))) return true;
        if (jury.contains(p.getUUID())) return true;
        for (String n : s.config().getStringList("notation.jury")) if (n.equalsIgnoreCase(p.getScoreboardName())) return true;
        return false;
    }

    public Set<UUID> jury() { return jury; }

    public boolean addJury(UUID u) { return jury.add(u); }

    public boolean removeJury(UUID u) { return jury.remove(u); }

    // ------------------------------------------------------------ constructions
    public Build get(int id) { return builds.get(id); }

    public List<Build> all() { return new ArrayList<>(builds.values()); }

    public List<Build> byAuthor(UUID u) {
        List<Build> out = new ArrayList<>();
        for (Build b : builds.values()) if (b.author.equals(u)) out.add(b);
        out.sort(Comparator.comparingLong((Build b) -> b.created).reversed());
        return out;
    }

    /** Constructions en attente du jury, les plus anciennes d'abord. */
    public List<Build> pending() {
        List<Build> out = new ArrayList<>();
        for (Build b : builds.values()) if (!b.rated()) out.add(b);
        out.sort(Comparator.comparingLong(b -> b.created));
        return out;
    }

    /** Constructions notées, de la meilleure à la moins bonne. */
    public List<Build> ranked() {
        List<Build> out = new ArrayList<>();
        for (Build b : builds.values()) if (b.rated()) out.add(b);
        out.sort(Comparator.comparingDouble(Build::global).reversed());
        return out;
    }

    /** Soumet la construction où se tient le joueur. Retourne un message d'erreur, ou null si c'est fait. */
    public String submit(ServerPlayer p, String name, String theme, String description, boolean forNation) {
        name = name == null ? "" : name.trim();
        theme = theme == null ? "" : theme.trim();
        if (name.length() < 3 || name.length() > 32) return "Le nom doit faire de 3 à 32 caractères.";
        if (theme.isEmpty() || theme.length() > 40) return "Indiquez le thème de la construction (40 caractères au plus).";
        int max = Math.max(1, s.config().getInt("notation.max-en-attente", 3));
        long waiting = byAuthor(p.getUUID()).stream().filter(b -> !b.rated()).count();
        if (waiting >= max) return "Vous avez déjà " + waiting + " construction(s) en attente du jury (maximum " + max + ").";
        Nation n = forNation ? s.nations().of(p) : null;
        Build b = new Build(nextId++, name, theme, description == null ? "" : description.trim(), p.getUUID(), p.getScoreboardName(),
                n == null ? null : n.id(), Locs.Spot.of(p.level(), p.position(), p.getYRot(), p.getXRot()), System.currentTimeMillis());
        builds.put(b.id, b);
        save();
        int pending = pending().size();
        for (ServerPlayer j : s.server().getPlayerList().getPlayers())
            if (j != p && isJury(j)) Msg.send(j, "<#8C96FF>Notation : <white>" + Txt.esc(p.getScoreboardName()) + " soumet « " + Txt.esc(name)
                    + " » <gray>(" + pending + " en attente, /notation)");
        return null;
    }

    /**
     * Inscription par un opérateur (par exemple pour un joueur absent) : la construction est enregistrée à la position
     * de l'opérateur et rattachée au pays propriétaire de ce territoire.
     */
    public Build register(ServerPlayer op, UUID author, String authorName, String name, String theme) {
        Nation n = s.nations().at(op.serverLevel(), op.blockPosition());
        Build b = new Build(nextId++, name.trim(), theme.trim(), "Inscrite par " + op.getScoreboardName(), author, authorName,
                n == null ? null : n.id(), Locs.Spot.of(op.level(), op.position(), op.getYRot(), op.getXRot()), System.currentTimeMillis());
        builds.put(b.id, b);
        save();
        return b;
    }

    /** Note d'un juré (remplace sa note précédente). Retourne un message d'erreur, ou null. */
    public String rate(ServerPlayer juror, int id, int[] values, String comment) {
        Build b = builds.get(id);
        if (b == null) return "Construction introuvable.";
        if (!isJury(juror)) return "Seul le jury peut noter.";
        if (b.author.equals(juror.getUUID())) return "Vous ne pouvez pas noter votre propre construction.";
        if (values.length != Criterion.values().length) return "Note incomplète.";
        int[] v = new int[values.length];
        for (int i = 0; i < v.length; i++) {
            if (values[i] < 0 || values[i] > 10) return "Chaque critère se note de 0 à 10.";
            v[i] = values[i];
        }
        boolean first = !b.rated();
        b.notes.put(juror.getUUID(), new Note(juror.getUUID(), juror.getScoreboardName(), v, comment == null ? "" : comment.trim(), System.currentTimeMillis()));
        if (first && !b.rewarded) reward(b);
        ServerPlayer author = s.player(b.author);
        String msg = "<#8C96FF>Notation : <white>votre construction « " + Txt.esc(b.name) + " » est notée <b>" + fmt(b.global()) + "/10</b>";
        if (author != null) {
            Msg.send(author, msg + " <gray>par " + Txt.esc(juror.getScoreboardName()) + ".");
            Msg.sound(author, SoundEvents.PLAYER_LEVELUP, 0.8f, 1.2f);
        } else b.unread = true;
        if (first && b.global() >= 8) Msg.broadcast(s.server(), "<gold>★ <white>« " + Txt.esc(b.name) + " » de " + Txt.esc(b.authorName)
                + " obtient <b>" + fmt(b.global()) + "/10</b> au jury de construction !");
        save();
        return null;
    }

    /** Première note : XP de Builder et argent selon la note globale, XP d'Ingénieur selon la note R&D. */
    private void reward(Build b) {
        b.rewarded = true;
        double g = b.global();
        if (g < s.config().getDouble("notation.note-minimale", 4)) return;
        double xpB = s.config().getDouble("notation.recompenses.xp-builder", 2000) * g / 10;
        double xpI = s.config().getDouble("notation.recompenses.xp-ingenieur", 800) * b.avg(Criterion.RD) / 10;
        double money = Math.round(s.config().getDouble("notation.recompenses.argent", 15000) * g / 10);
        s.jobs().award(b.author, Job.BUILDER, xpB);
        s.jobs().award(b.author, Job.INGENIEUR, xpI);
        if (money > 0) s.bank().deposit(b.author, money, "Notation : " + b.name);
        ServerPlayer p = s.player(b.author);
        if (p != null) Msg.send(p, "<#8C96FF>✦ <white>Récompense : <green>+" + Math.round(xpB) + " XP Builder<white>, <green>+" + Math.round(xpI)
                + " XP Ingénieur<white>, <green>" + Txt.money(money));
    }

    /** Retire une construction (auteur tant qu'elle n'est pas notée, ou juré). */
    public String remove(ServerPlayer p, int id) {
        Build b = builds.get(id);
        if (b == null) return "Construction introuvable.";
        boolean own = b.author.equals(p.getUUID()) && !b.rated();
        if (!own && !isJury(p)) return "Vous ne pouvez pas retirer cette construction.";
        builds.remove(id);
        save();
        return null;
    }

    /** Téléporte un juré à la construction. */
    public boolean visit(ServerPlayer p, int id) {
        Build b = builds.get(id);
        if (b == null || !isJury(p)) return false;
        ServerLevel level = Locs.level(s.server(), b.spot.dim());
        if (level == null) return false;
        p.teleportTo(level, b.spot.x(), b.spot.y(), b.spot.z(), Set.of(), b.spot.yaw(), b.spot.pitch(), true);
        return true;
    }

    // ------------------------------------------------------------ moyennes
    private static Score score(List<Build> list) {
        double[] c = new double[Criterion.values().length];
        int n = 0;
        for (Build b : list) {
            if (!b.rated()) continue;
            for (Criterion k : Criterion.values()) c[k.ordinal()] += b.avg(k);
            n++;
        }
        if (n == 0) return new Score(c, 0, 0);
        double g = 0;
        for (int i = 0; i < c.length; i++) {
            c[i] /= n;
            g += c[i];
        }
        return new Score(c, g / c.length, n);
    }

    public Score nationScore(String nationId) {
        List<Build> l = new ArrayList<>();
        for (Build b : builds.values()) if (nationId != null && nationId.equals(b.nation)) l.add(b);
        return score(l);
    }

    public Score playerScore(UUID u) { return score(byAuthor(u)); }

    /** Pays classés par note globale (seuls ceux qui ont au moins une construction notée). */
    public List<Map.Entry<Nation, Score>> nationRanking() {
        List<Map.Entry<Nation, Score>> out = new ArrayList<>();
        for (Nation n : s.nations().all()) {
            Score sc = nationScore(n.id());
            if (sc.count() > 0) out.add(Map.entry(n, sc));
        }
        out.sort((a, b) -> Double.compare(b.getValue().global(), a.getValue().global()));
        return out;
    }

    public static String fmt(double v) { return String.format(Locale.FRANCE, "%.1f", v); }

    // ------------------------------------------------------------ connexion
    public void onJoin(ServerPlayer p) {
        boolean changed = false;
        for (Build b : byAuthor(p.getUUID())) {
            if (!b.unread) continue;
            b.unread = false;
            changed = true;
            Msg.send(p, "<#8C96FF>Notation : <white>votre construction « " + Txt.esc(b.name) + " » a été notée <b>" + fmt(b.global()) + "/10</b> pendant votre absence.");
        }
        if (changed) save();
        if (isJury(p)) {
            int n = pending().size();
            if (n > 0) Msg.send(p, "<#8C96FF>Notation : <white>" + n + " construction(s) attendent le jury <gray>(/notation)");
        }
    }

    // ------------------------------------------------------------ persistance
    private void load() {
        Yaml y = store.get();
        nextId = Math.max(1, y.getInt("prochain-id", 1));
        for (String u : y.getStringList("jury")) {
            try { jury.add(UUID.fromString(u)); } catch (IllegalArgumentException ignored) {}
        }
        Yaml cs = y.getSection("constructions");
        if (cs == null) return;
        for (String k : cs.getKeys()) {
            Yaml c = cs.getSection(k);
            if (c == null) continue;
            try {
                Locs.Spot spot = Locs.Spot.parse(c.getString("position"));
                if (spot == null) continue;
                Build b = new Build(Integer.parseInt(k), c.getString("nom", "?"), c.getString("theme", ""), c.getString("description", ""),
                        UUID.fromString(c.getString("auteur")), c.getString("nom-auteur", "?"), c.getString("pays", null), spot, c.getLong("date"));
                b.rewarded = c.getBoolean("recompensee");
                b.unread = c.getBoolean("non-lue");
                Yaml ns = c.getSection("notes");
                if (ns != null) for (String j : ns.getKeys()) {
                    Yaml n = ns.getSection(j);
                    if (n == null) continue;
                    int[] v = new int[Criterion.values().length];
                    for (Criterion cr : Criterion.values()) v[cr.ordinal()] = Math.max(0, Math.min(10, n.getInt(cr.id)));
                    UUID ju = UUID.fromString(j);
                    b.notes.put(ju, new Note(ju, n.getString("jure", "?"), v, n.getString("commentaire", ""), n.getLong("date")));
                }
                builds.put(b.id, b);
                nextId = Math.max(nextId, b.id + 1);
            } catch (RuntimeException ignored) {
                // construction illisible : ignorée
            }
        }
    }

    public void save() {
        Yaml y = store.fresh();
        y.set("prochain-id", nextId);
        List<String> j = new ArrayList<>();
        for (UUID u : jury) j.add(u.toString());
        y.set("jury", j);
        for (Build b : builds.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nom", b.name);
            m.put("theme", b.theme);
            m.put("description", b.description);
            m.put("auteur", b.author.toString());
            m.put("nom-auteur", b.authorName);
            if (b.nation != null) m.put("pays", b.nation);
            m.put("position", b.spot.serialize());
            m.put("date", b.created);
            m.put("recompensee", b.rewarded);
            m.put("non-lue", b.unread);
            Map<String, Object> notes = new LinkedHashMap<>();
            for (Note n : b.notes.values()) {
                Map<String, Object> nm = new LinkedHashMap<>();
                nm.put("jure", n.jurorName());
                for (Criterion c : Criterion.values()) nm.put(c.id, n.values()[c.ordinal()]);
                nm.put("commentaire", n.comment());
                nm.put("date", n.time());
                notes.put(n.juror().toString(), nm);
            }
            m.put("notes", notes);
            y.set("constructions." + b.id, m);
        }
        store.save();
    }
}

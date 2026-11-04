package fr.fnations.company;

import fr.fnations.job.Job;
import fr.fnations.server.FnServer;
import fr.fnations.server.Msg;
import fr.fnations.util.Store;
import fr.fnations.util.Txt;
import fr.fnations.util.Yaml;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Entreprises : création, employés, capital, salaires horaires, niveaux et bonus. */
public final class CompanyManager {
    private static final double[] LEVELS = {0, 1500, 4000, 9000, 18000, 32000, 55000, 90000, 140000, 220000};

    private final FnServer s;
    private final Store store;
    private final Map<String, Company> companies = new LinkedHashMap<>();
    private final Map<UUID, String> byPlayer = new HashMap<>();
    private final Map<String, Double> pendingContrib = new HashMap<>();

    public CompanyManager(FnServer s) {
        this.s = s;
        this.store = new Store(s.dataDir(), "entreprises.yml");
        load();
    }

    // ------------------------------------------------------------ requêtes
    public Collection<Company> all() { return companies.values(); }

    public Company get(String id) { return id == null ? null : companies.get(id.toLowerCase()); }

    public Company of(UUID u) { return get(byPlayer.get(u)); }

    public int level(Company c) {
        int l = 1;
        for (int i = 1; i < LEVELS.length; i++) if (c.xp() >= LEVELS[i]) l = i + 1;
        return l;
    }

    public double levelStart(int level) { return LEVELS[Math.max(0, Math.min(LEVELS.length - 1, level - 1))]; }

    public double levelNext(int level) { return level >= LEVELS.length ? LEVELS[LEVELS.length - 1] : LEVELS[level]; }

    public int maxLevel() { return LEVELS.length; }

    public int maxMembers(Company c) {
        return s.config().getInt("entreprises.membres-base", 5) + s.config().getInt("entreprises.membres-par-niveau", 2) * (level(c) - 1);
    }

    public double creationCost() { return s.config().getDouble("entreprises.cout-creation", 10000); }

    /** Taux de taxe HDV (en %) d'un joueur : réduit pour les employés d'une entreprise de Trading. */
    public double marketTax(UUID u) {
        double base = s.config().getDouble("hdv.taxe-pourcent", 5);
        Company c = of(u);
        if (c != null && c.type() == Company.Type.TRADING) base -= 0.3 * level(c);
        return Math.max(0.5, base);
    }

    /** Emplacements de vente HDV d'un joueur. */
    public int marketSlots(UUID u) {
        int base = s.config().getInt("hdv.ventes-max", 20);
        Company c = of(u);
        if (c != null && c.type() == Company.Type.TRADING) base += 2 * level(c);
        return base;
    }

    /** Bonus affichés pour une entreprise. */
    public List<String> bonuses(Company c) {
        int l = level(c);
        List<String> out = new ArrayList<>();
        if (c.type().job != null) {
            out.add("XP de " + c.type().job.display + " +" + l + " %");
            out.add("Revenus de " + c.type().job.display + " +" + (2 * l) + " %");
        }
        switch (c.type()) {
            case TRADING -> {
                out.add("Taxe HDV : " + String.format(java.util.Locale.FRANCE, "%.1f", marketTax(c.ceo())) + " % au lieu de "
                        + s.config().getDouble("hdv.taxe-pourcent", 5) + " %");
                out.add("+" + (2 * l) + " emplacements de vente HDV");
                out.add("Ventes au nom de l'entreprise sur l'HDV");
            }
            case INGENIEUR -> out.add("+" + l + " % de chance de récupérer un ingrédient");
            default -> {
            }
        }
        out.add("Employés max : " + maxMembers(c));
        return out;
    }

    // ------------------------------------------------------------ économie
    public void addXp(Company c, double v) {
        int before = level(c);
        c.setXp(c.xp() + v);
        int after = level(c);
        if (after > before) {
            for (UUID u : c.members().keySet()) {
                ServerPlayer p = s.player(u);
                if (p == null) continue;
                Msg.title(p, "<#8C96FF><b>Niveau " + after, "<white>" + Txt.esc(c.name()) + " progresse !");
                Msg.sound(p, SoundEvents.PLAYER_LEVELUP, 1f, 1f);
            }
        }
    }

    public void addContribution(Company c, double v) { pendingContrib.merge(c.id(), v, Double::sum); }

    public void deposit(Company c, double v, String reason) {
        c.setBalance(c.balance() + v);
        s.bank().log(c.owner(), "<green>+" + Txt.money(v) + " <gray>" + reason);
    }

    public boolean withdraw(Company c, double v, String reason) {
        if (c.balance() < v) return false;
        c.setBalance(c.balance() - v);
        s.bank().log(c.owner(), "<red>-" + Txt.money(v) + " <gray>" + reason);
        return true;
    }

    /** Appelé chaque minute (contributions) et chaque heure (salaires). */
    public void tick(long ticks) {
        if (ticks % 1200 == 0 && !pendingContrib.isEmpty()) {
            for (var e : pendingContrib.entrySet()) {
                Company c = get(e.getKey());
                if (c != null && e.getValue() >= 0.01) {
                    deposit(c, e.getValue(), "Contributions des employés");
                    c.setEarned(c.earned() + e.getValue());
                }
            }
            pendingContrib.clear();
        }
        if (ticks % 72000 == 0) paySalaries();
    }

    public void paySalaries() {
        for (Company c : companies.values()) {
            for (var e : new ArrayList<>(c.salaries().entrySet())) {
                if (e.getValue() <= 0 || !c.members().containsKey(e.getKey())) continue;
                if (!withdraw(c, e.getValue(), "Salaire de " + s.name(e.getKey()))) {
                    ServerPlayer ceo = s.player(c.ceo());
                    if (ceo != null) Msg.err(ceo, "Capital insuffisant pour payer le salaire de " + s.name(e.getKey()) + ".");
                    continue;
                }
                s.bank().deposit(e.getKey(), e.getValue(), "Salaire " + c.name());
                ServerPlayer p = s.player(e.getKey());
                if (p != null) Msg.ok(p, "Salaire reçu de " + Txt.esc(c.name()) + " : " + Txt.money(e.getValue()));
            }
        }
    }

    // ------------------------------------------------------------ actions des joueurs
    public String validateName(String name) {
        if (name == null || !name.matches("[A-Za-z0-9 _\\-']{3,20}")) return "Le nom doit faire 3 à 20 caractères (lettres, chiffres, espaces, - _ ').";
        if (get(idOf(name)) != null) return "Une entreprise porte déjà ce nom.";
        return null;
    }

    private static String idOf(String name) { return name.trim().toLowerCase().replace(' ', '_'); }

    public Company create(ServerPlayer p, String name, Company.Type type) {
        if (of(p.getUUID()) != null) { Msg.err(p, "Vous travaillez déjà dans une entreprise."); return null; }
        String err = validateName(name);
        if (err != null) { Msg.err(p, err); return null; }
        int need = s.config().getInt("entreprises.palier-requis", 1);
        if (type.job != null && s.jobs().palier(p.getUUID(), type.job) < need) {
            Msg.err(p, "Il faut le palier " + need + " en " + type.job.display + " pour fonder une entreprise de " + type.display + ".");
            return null;
        }
        double cost = creationCost();
        if (!s.bank().withdraw(p.getUUID(), cost, "Création de l'entreprise " + name)) { Msg.err(p, "Il faut " + Txt.money(cost) + " en banque."); return null; }
        Company c = new Company(idOf(name), name.trim(), type, p.getUUID(), System.currentTimeMillis());
        companies.put(c.id(), c);
        byPlayer.put(p.getUUID(), c.id());
        Msg.broadcast(s.server(), "<green>" + Txt.esc(p.getScoreboardName()) + " a fondé l'entreprise <white>" + Txt.esc(c.name()) + " <gray>(" + type.display + ")");
        return c;
    }

    private Company need(ServerPlayer p, Company.Rank min) {
        Company c = of(p.getUUID());
        if (c == null) { Msg.err(p, "Vous n'avez pas d'entreprise."); return null; }
        if (!c.can(p.getUUID(), min)) { Msg.err(p, "Rang insuffisant (" + min.display + " requis)."); return null; }
        return c;
    }

    public void invite(ServerPlayer p, String target) {
        Company c = need(p, Company.Rank.DIRECTEUR);
        if (c == null) return;
        ServerPlayer t = s.playerByName(target);
        if (t == null) { Msg.err(p, "Joueur introuvable (il doit être connecté)."); return; }
        if (of(t.getUUID()) != null) { Msg.err(p, "Ce joueur travaille déjà dans une entreprise."); return; }
        if (c.members().size() >= maxMembers(c)) { Msg.err(p, "Effectif maximal atteint (" + maxMembers(c) + ")."); return; }
        c.invites().add(t.getUUID());
        Msg.ok(p, t.getScoreboardName() + " a reçu une offre d'emploi.");
        Msg.send(t, "<yellow>" + Txt.esc(c.name()) + " vous propose un emploi. Ouvrez la tablette (M) > Entreprise pour répondre.");
    }

    public void accept(ServerPlayer p, String id) {
        Company c = get(id);
        if (c == null || !c.invites().contains(p.getUUID())) { Msg.err(p, "Offre introuvable."); return; }
        if (of(p.getUUID()) != null) { Msg.err(p, "Vous avez déjà un emploi."); return; }
        if (c.members().size() >= maxMembers(c)) { Msg.err(p, "Cette entreprise est complète."); return; }
        c.invites().remove(p.getUUID());
        c.members().put(p.getUUID(), Company.Rank.EMPLOYE);
        byPlayer.put(p.getUUID(), c.id());
        broadcast(c, "<green>" + Txt.esc(p.getScoreboardName()) + " rejoint l'entreprise !");
    }

    public void decline(ServerPlayer p, String id) {
        Company c = get(id);
        if (c != null) c.invites().remove(p.getUUID());
    }

    public void leave(ServerPlayer p) {
        Company c = need(p, Company.Rank.EMPLOYE);
        if (c == null) return;
        if (c.ceo().equals(p.getUUID())) { Msg.err(p, "Le PDG doit céder l'entreprise ou la dissoudre."); return; }
        remove(c, p.getUUID());
        Msg.ok(p, "Vous avez quitté " + Txt.esc(c.name()) + ".");
        broadcast(c, "<red>" + Txt.esc(p.getScoreboardName()) + " a quitté l'entreprise.");
    }

    private void remove(Company c, UUID u) {
        c.members().remove(u);
        c.salaries().remove(u);
        byPlayer.remove(u);
    }

    public void kick(ServerPlayer p, UUID target) {
        Company c = need(p, Company.Rank.DIRECTEUR);
        if (c == null) return;
        Company.Rank r = c.rank(target);
        if (r == null || r.power >= c.rank(p.getUUID()).power) { Msg.err(p, "Vous ne pouvez pas licencier cet employé."); return; }
        remove(c, target);
        broadcast(c, "<red>" + s.name(target) + " a été licencié.");
        ServerPlayer t = s.player(target);
        if (t != null) Msg.err(t, "Vous avez été licencié de " + Txt.esc(c.name()) + ".");
    }

    public void promote(ServerPlayer p, UUID target) {
        Company c = need(p, Company.Rank.PDG);
        if (c == null) return;
        if (c.rank(target) == Company.Rank.EMPLOYE) {
            c.members().put(target, Company.Rank.DIRECTEUR);
            broadcast(c, "<yellow>" + s.name(target) + " est promu Directeur.");
        }
    }

    public void demote(ServerPlayer p, UUID target) {
        Company c = need(p, Company.Rank.PDG);
        if (c == null) return;
        if (c.rank(target) == Company.Rank.DIRECTEUR) {
            c.members().put(target, Company.Rank.EMPLOYE);
            broadcast(c, "<yellow>" + s.name(target) + " redevient Employé.");
        }
    }

    public void transfer(ServerPlayer p, UUID target) {
        Company c = need(p, Company.Rank.PDG);
        if (c == null || c.rank(target) == null || target.equals(p.getUUID())) return;
        c.setCeo(target);
        broadcast(c, "<gold>" + s.name(target) + " est le nouveau PDG !");
    }

    public void setSalary(ServerPlayer p, UUID target, double v) {
        Company c = need(p, Company.Rank.DIRECTEUR);
        if (c == null || c.rank(target) == null) return;
        double max = s.config().getDouble("entreprises.salaire-max", 5000);
        double val = Math.max(0, Math.min(max, v));
        if (val <= 0) c.salaries().remove(target); else c.salaries().put(target, val);
        Msg.ok(p, "Salaire de " + s.name(target) + " : " + Txt.money(val) + " par heure.");
    }

    public void setContribution(ServerPlayer p, int pct) {
        Company c = need(p, Company.Rank.PDG);
        if (c == null) return;
        c.setContribution(pct);
        broadcast(c, "<yellow>Contribution des employés : " + c.contribution() + " % de leurs revenus de métier.");
    }

    public void depositFrom(ServerPlayer p, double v) {
        Company c = need(p, Company.Rank.EMPLOYE);
        if (c == null) return;
        if (!s.bank().withdraw(p.getUUID(), v, "Apport à " + c.name())) { Msg.err(p, "Fonds insuffisants."); return; }
        deposit(c, v, "Apport de " + p.getScoreboardName());
        Msg.ok(p, Txt.money(v) + " versés au capital de l'entreprise.");
    }

    public void withdrawTo(ServerPlayer p, double v) {
        Company c = need(p, Company.Rank.DIRECTEUR);
        if (c == null) return;
        if (!withdraw(c, v, "Retrait par " + p.getScoreboardName())) { Msg.err(p, "Capital insuffisant."); return; }
        s.bank().deposit(p.getUUID(), v, "Retrait du capital de " + c.name());
        Msg.ok(p, Txt.money(v) + " retirés du capital.");
    }

    public void disband(ServerPlayer p) {
        Company c = need(p, Company.Rank.PDG);
        if (c == null) return;
        s.bank().deposit(p.getUUID(), c.balance(), "Dissolution de " + c.name());
        for (UUID u : new ArrayList<>(c.members().keySet())) byPlayer.remove(u);
        companies.remove(c.id());
        s.market().onOwnerRemoved(c.owner(), p);
        Msg.broadcast(s.server(), "<red>L'entreprise " + Txt.esc(c.name()) + " a été dissoute.");
    }

    public void broadcast(Company c, String mini) {
        for (UUID u : c.members().keySet()) {
            ServerPlayer p = s.player(u);
            if (p != null) Msg.send(p, mini);
        }
    }

    public List<Company> invitesFor(UUID u) {
        List<Company> out = new ArrayList<>();
        for (Company c : companies.values()) if (c.invites().contains(u)) out.add(c);
        return out;
    }

    public boolean matches(Company c, Job j) { return c != null && c.type().job == j; }

    // ------------------------------------------------------------ persistance
    private void load() {
        Yaml root = store.get().getSection("entreprises");
        if (root == null) return;
        for (String id : root.getKeys()) {
            Yaml y = root.getSection(id);
            Company.Type type = Company.Type.byName(y.getString("type", "TRADING"));
            if (type == null) continue;
            UUID ceo;
            try { ceo = UUID.fromString(y.getString("pdg")); } catch (Exception e) { continue; }
            Company c = new Company(id, y.getString("nom", id), type, ceo, y.getLong("creee"));
            c.members().clear();
            Yaml m = y.getSection("employes");
            if (m != null) for (String k : m.getKeys()) {
                try { c.members().put(UUID.fromString(k), Company.Rank.valueOf(m.getString(k))); } catch (Exception ignored) {}
            }
            c.members().put(ceo, Company.Rank.PDG);
            Yaml sal = y.getSection("salaires");
            if (sal != null) for (String k : sal.getKeys()) {
                try { c.salaries().put(UUID.fromString(k), sal.getDouble(k)); } catch (Exception ignored) {}
            }
            for (String k : y.getStringList("offres")) {
                try { c.invites().add(UUID.fromString(k)); } catch (Exception ignored) {}
            }
            c.setBalance(y.getDouble("capital"));
            c.setXp(y.getDouble("xp"));
            c.setEarned(y.getDouble("gains"));
            c.setDescription(y.getString("description", ""));
            c.setContribution(y.getInt("contribution", 10));
            companies.put(id, c);
            for (UUID u : c.members().keySet()) byPlayer.put(u, id);
        }
    }

    public void save() {
        for (var e : pendingContrib.entrySet()) {
            Company c = get(e.getKey());
            if (c != null) deposit(c, e.getValue(), "Contributions des employés");
        }
        pendingContrib.clear();
        Yaml y = store.fresh();
        for (Company c : companies.values()) {
            String p = "entreprises." + c.id() + ".";
            y.set(p + "nom", c.name());
            y.set(p + "type", c.type().name());
            y.set(p + "pdg", c.ceo().toString());
            y.set(p + "creee", c.createdAt());
            c.members().forEach((u, r) -> y.set(p + "employes." + u, r.name()));
            c.salaries().forEach((u, v) -> y.set(p + "salaires." + u, v));
            y.set(p + "offres", c.invites().stream().map(UUID::toString).toList());
            y.set(p + "capital", c.balance());
            y.set(p + "xp", Math.round(c.xp() * 100) / 100.0);
            y.set(p + "gains", c.earned());
            y.set(p + "description", c.description());
            y.set(p + "contribution", c.contribution());
        }
        store.save();
    }
}

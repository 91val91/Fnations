package fr.fnations.research;

import fr.fnations.FNations;
import fr.fnations.nation.Nation;
import fr.fnations.nation.Role;
import fr.fnations.util.Txt;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Système de Recherche & Développement des nations. */
public final class ResearchManager {
    public static final Map<String, String> CATEGORIES = new LinkedHashMap<>();
    static {
        CATEGORIES.put("economie", "<gold>Économie & Territoire");
        CATEGORIES.put("industrie", "<yellow>Industrie & Énergie");
        CATEGORIES.put("militaire", "<red>Armement");
        CATEGORIES.put("vehicules", "<green>Véhicules");
        CATEGORIES.put("missiles", "<dark_red>Missiles");
        CATEGORIES.put("spatial", "<light_purple>Conquête spatiale");
    }

    private final FNations plugin;
    private final Map<String, Technology> techs = new LinkedHashMap<>();
    /** item id -> technologie qui le débloque */
    private final Map<String, String> unlockIndex = new HashMap<>();

    public ResearchManager(FNations plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        techs.clear();
        unlockIndex.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "research.yml"));
        ConfigurationSection root = y.getConfigurationSection("technologies");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            Material icon = Material.matchMaterial(s.getString("icone", "BOOK"));
            Technology t = new Technology(id, s.getString("nom", id), s.getString("categorie", "industrie"),
                    icon == null ? Material.BOOK : icon, s.getDouble("cout"), s.getLong("duree-secondes", 60),
                    s.getStringList("requiert"), s.getStringList("debloque"), s.getInt("bonus-territoire"), s.getStringList("description"));
            techs.put(id, t);
            for (String u : t.unlocks()) unlockIndex.put(u, id);
        }
        plugin.getLogger().info(techs.size() + " technologies chargées.");
    }

    public Collection<Technology> all() { return techs.values(); }

    public Technology get(String id) { return id == null ? null : techs.get(id); }

    public List<Technology> byCategory(String cat) {
        List<Technology> l = new ArrayList<>();
        for (Technology t : techs.values()) if (t.category().equals(cat)) l.add(t);
        return l;
    }

    public boolean has(Nation n, String techId) {
        if (techId == null || techId.isEmpty()) return true;
        return n != null && n.researched().contains(techId);
    }

    /** Technologie requise pour fabriquer cet item (null = libre). */
    public Technology requiredFor(String itemId) { return get(unlockIndex.get(itemId)); }

    public boolean playerCanUse(Player p, String techId) {
        if (techId == null || techId.isEmpty() || p.hasPermission("fnations.admin.bypass")) return true;
        return has(plugin.nations().of(p), techId);
    }

    public enum Status { DONE, IN_PROGRESS, AVAILABLE, LOCKED }

    public Status status(Nation n, Technology t) {
        if (n.researched().contains(t.id())) return Status.DONE;
        if (t.id().equals(n.currentResearch())) return Status.IN_PROGRESS;
        for (String r : t.requires()) if (!n.researched().contains(r)) return Status.LOCKED;
        return Status.AVAILABLE;
    }

    public void start(Player p, Technology t) {
        Nation n = plugin.nations().of(p);
        if (n == null) { Txt.err(p, "Il faut une nation pour faire de la recherche."); return; }
        if (!n.can(p.getUniqueId(), Role.OFFICER)) { Txt.err(p, "Seuls les Ministres peuvent lancer une recherche."); return; }
        if (n.currentResearch() != null) { Txt.err(p, "Une recherche est déjà en cours."); return; }
        Status s = status(n, t);
        if (s == Status.DONE) { Txt.err(p, "Déjà recherchée."); return; }
        if (s == Status.LOCKED) { Txt.err(p, "Prérequis manquants."); return; }
        if (!plugin.bank().nationWithdraw(n, t.cost(), "Recherche : " + Txt.plain(Txt.raw(t.name())))) {
            Txt.err(p, "Le trésor national n'a pas " + Txt.money(t.cost()) + "."); return;
        }
        double speed = plugin.getConfig().getDouble("recherche.multiplicateur-duree", 1.0);
        n.setResearch(t.id(), System.currentTimeMillis() + (long) (t.seconds() * 1000 * speed));
        plugin.nations().broadcast(n, "<light_purple>Recherche lancée : " + t.name() + " <gray>(" + Txt.duration((long) (t.seconds() * speed)) + ")");
    }

    public void cancel(Player p) {
        Nation n = plugin.nations().of(p);
        if (n == null || !n.can(p.getUniqueId(), Role.OFFICER) || n.currentResearch() == null) return;
        Technology t = get(n.currentResearch());
        n.setResearch(null, 0);
        if (t != null) {
            double refund = t.cost() * plugin.getConfig().getDouble("recherche.remboursement-annulation", 0.5);
            plugin.bank().nationDeposit(n, refund, "Annulation de recherche");
            plugin.nations().broadcast(n, "<red>Recherche annulée : " + t.name() + " <gray>(remboursé " + Txt.money(refund) + ")");
        }
    }

    /** Appelé toutes les secondes. */
    public void tick() {
        long now = System.currentTimeMillis();
        for (Nation n : plugin.nations().all()) {
            if (n.currentResearch() == null || now < n.researchEnd()) continue;
            Technology t = get(n.currentResearch());
            n.setResearch(null, 0);
            if (t == null) continue;
            complete(n, t);
        }
    }

    public void complete(Nation n, Technology t) {
        n.researched().add(t.id());
        n.setBonusClaims(n.bonusClaims() + t.bonusClaims());
        plugin.nations().broadcast(n, "<green><b>Recherche terminée :</b> " + t.name());
        for (Player p : plugin.nations().online(n)) {
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
            plugin.advancements().grant(p, "first_research");
        }
        if (n.researched().size() == techs.size()) {
            Bukkit.broadcast(Txt.raw(Txt.PREFIX + n.colored() + " <light_purple>a terminé tout l'arbre technologique !"));
        }
    }
}

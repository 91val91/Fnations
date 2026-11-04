package fr.fnations.research;

import fr.fnations.nation.Nation;
import fr.fnations.nation.Role;
import fr.fnations.server.FnServer;
import fr.fnations.server.Msg;
import fr.fnations.util.Inv;
import fr.fnations.util.Txt;
import fr.fnations.util.Yaml;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Recherche & Développement des nations (research.yml). */
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

    private final FnServer plugin;
    private final Map<String, Technology> techs = new LinkedHashMap<>();
    /** item id -> technologie qui le débloque */
    private final Map<String, String> unlockIndex = new HashMap<>();

    public ResearchManager(FnServer plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        techs.clear();
        unlockIndex.clear();
        Yaml root = plugin.yaml("research.yml").getSection("technologies");
        if (root == null) return;
        for (String id : root.getKeys()) {
            Yaml s = root.getSection(id);
            Item icon = Inv.item(s.getString("icone", "BOOK"));
            Technology t = new Technology(id, s.getString("nom", id), s.getString("categorie", "industrie"),
                    icon == Items.AIR ? Items.BOOK : icon, s.getDouble("cout"), s.getLong("duree-secondes", 60),
                    s.getStringList("requiert"), s.getStringList("debloque"), s.getInt("bonus-territoire"), s.getStringList("description"));
            techs.put(id, t);
            for (String u : t.unlocks()) unlockIndex.put(u, id);
        }
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

    public boolean playerCanUse(ServerPlayer p, String techId) {
        if (techId == null || techId.isEmpty() || fr.fnations.nation.Territory.bypass(p)) return true;
        return has(plugin.nations().of(p), techId);
    }

    public enum Status { DONE, IN_PROGRESS, AVAILABLE, LOCKED }

    public Status status(Nation n, Technology t) {
        if (n.researched().contains(t.id())) return Status.DONE;
        if (t.id().equals(n.currentResearch())) return Status.IN_PROGRESS;
        for (String r : t.requires()) if (!n.researched().contains(r)) return Status.LOCKED;
        return Status.AVAILABLE;
    }

    public double speed() { return plugin.config().getDouble("recherche.multiplicateur-duree", 1.0); }

    public void start(ServerPlayer p, Technology t) {
        Nation n = plugin.nations().of(p);
        if (n == null) { Msg.err(p, "Il faut une nation pour faire de la recherche."); return; }
        if (!n.can(p.getUUID(), Role.OFFICER)) { Msg.err(p, "Seuls les Ministres peuvent lancer une recherche."); return; }
        if (n.currentResearch() != null) { Msg.err(p, "Une recherche est déjà en cours."); return; }
        Status s = status(n, t);
        if (s == Status.DONE) { Msg.err(p, "Déjà recherchée."); return; }
        if (s == Status.LOCKED) { Msg.err(p, "Prérequis manquants."); return; }
        if (!plugin.bank().nationWithdraw(n, t.cost(), "Recherche : " + Txt.plain(t.name()))) {
            Msg.err(p, "Le trésor national n'a pas " + Txt.money(t.cost()) + "."); return;
        }
        n.setResearch(t.id(), System.currentTimeMillis() + (long) (t.seconds() * 1000 * speed()));
        plugin.nations().broadcast(n, "<light_purple>Recherche lancée : " + t.name() + " <gray>(" + Txt.duration((long) (t.seconds() * speed())) + ")");
    }

    public void cancel(ServerPlayer p) {
        Nation n = plugin.nations().of(p);
        if (n == null || !n.can(p.getUUID(), Role.OFFICER) || n.currentResearch() == null) return;
        Technology t = get(n.currentResearch());
        n.setResearch(null, 0);
        if (t != null) {
            double refund = t.cost() * plugin.config().getDouble("recherche.remboursement-annulation", 0.5);
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
            if (t != null) complete(n, t);
        }
    }

    public void complete(Nation n, Technology t) {
        n.researched().add(t.id());
        n.setBonusClaims(n.bonusClaims() + t.bonusClaims());
        plugin.nations().broadcast(n, "<green><b>Recherche terminée :</b> " + t.name());
        for (ServerPlayer p : plugin.nations().online(n)) {
            Msg.sound(p, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
            plugin.advancements().grant(p, "first_research");
        }
        if (n.researched().size() == techs.size()) {
            Msg.broadcast(plugin.server(), n.colored() + " <light_purple>a terminé tout l'arbre technologique !");
        }
    }
}

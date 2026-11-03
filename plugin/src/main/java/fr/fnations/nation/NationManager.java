package fr.fnations.nation;

import fr.fnations.FNations;
import fr.fnations.util.Locs;
import fr.fnations.util.Txt;
import fr.fnations.util.YamlStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Stockage et règles des nations. */
public final class NationManager {
    private final FNations plugin;
    private final YamlStore store;
    private final Map<String, Nation> nations = new HashMap<>();
    private final Map<UUID, String> byPlayer = new HashMap<>();
    private final Map<String, String> byChunk = new HashMap<>();

    public NationManager(FNations plugin) {
        this.plugin = plugin;
        this.store = new YamlStore(plugin, "data/nations.yml");
        load();
    }

    // ------------------------------------------------------------ requêtes
    public Collection<Nation> all() { return nations.values(); }

    public Nation get(String id) { return id == null ? null : nations.get(id.toLowerCase()); }

    public Nation of(UUID player) { return get(byPlayer.get(player)); }

    public Nation of(Player p) { return of(p.getUniqueId()); }

    public Nation at(Location l) { return get(byChunk.get(Locs.chunkKey(l))); }

    public Nation atChunk(String key) { return get(byChunk.get(key)); }

    public boolean allied(Nation a, Nation b) {
        return a != null && b != null && (a == b || a.allies().contains(b.id()));
    }

    public int maxClaims(Nation n) {
        var c = plugin.getConfig();
        return c.getInt("nations.claims-base", 9) + c.getInt("nations.claims-par-membre", 4) * n.members().size() + n.bonusClaims();
    }

    // ------------------------------------------------------------ actions
    public String validateName(String name) {
        int min = plugin.getConfig().getInt("nations.nom-min", 3), max = plugin.getConfig().getInt("nations.nom-max", 16);
        if (!name.matches("[A-Za-z0-9_\\-]+")) return "Le nom ne peut contenir que lettres, chiffres, _ et -.";
        if (name.length() < min || name.length() > max) return "Le nom doit faire entre " + min + " et " + max + " caractères.";
        if (get(name) != null) return "Une nation porte déjà ce nom.";
        return null;
    }

    public Nation create(Player leader, String name) {
        Nation n = new Nation(name.toLowerCase(), name, leader.getUniqueId(), System.currentTimeMillis());
        n.setHome(leader.getLocation());
        nations.put(n.id(), n);
        byPlayer.put(leader.getUniqueId(), n.id());
        plugin.advancements().grant(leader, "nation_created");
        return n;
    }

    public void disband(Nation n) {
        for (UUID u : n.members().keySet()) byPlayer.remove(u);
        for (String c : n.claims()) byChunk.remove(c);
        for (Nation o : nations.values()) {
            o.allies().remove(n.id());
            o.allyRequests().remove(n.id());
        }
        plugin.wars().onNationDisband(n);
        nations.remove(n.id());
        Bukkit.broadcast(Txt.raw(Txt.PREFIX + "<red>La nation " + n.colored() + " <red>a été dissoute."));
    }

    public void addMember(Nation n, UUID u) {
        n.members().put(u, Role.MEMBER);
        n.invites().remove(u);
        byPlayer.put(u, n.id());
    }

    public void removeMember(Nation n, UUID u) {
        n.members().remove(u);
        byPlayer.remove(u);
    }

    public boolean claim(Nation n, Location l) {
        String key = Locs.chunkKey(l);
        if (byChunk.containsKey(key)) return false;
        n.claims().add(key);
        byChunk.put(key, n.id());
        return true;
    }

    public void unclaim(Nation n, String key) {
        n.claims().remove(key);
        byChunk.remove(key);
    }

    /** Transfère un chunk d'une nation à une autre (capture en guerre). */
    public void transferClaim(String key, Nation to) {
        Nation from = atChunk(key);
        if (from != null) from.claims().remove(key);
        to.claims().add(key);
        byChunk.put(key, to.id());
    }

    /** Un chunk est adjacent au territoire existant (ou c'est le premier claim). */
    public boolean adjacent(Nation n, Location l) {
        if (n.claims().isEmpty()) return true;
        String w = l.getWorld().getName();
        int x = l.getBlockX() >> 4, z = l.getBlockZ() >> 4;
        return n.claims().contains(Locs.chunkKey(w, x + 1, z)) || n.claims().contains(Locs.chunkKey(w, x - 1, z))
                || n.claims().contains(Locs.chunkKey(w, x, z + 1)) || n.claims().contains(Locs.chunkKey(w, x, z - 1));
    }

    public void broadcast(Nation n, String mini) {
        for (UUID u : n.members().keySet()) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) Txt.send(p, mini);
        }
    }

    public List<Player> online(Nation n) {
        List<Player> out = new ArrayList<>();
        for (UUID u : n.members().keySet()) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) out.add(p);
        }
        return out;
    }

    /** Impôt horaire prélevé sur le compte des citoyens. */
    public void collectTaxes() {
        for (Nation n : nations.values()) {
            if (n.taxPerHour() <= 0) continue;
            for (UUID u : n.members().keySet()) {
                if (u.equals(n.leader())) continue;
                if (plugin.bank().withdraw(u, n.taxPerHour(), "Impôt de " + n.name())) {
                    plugin.bank().nationDeposit(n, n.taxPerHour(), "Impôt de " + Bukkit.getOfflinePlayer(u).getName());
                }
            }
        }
    }

    // ------------------------------------------------------------ persistance
    private void load() {
        YamlConfiguration y = store.get();
        ConfigurationSection root = y.getConfigurationSection("nations");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            UUID leader = UUID.fromString(s.getString("leader"));
            Nation n = new Nation(id, s.getString("name"), leader, s.getLong("created"));
            n.members().clear();
            ConfigurationSection m = s.getConfigurationSection("members");
            if (m != null) for (String u : m.getKeys(false)) n.members().put(UUID.fromString(u), Role.valueOf(m.getString(u)));
            n.members().put(leader, Role.LEADER);
            n.setTag(s.getString("tag", n.tag()));
            n.setColor(s.getString("color", "aqua"));
            n.setBalance(s.getDouble("balance"));
            n.setTaxPerHour(s.getDouble("tax"));
            n.setOpen(s.getBoolean("open"));
            n.claims().addAll(s.getStringList("claims"));
            n.allies().addAll(s.getStringList("allies"));
            n.allyRequests().addAll(s.getStringList("ally-requests"));
            n.setHome(Locs.loc(s.getString("home")));
            n.researched().addAll(s.getStringList("research.done"));
            n.setResearch(s.getString("research.current"), s.getLong("research.end"));
            n.setBonusClaims(s.getInt("bonus-claims"));
            n.setRecord(s.getInt("wins"), s.getInt("losses"));
            nations.put(id, n);
            n.members().keySet().forEach(u -> byPlayer.put(u, id));
            n.claims().forEach(c -> byChunk.put(c, id));
        }
    }

    public void save() {
        YamlConfiguration y = store.fresh();
        for (Nation n : nations.values()) {
            String p = "nations." + n.id() + ".";
            y.set(p + "name", n.name());
            y.set(p + "tag", n.tag());
            y.set(p + "color", n.color());
            y.set(p + "leader", n.leader().toString());
            y.set(p + "created", n.createdAt());
            n.members().forEach((u, r) -> y.set(p + "members." + u, r.name()));
            y.set(p + "balance", n.balance());
            y.set(p + "tax", n.taxPerHour());
            y.set(p + "open", n.open());
            y.set(p + "claims", new ArrayList<>(n.claims()));
            y.set(p + "allies", new ArrayList<>(n.allies()));
            y.set(p + "ally-requests", new ArrayList<>(n.allyRequests()));
            if (n.home() != null && n.home().getWorld() != null) y.set(p + "home", Locs.loc(n.home()));
            y.set(p + "research.done", new ArrayList<>(n.researched()));
            y.set(p + "research.current", n.currentResearch());
            y.set(p + "research.end", n.researchEnd());
            y.set(p + "bonus-claims", n.bonusClaims());
            y.set(p + "wins", n.wins());
            y.set(p + "losses", n.losses());
        }
        store.save();
    }
}

package fr.fnations.room;

import fr.fnations.FNations;
import fr.fnations.util.Locs;
import fr.fnations.util.Txt;
import fr.fnations.util.YamlStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
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
import java.util.UUID;
import java.util.logging.Level;

/** Système de chambres : location / achat, colocataires, réinitialisation WorldEdit. */
public final class RoomManager {
    private final FNations plugin;
    private final YamlStore store;
    private final Map<String, Room> rooms = new LinkedHashMap<>();
    /** Guichets de réception : bloc -> groupe ("" = toutes les chambres) */
    private final Map<String, String> desks = new HashMap<>();
    private final RoomSnapshots snapshots;

    public RoomManager(FNations plugin) {
        this.plugin = plugin;
        this.store = new YamlStore(plugin, "data/rooms.yml");
        this.snapshots = new RoomSnapshots(new File(plugin.getDataFolder(), "rooms"));
        load();
    }

    public RoomSnapshots snapshots() { return snapshots; }

    public Collection<Room> all() { return rooms.values(); }

    public Room get(String id) { return id == null ? null : rooms.get(id.toLowerCase()); }

    public Room at(Location l) {
        for (Room r : rooms.values()) if (r.contains(l)) return r;
        return null;
    }

    public boolean intersectsChunk(Location l) { return intersectsChunk(l.getWorld().getName(), l.getBlockX() >> 4, l.getBlockZ() >> 4); }

    public boolean intersectsChunk(String w, int cx, int cz) {
        for (Room r : rooms.values()) if (r.intersectsChunk(w, cx, cz)) return true;
        return false;
    }

    public List<Room> ownedBy(UUID u) {
        List<Room> l = new ArrayList<>();
        for (Room r : rooms.values()) if (u.equals(r.owner())) l.add(r);
        return l;
    }

    public List<String> groups() {
        List<String> g = new ArrayList<>();
        for (Room r : rooms.values()) if (!g.contains(r.group())) g.add(r.group());
        return g;
    }

    public String deskGroup(Block b) { return desks.get(Locs.blockKey(b)); }

    public void setDesk(Block b, String group) { desks.put(Locs.blockKey(b), group == null ? "" : group); }

    public boolean removeDesk(Block b) { return desks.remove(Locs.blockKey(b)) != null; }

    // ------------------------------------------------------------ administration
    public void create(Player p, String id, double buy, double rent, String group) {
        id = id.toLowerCase();
        if (rooms.containsKey(id)) { Txt.err(p, "Une chambre porte déjà cet identifiant."); return; }
        int[] s = snapshots.selection(p);
        if (s == null) { Txt.err(p, "Faites d'abord une sélection WorldEdit (//wand, //pos1, //pos2)."); return; }
        Room r = new Room(id, p.getWorld().getName(), s[0], s[1], s[2], s[3], s[4], s[5]);
        int max = plugin.getConfig().getInt("chambres.volume-max", 20000);
        if (r.volume() > max) { Txt.err(p, "Sélection trop grande (" + r.volume() + " > " + max + " blocs)."); return; }
        for (Room o : rooms.values()) if (overlap(o, r)) { Txt.err(p, "La sélection chevauche la chambre " + o.id() + "."); return; }
        for (int cx = r.minX() >> 4; cx <= r.maxX() >> 4; cx++)
            for (int cz = r.minZ() >> 4; cz <= r.maxZ() >> 4; cz++)
                if (plugin.nations().atChunk(Locs.chunkKey(r.world(), cx, cz)) != null) {
                    Txt.err(p, "La sélection est sur le territoire d'une nation."); return;
                }
        r.setGroup(group == null ? "Hôtel" : group);
        r.setPrices(buy, rent);
        r.setSpawn(r.contains(p.getLocation()) ? p.getLocation() : new Location(p.getWorld(), (r.minX() + r.maxX()) / 2.0 + 0.5, r.minY() + 1, (r.minZ() + r.maxZ()) / 2.0 + 0.5));
        try {
            snapshots.save(r);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Snapshot WorldEdit impossible pour " + id, e);
            Txt.err(p, "Erreur WorldEdit lors de la sauvegarde : " + e.getMessage());
            return;
        }
        rooms.put(id, r);
        Txt.ok(p, "Chambre <white>" + id + "</white> créée (" + r.size() + ", groupe " + r.group() + "). État sauvegardé dans rooms/" + id + ".schem");
    }

    private static boolean overlap(Room a, Room b) {
        return a.world().equals(b.world()) && a.minX() <= b.maxX() && a.maxX() >= b.minX()
                && a.minY() <= b.maxY() && a.maxY() >= b.minY() && a.minZ() <= b.maxZ() && a.maxZ() >= b.minZ();
    }

    public void delete(Room r) {
        rooms.remove(r.id());
        snapshots.delete(r);
    }

    public boolean reset(Room r) {
        try {
            return snapshots.restore(r);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Restauration WorldEdit impossible pour " + r.id(), e);
            return false;
        }
    }

    // ------------------------------------------------------------ joueurs
    private boolean limit(Player p) {
        int max = plugin.getConfig().getInt("chambres.max-par-joueur", 2);
        if (ownedBy(p.getUniqueId()).size() >= max) { Txt.err(p, "Vous possédez déjà " + max + " chambre(s)."); return false; }
        return true;
    }

    public void buy(Player p, Room r) {
        if (r.mode() != Room.Mode.FREE) { Txt.err(p, "Cette chambre n'est pas disponible."); return; }
        if (r.buyPrice() <= 0) { Txt.err(p, "Cette chambre n'est pas à vendre (location uniquement)."); return; }
        if (!limit(p)) return;
        if (!plugin.bank().withdraw(p.getUniqueId(), r.buyPrice(), "Achat de la chambre " + r.id())) { Txt.err(p, "Fonds insuffisants (" + Txt.money(r.buyPrice()) + ")."); return; }
        r.setOwner(p.getUniqueId());
        r.setMode(Room.Mode.OWNED);
        Txt.ok(p, "Vous êtes propriétaire de la chambre " + r.id() + " !");
    }

    public void rent(Player p, Room r, int days) {
        if (r.rentPrice() <= 0) { Txt.err(p, "Cette chambre n'est pas à louer."); return; }
        boolean extend = r.mode() == Room.Mode.RENTED && p.getUniqueId().equals(r.owner());
        if (!extend && r.mode() != Room.Mode.FREE) { Txt.err(p, "Cette chambre n'est pas disponible."); return; }
        if (!extend && !limit(p)) return;
        long maxDays = plugin.getConfig().getLong("chambres.location-max-jours", 30);
        long base = extend ? r.expiry() : System.currentTimeMillis();
        if ((base - System.currentTimeMillis()) / 86_400_000L + days > maxDays) { Txt.err(p, "Location limitée à " + maxDays + " jours à l'avance."); return; }
        double cost = r.rentPrice() * days;
        if (!plugin.bank().withdraw(p.getUniqueId(), cost, "Location chambre " + r.id() + " (" + days + "j)")) { Txt.err(p, "Fonds insuffisants (" + Txt.money(cost) + ")."); return; }
        r.setOwner(p.getUniqueId());
        r.setMode(Room.Mode.RENTED);
        r.setExpiry(base + days * 86_400_000L);
        Txt.ok(p, (extend ? "Location prolongée" : "Chambre louée") + " jusqu'au " + new java.text.SimpleDateFormat("dd/MM HH:mm").format(new java.util.Date(r.expiry())) + ".");
    }

    public void release(Player p, Room r) {
        if (!p.getUniqueId().equals(r.owner()) && !p.hasPermission("fnations.admin")) return;
        if (r.mode() == Room.Mode.OWNED && p.getUniqueId().equals(r.owner())) {
            double refund = r.buyPrice() * plugin.getConfig().getDouble("chambres.revente-pourcent", 50) / 100.0;
            plugin.bank().deposit(p.getUniqueId(), refund, "Revente chambre " + r.id());
            Txt.ok(p, "Chambre revendue pour " + Txt.money(refund) + ".");
        } else {
            Txt.ok(p, "Chambre libérée.");
        }
        free(r);
    }

    private void free(Room r) {
        r.free();
        if (plugin.getConfig().getBoolean("chambres.reinitialiser-a-la-liberation", true)) {
            for (Player pl : Bukkit.getOnlinePlayers()) if (r.contains(pl.getLocation())) pl.teleport(pl.getWorld().getSpawnLocation());
            reset(r);
        }
    }

    /** Vérifie l'expiration des locations (toutes les minutes). */
    public void tick() {
        long now = System.currentTimeMillis();
        long warn = 3_600_000L;
        for (Room r : rooms.values()) {
            if (r.mode() != Room.Mode.RENTED) continue;
            if (now >= r.expiry()) {
                Player p = r.owner() == null ? null : Bukkit.getPlayer(r.owner());
                if (p != null) Txt.err(p, "Votre location de la chambre " + r.id() + " a expiré.");
                free(r);
            } else if (r.expiry() - now <= warn && r.expiry() - now > warn - 60_000L) {
                Player p = Bukkit.getPlayer(r.owner());
                if (p != null) Txt.send(p, "<yellow>Votre location de la chambre " + r.id() + " expire dans 1h. Prolongez-la à la réception !");
            }
        }
    }

    // ------------------------------------------------------------ persistance
    private void load() {
        YamlConfiguration y = store.get();
        ConfigurationSection root = y.getConfigurationSection("rooms");
        if (root != null) for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            List<Integer> b = s.getIntegerList("bounds");
            Room r = new Room(id, s.getString("world"), b.get(0), b.get(1), b.get(2), b.get(3), b.get(4), b.get(5));
            r.setGroup(s.getString("group", "Hôtel"));
            r.setPrices(s.getDouble("buy"), s.getDouble("rent"));
            if (s.getString("owner") != null) r.setOwner(UUID.fromString(s.getString("owner")));
            r.setMode(Room.Mode.valueOf(s.getString("mode", "FREE")));
            r.setExpiry(s.getLong("expiry"));
            for (String m : s.getStringList("members")) r.members().add(UUID.fromString(m));
            r.setSpawn(Locs.loc(s.getString("spawn")));
            rooms.put(id, r);
        }
        for (String line : y.getStringList("desks")) {
            int i = line.indexOf('=');
            if (i > 0) desks.put(line.substring(0, i), line.substring(i + 1));
        }
    }

    public void save() {
        YamlConfiguration y = store.fresh();
        for (Room r : rooms.values()) {
            String p = "rooms." + r.id() + ".";
            y.set(p + "world", r.world());
            y.set(p + "bounds", List.of(r.minX(), r.minY(), r.minZ(), r.maxX(), r.maxY(), r.maxZ()));
            y.set(p + "group", r.group());
            y.set(p + "buy", r.buyPrice());
            y.set(p + "rent", r.rentPrice());
            y.set(p + "owner", r.owner() == null ? null : r.owner().toString());
            y.set(p + "mode", r.mode().name());
            y.set(p + "expiry", r.expiry());
            y.set(p + "members", r.members().stream().map(UUID::toString).toList());
            if (r.spawn() != null && r.spawn().getWorld() != null) y.set(p + "spawn", Locs.loc(r.spawn()));
        }
        y.set("desks", desks.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toList());
        store.save();
    }
}

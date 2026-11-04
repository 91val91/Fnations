package fr.fnations.room;

import fr.fnations.FNations;
import fr.fnations.server.FnServer;
import fr.fnations.server.Msg;
import fr.fnations.util.Locs;
import fr.fnations.util.Store;
import fr.fnations.util.Txt;
import fr.fnations.util.Yaml;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Chambres : location / achat, colocataires, réinitialisation (WorldEdit si présent). */
public final class RoomManager {
    private final FnServer plugin;
    private final Store store;
    private final Map<String, Room> rooms = new LinkedHashMap<>();
    /** Guichets : "dim;x;y;z" -> groupe ("" = toutes les chambres) */
    private final Map<String, String> desks = new HashMap<>();
    private final VanillaSnapshots snapshots;

    public RoomManager(FnServer plugin) {
        this.plugin = plugin;
        this.store = new Store(plugin.dataDir(), "rooms.yml");
        var folder = plugin.dataDir().resolve("rooms");
        if (FabricLoader.getInstance().isModLoaded("worldedit")) {
            snapshots = new WorldEditSnapshots(folder);
            FNations.LOG.info("Chambres : WorldEdit détecté, sauvegardes en schematics.");
        } else {
            snapshots = new VanillaSnapshots(folder);
            FNations.LOG.info("Chambres : WorldEdit absent, sauvegardes en structures vanilla (/chambre pos1|pos2).");
        }
        load();
    }

    public VanillaSnapshots snapshots() { return snapshots; }

    public Collection<Room> all() { return rooms.values(); }

    public Room get(String id) { return id == null ? null : rooms.get(id.toLowerCase()); }

    public Room at(Level l, BlockPos p) {
        for (Room r : rooms.values()) if (r.contains(l, p)) return r;
        return null;
    }

    public boolean intersectsChunk(String dim, int cx, int cz) {
        for (Room r : rooms.values()) if (r.intersectsChunk(dim, cx, cz)) return true;
        return false;
    }

    public List<Room> ownedBy(UUID u) {
        List<Room> l = new ArrayList<>();
        for (Room r : rooms.values()) if (u.equals(r.owner())) l.add(r);
        return l;
    }

    private static String deskKey(Level l, BlockPos p) { return Locs.dim(l) + ";" + p.getX() + ";" + p.getY() + ";" + p.getZ(); }

    public String deskGroup(Level l, BlockPos p) { return desks.get(deskKey(l, p)); }

    public void setDesk(Level l, BlockPos p, String group) { desks.put(deskKey(l, p), group == null ? "" : group); }

    public boolean removeDesk(Level l, BlockPos p) { return desks.remove(deskKey(l, p)) != null; }

    // ------------------------------------------------------------ administration
    public void create(ServerPlayer p, String id, double buy, double rent, String group) {
        id = id.toLowerCase();
        if (!id.matches("[a-z0-9_\\-]+")) { Msg.err(p, "Identifiant invalide (lettres, chiffres, _ et -)."); return; }
        if (rooms.containsKey(id)) { Msg.err(p, "Une chambre porte déjà cet identifiant."); return; }
        int[] s = snapshots.selection(p);
        if (s == null) { Msg.err(p, "Faites d'abord une sélection (//pos1 //pos2 avec WorldEdit, ou /chambre pos1 et /chambre pos2)."); return; }
        String dim = Locs.dim(p.level());
        Room r = new Room(id, dim, s[0], s[1], s[2], s[3], s[4], s[5]);
        int max = plugin.config().getInt("chambres.volume-max", 20000);
        if (r.volume() > max) { Msg.err(p, "Sélection trop grande (" + r.volume() + " > " + max + " blocs)."); return; }
        for (Room o : rooms.values()) if (overlap(o, r)) { Msg.err(p, "La sélection chevauche la chambre " + o.id() + "."); return; }
        for (int cx = r.minX() >> 4; cx <= r.maxX() >> 4; cx++)
            for (int cz = r.minZ() >> 4; cz <= r.maxZ() >> 4; cz++)
                if (plugin.nations().atChunk(Locs.chunkKey(dim, cx, cz)) != null) { Msg.err(p, "La sélection est sur le territoire d'une nation."); return; }
        r.setGroup(group == null || group.isBlank() ? "Hôtel" : group);
        r.setPrices(buy, rent);
        r.setSpawn(r.contains(p.level(), p.blockPosition()) ? Locs.Spot.of(p.level(), p.position(), p.getYRot(), p.getXRot())
                : new Locs.Spot(dim, (r.minX() + r.maxX()) / 2.0 + 0.5, r.minY() + 1, (r.minZ() + r.maxZ()) / 2.0 + 0.5, 0, 0));
        try {
            snapshots.save(r, p.serverLevel());
        } catch (Exception e) {
            FNations.LOG.warn("Sauvegarde impossible pour la chambre {}", id, e);
            Msg.err(p, "Erreur lors de la sauvegarde : " + Txt.esc(String.valueOf(e.getMessage())));
            return;
        }
        rooms.put(id, r);
        Msg.ok(p, "Chambre <white>" + id + "</white> créée (" + r.size() + ", groupe " + Txt.esc(r.group()) + "). État sauvegardé (" + snapshots.name() + ").");
    }

    private static boolean overlap(Room a, Room b) {
        return a.dim().equals(b.dim()) && a.minX() <= b.maxX() && a.maxX() >= b.minX()
                && a.minY() <= b.maxY() && a.maxY() >= b.minY() && a.minZ() <= b.maxZ() && a.maxZ() >= b.minZ();
    }

    public void delete(Room r) {
        rooms.remove(r.id());
        snapshots.delete(r);
    }

    public boolean reset(Room r) {
        ServerLevel level = Locs.level(plugin.server(), r.dim());
        if (level == null) return false;
        try {
            return snapshots.restore(r, level);
        } catch (Exception e) {
            FNations.LOG.warn("Restauration impossible pour la chambre {}", r.id(), e);
            return false;
        }
    }

    public void snapshot(ServerPlayer p, Room r) {
        ServerLevel level = Locs.level(plugin.server(), r.dim());
        try {
            snapshots.save(r, level);
            Msg.ok(p, "Nouvel état de référence sauvegardé.");
        } catch (Exception e) {
            Msg.err(p, Txt.esc(String.valueOf(e.getMessage())));
        }
    }

    // ------------------------------------------------------------ joueurs
    private boolean limit(ServerPlayer p) {
        int max = plugin.config().getInt("chambres.max-par-joueur", 2);
        if (ownedBy(p.getUUID()).size() >= max) { Msg.err(p, "Vous possédez déjà " + max + " chambre(s)."); return false; }
        return true;
    }

    public void buy(ServerPlayer p, Room r) {
        if (r.mode() != Room.Mode.FREE) { Msg.err(p, "Cette chambre n'est pas disponible."); return; }
        if (r.buyPrice() <= 0) { Msg.err(p, "Cette chambre n'est pas à vendre (location uniquement)."); return; }
        if (!limit(p)) return;
        if (!plugin.bank().withdraw(p.getUUID(), r.buyPrice(), "Achat de la chambre " + r.id())) { Msg.err(p, "Fonds insuffisants (" + Txt.money(r.buyPrice()) + ")."); return; }
        r.setOwner(p.getUUID());
        r.setMode(Room.Mode.OWNED);
        Msg.ok(p, "Vous êtes propriétaire de la chambre " + r.id() + " !");
    }

    public void rent(ServerPlayer p, Room r, int days) {
        if (r.rentPrice() <= 0) { Msg.err(p, "Cette chambre n'est pas à louer."); return; }
        boolean extend = r.mode() == Room.Mode.RENTED && p.getUUID().equals(r.owner());
        if (!extend && r.mode() != Room.Mode.FREE) { Msg.err(p, "Cette chambre n'est pas disponible."); return; }
        if (!extend && !limit(p)) return;
        long maxDays = plugin.config().getLong("chambres.location-max-jours", 30);
        long base = extend ? r.expiry() : System.currentTimeMillis();
        if ((base - System.currentTimeMillis()) / 86_400_000L + days > maxDays) { Msg.err(p, "Location limitée à " + maxDays + " jours à l'avance."); return; }
        double cost = r.rentPrice() * days;
        if (!plugin.bank().withdraw(p.getUUID(), cost, "Location chambre " + r.id() + " (" + days + "j)")) { Msg.err(p, "Fonds insuffisants (" + Txt.money(cost) + ")."); return; }
        r.setOwner(p.getUUID());
        r.setMode(Room.Mode.RENTED);
        r.setExpiry(base + days * 86_400_000L);
        Msg.ok(p, (extend ? "Location prolongée" : "Chambre louée") + " jusqu'au " + new SimpleDateFormat("dd/MM HH:mm").format(new Date(r.expiry())) + ".");
    }

    public void release(ServerPlayer p, Room r) {
        if (!p.getUUID().equals(r.owner()) && !p.hasPermissions(2)) return;
        if (r.mode() == Room.Mode.OWNED && p.getUUID().equals(r.owner())) {
            double refund = r.buyPrice() * plugin.config().getDouble("chambres.revente-pourcent", 50) / 100.0;
            plugin.bank().deposit(p.getUUID(), refund, "Revente chambre " + r.id());
            Msg.ok(p, "Chambre revendue pour " + Txt.money(refund) + ".");
        } else {
            Msg.ok(p, "Chambre libérée.");
        }
        free(r);
    }

    private void free(Room r) {
        r.free();
        if (plugin.config().getBoolean("chambres.reinitialiser-a-la-liberation", true)) {
            ServerLevel level = Locs.level(plugin.server(), r.dim());
            if (level != null) for (ServerPlayer pl : level.players()) {
                if (r.contains(level, pl.blockPosition())) {
                    var sp = level.getSharedSpawnPos();
                    pl.teleportTo(sp.getX() + 0.5, sp.getY(), sp.getZ() + 0.5);
                }
            }
            reset(r);
        }
    }

    public void teleport(ServerPlayer p, Room r) {
        Locs.Spot s = r.spawn();
        if (s == null) return;
        ServerLevel level = Locs.level(plugin.server(), s.dim());
        if (level == null) return;
        p.teleportTo(level, s.x(), s.y(), s.z(), java.util.Set.of(), s.yaw(), s.pitch(), true);
    }

    /** Expiration des locations (toutes les minutes). */
    public void tick() {
        long now = System.currentTimeMillis();
        long warn = 3_600_000L;
        for (Room r : rooms.values()) {
            if (r.mode() != Room.Mode.RENTED) continue;
            if (now >= r.expiry()) {
                ServerPlayer p = plugin.player(r.owner());
                if (p != null) Msg.err(p, "Votre location de la chambre " + r.id() + " a expiré.");
                free(r);
            } else if (r.expiry() - now <= warn && r.expiry() - now > warn - 60_000L) {
                ServerPlayer p = plugin.player(r.owner());
                if (p != null) Msg.send(p, "<yellow>Votre location de la chambre " + r.id() + " expire dans 1h. Prolongez-la à la réception !");
            }
        }
    }

    // ------------------------------------------------------------ persistance
    private void load() {
        Yaml y = store.get();
        Yaml root = y.getSection("rooms");
        if (root != null) for (String id : root.getKeys()) {
            Yaml s = root.getSection(id);
            List<String> b = s.getStringList("bounds");
            if (b.size() < 6) continue;
            Room r = new Room(id, s.getString("dim", "minecraft:overworld"), Integer.parseInt(b.get(0)), Integer.parseInt(b.get(1)), Integer.parseInt(b.get(2)),
                    Integer.parseInt(b.get(3)), Integer.parseInt(b.get(4)), Integer.parseInt(b.get(5)));
            r.setGroup(s.getString("group", "Hôtel"));
            r.setPrices(s.getDouble("buy"), s.getDouble("rent"));
            if (s.getString("owner") != null) r.setOwner(UUID.fromString(s.getString("owner")));
            r.setMode(Room.Mode.valueOf(s.getString("mode", "FREE")));
            r.setExpiry(s.getLong("expiry"));
            for (String m : s.getStringList("members")) r.members().add(UUID.fromString(m));
            r.setSpawn(Locs.Spot.parse(s.getString("spawn")));
            rooms.put(id, r);
        }
        for (String line : y.getStringList("desks")) {
            int i = line.indexOf('=');
            if (i > 0) desks.put(line.substring(0, i), line.substring(i + 1));
        }
    }

    public void save() {
        Yaml y = store.fresh();
        for (Room r : rooms.values()) {
            String p = "rooms." + r.id() + ".";
            y.set(p + "dim", r.dim());
            y.set(p + "bounds", List.of(r.minX(), r.minY(), r.minZ(), r.maxX(), r.maxY(), r.maxZ()));
            y.set(p + "group", r.group());
            y.set(p + "buy", r.buyPrice());
            y.set(p + "rent", r.rentPrice());
            if (r.owner() != null) y.set(p + "owner", r.owner().toString());
            y.set(p + "mode", r.mode().name());
            y.set(p + "expiry", r.expiry());
            y.set(p + "members", r.members().stream().map(UUID::toString).toList());
            if (r.spawn() != null) y.set(p + "spawn", r.spawn().serialize());
        }
        y.set("desks", desks.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toList());
        store.save();
    }
}

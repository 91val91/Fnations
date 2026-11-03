package fr.fnations.rocket;

import fr.fnations.FNations;
import fr.fnations.gui.Gui;
import fr.fnations.gui.Icons;
import fr.fnations.item.ItemRegistry;
import fr.fnations.util.Inv;
import fr.fnations.util.Keys;
import fr.fnations.util.Locs;
import fr.fnations.util.Txt;
import fr.fnations.util.YamlStore;
import io.papermc.paper.entity.TeleportFlag;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Système de fusée : installation, remplissage en ergol, compte à rebours, décollage et voyage vers d'autres mondes. */
public final class RocketManager implements Listener {
    private final FNations plugin;
    private final YamlStore store;
    private final Map<UUID, Rocket> rockets = new HashMap<>();

    static final class Rocket {
        final UUID id;
        final UUID owner;
        Location loc;
        int fuel;
        boolean launching;
        ArmorStand seat;
        ItemDisplay body;
        Interaction hitbox;

        Rocket(UUID id, UUID owner, Location loc, int fuel) {
            this.id = id;
            this.owner = owner;
            this.loc = loc;
            this.fuel = fuel;
        }

        boolean spawned() { return seat != null && seat.isValid(); }

        Player pilot() {
            if (!spawned()) return null;
            for (Entity e : seat.getPassengers()) if (e instanceof Player p) return p;
            return null;
        }
    }

    public RocketManager(FNations plugin) {
        this.plugin = plugin;
        this.store = new YamlStore(plugin, "data/rockets.yml");
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::gravityTick, 40L, 40L);
    }

    private int fuelNeeded() { return plugin.getConfig().getInt("fusee.ergol-requis", 16); }

    private float scale() { return (float) plugin.getConfig().getDouble("fusee.echelle", 3.0); }

    // ------------------------------------------------------------ entités
    private void spawn(Rocket r) {
        if (r.spawned()) return;
        World w = r.loc.getWorld();
        String tag = r.id.toString();
        r.seat = w.spawn(r.loc.clone().add(0, plugin.getConfig().getDouble("fusee.siege-y", 3.0), 0), ArmorStand.class, a -> {
            a.setInvisible(true);
            a.setMarker(true);
            a.setGravity(false);
            a.setInvulnerable(true);
            a.setPersistent(false);
            a.getPersistentDataContainer().set(Keys.ROCKET, PersistentDataType.STRING, tag);
        });
        r.body = w.spawn(r.loc.clone().add(0, scale() * 1.5, 0), ItemDisplay.class, d -> {
            d.setItemStack(ItemRegistry.create("rocket"));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBillboard(Display.Billboard.FIXED);
            d.setPersistent(false);
            d.setTeleportDuration(2);
            d.setViewRange(6f);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(scale(), scale(), scale()), new AxisAngle4f()));
            d.getPersistentDataContainer().set(Keys.ROCKET, PersistentDataType.STRING, tag);
        });
        r.hitbox = w.spawn(r.loc, Interaction.class, i -> {
            i.setInteractionWidth(2.2f);
            i.setInteractionHeight(scale() * 3);
            i.setPersistent(false);
            i.getPersistentDataContainer().set(Keys.ROCKET, PersistentDataType.STRING, tag);
        });
    }

    private void despawn(Rocket r) {
        if (r.seat != null) { r.seat.eject(); r.seat.remove(); }
        if (r.body != null) r.body.remove();
        if (r.hitbox != null) r.hitbox.remove();
        r.seat = null; r.body = null; r.hitbox = null;
    }

    private Rocket of(Entity e) {
        if (e == null) return null;
        String id = e.getPersistentDataContainer().get(Keys.ROCKET, PersistentDataType.STRING);
        return id == null ? null : rockets.get(UUID.fromString(id));
    }

    // ------------------------------------------------------------ interactions
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        if (!ItemRegistry.is(e.getItem(), "rocket")) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (!plugin.territory().canBuild(p, e.getClickedBlock().getLocation())) { Txt.err(p, "Vous ne pouvez pas installer de fusée ici."); return; }
        Location at = e.getClickedBlock().getLocation().add(0.5, 1, 0.5);
        int clearance = (int) Math.ceil(scale() * 3) + 1;
        for (int y = 0; y < clearance; y++) if (at.clone().add(0, y, 0).getBlock().getType().isSolid()) { Txt.err(p, "Il faut " + clearance + " blocs d'espace libre au-dessus."); return; }
        Rocket r = new Rocket(UUID.randomUUID(), p.getUniqueId(), at, 0);
        rockets.put(r.id, r);
        spawn(r);
        e.getItem().setAmount(e.getItem().getAmount() - 1);
        Txt.ok(p, "Fusée installée ! Remplissez-la avec " + fuelNeeded() + " Ergols de fusée (clic droit), puis montez à bord.");
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent e) {
        Rocket r = of(e.getRightClicked());
        if (r == null) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND || r.launching) return;
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (ItemRegistry.is(hand, "rocket_fuel")) {
            int add = Math.min(hand.getAmount(), fuelNeeded() - r.fuel);
            if (add <= 0) { Txt.err(p, "Réservoirs pleins."); return; }
            hand.setAmount(hand.getAmount() - add);
            r.fuel += add;
            p.playSound(p.getLocation(), Sound.ITEM_BUCKET_EMPTY_LAVA, 1f, 1f);
            Txt.ok(p, "Ergol : " + r.fuel + "/" + fuelNeeded());
            return;
        }
        if (p.isSneaking() && (p.getUniqueId().equals(r.owner) || p.hasPermission("fnations.admin"))) {
            if (r.pilot() != null) return;
            despawn(r);
            rockets.remove(r.id);
            Inv.give(p, ItemRegistry.create("rocket"));
            if (r.fuel > 0) Inv.give(p, ItemRegistry.create("rocket_fuel", r.fuel));
            Txt.ok(p, "Fusée démontée.");
            return;
        }
        if (r.pilot() == null) r.seat.addPassenger(p);
        if (r.pilot() == p) controls(p, r);
    }

    @EventHandler
    public void onDismount(EntityDismountEvent e) {
        Rocket r = of(e.getDismounted());
        if (r == null) return;
        if (r.launching) { e.setCancelled(true); return; }
        if (e.getEntity() instanceof Player p) Bukkit.getScheduler().runTask(plugin, () -> p.teleport(r.loc.clone().add(2, 0, 0)));
    }

    // ------------------------------------------------------------ GUI de vol
    public void controls(Player p, Rocket r) {
        Gui g = new Gui(4, "<gold>Poste de pilotage");
        g.border(Material.ORANGE_STAINED_GLASS_PANE);
        g.set(4, Icons.of(Material.FIREWORK_ROCKET, "<gold><b>Fusée spatiale",
                "<gray>Ergol : " + Txt.progress(r.fuel / (double) fuelNeeded(), 16, "gold", "dark_gray") + " <white>" + r.fuel + "/" + fuelNeeded(),
                "<gray>Monde actuel : <white>" + r.loc.getWorld().getName()));
        ConfigurationSection dest = plugin.getConfig().getConfigurationSection("fusee.destinations");
        int slot = 10;
        if (dest != null) for (String id : dest.getKeys(false)) {
            ConfigurationSection d = dest.getConfigurationSection(id);
            Material icon = Material.matchMaterial(d.getString("icone", "ENDER_PEARL"));
            boolean here = d.getString("monde", "").equals(r.loc.getWorld().getName());
            List<String> lore = new ArrayList<>(d.getStringList("description"));
            lore.add("");
            lore.add(here ? "<gray>Vous y êtes déjà" : r.fuel >= fuelNeeded() ? "<green>Clic : DÉCOLLAGE" : "<red>Ergol insuffisant");
            g.button(slot++, Icons.of(icon == null ? Material.ENDER_PEARL : icon, d.getString("nom", id), lore), pl -> {
                if (here) return;
                pl.closeInventory();
                launch(pl, r, d);
            });
            if (slot == 17) slot = 19;
        }
        g.button(31, Icons.of(Material.OAK_DOOR, "<red>Descendre"), pl -> { pl.closeInventory(); r.seat.removePassenger(pl); });
        g.fill(Material.GRAY_STAINED_GLASS_PANE);
        g.open(p);
    }

    private void launch(Player p, Rocket r, ConfigurationSection dest) {
        if (r.fuel < fuelNeeded() && p.getGameMode() != org.bukkit.GameMode.CREATIVE) { Txt.err(p, "Il faut " + fuelNeeded() + " Ergols de fusée."); return; }
        World target = world(dest);
        if (target == null) { Txt.err(p, "Destination indisponible (monde introuvable)."); return; }
        r.launching = true;
        r.fuel = 0;
        int count = plugin.getConfig().getInt("fusee.compte-a-rebours", 10);
        double ceiling = plugin.getConfig().getDouble("fusee.altitude-sortie", 420);
        new BukkitRunnable() {
            int c = count * 20;
            double speed = 0.05;

            @Override
            public void run() {
                if (!r.spawned()) { cancel(); r.launching = false; return; }
                Location base = r.loc;
                if (c > 0) {
                    if (c % 20 == 0) {
                        int s = c / 20;
                        for (Player pl : base.getWorld().getPlayers()) if (pl.getLocation().distanceSquared(base) < 10000)
                            pl.showTitle(Title.title(Txt.raw("<gold><b>" + s), Txt.raw("<yellow>Décollage imminent"), Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ZERO)));
                        base.getWorld().playSound(base, Sound.BLOCK_NOTE_BLOCK_BELL, 2f, 0.6f + (count - s) * 0.08f);
                    }
                    if (c < 60) base.getWorld().spawnParticle(Particle.CLOUD, base, 20, 1.5, 0.2, 1.5, 0.08);
                    c--;
                    return;
                }
                if (c == 0) {
                    Bukkit.broadcast(Txt.raw(Txt.PREFIX + "<gold>🚀 " + Txt.esc(p.getName()) + " a décollé vers " + dest.getString("nom") + "<gold> !"));
                    plugin.advancements().grant(p, "rocket_launched");
                    c = -1;
                }
                speed = Math.min(2.5, speed * 1.04 + 0.01);
                r.loc = base.clone().add(0, speed, 0);
                r.seat.teleport(r.loc.clone().add(0, plugin.getConfig().getDouble("fusee.siege-y", 3.0), 0), TeleportFlag.EntityState.RETAIN_PASSENGERS);
                r.body.teleport(r.loc.clone().add(0, scale() * 1.5, 0));
                r.hitbox.teleport(r.loc);
                r.loc.getWorld().spawnParticle(Particle.FLAME, r.loc, 30, 0.4, 0.3, 0.4, 0.15);
                r.loc.getWorld().spawnParticle(Particle.LARGE_SMOKE, r.loc.clone().add(0, -1, 0), 15, 0.6, 0.6, 0.6, 0.05);
                if (r.loc.getY() % 10 < speed) r.loc.getWorld().playSound(r.loc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.4f);
                if (r.loc.getY() >= ceiling) {
                    cancel();
                    arrive(p, r, dest, target);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void arrive(Player p, Rocket r, ConfigurationSection dest, World target) {
        despawn(r);
        rockets.remove(r.id);
        Location land;
        if (dest.getBoolean("spawn-du-monde", false)) land = target.getSpawnLocation();
        else land = new Location(target, dest.getDouble("x"), dest.getDouble("y", 100), dest.getDouble("z"));
        if (dest.getBoolean("plateforme", false)) platform(land);
        land.setY(Math.max(land.getY(), target.getHighestBlockYAt(land) + 1));
        Rocket landed = new Rocket(UUID.randomUUID(), r.owner, land.clone().add(3, 0, 0), 0);
        rockets.put(landed.id, landed);
        if (p.isOnline()) {
            p.teleportAsync(land).thenRun(() -> {
                spawn(landed);
                p.showTitle(Title.title(Txt.raw(dest.getString("nom", "Arrivée")), Txt.raw("<gray>Atterrissage réussi"), Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofSeconds(1))));
                if (dest.getBoolean("gravite-faible", false)) plugin.advancements().grant(p, "moon_landing");
            });
        } else spawn(landed);
    }

    private void platform(Location l) {
        World w = l.getWorld();
        int y = l.getBlockY() - 1;
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) {
            var b = w.getBlockAt(l.getBlockX() + x, y, l.getBlockZ() + z);
            if (b.getType().isAir()) b.setType(Math.abs(x) == 6 || Math.abs(z) == 6 ? Material.LIGHT_GRAY_CONCRETE : Material.IRON_BLOCK);
        }
    }

    /** Monde de destination, créé si besoin (vide ou lunaire). */
    private World world(ConfigurationSection d) {
        String name = d.getString("monde");
        if (name == null) return null;
        World w = Bukkit.getWorld(name);
        if (w != null || !d.getBoolean("creer-si-absent", false)) return w;
        String gen = d.getString("generation", "vide");
        WorldCreator wc = new WorldCreator(name).type(WorldType.FLAT).generateStructures(false);
        if (gen.equals("lune")) {
            wc.environment(World.Environment.THE_END);
            wc.generatorSettings("{\"layers\":[{\"block\":\"minecraft:bedrock\",\"height\":1},{\"block\":\"minecraft:end_stone\",\"height\":60},{\"block\":\"minecraft:light_gray_concrete_powder\",\"height\":3}],\"biome\":\"minecraft:the_end\",\"lakes\":false,\"features\":false,\"structure_overrides\":[]}");
        } else {
            wc.environment(World.Environment.THE_END);
            wc.generatorSettings("{\"layers\":[{\"block\":\"minecraft:air\",\"height\":1}],\"biome\":\"minecraft:the_void\",\"lakes\":false,\"features\":false,\"structure_overrides\":[]}");
        }
        plugin.getLogger().info("Création du monde spatial " + name + " (" + gen + ")");
        return wc.createWorld();
    }

    /** Faible gravité dans les mondes spatiaux. */
    private void gravityTick() {
        for (Rocket r : rockets.values()) {
            Player pilot = r.pilot();
            if (pilot != null && !r.launching && pilot.getCurrentInput().isSneak()) r.seat.removePassenger(pilot);
        }
        ConfigurationSection dest = plugin.getConfig().getConfigurationSection("fusee.destinations");
        if (dest == null) return;
        for (String id : dest.getKeys(false)) {
            ConfigurationSection d = dest.getConfigurationSection(id);
            if (!d.getBoolean("gravite-faible", false)) continue;
            World w = Bukkit.getWorld(d.getString("monde", ""));
            if (w == null) continue;
            for (Player p : w.getPlayers()) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 60, 3, true, false, false));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 60, 0, true, false, false));
            }
        }
    }

    // ------------------------------------------------------------ chunks & persistance
    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent e) {
        String k = Locs.chunkKey(e.getChunk());
        for (Rocket r : rockets.values()) if (!r.launching && r.spawned() && Locs.chunkKey(r.loc).equals(k)) despawn(r);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        String k = Locs.chunkKey(e.getChunk());
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Rocket r : rockets.values()) if (!r.spawned() && r.loc.getWorld() == e.getWorld() && Locs.chunkKey(r.loc).equals(k)) spawn(r);
        });
    }

    public void load() {
        ConfigurationSection root = store.get().getConfigurationSection("rockets");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            Location l = Locs.loc(s.getString("loc"));
            if (l == null) continue;
            Rocket r = new Rocket(UUID.fromString(id), UUID.fromString(s.getString("owner")), l, s.getInt("fuel"));
            rockets.put(r.id, r);
            if (l.isChunkLoaded()) spawn(r);
        }
    }

    public void shutdown() {
        YamlConfiguration y = store.fresh();
        for (Rocket r : rockets.values()) {
            String p = "rockets." + r.id + ".";
            y.set(p + "owner", r.owner.toString());
            y.set(p + "loc", Locs.loc(r.loc));
            y.set(p + "fuel", r.fuel);
            despawn(r);
        }
        store.save();
    }
}

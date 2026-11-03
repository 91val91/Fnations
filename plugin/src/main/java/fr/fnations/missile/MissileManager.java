package fr.fnations.missile;

import fr.fnations.FNations;
import fr.fnations.gui.Icons;
import fr.fnations.item.ItemRegistry;
import fr.fnations.machine.Machine;
import fr.fnations.machine.MachineGui;
import fr.fnations.nation.Nation;
import fr.fnations.util.ChatPrompt;
import fr.fnations.util.Explosions;
import fr.fnations.util.Locs;
import fr.fnations.util.Txt;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Système de missiles : lanceur, ciblage, vol balistique, impacts et radiations. */
public final class MissileManager {
    private final FNations plugin;
    private final Map<String, MissileType> types = new HashMap<>();
    private final List<Radiation> zones = new ArrayList<>();

    private record Radiation(Location center, double radius, long until) {}

    public MissileManager(FNations plugin) {
        this.plugin = plugin;
        reload();
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::radiationTick, 20L, 20L);
    }

    public void reload() {
        types.clear();
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("missiles.types");
        if (s != null) for (String id : s.getKeys(false)) types.put(id, MissileType.load(id, s.getConfigurationSection(id)));
    }

    public MissileType type(ItemStack it) { return types.get(ItemRegistry.idOf(it)); }

    // ------------------------------------------------------------ GUI du lanceur
    public void renderLauncher(MachineGui gui, Machine m) {
        ItemStack missile = gui.slot(MachineGui.MISSILE);
        MissileType t = type(missile);
        gui.set(12, Icons.of(Material.IRON_BARS, "<gray>Rampe →", "<gray>Placez un missile au centre"));
        gui.set(14, Icons.of(Material.IRON_BARS, "<gray>← Rampe"));
        String target = m.targetX() == null ? "<red>aucune" : "<white>X " + m.targetX() + " / Z " + m.targetZ();
        double dist = m.targetX() == null ? 0 : Math.hypot(m.targetX() - m.block().getX(), m.targetZ() - m.block().getZ());
        Nation owner = m.targetX() == null ? null : plugin.nations().atChunk(Locs.chunkKey(m.block().getWorld().getName(), m.targetX() >> 4, m.targetZ() >> 4));
        gui.button(10, Icons.of(Material.COMPASS, "<aqua>Définir la cible", "<gray>Cible : " + target,
                "<gray>Distance : <white>" + (int) dist + " blocs", "<gray>Territoire : " + (owner == null ? "<gray>sauvage" : owner.colored()),
                "", "<yellow>Clic : saisir « X Z » dans le chat"), p -> ChatPrompt.ask(p, "Coordonnées de la cible « X Z » :", s -> {
            String[] parts = s.trim().split("[ ,;]+");
            try {
                m.setTarget(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                Txt.ok(p, "Cible verrouillée : X " + m.targetX() + " Z " + m.targetZ());
            } catch (Exception e) {
                Txt.err(p, "Format attendu : X Z (ex: 1200 -350)");
            }
        }));
        List<String> lore = new ArrayList<>();
        if (t == null) lore.add("<red>Aucun missile chargé");
        else {
            lore.add("<gray>Missile : " + ItemRegistry.def(t.item()).name());
            lore.add("<gray>Portée : <white>" + (int) t.minRange() + " - " + (int) t.range() + " blocs");
            lore.add("<gray>Puissance : <white>" + t.power());
            lore.add("<gray>Énergie requise : <yellow>" + (int) t.energy() + " E <gray>(dispo " + (int) m.energy() + ")");
        }
        lore.add("");
        lore.add("<red><b>Clic : LANCEMENT");
        gui.button(16, Icons.of(Material.TNT, "<dark_red><b>LANCER LE MISSILE", lore), p -> {
            p.closeInventory();
            launch(p, m);
        });
    }

    // ------------------------------------------------------------ lancement
    public void launch(Player p, Machine m) {
        ItemStack missile = m.gui().slot(MachineGui.MISSILE);
        MissileType t = type(missile);
        if (t == null) { Txt.err(p, "Aucun missile sur la rampe."); return; }
        if (m.targetX() == null) { Txt.err(p, "Aucune cible définie."); return; }
        double dist = Math.hypot(m.targetX() - m.block().getX(), m.targetZ() - m.block().getZ());
        if (dist > t.range()) { Txt.err(p, "Cible hors de portée (" + (int) dist + " > " + (int) t.range() + ")."); return; }
        if (dist < t.minRange()) { Txt.err(p, "Cible trop proche (minimum " + (int) t.minRange() + ")."); return; }
        if (m.energy() < t.energy() && p.getGameMode() != org.bukkit.GameMode.CREATIVE) { Txt.err(p, "Énergie insuffisante : alimentez le lanceur avec des générateurs."); return; }
        World w = m.block().getWorld();
        Nation mine = plugin.nations().of(p);
        Nation victim = plugin.nations().atChunk(Locs.chunkKey(w.getName(), m.targetX() >> 4, m.targetZ() >> 4));
        if (victim != null && victim != mine && !plugin.wars().activeBetween(mine, victim) && !p.hasPermission("fnations.admin.bypass")) {
            Txt.err(p, "Vous n'êtes pas en guerre active contre " + victim.colored() + "<red>. Tir refusé."); return;
        }
        if (victim == null && !plugin.getConfig().getBoolean("missiles.autoriser-zones-sauvages", true)) { Txt.err(p, "Tir hors territoire ennemi interdit."); return; }
        if (victim == mine) { Txt.err(p, "Vous visez votre propre territoire !"); return; }
        m.useEnergy(t.energy());
        missile.setAmount(missile.getAmount() - 1);
        Location start = m.block().getLocation().add(0.5, 1.5, 0.5);
        int countdown = plugin.getConfig().getInt("missiles.compte-a-rebours", 5);
        if (t.item().equals("missile_nuclear")) {
            Bukkit.broadcast(Txt.raw(Txt.PREFIX + "<dark_red><b>☢ ALERTE NUCLÉAIRE !</b> <red>Un missile nucléaire a été lancé par "
                    + (mine == null ? Txt.esc(p.getName()) : mine.colored()) + "<red> !"));
        }
        if (victim != null) for (Player pl : plugin.nations().online(victim)) {
            pl.showTitle(Title.title(Txt.raw("<dark_red><b>⚠ MISSILE ENTRANT"), Txt.raw("<red>Impact estimé vers X " + m.targetX() + " Z " + m.targetZ()),
                    Title.Times.times(Duration.ZERO, Duration.ofSeconds(3), Duration.ofMillis(500))));
            pl.playSound(pl.getLocation(), Sound.BLOCK_BELL_RESONATE, 1f, 0.5f);
        }
        plugin.advancements().grant(p, "missile_launched");
        new BukkitRunnable() {
            int c = countdown;

            @Override
            public void run() {
                if (c > 0) {
                    for (Player pl : w.getPlayers()) if (pl.getLocation().distanceSquared(start) < 2500)
                        pl.showTitle(Title.title(Txt.raw("<red><b>" + c), Txt.raw("<gray>Lancement du missile"), Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ZERO)));
                    w.playSound(start, Sound.BLOCK_NOTE_BLOCK_PLING, 1.5f, 0.5f + (countdown - c) * 0.1f);
                    w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, start, 8, 0.3, 0.1, 0.3, 0.02);
                    c--;
                    return;
                }
                cancel();
                new Flight(p, mine, t, start, m.targetX() + 0.5, m.targetZ() + 0.5);
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private final class Flight extends BukkitRunnable {
        final Player shooter;
        final Nation attacker;
        final MissileType t;
        final Location start;
        final double tx, tz, ty;
        final int duration;
        final double apex;
        ItemDisplay display;
        int tick;
        Location last;

        Flight(Player shooter, Nation attacker, MissileType t, Location start, double tx, double tz) {
            this.shooter = shooter;
            this.attacker = attacker;
            this.t = t;
            this.start = start;
            this.tx = tx;
            this.tz = tz;
            this.ty = start.getY();
            double dist = Math.hypot(tx - start.getX(), tz - start.getZ());
            this.duration = (int) Math.max(40, dist / t.speed());
            this.apex = Math.min(plugin.getConfig().getDouble("missiles.altitude-max", 260), Math.max(25, dist * t.arc()));
            this.last = start.clone();
            display = start.getWorld().spawn(start, ItemDisplay.class, d -> {
                d.setItemStack(ItemRegistry.create(t.item()));
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setPersistent(false);
                d.setTeleportDuration(1);
                d.setBillboard(Display.Billboard.FIXED);
                d.setViewRange(8f);
                d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(2.5f, 2.5f, 2.5f), new AxisAngle4f()));
            });
            start.getWorld().playSound(start, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 3f, 0.4f);
            start.getWorld().playSound(start, Sound.ENTITY_BLAZE_SHOOT, 3f, 0.5f);
            runTaskTimer(plugin, 1L, 1L);
        }

        Location at(double u) {
            double x = start.getX() + (tx - start.getX()) * u;
            double z = start.getZ() + (tz - start.getZ()) * u;
            double y = ty + apex * 4 * u * (1 - u);
            return new Location(start.getWorld(), x, y, z);
        }

        @Override
        public void run() {
            tick++;
            double u = Math.min(1.0, tick / (double) duration);
            Location pos = at(u);
            Vector vel = pos.toVector().subtract(last.toVector());
            boolean loaded = pos.isChunkLoaded();
            if (display != null && display.isValid()) {
                if (loaded) {
                    Location l = pos.clone();
                    if (vel.lengthSquared() > 1e-6) l.setDirection(vel);
                    display.teleport(l);
                } else {
                    display.remove();
                    display = null;
                }
            }
            if (loaded) {
                pos.getWorld().spawnParticle(Particle.FLAME, pos, 4, 0.1, 0.1, 0.1, 0.02);
                pos.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, pos, 2, 0.1, 0.1, 0.1, 0.01);
                if (u > 0.5 && pos.getBlock().getType().isSolid()) { impact(pos); return; }
            }
            last = pos;
            if (u >= 1.0) impact(null);
        }

        void impact(Location direct) {
            cancel();
            if (display != null) display.remove();
            World w = start.getWorld();
            w.getChunkAtAsync((int) Math.floor(tx) >> 4, (int) Math.floor(tz) >> 4).thenAccept(chunk -> {
                Location hit = direct != null ? direct : new Location(w, tx, w.getHighestBlockYAt((int) Math.floor(tx), (int) Math.floor(tz)) + 1, tz);
                detonate(shooter, attacker, t, hit);
            });
        }
    }

    private void detonate(Player shooter, Nation attacker, MissileType t, Location hit) {
        World w = hit.getWorld();
        boolean breakBlocks = plugin.getConfig().getBoolean("missiles.casser-blocs", true);
        Nation victim = plugin.nations().at(hit);
        Explosions.explode(hit, t.power(), t.radiationRadius() > 0, breakBlocks, attacker, shooter);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < t.extraExplosions(); i++) {
            double a = r.nextDouble(Math.PI * 2), d = r.nextDouble(t.blastRadius());
            Location l = hit.clone().add(Math.cos(a) * d, 0, Math.sin(a) * d);
            l.setY(w.getHighestBlockYAt(l) + 1);
            int delay = 2 + i * 2;
            Bukkit.getScheduler().runTaskLater(plugin, () -> Explosions.explode(l, t.power() * 0.75f, true, breakBlocks, attacker, shooter), delay);
        }
        plugin.vehicles().damageNearby(hit, t.power() * 2 + t.blastRadius(), t.power() * 30, shooter);
        if (t.radiationRadius() > 0) {
            zones.add(new Radiation(hit.clone(), t.radiationRadius(), System.currentTimeMillis() + t.radiationSeconds() * 1000L));
            w.spawnParticle(Particle.EXPLOSION_EMITTER, hit, 20, 6, 6, 6);
            for (Player pl : w.getPlayers()) {
                if (pl.getLocation().distance(hit) < 300) {
                    pl.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
                    pl.playSound(pl.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 4f, 0.3f);
                    pl.playSound(pl.getLocation(), Sound.ENTITY_WITHER_DEATH, 2f, 0.5f);
                }
            }
            Bukkit.broadcast(Txt.raw(Txt.PREFIX + "<dark_green><b>☢ Détonation nucléaire</b> <green>en X " + hit.getBlockX() + " Z " + hit.getBlockZ()
                    + " <gray>— zone irradiée pendant " + Txt.duration(t.radiationSeconds())));
        }
        if (attacker != null && victim != null && victim != attacker) {
            plugin.wars().reward(attacker, victim, plugin.getConfig().getInt("guerres.points-missile", 10) * (t.radiationRadius() > 0 ? 3 : 1), "frappe de missile");
        }
        if (shooter != null && shooter.isOnline()) Txt.send(shooter, "<gold>💥 Impact confirmé en X " + hit.getBlockX() + " Z " + hit.getBlockZ() + ".");
    }

    private void radiationTick() {
        long now = System.currentTimeMillis();
        zones.removeIf(z -> z.until() < now);
        for (Radiation z : zones) {
            World w = z.center().getWorld();
            if (w == null) continue;
            for (Player p : w.getPlayers()) {
                if (p.getLocation().distanceSquared(z.center()) > z.radius() * z.radius()) continue;
                p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 120, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 120, 1));
                Txt.bar(p, "<dark_green><b>☢ ZONE IRRADIÉE ☢");
            }
            if (z.center().isChunkLoaded()) w.spawnParticle(Particle.HAPPY_VILLAGER, z.center(), 30, z.radius() / 2, 5, z.radius() / 2, 0);
        }
    }
}

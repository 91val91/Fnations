package fr.fnations.vehicle;

import fr.fnations.FNations;
import fr.fnations.item.ItemRegistry;
import fr.fnations.nation.Nation;
import fr.fnations.util.Ballistic;
import fr.fnations.util.Explosions;
import fr.fnations.util.Inv;
import fr.fnations.util.Keys;
import fr.fnations.util.Locs;
import fr.fnations.util.Txt;
import fr.fnations.util.YamlStore;
import fr.fnations.weapon.GunDef;
import io.papermc.paper.entity.TeleportFlag;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.Bukkit;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Système de véhicules : déploiement, conduite, carburant, combat, persistance. */
public final class VehicleManager implements Listener {
    private final FNations plugin;
    private final YamlStore store;
    private final Map<String, VehicleType> types = new LinkedHashMap<>();
    private final Map<UUID, Vehicle> vehicles = new HashMap<>();
    private GunDef machineGun;

    public VehicleManager(FNations plugin) {
        this.plugin = plugin;
        this.store = new YamlStore(plugin, "data/vehicles.yml");
        reload();
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void reload() {
        types.clear();
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("vehicules");
        if (s != null) for (String id : s.getKeys(false)) {
            if (id.equals("mitrailleuse")) continue;
            types.put(id, VehicleType.load(id, s.getConfigurationSection(id)));
        }
        ConfigurationSection mg = plugin.getConfig().getConfigurationSection("vehicules.mitrailleuse");
        machineGun = new GunDef("assault_rifle", mg == null ? "ammo_762" : mg.getString("munition", "ammo_762"), "balle",
                mg == null ? 6 : mg.getDouble("degats", 6), 3, 100, 0, mg == null ? 120 : mg.getDouble("portee", 120),
                0.03, 0.03, 1, 1.3, 0, 0, "minecraft:entity.firework_rocket.blast", 1.4f, 0);
    }

    public Collection<VehicleType> types() { return types.values(); }

    // ------------------------------------------------------------ recherche
    public Vehicle byHitbox(Entity e) {
        if (e == null) return null;
        String id = e.getPersistentDataContainer().get(Keys.VEHICLE, PersistentDataType.STRING);
        return id == null ? null : vehicles.get(UUID.fromString(id));
    }

    public Vehicle driving(Player p) {
        Entity v = p.getVehicle();
        if (v == null) return null;
        Vehicle veh = byHitbox(v);
        return veh != null && veh.driver() == p ? veh : null;
    }

    public Vehicle riding(Player p) { return byHitbox(p.getVehicle()); }

    // ------------------------------------------------------------ déploiement
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeploy(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        // Tir depuis un véhicule (clic gauche)
        if (e.getAction() == Action.LEFT_CLICK_AIR || e.getAction() == Action.LEFT_CLICK_BLOCK) {
            Vehicle v = driving(p);
            if (v != null) { e.setCancelled(true); shoot(p, v); }
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        String id = ItemRegistry.idOf(e.getItem());
        if (id == null || !types.containsKey(id)) return;
        e.setCancelled(true);
        if (!plugin.territory().canBuild(p, e.getClickedBlock().getLocation())) { Txt.err(p, "Vous ne pouvez pas déployer de véhicule ici."); return; }
        VehicleType t = types.get(id);
        Location at = e.getClickedBlock().getLocation().add(0.5, 1, 0.5);
        at.setYaw(p.getLocation().getYaw());
        Double fuel = e.getItem().getItemMeta().getPersistentDataContainer().get(Keys.FUEL, PersistentDataType.DOUBLE);
        Vehicle v = new Vehicle(UUID.randomUUID(), t, p.getUniqueId(), at, t.health(), fuel == null ? t.fuelCapacity() * 0.25 : fuel);
        vehicles.put(v.id, v);
        v.spawn();
        e.getItem().setAmount(e.getItem().getAmount() - 1);
        p.playSound(at, Sound.BLOCK_ANVIL_PLACE, 0.6f, 0.7f);
        Txt.ok(p, t.name() + " <green>déployé. Clic droit pour monter, accroupi + clic droit pour ranger.");
    }

    @EventHandler
    public void onInteractVehicle(PlayerInteractEntityEvent e) {
        Vehicle v = byHitbox(e.getRightClicked());
        if (v == null) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (ItemRegistry.is(hand, "fuel_canister")) {
            if (v.fuel >= v.type.fuelCapacity()) { Txt.err(p, "Le réservoir est plein."); return; }
            double add = plugin.getConfig().getDouble("vehicules-general.carburant-par-jerrican", 50);
            v.fuel = Math.min(v.type.fuelCapacity(), v.fuel + add);
            hand.setAmount(hand.getAmount() - 1);
            p.playSound(p.getLocation(), Sound.ITEM_BUCKET_EMPTY, 1f, 1.2f);
            Txt.ok(p, "Ravitaillé : " + (int) v.fuel + "/" + (int) v.type.fuelCapacity());
            return;
        }
        if (p.isSneaking()) {
            if (!p.getUniqueId().equals(v.owner) && !p.hasPermission("fnations.admin")) { Txt.err(p, "Ce véhicule ne vous appartient pas."); return; }
            if (v.passengers() > 0) { Txt.err(p, "Le véhicule est occupé."); return; }
            pickup(p, v);
            return;
        }
        if (!canUse(p, v)) { Txt.err(p, "Ce véhicule appartient à une autre nation."); return; }
        if (v.passengers() >= v.type.seats()) { Txt.err(p, "Plus de place à bord."); return; }
        v.seat.addPassenger(p);
        if (v.driver() == p) Txt.send(p, "<gray>ZQSD : conduire • Clic gauche : tirer • Accroupi : descendre"
                + (v.type.mode() == VehicleType.Mode.HELICOPTER ? " • Espace/Ctrl : monter/descendre" : "")
                + (v.type.id().equals("tractor") ? " • Espace : charrue on/off" : ""));
    }

    private boolean canUse(Player p, Vehicle v) {
        if (p.getUniqueId().equals(v.owner) || p.hasPermission("fnations.admin")) return true;
        Nation mine = plugin.nations().of(p), theirs = plugin.nations().of(v.owner);
        return theirs == null || plugin.nations().allied(mine, theirs) || plugin.wars().activeBetween(mine, theirs);
    }

    @EventHandler
    public void onAttack(PrePlayerAttackEntityEvent e) {
        Vehicle v = byHitbox(e.getAttacked());
        if (v == null) return;
        e.setCancelled(true);
        // Le conducteur est assis dans la hitbox : son clic gauche "touche" son propre véhicule -> tir
        if (driving(e.getPlayer()) == v) { shoot(e.getPlayer(), v); return; }
        if (riding(e.getPlayer()) == v) return;
        damage(v, plugin.getConfig().getDouble("vehicules-general.degats-melee", 1), e.getPlayer());
    }

    @EventHandler
    public void onDismount(EntityDismountEvent e) {
        Vehicle v = byHitbox(e.getDismounted());
        if (v == null || !(e.getEntity() instanceof Player p)) return;
        Location out = v.loc.clone().add(new Vector(Math.cos(Math.toRadians(v.yaw)), 0, Math.sin(Math.toRadians(v.yaw))).multiply(v.type.hitboxWidth() / 2 + 0.8));
        out.setY(Math.max(out.getY(), out.getWorld().getHighestBlockYAt(out) + 1));
        out.setDirection(p.getLocation().getDirection());
        Bukkit.getScheduler().runTask(plugin, () -> p.teleport(out));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Vehicle v = riding(e.getPlayer());
        if (v != null) v.seat.removePassenger(e.getPlayer());
    }

    private void pickup(Player p, Vehicle v) {
        v.despawn();
        vehicles.remove(v.id);
        ItemStack it = ItemRegistry.create(v.type.id());
        ItemMeta m = it.getItemMeta();
        m.getPersistentDataContainer().set(Keys.FUEL, PersistentDataType.DOUBLE, v.fuel);
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>(m.lore() == null ? List.of() : m.lore());
        lore.add(0, Txt.c("<gray>Carburant : <yellow>" + (int) v.fuel + "/" + (int) v.type.fuelCapacity()));
        m.lore(lore);
        it.setItemMeta(m);
        Inv.give(p, it);
        Txt.ok(p, "Véhicule rangé.");
    }

    // ------------------------------------------------------------ dégâts
    public void damage(Vehicle v, double amount, Player source) {
        if (!vehicles.containsKey(v.id)) return;
        v.health -= amount;
        v.loc.getWorld().playSound(v.loc, Sound.ENTITY_IRON_GOLEM_HURT, 0.6f, 1.3f);
        Player d = v.driver();
        if (d != null) Txt.bar(d, "<red>Blindage : " + (int) Math.max(0, v.health) + "/" + (int) v.type.health());
        if (v.health <= 0) destroy(v, source);
    }

    public void damageNearby(Location l, double radius, double amount, Player source) {
        for (Vehicle v : new ArrayList<>(vehicles.values())) {
            if (v.loc.getWorld() != l.getWorld()) continue;
            double d = v.loc.distance(l);
            if (d <= radius) damage(v, amount * (1 - d / (radius + 1)), source);
        }
    }

    private void destroy(Vehicle v, Player source) {
        vehicles.remove(v.id);
        Location l = v.loc.clone();
        for (Entity e : v.seat == null ? List.<Entity>of() : new ArrayList<>(v.seat.getPassengers())) {
            if (e instanceof LivingEntity le) le.damage(plugin.getConfig().getDouble("vehicules-general.degats-destruction-passagers", 8), source);
        }
        v.despawn();
        l.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, l, 1);
        l.getWorld().spawnParticle(Particle.LARGE_SMOKE, l, 40, 1, 1, 1, 0.05);
        l.getWorld().playSound(l, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.7f);
        l.getWorld().dropItemNaturally(l, ItemRegistry.create("steel_plate", 2));
        if (source != null) {
            Nation a = plugin.nations().of(source), o = plugin.nations().of(v.owner);
            if (a != null && o != null) plugin.wars().reward(a, o, plugin.getConfig().getInt("guerres.points-vehicule", 8), "véhicule ennemi détruit");
        }
    }

    // ------------------------------------------------------------ armes embarquées
    private void shoot(Player p, Vehicle v) {
        long now = System.currentTimeMillis();
        String w = v.type.weapon();
        if (w.equals("aucune")) return;
        if (w.equals("canon")) {
            long cd = plugin.getConfig().getLong("vehicules-general.canon-cooldown-ms", 3000);
            if (now - v.lastShot < cd) { Txt.bar(p, "<red>Rechargement du canon..."); return; }
            if (p.getGameMode() != org.bukkit.GameMode.CREATIVE && !Inv.take(p, "fnations:tank_shell", 1)) { Txt.bar(p, "<red>Aucun obus de char !"); return; }
            v.lastShot = now;
            Location eye = p.getEyeLocation();
            Vector dir = eye.getDirection();
            Location start = v.loc.clone().add(0, v.type.hitboxHeight() * 0.8, 0).add(dir.clone().multiply(v.type.hitboxWidth() * 0.8));
            l(start, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.5f);
            start.getWorld().spawnParticle(Particle.EXPLOSION, start, 1);
            float power = (float) plugin.getConfig().getDouble("vehicules-general.canon-explosion", 3.5);
            Nation n = plugin.nations().of(p);
            new Ballistic(plugin, start, dir.multiply(3.0), 0.025, 80, ItemRegistry.create("tank_shell"), 0.8f,
                    plugin.weapons().targets(p), Particle.SMOKE, (loc, ent) -> {
                Vehicle hit = byHitbox(ent);
                if (hit != null) damage(hit, plugin.getConfig().getDouble("vehicules-general.canon-degats", 120), p);
                Explosions.explode(loc, power, false, true, n, p);
                damageNearby(loc, power, 30, p);
            });
            return;
        }
        // mitrailleuse
        if (now - v.lastShot < 120) return;
        if (p.getGameMode() != org.bukkit.GameMode.CREATIVE && !Inv.take(p, "fnations:" + machineGun.ammo(), 1)) { Txt.bar(p, "<red>Plus de munitions 7.62mm !"); return; }
        v.lastShot = now;
        Location eye = p.getEyeLocation();
        Location muzzle = eye.clone().add(eye.getDirection().multiply(v.type.hitboxWidth() * 0.7));
        l(muzzle, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.2f, 1.5f);
        plugin.weapons().hitscan(p, muzzle, eye.getDirection(), machineGun);
    }

    private static void l(Location l, Sound s, float v, float p) { l.getWorld().playSound(l, s, v, p); }

    // ------------------------------------------------------------ physique
    private void tick() {
        for (Vehicle v : new ArrayList<>(vehicles.values())) {
            if (!v.spawned()) continue;
            for (Entity pas : new ArrayList<>(v.seat.getPassengers())) {
                if (pas instanceof Player pl && pl.getCurrentInput().isSneak()) v.seat.removePassenger(pl);
            }
            Player d = v.driver();
            switch (v.type.mode()) {
                case GROUND -> ground(v, d);
                case PLANE -> plane(v, d);
                case HELICOPTER -> heli(v, d);
            }
            move(v);
            if (d != null && d.getTicksLived() % 5 == 0) hud(d, v);
        }
    }

    private boolean solid(Location l) { return l.getBlock().getType().isSolid(); }

    private boolean onGround(Vehicle v) { return solid(v.loc.clone().add(0, -0.05, 0)); }

    private boolean hasFuel(Vehicle v, Player d) {
        return v.fuel > 0 || (d != null && d.getGameMode() == org.bukkit.GameMode.CREATIVE);
    }

    private void burn(Vehicle v, double factor) { v.fuel = Math.max(0, v.fuel - v.type.fuelPerTick() * factor); }

    private void ground(Vehicle v, Player d) {
        Input in = d == null ? null : d.getCurrentInput();
        boolean fuel = hasFuel(v, d);
        if (in != null && fuel && in.isForward()) v.speed = Math.min(v.type.maxSpeed(), v.speed + v.type.accel());
        else if (in != null && fuel && in.isBackward()) v.speed = Math.max(-v.type.maxSpeed() * 0.4, v.speed - v.type.accel());
        else v.speed *= 0.85;
        if (Math.abs(v.speed) < 0.003) v.speed = 0;
        if (in != null) {
            double turn = v.type.turn() * (Math.abs(v.speed) > 0.01 || v.type.id().equals("tank") ? 1 : 0);
            if (in.isLeft()) v.yaw -= (float) turn;
            if (in.isRight()) v.yaw += (float) turn;
            if (v.type.id().equals("tractor") && in.isJump() && d.getTicksLived() % 10 == 0) {
                v.plow = !v.plow;
                Txt.bar(d, v.plow ? "<green>Charrue abaissée" : "<gray>Charrue relevée");
            }
        }
        if (v.speed != 0) burn(v, Math.abs(v.speed) / v.type.maxSpeed());
        Vector dir = forward(v.yaw, 0).multiply(v.speed);
        Location next = v.loc.clone().add(dir);
        // collisions et marches (jusqu'à 1 bloc)
        if (solid(next) || solid(next.clone().add(0, 0.9, 0))) {
            Location up = next.clone().add(0, 1.0, 0);
            if (!solid(up) && !solid(up.clone().add(0, 0.9, 0)) && onGround(v)) next = up;
            else { next = v.loc.clone(); v.speed = 0; }
        }
        // gravité
        if (!solid(next.clone().add(0, -0.05, 0))) {
            v.vy = Math.max(-1.5, v.vy - 0.08);
            Location fall = next.clone().add(0, v.vy, 0);
            if (solid(fall)) { fall.setY(fall.getBlockY() + 1); v.vy = 0; }
            next = fall;
        } else {
            v.vy = 0;
            next.setY(Math.floor(next.getY() + 0.01));
            if (solid(next)) next.setY(next.getY() + 1);
        }
        v.loc = next;
        if (v.type.id().equals("tractor") && Math.abs(v.speed) > 0.01 && d != null && d.getTicksLived() % 4 == 0) farm(v, d);
    }

    private void farm(Vehicle v, Player d) {
        Block center = v.loc.getBlock();
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            Block b = center.getRelative(x, 0, z);
            if (!plugin.territory().canBuild(d, b.getLocation())) continue;
            if (b.getBlockData() instanceof Ageable age && age.getAge() == age.getMaximumAge() && b.getType() != Material.SUGAR_CANE) {
                for (ItemStack drop : b.getDrops()) b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), drop);
                age.setAge(0);
                b.setBlockData(age);
            } else if (v.plow && b.getType().isAir()) {
                Block below = b.getRelative(0, -1, 0);
                if (below.getType() == Material.GRASS_BLOCK || below.getType() == Material.DIRT) below.setType(Material.FARMLAND);
            }
        }
    }

    private void plane(Vehicle v, Player d) {
        Input in = d == null ? null : d.getCurrentInput();
        boolean fuel = hasFuel(v, d);
        boolean grounded = onGround(v);
        if (in != null && fuel && in.isForward()) v.speed = Math.min(v.type.maxSpeed(), v.speed + v.type.accel());
        else if (in != null && in.isBackward()) v.speed = Math.max(0, v.speed - v.type.accel() * 2);
        else v.speed = Math.max(0, v.speed - (grounded ? v.type.accel() : v.type.accel() * 0.15));
        if (!fuel) v.speed = Math.max(0, v.speed - v.type.accel() * 0.3);
        if (v.speed > 0) burn(v, 0.3 + v.speed / v.type.maxSpeed());
        if (d != null) {
            float targetYaw = d.getLocation().getYaw();
            float dy = wrap(targetYaw - v.yaw);
            v.yaw += Math.max(-(float) v.type.turn(), Math.min((float) v.type.turn(), dy));
            float targetPitch = d.getLocation().getPitch();
            v.pitch += Math.max(-3f, Math.min(3f, targetPitch - v.pitch));
        }
        boolean lift = v.speed >= v.type.takeoffSpeed();
        if (grounded && !lift && v.pitch < 0) v.pitch = 0;
        if (grounded && v.pitch > 0) v.pitch = 0;
        Vector move = forward(v.yaw, v.pitch).multiply(v.speed);
        if (!lift && !grounded) move.setY(move.getY() - (v.type.takeoffSpeed() - v.speed) * 1.2 - 0.1); // décrochage
        Location next = v.loc.clone().add(move);
        if (solid(next) || solid(next.clone().add(0, 1, 0))) {
            if (v.speed > v.type.takeoffSpeed() * 0.8 && !grounded) { crash(v, d); return; }
            if (solid(next)) next.setY(Math.floor(next.getY()) + 1);
            if (solid(next)) { next = v.loc.clone(); v.speed = 0; }
        }
        if (!solid(next.clone().add(0, -0.05, 0)) && !lift) next.add(0, -0.15, 0);
        if (solid(next)) next.setY(Math.floor(next.getY()) + 1);
        v.loc = next;
    }

    private void heli(Vehicle v, Player d) {
        Input in = d == null ? null : d.getCurrentInput();
        boolean fuel = hasFuel(v, d);
        double target = 0, strafe = 0, climb = 0;
        if (in != null && fuel) {
            if (in.isForward()) target = v.type.maxSpeed();
            if (in.isBackward()) target = -v.type.maxSpeed() * 0.5;
            if (in.isLeft()) strafe = -v.type.maxSpeed() * 0.5;
            if (in.isRight()) strafe = v.type.maxSpeed() * 0.5;
            if (in.isJump()) climb = v.type.climb();
            if (in.isSprint()) climb = -v.type.climb();
            float dy = wrap(d.getLocation().getYaw() - v.yaw);
            v.yaw += Math.max(-(float) v.type.turn(), Math.min((float) v.type.turn(), dy));
        }
        v.speed += (target - v.speed) * 0.08;
        v.strafe += (strafe - v.strafe) * 0.1;
        boolean grounded = onGround(v);
        if (fuel && (d != null)) {
            v.vy += (climb - v.vy) * 0.2;
            burn(v, 0.6 + Math.abs(v.speed) / v.type.maxSpeed());
            v.rotorAngle += 35;
        } else {
            v.vy = grounded ? 0 : Math.max(-1.2, v.vy - 0.04);
            if (!grounded) v.rotorAngle += 10;
        }
        Vector move = forward(v.yaw, 0).multiply(v.speed).add(forward(v.yaw + 90, 0).multiply(v.strafe));
        move.setY(v.vy);
        Location next = v.loc.clone().add(move);
        if (solid(next) || solid(next.clone().add(0, v.type.hitboxHeight() * 0.8, 0))) {
            if (v.vy < -0.8) { crash(v, d); return; }
            Location only = v.loc.clone().add(0, Math.max(0, v.vy), 0);
            next = solid(only) ? v.loc.clone() : only;
            v.speed *= 0.3;
            if (v.vy < 0) v.vy = 0;
        }
        v.loc = next;
    }

    private void crash(Vehicle v, Player d) {
        if (d != null) Txt.err(d, "CRASH !");
        damage(v, v.type.health() * 0.6, null);
        if (vehicles.containsKey(v.id)) { v.speed = 0; v.vy = 0; }
    }

    private void move(Vehicle v) {
        v.loc.setYaw(v.yaw);
        v.seat.teleport(v.seatLoc(), TeleportFlag.EntityState.RETAIN_PASSENGERS);
        v.body.teleport(v.modelLoc());
        v.hitbox.teleport(v.loc);
        if (v.rotor != null) v.rotor.teleport(v.rotorLoc());
        if (v.driver() != null && (Math.abs(v.speed) > 0.05 || v.vy != 0) && v.driver().getTicksLived() % 8 == 0) {
            v.loc.getWorld().spawnParticle(Particle.SMOKE, v.loc.clone().add(forward(v.yaw, 0).multiply(-v.type.hitboxWidth() / 2)).add(0, 1, 0), 2, 0.1, 0.1, 0.1, 0.01);
        }
    }

    private void hud(Player d, Vehicle v) {
        double kmh = Math.abs(v.speed) * 20 * 3.6;
        String alt = v.type.mode() == VehicleType.Mode.GROUND ? "" : " <dark_gray>| <gray>Alt. <white>" + v.loc.getBlockY();
        String extra = v.type.id().equals("tractor") ? " <dark_gray>| " + (v.plow ? "<green>Charrue" : "<gray>Charrue off") : "";
        Txt.bar(d, "<gray>" + (int) kmh + " km/h" + alt + " <dark_gray>| <yellow>⛽ " + Txt.progress(v.fuel / v.type.fuelCapacity(), 10, "yellow", "dark_gray")
                + " <dark_gray>| <red>❤ " + (int) v.health + "/" + (int) v.type.health() + extra);
    }

    private static Vector forward(float yaw, float pitch) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        return new Vector(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
    }

    private static float wrap(float a) {
        a %= 360;
        if (a > 180) a -= 360;
        if (a < -180) a += 360;
        return a;
    }

    // ------------------------------------------------------------ chunks & persistance
    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent e) {
        String key = Locs.chunkKey(e.getChunk());
        for (Vehicle v : vehicles.values()) if (v.spawned() && Locs.chunkKey(v.loc).equals(key)) v.despawn();
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        String key = Locs.chunkKey(e.getChunk());
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Vehicle v : vehicles.values()) if (!v.spawned() && v.loc.getWorld() == e.getWorld() && Locs.chunkKey(v.loc).equals(key)) v.spawn();
        });
    }

    public void load() {
        YamlConfiguration y = store.get();
        ConfigurationSection root = y.getConfigurationSection("vehicles");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            VehicleType t = types.get(s.getString("type"));
            Location l = Locs.loc(s.getString("loc"));
            if (t == null || l == null) continue;
            Vehicle v = new Vehicle(UUID.fromString(id), t, UUID.fromString(s.getString("owner")), l, s.getDouble("health"), s.getDouble("fuel"));
            vehicles.put(v.id, v);
            if (l.isChunkLoaded()) v.spawn();
        }
    }

    public void save() {
        YamlConfiguration y = store.fresh();
        for (Vehicle v : vehicles.values()) {
            String p = "vehicles." + v.id + ".";
            y.set(p + "type", v.type.id());
            y.set(p + "owner", v.owner.toString());
            Location l = v.loc.clone();
            l.setYaw(v.yaw);
            y.set(p + "loc", Locs.loc(l));
            y.set(p + "health", v.health);
            y.set(p + "fuel", v.fuel);
        }
        store.save();
    }

    public void shutdown() {
        save();
        vehicles.values().forEach(Vehicle::despawn);
    }
}

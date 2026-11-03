package fr.fnations.weapon;

import fr.fnations.FNations;
import fr.fnations.item.ItemRegistry;
import fr.fnations.nation.Nation;
import fr.fnations.util.Ballistic;
import fr.fnations.util.Explosions;
import fr.fnations.util.Inv;
import fr.fnations.util.Keys;
import fr.fnations.util.Txt;
import fr.fnations.vehicle.Vehicle;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/** Système d'armes à feu : tir hitscan / roquettes, chargeurs, rechargement, visée et headshots. */
public final class WeaponManager implements Listener {
    private final FNations plugin;
    private final Map<String, GunDef> guns = new HashMap<>();
    private final Map<UUID, Long> cooldown = new HashMap<>();
    private final Set<UUID> reloading = new HashSet<>();

    public WeaponManager(FNations plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        guns.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "weapons.yml"));
        ConfigurationSection root = y.getConfigurationSection("armes");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            guns.put(id, new GunDef(id, s.getString("munition"), s.getString("type", "balle"), s.getDouble("degats", 5),
                    s.getInt("cadence-ticks", 5), s.getInt("chargeur", 10), s.getInt("rechargement-ticks", 40),
                    s.getDouble("portee", 60), s.getDouble("dispersion", 0.02), s.getDouble("dispersion-visee", 0.005),
                    s.getInt("plombs", 1), s.getDouble("multiplicateur-tete", 1.5), (float) s.getDouble("explosion", 0),
                    s.getDouble("vitesse", 2.5), s.getString("son", "minecraft:entity.generic.explode"),
                    (float) s.getDouble("son-hauteur", 1.5), s.getInt("zoom", 0)));
        }
        plugin.getLogger().info(guns.size() + " armes chargées.");
    }

    public GunDef gun(ItemStack it) {
        String id = ItemRegistry.idOf(it);
        return id == null ? null : guns.get(id);
    }

    public int ammo(ItemStack it) {
        Integer v = it.getItemMeta().getPersistentDataContainer().get(Keys.AMMO, PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    private void setAmmo(ItemStack it, GunDef g, int n) {
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(Keys.AMMO, PersistentDataType.INTEGER, n);
        var def = ItemRegistry.def(g.item());
        meta.displayName(Txt.c(def.name() + " <gray>«" + (n == 0 ? "<red>" : "<white>") + n + "<gray>/" + g.magazine() + "»"));
        it.setItemMeta(meta);
    }

    private void showAmmo(Player p, GunDef g, int n) {
        var def = ItemRegistry.def(g.ammo());
        Txt.bar(p, "<gray>" + ItemRegistry.def(g.item()).name() + " <dark_gray>| " + (n == 0 ? "<red>" : "<yellow>") + n + "<gray>/" + g.magazine()
                + " <dark_gray>| <gray>Réserve : <white>" + Inv.count(p, "fnations:" + g.ammo()) + " <dark_gray>" + (def == null ? "" : Txt.plain(Txt.raw(def.name()))));
    }

    // ------------------------------------------------------------ événements
    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        GunDef g = gun(it);
        if (g == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (p.isInsideVehicle() && plugin.vehicles().driving(p) != null) return;
        fire(p, it, g);
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        ItemStack it = e.getPlayer().getInventory().getItemInMainHand();
        GunDef g = gun(it);
        if (g == null) return;
        e.setCancelled(true);
        reloadGun(e.getPlayer(), g);
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        p.removePotionEffect(PotionEffectType.SLOWNESS);
        ItemStack it = p.getInventory().getItem(e.getNewSlot());
        GunDef g = gun(it);
        if (g != null) showAmmo(p, g, ammo(it));
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        GunDef g = gun(p.getInventory().getItemInMainHand());
        if (g == null || g.zoom() <= 0) return;
        if (e.isSneaking()) p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, PotionEffect.INFINITE_DURATION, g.zoom() - 1, false, false, false));
        else p.removePotionEffect(PotionEffectType.SLOWNESS);
    }

    // ------------------------------------------------------------ tir
    private void fire(Player p, ItemStack it, GunDef g) {
        long now = System.currentTimeMillis();
        if (reloading.contains(p.getUniqueId())) return;
        if (now < cooldown.getOrDefault(p.getUniqueId(), 0L)) return;
        int ammo = ammo(it);
        if (ammo <= 0) {
            p.playSound(p.getLocation(), "minecraft:block.dispenser.fail", 1f, 1.6f);
            if (plugin.getConfig().getBoolean("armes.rechargement-auto", true)) reloadGun(p, g);
            else Txt.bar(p, "<red>Chargeur vide ! Appuyez sur F pour recharger.");
            return;
        }
        cooldown.put(p.getUniqueId(), now + g.fireDelay() * 50L);
        if (p.getGameMode() != org.bukkit.GameMode.CREATIVE || !plugin.getConfig().getBoolean("armes.munitions-infinies-creatif", true)) ammo--;
        setAmmo(it, g, ammo);
        p.getInventory().setItemInMainHand(it);
        showAmmo(p, g, ammo);
        Location eye = p.getEyeLocation();
        p.getWorld().playSound(eye, g.sound(), 1.5f, g.soundPitch());
        p.getWorld().spawnParticle(Particle.SMOKE, eye.clone().add(eye.getDirection().multiply(0.8)), 3, 0.02, 0.02, 0.02, 0.01);
        double spread = p.isSneaking() ? g.aimSpread() : g.spread();
        if (p.isSprinting()) spread *= 2;
        if ("roquette".equals(g.type())) {
            fireRocket(p, eye, jitter(eye.getDirection(), spread), g);
            return;
        }
        for (int i = 0; i < Math.max(1, g.pellets()); i++) hitscan(p, eye, jitter(eye.getDirection(), spread), g);
    }

    private static Vector jitter(Vector dir, double spread) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return dir.clone().add(new Vector(r.nextGaussian() * spread, r.nextGaussian() * spread, r.nextGaussian() * spread)).normalize();
    }

    /** Filtre de collision des tirs : êtres vivants et hitbox de véhicules, sauf le tireur et son véhicule. */
    public Predicate<Entity> targets(Player shooter) {
        Vehicle own = plugin.vehicles().driving(shooter);
        return e -> {
            if (e == shooter || e instanceof Display || e instanceof ArmorStand) return false;
            if (e instanceof Interaction) {
                Vehicle v = plugin.vehicles().byHitbox(e);
                return v != null && v != own;
            }
            if (own != null && e.getVehicle() != null && own.isPart(e.getVehicle())) return false;
            return e instanceof LivingEntity && !e.isDead();
        };
    }

    public void hitscan(Player p, Location eye, Vector dir, GunDef g) {
        RayTraceResult r = p.getWorld().rayTrace(eye, dir, g.range(), FluidCollisionMode.NEVER, true, 0.1, targets(p));
        double dist = r == null ? g.range() : r.getHitPosition().distance(eye.toVector());
        for (double d = 1.2; d < dist; d += 0.6) {
            p.getWorld().spawnParticle(Particle.CRIT, eye.clone().add(dir.clone().multiply(d)), 1, 0, 0, 0, 0);
        }
        if (r == null) return;
        Location hit = r.getHitPosition().toLocation(p.getWorld());
        if (r.getHitBlock() != null) {
            p.getWorld().spawnParticle(Particle.BLOCK, hit, 6, 0.05, 0.05, 0.05, r.getHitBlock().getBlockData());
            p.getWorld().playSound(hit, r.getHitBlock().getBlockSoundGroup().getHitSound(), 0.6f, 1.4f);
        }
        Entity e = r.getHitEntity();
        if (e == null) return;
        Vehicle v = plugin.vehicles().byHitbox(e);
        if (v != null) {
            plugin.vehicles().damage(v, g.damage() * plugin.getConfig().getDouble("armes.degats-vehicules", 0.3), p);
            p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, hit, 5, 0.1, 0.1, 0.1, 0.1);
            return;
        }
        if (!(e instanceof LivingEntity le)) return;
        double dmg = g.damage();
        boolean head = hit.getY() >= le.getEyeLocation().getY() - 0.25;
        if (head) {
            dmg *= g.headshot();
            p.playSound(p.getLocation(), "minecraft:entity.arrow.hit_player", 0.8f, 1.8f);
        }
        le.setNoDamageTicks(0);
        le.damage(dmg, p);
        le.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, hit, 2, 0.1, 0.1, 0.1, 0);
        if (head && le instanceof Player) Txt.bar(p, "<red><b>HEADSHOT !");
    }

    private void fireRocket(Player p, Location eye, Vector dir, GunDef g) {
        Nation n = plugin.nations().of(p);
        Location start = eye.clone().add(dir.clone().multiply(1.0));
        new Ballistic(plugin, start, dir.clone().multiply(g.speed()), 0.01, (int) (g.range() / g.speed()) + 5,
                ItemRegistry.create(g.ammo()), 0.8f, targets(p), Particle.FLAME, (loc, ent) -> {
            Vehicle v = ent == null ? null : plugin.vehicles().byHitbox(ent);
            if (v != null) plugin.vehicles().damage(v, g.damage(), p);
            if (ent instanceof LivingEntity le) le.damage(g.damage(), p);
            Explosions.explode(loc, g.explosion(), false, plugin.getConfig().getBoolean("armes.roquettes-cassent-blocs", true), n, p);
            plugin.vehicles().damageNearby(loc, g.explosion() + 1.5, g.damage() * 0.5, p);
        });
    }

    public void reloadGun(Player p, GunDef g) {
        ItemStack it = p.getInventory().getItemInMainHand();
        int current = ammo(it);
        if (current >= g.magazine() || reloading.contains(p.getUniqueId())) return;
        int available = Inv.count(p, "fnations:" + g.ammo());
        if (available <= 0 && p.getGameMode() != org.bukkit.GameMode.CREATIVE) { Txt.bar(p, "<red>Plus de munitions !"); return; }
        reloading.add(p.getUniqueId());
        int slot = p.getInventory().getHeldItemSlot();
        p.playSound(p.getLocation(), "minecraft:item.crossbow.loading_middle", 1f, 1.2f);
        final int[] t = {0};
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            t[0]++;
            ItemStack held = p.getInventory().getItemInMainHand();
            if (!p.isOnline() || p.getInventory().getHeldItemSlot() != slot || gun(held) != g) {
                reloading.remove(p.getUniqueId());
                Txt.bar(p, "<red>Rechargement annulé");
                task.cancel();
                return;
            }
            Txt.bar(p, "<yellow>Rechargement " + Txt.progress(t[0] / (double) g.reloadTicks(), 20, "yellow", "dark_gray"));
            if (t[0] < g.reloadTicks()) return;
            task.cancel();
            reloading.remove(p.getUniqueId());
            int need = g.magazine() - ammo(held);
            int got = p.getGameMode() == org.bukkit.GameMode.CREATIVE ? need : Math.min(need, Inv.count(p, "fnations:" + g.ammo()));
            if (p.getGameMode() != org.bukkit.GameMode.CREATIVE) Inv.take(p, "fnations:" + g.ammo(), got);
            held = p.getInventory().getItemInMainHand();
            if (gun(held) != g) return;
            setAmmo(held, g, Math.min(g.magazine(), ammo(held) + got));
            p.getInventory().setItemInMainHand(held);
            p.playSound(p.getLocation(), "minecraft:item.crossbow.loading_end", 1f, 1.4f);
            showAmmo(p, g, ammo(held));
        }, 1L, 1L);
    }
}

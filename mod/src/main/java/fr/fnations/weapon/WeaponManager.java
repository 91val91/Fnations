package fr.fnations.weapon;

import fr.fnations.registry.ModComponents;
import fr.fnations.server.FnServer;
import fr.fnations.server.Msg;
import fr.fnations.util.Inv;
import fr.fnations.util.Txt;
import fr.fnations.util.Yaml;
import fr.fnations.vehicle.VehicleEntity;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/** Armes à feu côté serveur : tir hitscan / roquettes, chargeurs, rechargement, headshots. */
public final class WeaponManager {
    private final FnServer plugin;
    private final Map<String, GunDef> guns = new HashMap<>();
    private final Map<UUID, Long> cooldown = new HashMap<>();
    private final Map<UUID, Reload> reloading = new HashMap<>();

    private static final class Reload {
        final int slot;
        final GunDef gun;
        int ticks;

        Reload(int slot, GunDef gun) {
            this.slot = slot;
            this.gun = gun;
        }
    }

    public WeaponManager(FnServer plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        guns.clear();
        Yaml root = plugin.yaml("weapons.yml").getSection("armes");
        if (root == null) return;
        for (String id : root.getKeys()) {
            Yaml s = root.getSection(id);
            guns.put(id, new GunDef(id, s.getString("munition"), s.getString("type", "balle"), s.getDouble("degats", 5),
                    s.getInt("cadence-ticks", 5), s.getInt("chargeur", 10), s.getInt("rechargement-ticks", 40),
                    s.getDouble("portee", 60), s.getDouble("dispersion", 0.02), s.getDouble("dispersion-visee", 0.005),
                    s.getInt("plombs", 1), s.getDouble("multiplicateur-tete", 1.5), (float) s.getDouble("explosion", 0),
                    s.getDouble("vitesse", 2.5), s.getString("son", "minecraft:entity.generic.explode"), (float) s.getDouble("son-hauteur", 1.5)));
        }
    }

    public GunDef gun(ItemStack s) {
        return s.getItem() instanceof GunItem g ? guns.get(g.gunId) : null;
    }

    private static SoundEvent sound(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? SoundEvents.GENERIC_EXPLODE.value() : SoundEvent.createVariableRangeEvent(rl);
    }

    private void showAmmo(ServerPlayer p, GunDef g, int n) {
        Msg.bar(p, "<gray><item:fnations:" + g.item() + "> <dark_gray>| " + (n == 0 ? "<red>" : "<yellow>") + n + "<gray>/" + g.magazine()
                + " <dark_gray>| <gray>Réserve : <white>" + Inv.count(p, "fnations:" + g.ammo()));
    }

    // ------------------------------------------------------------ tir
    public void fire(ServerPlayer p, boolean aiming, boolean held) {
        ItemStack it = p.getMainHandItem();
        GunDef g = gun(it);
        if (g == null || p.isSpectator() || p.isDeadOrDying()) return;
        if (p.getVehicle() instanceof VehicleEntity v && v.getControllingPassenger() == p) return;
        GunItem gi = (GunItem) it.getItem();
        if (held && !gi.automatic) return;
        if (reloading.containsKey(p.getUUID())) return;
        long now = p.serverLevel().getGameTime();
        if (now < cooldown.getOrDefault(p.getUUID(), 0L)) return;
        int ammo = GunItem.ammo(it);
        if (ammo <= 0) {
            p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 1f, 1.6f);
            if (plugin.config().getBoolean("armes.rechargement-auto", true)) reload(p);
            else Msg.bar(p, "<red>Chargeur vide ! Appuyez sur R pour recharger.");
            cooldown.put(p.getUUID(), now + 10);
            return;
        }
        cooldown.put(p.getUUID(), now + Math.max(1, g.fireDelay()));
        if (!p.isCreative() || !plugin.config().getBoolean("armes.munitions-infinies-creatif", true)) ammo--;
        it.set(ModComponents.AMMO, ammo);
        it.set(ModComponents.MAGAZINE, g.magazine());
        showAmmo(p, g, ammo);
        ServerLevel level = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        level.playSound(null, eye.x, eye.y, eye.z, sound(g.sound()), SoundSource.PLAYERS, 1.5f, g.soundPitch());
        Vec3 look = p.getLookAngle();
        level.sendParticles(ParticleTypes.SMOKE, eye.x + look.x * 0.8, eye.y + look.y * 0.8 - 0.1, eye.z + look.z * 0.8, 3, 0.02, 0.02, 0.02, 0.01);
        double spread = aiming ? g.aimSpread() : g.spread();
        if (p.isSprinting()) spread *= 2;
        if ("roquette".equals(g.type())) {
            Vec3 dir = jitter(look, spread);
            ShellEntity shell = new ShellEntity(level, p, new ItemStack(Inv.item("fnations:" + g.ammo())), (float) g.damage(), g.explosion(),
                    plugin.config().getBoolean("armes.roquettes-cassent-blocs", true), 0.01);
            shell.setPos(eye.add(dir));
            shell.shoot(dir.x, dir.y, dir.z, (float) g.speed(), 0f);
            level.addFreshEntity(shell);
            return;
        }
        for (int i = 0; i < Math.max(1, g.pellets()); i++) hitscan(p, eye, jitter(look, spread), g.damage(), g.range(), g.headshot());
    }

    private static Vec3 jitter(Vec3 dir, double spread) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return dir.add(r.nextGaussian() * spread, r.nextGaussian() * spread, r.nextGaussian() * spread).normalize();
    }

    /** Filtre des cibles : tout ce qui est "cliquable" sauf le tireur et son véhicule. */
    public static Predicate<Entity> targets(ServerPlayer shooter) {
        Entity own = shooter.getVehicle();
        return e -> e != shooter && e != own && !e.isSpectator() && e.isPickable() && !(own != null && e.getVehicle() == own);
    }

    /** Tir instantané (balles, mitrailleuses de véhicules). */
    public void hitscan(ServerPlayer p, Vec3 from, Vec3 dir, double damage, double range, double headshotMult) {
        ServerLevel level = p.serverLevel();
        Vec3 to = from.add(dir.scale(range));
        BlockHitResult bh = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (bh.getType() != HitResult.Type.MISS) to = bh.getLocation();
        AABB box = new AABB(from, to).inflate(1.0);
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(level, p, from, to, box, targets(p), 0.1f);
        Vec3 end = eh != null ? eh.getLocation() : to;
        double dist = end.distanceTo(from);
        for (double d = 1.2; d < dist; d += 0.8) {
            Vec3 pt = from.add(dir.scale(d));
            level.sendParticles(ParticleTypes.CRIT, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
        }
        if (eh == null) {
            if (bh.getType() == HitResult.Type.BLOCK) {
                var state = level.getBlockState(bh.getBlockPos());
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), end.x, end.y, end.z, 6, 0.05, 0.05, 0.05, 0.1);
                level.playSound(null, bh.getBlockPos(), state.getSoundType().getHitSound(), SoundSource.BLOCKS, 0.6f, 1.4f);
            }
            return;
        }
        Entity e = eh.getEntity();
        if (e instanceof VehicleEntity v) {
            v.damageFrom(p, (float) (damage * plugin.config().getDouble("armes.degats-vehicules", 0.3)));
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z, 5, 0.1, 0.1, 0.1, 0.1);
            return;
        }
        double dmg = damage;
        boolean head = e instanceof LivingEntity le && end.y >= le.getEyeY() - 0.25;
        if (head) {
            dmg *= headshotMult;
            Msg.sound(p, SoundEvents.ARROW_HIT_PLAYER, 0.8f, 1.8f);
        }
        e.invulnerableTime = 0;
        e.hurtServer(level, p.damageSources().playerAttack(p), (float) dmg);
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, end.x, end.y, end.z, 2, 0.1, 0.1, 0.1, 0);
        if (head && e instanceof ServerPlayer) Msg.bar(p, "<red><b>HEADSHOT !");
    }

    // ------------------------------------------------------------ rechargement
    public void reload(ServerPlayer p) {
        ItemStack it = p.getMainHandItem();
        GunDef g = gun(it);
        if (g == null || reloading.containsKey(p.getUUID())) return;
        if (GunItem.ammo(it) >= g.magazine()) return;
        if (Inv.count(p, "fnations:" + g.ammo()) <= 0 && !p.isCreative()) { Msg.bar(p, "<red>Plus de munitions !"); return; }
        reloading.put(p.getUUID(), new Reload(p.getInventory().selected, g));
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.CROSSBOW_LOADING_MIDDLE.value(), SoundSource.PLAYERS, 1f, 1.2f);
    }

    public void tick() {
        Iterator<Map.Entry<UUID, Reload>> it = reloading.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            Reload r = e.getValue();
            ServerPlayer p = plugin.player(e.getKey());
            ItemStack held = p == null ? ItemStack.EMPTY : p.getMainHandItem();
            if (p == null || p.getInventory().selected != r.slot || gun(held) != r.gun) {
                if (p != null) Msg.bar(p, "<red>Rechargement annulé");
                it.remove();
                continue;
            }
            r.ticks++;
            if (r.ticks % 2 == 0) Msg.bar(p, "<yellow>Rechargement " + Txt.progress(r.ticks / (double) r.gun.reloadTicks(), 20, "yellow", "dark_gray"));
            if (r.ticks < r.gun.reloadTicks()) continue;
            it.remove();
            int need = r.gun.magazine() - GunItem.ammo(held);
            int got = p.isCreative() ? need : Math.min(need, Inv.count(p, "fnations:" + r.gun.ammo()));
            if (!p.isCreative()) Inv.take(p, "fnations:" + r.gun.ammo(), got);
            held.set(ModComponents.AMMO, Math.min(r.gun.magazine(), GunItem.ammo(held) + got));
            held.set(ModComponents.MAGAZINE, r.gun.magazine());
            p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.CROSSBOW_LOADING_END.value(), SoundSource.PLAYERS, 1f, 1.4f);
            showAmmo(p, r.gun, GunItem.ammo(held));
        }
    }

    public void onQuit(ServerPlayer p) {
        reloading.remove(p.getUUID());
        cooldown.remove(p.getUUID());
    }
}

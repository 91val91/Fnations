package fr.fnations.missile;

import fr.fnations.machine.MachineBlockEntity;
import fr.fnations.nation.Nation;
import fr.fnations.nation.Territory;
import fr.fnations.registry.ModEntities;
import fr.fnations.registry.ModItems;
import fr.fnations.server.FnServer;
import fr.fnations.server.Msg;
import fr.fnations.util.Explosions;
import fr.fnations.util.Locs;
import fr.fnations.util.Txt;
import fr.fnations.util.Yaml;
import fr.fnations.vehicle.VehicleEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Missiles : lancement depuis le lanceur, vol balistique, impacts et radiations. */
public final class MissileManager {
    private final FnServer plugin;
    private final Map<String, MissileType> types = new HashMap<>();
    private final List<Flight> flights = new ArrayList<>();
    private final List<Countdown> countdowns = new ArrayList<>();
    private final List<Radiation> zones = new ArrayList<>();
    private final List<Runnable> delayed = new ArrayList<>();
    private final Map<Runnable, Integer> delays = new HashMap<>();

    private record Radiation(ServerLevel level, Vec3 center, double radius, long until) {}

    private static final class Countdown {
        final ServerPlayer shooter;
        final Nation attacker;
        final MissileType type;
        final ServerLevel level;
        final Vec3 start;
        final double tx, tz;
        int left;

        Countdown(ServerPlayer shooter, Nation attacker, MissileType type, ServerLevel level, Vec3 start, double tx, double tz, int seconds) {
            this.shooter = shooter;
            this.attacker = attacker;
            this.type = type;
            this.level = level;
            this.start = start;
            this.tx = tx;
            this.tz = tz;
            this.left = seconds * 20;
        }
    }

    private final class Flight {
        final ServerPlayer shooter;
        final Nation attacker;
        final MissileType t;
        final ServerLevel level;
        final Vec3 start;
        final double tx, tz;
        final int duration;
        final double apex;
        MissileEntity display;
        int tick;
        Vec3 last;

        Flight(Countdown c) {
            this.shooter = c.shooter;
            this.attacker = c.attacker;
            this.t = c.type;
            this.level = c.level;
            this.start = c.start;
            this.tx = c.tx;
            this.tz = c.tz;
            double dist = Math.hypot(tx - start.x, tz - start.z);
            this.duration = (int) Math.max(40, dist / t.speed());
            this.apex = Math.min(plugin.config().getDouble("missiles.altitude-max", 260), Math.max(25, dist * t.arc()));
            this.last = start;
            MissileEntity m = new MissileEntity(ModEntities.MISSILE, level);
            m.setItem(ModItems.stack(t.item()));
            m.setPos(start);
            level.addFreshEntity(m);
            display = m;
            level.playSound(null, start.x, start.y, start.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.HOSTILE, 3f, 0.4f);
            level.playSound(null, start.x, start.y, start.z, SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 3f, 0.5f);
        }

        Vec3 at(double u) {
            return new Vec3(start.x + (tx - start.x) * u, start.y + apex * 4 * u * (1 - u), start.z + (tz - start.z) * u);
        }

        /** @return true quand le vol est terminé. */
        boolean step() {
            tick++;
            double u = Math.min(1.0, tick / (double) duration);
            Vec3 pos = at(u);
            Vec3 vel = pos.subtract(last);
            boolean loaded = level.hasChunkAt(BlockPos.containing(pos));
            if (display != null && !display.isRemoved()) {
                if (loaded) {
                    display.setPos(pos);
                    if (vel.lengthSqr() > 1e-6) {
                        display.setYRot((float) (Math.atan2(-vel.x, vel.z) * (180 / Math.PI)));
                        display.setXRot((float) (-Math.atan2(vel.y, vel.horizontalDistance()) * (180 / Math.PI)));
                    }
                } else {
                    display.discard();
                    display = null;
                }
            }
            if (loaded && u > 0.5 && !level.getBlockState(BlockPos.containing(pos)).getCollisionShape(level, BlockPos.containing(pos)).isEmpty()) {
                impact(pos);
                return true;
            }
            last = pos;
            if (u >= 1.0) {
                impact(null);
                return true;
            }
            return false;
        }

        void impact(Vec3 direct) {
            if (display != null) display.discard();
            int x = (int) Math.floor(tx), z = (int) Math.floor(tz);
            level.getChunk(x >> 4, z >> 4); // charge le chunk cible
            Vec3 hit = direct != null ? direct : new Vec3(tx, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), tz);
            detonate(shooter, attacker, t, level, hit);
        }
    }

    public MissileManager(FnServer plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        types.clear();
        Yaml s = plugin.config().getSection("missiles.types");
        if (s != null) for (String id : s.getKeys()) types.put(id, MissileType.load(id, s.getSection(id)));
    }

    public static boolean isMissile(ItemStack s) {
        String id = ModItems.idOf(s);
        return id != null && id.startsWith("missile_") && !id.equals("missile_launcher");
    }

    public MissileType type(ItemStack it) { return types.get(ModItems.idOf(it)); }

    // ------------------------------------------------------------ lancement
    public void launch(ServerPlayer p, MachineBlockEntity m) {
        ItemStack missile = m.getItem(MachineBlockEntity.SLOT_A);
        MissileType t = type(missile);
        if (t == null) { Msg.err(p, "Aucun missile sur la rampe."); return; }
        if (m.targetX() == null) { Msg.err(p, "Aucune cible définie."); return; }
        BlockPos bp = m.getBlockPos();
        double dist = Math.hypot(m.targetX() - bp.getX(), m.targetZ() - bp.getZ());
        if (dist > t.range()) { Msg.err(p, "Cible hors de portée (" + (int) dist + " > " + (int) t.range() + ")."); return; }
        if (dist < t.minRange()) { Msg.err(p, "Cible trop proche (minimum " + (int) t.minRange() + ")."); return; }
        if (m.energy() < t.energy() && !p.isCreative()) { Msg.err(p, "Énergie insuffisante (" + (int) m.energy() + "/" + (int) t.energy() + " E) : alimentez le lanceur avec des générateurs."); return; }
        ServerLevel level = (ServerLevel) m.getLevel();
        Nation mine = plugin.nations().of(p);
        Nation victim = plugin.nations().atChunk(Locs.chunkKey(Locs.dim(level), m.targetX() >> 4, m.targetZ() >> 4));
        if (victim != null && victim != mine && !plugin.wars().activeBetween(mine, victim) && !Territory.bypass(p)) {
            Msg.err(p, "Vous n'êtes pas en guerre active contre " + victim.colored() + "<red>. Tir refusé."); return;
        }
        if (victim == null && !plugin.config().getBoolean("missiles.autoriser-zones-sauvages", true)) { Msg.err(p, "Tir hors territoire ennemi interdit."); return; }
        if (victim != null && victim == mine) { Msg.err(p, "Vous visez votre propre territoire !"); return; }
        m.useEnergy(t.energy());
        missile.shrink(1);
        m.setChanged();
        p.closeContainer();
        Vec3 start = Vec3.atBottomCenterOf(bp).add(0, 1.5, 0);
        int count = plugin.config().getInt("missiles.compte-a-rebours", 5);
        if (t.item().equals("missile_nuclear")) {
            Msg.broadcast(plugin.server(), "<dark_red><b>☢ ALERTE NUCLÉAIRE !</b> <red>Un missile nucléaire a été lancé par "
                    + (mine == null ? Txt.esc(p.getScoreboardName()) : mine.colored()) + "<red> !");
        }
        if (victim != null) for (ServerPlayer pl : plugin.nations().online(victim)) {
            Msg.title(pl, "<dark_red><b>⚠ MISSILE ENTRANT", "<red>Impact estimé vers X " + m.targetX() + " Z " + m.targetZ(), 0, 60, 10);
            Msg.sound(pl, SoundEvents.BELL_RESONATE, 1f, 0.5f);
        }
        plugin.advancements().grant(p, "missile_launched");
        countdowns.add(new Countdown(p, mine, t, level, start, m.targetX() + 0.5, m.targetZ() + 0.5, count));
    }

    /** Chaque tick serveur. */
    public void tick() {
        Iterator<Countdown> ci = countdowns.iterator();
        while (ci.hasNext()) {
            Countdown c = ci.next();
            if (c.left % 20 == 0 && c.left > 0) {
                int s = c.left / 20;
                for (ServerPlayer pl : c.level.players()) if (pl.distanceToSqr(c.start) < 2500) Msg.title(pl, "<red><b>" + s, "<gray>Lancement du missile", 0, 18, 0);
                c.level.playSound(null, c.start.x, c.start.y, c.start.z, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 1.5f, 0.5f + (5 - s) * 0.1f);
                c.level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, c.start.x, c.start.y, c.start.z, 8, 0.3, 0.1, 0.3, 0.02);
            }
            if (--c.left <= 0) {
                ci.remove();
                flights.add(new Flight(c));
            }
        }
        flights.removeIf(Flight::step);
        // explosions secondaires différées
        Iterator<Map.Entry<Runnable, Integer>> di = delays.entrySet().iterator();
        while (di.hasNext()) {
            var e = di.next();
            int left = e.getValue() - 1;
            if (left <= 0) { di.remove(); e.getKey().run(); }
            else e.setValue(left);
        }
        if (plugin.server().getTickCount() % 20 == 0) radiationTick();
    }

    private void later(int ticks, Runnable r) { delays.put(r, ticks); }

    private void detonate(ServerPlayer shooter, Nation attacker, MissileType t, ServerLevel level, Vec3 hit) {
        boolean breakBlocks = plugin.config().getBoolean("missiles.casser-blocs", true);
        Nation victim = plugin.nations().at(level, BlockPos.containing(hit));
        Explosions.explode(level, hit.x, hit.y, hit.z, t.power(), t.radiationRadius() > 0, breakBlocks, attacker, shooter);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < t.extraExplosions(); i++) {
            double a = r.nextDouble(Math.PI * 2), d = r.nextDouble(Math.max(1, t.blastRadius()));
            int x = (int) Math.floor(hit.x + Math.cos(a) * d), z = (int) Math.floor(hit.z + Math.sin(a) * d);
            later(2 + i * 2, () -> {
                double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                Explosions.explode(level, x + 0.5, y, z + 0.5, t.power() * 0.75f, true, breakBlocks, attacker, shooter);
            });
        }
        VehicleEntity.damageNearby(level, hit, t.power() * 2 + t.blastRadius(), t.power() * 30, shooter);
        if (t.radiationRadius() > 0) {
            zones.add(new Radiation(level, hit, t.radiationRadius(), System.currentTimeMillis() + t.radiationSeconds() * 1000L));
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, hit.x, hit.y, hit.z, 20, 6, 6, 6, 0);
            for (ServerPlayer pl : level.players()) {
                if (pl.distanceToSqr(hit) < 300 * 300) {
                    pl.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
                    Msg.sound(pl, SoundEvents.GENERIC_EXPLODE, 4f, 0.3f);
                    Msg.sound(pl, SoundEvents.WITHER_DEATH, 2f, 0.5f);
                }
            }
            Msg.broadcast(plugin.server(), "<dark_green><b>☢ Détonation nucléaire</b> <green>en X " + (int) hit.x + " Z " + (int) hit.z
                    + " <gray>— zone irradiée pendant " + Txt.duration(t.radiationSeconds()));
        }
        if (attacker != null && victim != null && victim != attacker) {
            plugin.wars().reward(attacker, victim, plugin.config().getInt("guerres.points-missile", 10) * (t.radiationRadius() > 0 ? 3 : 1), "frappe de missile");
        }
        if (shooter != null && !shooter.hasDisconnected()) Msg.send(shooter, "<gold>💥 Impact confirmé en X " + (int) hit.x + " Z " + (int) hit.z + ".");
    }

    private void radiationTick() {
        long now = System.currentTimeMillis();
        zones.removeIf(z -> z.until() < now);
        for (Radiation z : zones) {
            for (ServerPlayer p : z.level().players()) {
                if (p.distanceToSqr(z.center()) > z.radius() * z.radius()) continue;
                p.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 1));
                p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0));
                p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 1));
                Msg.bar(p, "<dark_green><b>☢ ZONE IRRADIÉE ☢");
            }
            if (z.level().hasChunkAt(BlockPos.containing(z.center())))
                z.level().sendParticles(ParticleTypes.HAPPY_VILLAGER, z.center().x, z.center().y, z.center().z, 30, z.radius() / 2, 5, z.radius() / 2, 0);
        }
    }

    @SuppressWarnings("unused")
    private List<Runnable> unused() { return delayed; }
}

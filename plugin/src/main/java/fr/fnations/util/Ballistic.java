package fr.fnations.util;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.function.BiConsumer;
import java.util.function.Predicate;

/** Projectile simulé (roquettes, obus) avec modèle 3D (ItemDisplay), gravité et détection de collision. */
public final class Ballistic extends BukkitRunnable {
    private Location pos;
    private final Vector vel;
    private final double gravity;
    private int life;
    private final ItemDisplay display;
    private final Predicate<Entity> filter;
    private final BiConsumer<Location, Entity> onHit;
    private final Particle trail;

    public Ballistic(Plugin plugin, Location start, Vector velocity, double gravity, int lifeTicks, ItemStack model, float scale,
                     Predicate<Entity> filter, Particle trail, BiConsumer<Location, Entity> onHit) {
        this.pos = start.clone();
        this.vel = velocity.clone();
        this.gravity = gravity;
        this.life = lifeTicks;
        this.filter = filter;
        this.onHit = onHit;
        this.trail = trail;
        if (model != null) {
            display = start.getWorld().spawn(start, ItemDisplay.class, d -> {
                d.setItemStack(model);
                d.setPersistent(false);
                d.setTeleportDuration(1);
                d.setBillboard(Display.Billboard.FIXED);
                d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f()));
            });
            orient();
        } else display = null;
        runTaskTimer(plugin, 1L, 1L);
    }

    private void orient() {
        if (display == null || vel.lengthSquared() < 1e-6) return;
        Location l = pos.clone();
        l.setDirection(vel);
        display.setRotation(l.getYaw(), l.getPitch());
    }

    @Override
    public void run() {
        if (life-- <= 0 || pos.getY() < pos.getWorld().getMinHeight()) {
            finish(pos, null);
            return;
        }
        double len = vel.length();
        if (len > 1e-6) {
            RayTraceResult r = pos.getWorld().rayTrace(pos, vel.clone().normalize(), len, FluidCollisionMode.NEVER, true, 0.25, filter);
            if (r != null) {
                finish(r.getHitPosition().toLocation(pos.getWorld()), r.getHitEntity());
                return;
            }
        }
        pos.add(vel);
        vel.setY(vel.getY() - gravity);
        if (display != null && display.isValid()) {
            display.teleport(pos);
            orient();
        }
        if (trail != null) pos.getWorld().spawnParticle(trail, pos, 2, 0.05, 0.05, 0.05, 0.01);
    }

    private void finish(Location at, Entity hit) {
        cancel();
        if (display != null) display.remove();
        onHit.accept(at, hit);
    }
}

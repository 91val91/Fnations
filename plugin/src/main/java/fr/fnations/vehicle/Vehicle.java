package fr.fnations.vehicle;

import fr.fnations.item.ItemRegistry;
import fr.fnations.util.Keys;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.UUID;

/** Un véhicule dans le monde : siège (ArmorStand), modèle (ItemDisplay), hitbox (Interaction). */
public final class Vehicle {
    final UUID id;
    final VehicleType type;
    final UUID owner;
    Location loc;
    float yaw, pitch;
    double speed, vy, strafe;
    double health, fuel;
    boolean plow;
    long lastShot;
    float rotorAngle;

    ArmorStand seat;
    ItemDisplay body, rotor;
    Interaction hitbox;

    Vehicle(UUID id, VehicleType type, UUID owner, Location loc, double health, double fuel) {
        this.id = id;
        this.type = type;
        this.owner = owner;
        this.loc = loc.clone();
        this.yaw = loc.getYaw();
        this.health = health;
        this.fuel = fuel;
    }

    public VehicleType type() { return type; }
    public UUID owner() { return owner; }
    public double health() { return health; }
    public double fuel() { return fuel; }
    public Location location() { return loc.clone(); }

    boolean spawned() { return seat != null && seat.isValid(); }

    public boolean isPart(Entity e) { return e == seat || e == body || e == hitbox || e == rotor; }

    public Player driver() {
        if (!spawned()) return null;
        for (Entity e : seat.getPassengers()) if (e instanceof Player p) return p;
        return null;
    }

    public int passengers() { return spawned() ? seat.getPassengers().size() : 0; }

    void spawn() {
        if (spawned()) return;
        var w = loc.getWorld();
        seat = w.spawn(seatLoc(), ArmorStand.class, a -> {
            a.setInvisible(true);
            a.setMarker(true);
            a.setGravity(false);
            a.setInvulnerable(true);
            a.setPersistent(false);
            a.setSilent(true);
            tag(a);
        });
        body = w.spawn(modelLoc(), ItemDisplay.class, d -> {
            d.setItemStack(ItemRegistry.create(type.id()));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBillboard(Display.Billboard.FIXED);
            d.setPersistent(false);
            d.setTeleportDuration(2);
            d.setViewRange(4f);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(type.scale(), type.scale(), type.scale()), new AxisAngle4f()));
            tag(d);
        });
        if (type.mode() == VehicleType.Mode.HELICOPTER) {
            rotor = w.spawn(rotorLoc(), ItemDisplay.class, d -> {
                ItemStack it = new ItemStack(Material.PAPER);
                ItemMeta m = it.getItemMeta();
                m.setItemModel(new NamespacedKey("fnations", "helicopter_rotor"));
                it.setItemMeta(m);
                d.setItemStack(it);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setPersistent(false);
                d.setTeleportDuration(1);
                d.setViewRange(4f);
                d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(type.scale(), type.scale(), type.scale()), new AxisAngle4f()));
                tag(d);
            });
        }
        hitbox = w.spawn(loc, Interaction.class, i -> {
            i.setInteractionWidth(type.hitboxWidth());
            i.setInteractionHeight(type.hitboxHeight());
            i.setResponsive(true);
            i.setPersistent(false);
            tag(i);
        });
    }

    private void tag(Entity e) { e.getPersistentDataContainer().set(Keys.VEHICLE, PersistentDataType.STRING, id.toString()); }

    void despawn() {
        if (seat != null) { seat.eject(); seat.remove(); }
        if (body != null) body.remove();
        if (rotor != null) rotor.remove();
        if (hitbox != null) hitbox.remove();
        seat = null; body = null; rotor = null; hitbox = null;
    }

    Location seatLoc() { Location l = loc.clone().add(0, type.seatY(), 0); l.setYaw(yaw); return l; }

    Location modelLoc() {
        Location l = loc.clone().add(0, type.modelY(), 0);
        l.setYaw(yaw + type.yawOffset());
        float sign = Math.abs(((type.yawOffset() % 360) + 360) % 360 - 180) < 90 ? -1f : 1f;
        l.setPitch(sign * (type.mode() == VehicleType.Mode.PLANE ? pitch : type.mode() == VehicleType.Mode.HELICOPTER ? (float) (speed * 25) : 0));
        return l;
    }

    Location rotorLoc() {
        Location l = loc.clone().add(0, type.modelY(), 0);
        l.setYaw(rotorAngle);
        l.setPitch(0);
        return l;
    }
}

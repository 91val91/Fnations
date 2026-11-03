package fr.fnations.machine;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.ItemDisplay;

import java.util.UUID;

/** Une machine posée dans le monde. */
public final class Machine {
    public static final double OVERHEAT = 100, MELTDOWN = 150;

    final String key;
    final MachineType type;
    final UUID owner;
    final Block block;
    double energy, heat;
    boolean enabled = true;
    boolean overheated;
    int progress;
    int burn;
    /** Interrupteur : mode automatique (coupe à 90°C) */
    boolean auto = true;
    /** Missiles : coordonnées de la cible */
    Integer targetX, targetZ;
    MachineGui gui;
    ItemDisplay display;
    String status = "";

    Machine(String key, MachineType type, UUID owner, Block block) {
        this.key = key;
        this.type = type;
        this.owner = owner;
        this.block = block;
    }

    public MachineType type() { return type; }
    public UUID owner() { return owner; }
    public Block block() { return block; }
    public Location center() { return block.getLocation().add(0.5, 0.5, 0.5); }
    public double energy() { return energy; }
    public void useEnergy(double e) { energy = Math.max(0, energy - e); }
    public double heat() { return heat; }
    public boolean enabled() { return enabled; }
    public Integer targetX() { return targetX; }
    public Integer targetZ() { return targetZ; }
    public void setTarget(Integer x, Integer z) { targetX = x; targetZ = z; }
    public MachineGui gui() { return gui; }
}

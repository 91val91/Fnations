package fr.fnations.room;

import org.bukkit.Location;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Une chambre (région cuboïde définie avec WorldEdit) à louer ou acheter. */
public final class Room {
    public enum Mode { FREE, RENTED, OWNED }

    private final String id;
    private final String world;
    private final int minX, minY, minZ, maxX, maxY, maxZ;
    private String group;
    private double buyPrice, rentPrice;
    private UUID owner;
    private Mode mode = Mode.FREE;
    private long expiry;
    private final Set<UUID> members = new HashSet<>();
    private Location spawn;

    public Room(String id, String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.id = id;
        this.world = world;
        this.minX = minX; this.minY = minY; this.minZ = minZ;
        this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
    }

    public boolean contains(Location l) {
        return l.getWorld() != null && l.getWorld().getName().equals(world)
                && l.getBlockX() >= minX && l.getBlockX() <= maxX
                && l.getBlockY() >= minY && l.getBlockY() <= maxY
                && l.getBlockZ() >= minZ && l.getBlockZ() <= maxZ;
    }

    public boolean intersectsChunk(String w, int cx, int cz) {
        if (!world.equals(w)) return false;
        int x0 = cx << 4, z0 = cz << 4;
        return maxX >= x0 && minX <= x0 + 15 && maxZ >= z0 && minZ <= z0 + 15;
    }

    public boolean canUse(UUID u) { return u.equals(owner) || members.contains(u); }

    public int volume() { return (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1); }

    public String size() { return (maxX - minX + 1) + "×" + (maxY - minY + 1) + "×" + (maxZ - minZ + 1); }

    public void free() {
        owner = null;
        mode = Mode.FREE;
        expiry = 0;
        members.clear();
    }

    public String id() { return id; }
    public String world() { return world; }
    public int minX() { return minX; } public int minY() { return minY; } public int minZ() { return minZ; }
    public int maxX() { return maxX; } public int maxY() { return maxY; } public int maxZ() { return maxZ; }
    public String group() { return group; }
    public void setGroup(String g) { group = g; }
    public double buyPrice() { return buyPrice; }
    public double rentPrice() { return rentPrice; }
    public void setPrices(double buy, double rent) { buyPrice = buy; rentPrice = rent; }
    public UUID owner() { return owner; }
    public void setOwner(UUID u) { owner = u; }
    public Mode mode() { return mode; }
    public void setMode(Mode m) { mode = m; }
    public long expiry() { return expiry; }
    public void setExpiry(long e) { expiry = e; }
    public Set<UUID> members() { return members; }
    public Location spawn() { return spawn; }
    public void setSpawn(Location l) { spawn = l; }
}

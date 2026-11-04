package fr.fnations.room;

import fr.fnations.util.Locs;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Une chambre (région cuboïde) à louer ou acheter. */
public final class Room {
    public enum Mode { FREE, RENTED, OWNED }

    private final String id;
    private final String dim;
    private final int minX, minY, minZ, maxX, maxY, maxZ;
    private String group;
    private double buyPrice, rentPrice;
    private UUID owner;
    private Mode mode = Mode.FREE;
    private long expiry;
    private final Set<UUID> members = new HashSet<>();
    private Locs.Spot spawn;

    public Room(String id, String dim, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.id = id;
        this.dim = dim;
        this.minX = minX; this.minY = minY; this.minZ = minZ;
        this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
    }

    public boolean contains(Level l, BlockPos p) {
        return Locs.dim(l).equals(dim) && p.getX() >= minX && p.getX() <= maxX && p.getY() >= minY && p.getY() <= maxY && p.getZ() >= minZ && p.getZ() <= maxZ;
    }

    public boolean intersectsChunk(String d, int cx, int cz) {
        if (!dim.equals(d)) return false;
        int x0 = cx << 4, z0 = cz << 4;
        return maxX >= x0 && minX <= x0 + 15 && maxZ >= z0 && minZ <= z0 + 15;
    }

    public boolean canUse(UUID u) { return u.equals(owner) || members.contains(u); }

    public int volume() { return (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1); }

    public String size() { return (maxX - minX + 1) + "×" + (maxY - minY + 1) + "×" + (maxZ - minZ + 1); }

    public BlockPos min() { return new BlockPos(minX, minY, minZ); }

    public BlockPos max() { return new BlockPos(maxX, maxY, maxZ); }

    public void free() {
        owner = null;
        mode = Mode.FREE;
        expiry = 0;
        members.clear();
    }

    public String id() { return id; }
    public String dim() { return dim; }
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
    public Locs.Spot spawn() { return spawn; }
    public void setSpawn(Locs.Spot s) { spawn = s; }
}

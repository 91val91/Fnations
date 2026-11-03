package fr.fnations.util;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

/** Sérialisation compacte des positions. */
public final class Locs {
    private Locs() {}

    public static String chunkKey(String world, int x, int z) { return world + ";" + x + ";" + z; }

    public static String chunkKey(Chunk c) { return chunkKey(c.getWorld().getName(), c.getX(), c.getZ()); }

    public static String chunkKey(Location l) { return chunkKey(l.getWorld().getName(), l.getBlockX() >> 4, l.getBlockZ() >> 4); }

    public static String blockKey(Block b) { return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ(); }

    public static Block block(String key) {
        String[] p = key.split(";");
        World w = Bukkit.getWorld(p[0]);
        if (w == null) return null;
        return w.getBlockAt(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
    }

    public static String loc(Location l) {
        return l.getWorld().getName() + ";" + l.getX() + ";" + l.getY() + ";" + l.getZ() + ";" + l.getYaw() + ";" + l.getPitch();
    }

    public static Location loc(String s) {
        if (s == null) return null;
        String[] p = s.split(";");
        World w = Bukkit.getWorld(p[0]);
        if (w == null) return null;
        return new Location(w, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                p.length > 4 ? Float.parseFloat(p[4]) : 0, p.length > 5 ? Float.parseFloat(p[5]) : 0);
    }
}

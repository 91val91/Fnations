package fr.fnations.util;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Clés de chunks et sérialisation de positions (dimension;x;y;z;yaw;pitch). */
public final class Locs {
    private Locs() {}

    public static String dim(Level l) { return l.dimension().location().toString(); }

    public static String chunkKey(String dim, int cx, int cz) { return dim + ";" + cx + ";" + cz; }

    public static String chunkKey(Level l, BlockPos p) { return chunkKey(dim(l), p.getX() >> 4, p.getZ() >> 4); }

    public static String chunkKey(Level l, Vec3 v) { return chunkKey(dim(l), ((int) Math.floor(v.x)) >> 4, ((int) Math.floor(v.z)) >> 4); }

    public static ServerLevel level(MinecraftServer server, String dim) {
        ResourceLocation id = ResourceLocation.tryParse(dim);
        return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    /** Position avec orientation. */
    public record Spot(String dim, double x, double y, double z, float yaw, float pitch) {
        public Vec3 vec() { return new Vec3(x, y, z); }

        public BlockPos block() { return BlockPos.containing(x, y, z); }

        public String serialize() { return dim + ";" + x + ";" + y + ";" + z + ";" + yaw + ";" + pitch; }

        public static Spot parse(String s) {
            if (s == null) return null;
            String[] p = s.split(";");
            if (p.length < 4) return null;
            try {
                return new Spot(p[0], Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                        p.length > 4 ? Float.parseFloat(p[4]) : 0, p.length > 5 ? Float.parseFloat(p[5]) : 0);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        public static Spot of(Level l, Vec3 v, float yaw, float pitch) { return new Spot(Locs.dim(l), v.x, v.y, v.z, yaw, pitch); }
    }
}

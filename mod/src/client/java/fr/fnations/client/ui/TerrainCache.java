package fr.fnations.client.ui;

import com.mojang.blaze3d.platform.NativeImage;
import fr.fnations.FNations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Cartes du relief vues du dessus (couleurs des cartes vanilla), générées à partir des chunks chargés du client. */
final class TerrainCache {
    record Entry(ResourceLocation id, int w, int h, long time) {}

    private static final Map<String, Entry> CACHE = new LinkedHashMap<>();
    private static int counter;

    private TerrainCache() {}

    /** Zone carrée centrée sur (x, z). */
    static Entry around(int x, int z, int radius) {
        int step = 4; // recentrage par pas de 4 blocs pour limiter les régénérations
        return area(Math.floorDiv(x, step) * step - radius, Math.floorDiv(z, step) * step - radius, radius * 2, radius * 2);
    }

    static Entry area(int x0, int z0, int w, int h) {
        String key = x0 + ";" + z0 + ";" + w + ";" + h;
        long now = System.currentTimeMillis();
        Entry e = CACHE.get(key);
        if (e != null && now - e.time() < 20_000) return e;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return null;
        NativeImage img = new NativeImage(Math.max(1, w), Math.max(1, h), false);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int px = 0; px < w; px++) {
            int bx = x0 + px;
            int prev = level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, z0 - 1);
            for (int pz = 0; pz < h; pz++) {
                int bz = z0 + pz;
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz);
                int argb = 0xFF101218;
                if (top > level.getMinY()) {
                    pos.set(bx, top - 1, bz);
                    BlockState st = level.getBlockState(pos);
                    MapColor mcol = st.getMapColor(level, pos);
                    if (mcol != MapColor.NONE) {
                        MapColor.Brightness b = top > prev ? MapColor.Brightness.HIGH : top < prev ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                        argb = mcol.calculateARGBColor(b) | 0xFF000000;
                    }
                }
                prev = top;
                img.setPixel(px, pz, argb);
            }
        }
        ResourceLocation id = FNations.id("dynamic/terrain_" + (counter++));
        mc.getTextureManager().register(id, new DynamicTexture(img));
        if (e != null) mc.getTextureManager().release(e.id());
        Entry ne = new Entry(id, w, h, now);
        CACHE.put(key, ne);
        // on garde quelques cartes au maximum
        if (CACHE.size() > 6) {
            List<String> keys = new ArrayList<>(CACHE.keySet());
            for (int i = 0; i < keys.size() - 6; i++) {
                Entry old = CACHE.remove(keys.get(i));
                if (old != null) mc.getTextureManager().release(old.id());
            }
        }
        return ne;
    }
}

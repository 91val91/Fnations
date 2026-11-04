package fr.fnations.earth;

import fr.fnations.FNations;
import fr.fnations.server.FnServer;
import fr.fnations.util.Store;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;

/** Enregistrement du générateur « Terre » et réglages du monde (bordure, point d'apparition). */
public final class EarthWorld {
    private EarthWorld() {}

    public static void init() {
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR, FNations.id("earth"), EarthChunkGenerator.CODEC);
        Registry.register(BuiltInRegistries.BIOME_SOURCE, FNations.id("earth"), EarthBiomeSource.CODEC);
    }

    public static boolean isEarth(ServerLevel level) { return level != null && level.getChunkSource().getGenerator() instanceof EarthChunkGenerator; }

    /** Au démarrage du serveur : bordure aux dimensions de la Terre et apparition à Paris (une seule fois). */
    public static void setup(FnServer s) {
        ServerLevel ow = s.server().overworld();
        if (!isEarth(ow)) return;
        EarthData.get();
        WorldBorder wb = ow.getWorldBorder();
        if (wb.getSize() > 1.0E7) {
            wb.setCenter(0, 0);
            wb.setSize(EarthData.WORLD_W + 2);
        }
        Store store = new Store(s.dataDir(), "terre.yml");
        if (!store.get().getBoolean("apparition-definie")) {
            double lat = s.config().getDouble("carte.apparition-latitude", 48.8566);
            double lon = s.config().getDouble("carte.apparition-longitude", 2.3522);
            int x = (int) Math.round(EarthData.blockX(lon)), z = (int) Math.round(EarthData.blockZ(lat));
            var gen = ow.getChunkSource().getGenerator();
            int y = gen.getBaseHeight(x, z, Heightmap.Types.MOTION_BLOCKING, ow, ow.getChunkSource().randomState());
            ow.setDefaultSpawnPos(new BlockPos(x, y, z), 0f);
            store.get().set("apparition-definie", true);
            store.save();
            FNations.LOG.info("Monde Terre : apparition définie en {} ({}, {}, {})", EarthData.latLon(x, z), x, y, z);
        }
    }
}

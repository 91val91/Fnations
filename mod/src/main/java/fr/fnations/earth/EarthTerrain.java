package fr.fnations.earth;

import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import java.util.List;

/**
 * Relief et biomes de la Terre au 1/1000 (1 bloc = 1 km), calculés à partir d'EarthData.
 * Échelle verticale exagérée : niveau de la mer en Y = 63, 1 500 m vers Y = 106, l'Everest vers Y = 200.
 */
public final class EarthTerrain {
    public static final int SEA = 63;

    /** Colonne de terrain : sommet du sol, sommet de l'eau (mer ou lac), lac ou non. */
    public record Column(int height, int waterTop, boolean lake, double meters) {}

    public static final List<ResourceKey<Biome>> BIOMES = List.of(
            Biomes.OCEAN, Biomes.DEEP_OCEAN, Biomes.WARM_OCEAN, Biomes.LUKEWARM_OCEAN, Biomes.DEEP_LUKEWARM_OCEAN, Biomes.COLD_OCEAN,
            Biomes.DEEP_COLD_OCEAN, Biomes.FROZEN_OCEAN, Biomes.DEEP_FROZEN_OCEAN, Biomes.RIVER, Biomes.FROZEN_RIVER, Biomes.BEACH,
            Biomes.SNOWY_BEACH, Biomes.STONY_SHORE, Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.SNOWY_PLAINS, Biomes.ICE_SPIKES,
            Biomes.DESERT, Biomes.SWAMP, Biomes.MANGROVE_SWAMP, Biomes.FOREST, Biomes.FLOWER_FOREST, Biomes.BIRCH_FOREST, Biomes.DARK_FOREST,
            Biomes.OLD_GROWTH_SPRUCE_TAIGA, Biomes.TAIGA, Biomes.SNOWY_TAIGA, Biomes.SAVANNA, Biomes.SAVANNA_PLATEAU, Biomes.WINDSWEPT_HILLS,
            Biomes.WINDSWEPT_GRAVELLY_HILLS, Biomes.WINDSWEPT_FOREST, Biomes.JUNGLE, Biomes.SPARSE_JUNGLE, Biomes.BAMBOO_JUNGLE,
            Biomes.BADLANDS, Biomes.ERODED_BADLANDS, Biomes.WOODED_BADLANDS, Biomes.MEADOW, Biomes.CHERRY_GROVE, Biomes.GROVE,
            Biomes.SNOWY_SLOPES, Biomes.FROZEN_PEAKS, Biomes.JAGGED_PEAKS, Biomes.STONY_PEAKS);

    private static volatile EarthTerrain instance;

    private final EarthData d = EarthData.get();
    private final SimplexNoise n1, n2, n3, n4, warpX, warpZ, variety, lakeN;

    private EarthTerrain() {
        // graine fixe : la Terre est la même sur tous les serveurs
        RandomSource r = RandomSource.create(1000L);
        n1 = new SimplexNoise(r);
        n2 = new SimplexNoise(r);
        n3 = new SimplexNoise(r);
        n4 = new SimplexNoise(r);
        warpX = new SimplexNoise(r);
        warpZ = new SimplexNoise(r);
        variety = new SimplexNoise(r);
        lakeN = new SimplexNoise(r);
    }

    public static EarthTerrain get() {
        EarthTerrain t = instance;
        if (t == null) {
            synchronized (EarthTerrain.class) {
                t = instance;
                if (t == null) instance = t = new EarthTerrain();
            }
        }
        return t;
    }

    public EarthData data() { return d; }

    // ------------------------------------------------------------ relief
    /** Altitude réelle (m) avec un bruit de côte qui casse les rivages trop réguliers. */
    public double meters(int x, int z) {
        double m = d.elevation(EarthData.px(x), EarthData.py(z));
        double near = 1 - Math.min(1, Math.abs(m) / 260.0);
        if (near > 0) m += (n1.getValue(x / 90.0, z / 90.0) * 0.65 + n2.getValue(x / 23.0, z / 23.0) * 0.35) * 95 * near;
        return m;
    }

    /** Hauteur de base (sans détail) d'une altitude en mètres. */
    public static double baseY(double m) {
        if (m >= 0) return SEA + 1 + (m < 300 ? m / 25 : 12 + (m < 1500 ? (m - 300) / 40 : 30 + (m - 1500) / 75));
        double depth = -m;
        return SEA - 1 - (depth < 200 ? depth / 25 : 8 + (depth - 200) / 110);
    }

    public int coverAt(int x, int z) {
        double gx = EarthData.px(x) + warpX.getValue(x / 55.0, z / 55.0) * 0.9;
        double gy = EarthData.py(z) + warpZ.getValue(x / 55.0, z / 55.0) * 0.9;
        return d.cover((int) Math.round(gx), (int) Math.round(gy));
    }

    public Column column(int x, int z) {
        double m = meters(x, z);
        double y = baseY(m);
        if (m >= 0) {
            double rough = 1.2 + Math.min(1, m / 800.0) * 4 + Math.max(0, Math.min(1, (m - 1200) / 2500.0)) * 12;
            double det = n3.getValue(x / 60.0, z / 60.0) * 0.6 + n4.getValue(x / 17.0, z / 17.0) * 0.3 + n2.getValue(x / 6.0, z / 6.0) * 0.1;
            y += det * rough;
            if (m > 900) {
                double ridge = 1 - Math.abs(n1.getValue(x / 35.0 + 100, z / 35.0 - 100));
                y += (ridge * ridge - 0.45) * rough * 1.3;
            }
            if (m > 15) y = Math.max(y, SEA + 1); // pas de mares au milieu des terres
        } else {
            y += n3.getValue(x / 40.0, z / 40.0) * 2 + n4.getValue(x / 11.0, z / 11.0) * 0.6;
        }
        int h = (int) Math.floor(Math.max(-58, Math.min(300, y)));
        if (m >= 0 && coverAt(x, z) == EarthData.LAKE) {
            int top = (int) Math.floor(baseY(m)) - 1;
            int depth = 3 + (int) Math.round((lakeN.getValue(x / 30.0, z / 30.0) + 1) * 2.5);
            return new Column(Math.min(h, top - depth), top, true, m);
        }
        return new Column(h, Math.max(h, SEA), false, m);
    }

    /** Variation locale (−1..1) pour mélanger les biomes voisins (forêt de bouleaux, jungle de bambous...). */
    public double variety(int x, int z) { return variety.getValue(x / 260.0, z / 260.0) * 0.8 + variety.getValue(x / 70.0, z / 70.0) * 0.2; }

    // ------------------------------------------------------------ biomes
    public ResourceKey<Biome> biome(int x, int z) {
        double lat = EarthData.lat(z), alat = Math.abs(lat), lon = EarthData.lon(x);
        double m = meters(x, z);
        int cls = coverAt(x, z);
        double v = variety(x, z);
        if (m < 0 || cls == EarthData.WATER) {
            double depth = -m;
            if (alat > 66) return depth > 1500 ? Biomes.DEEP_FROZEN_OCEAN : Biomes.FROZEN_OCEAN;
            if (alat > 50) return depth > 1500 ? Biomes.DEEP_COLD_OCEAN : Biomes.COLD_OCEAN;
            if (alat > 32) return depth > 1500 ? Biomes.DEEP_OCEAN : Biomes.OCEAN;
            if (alat > 22) return depth > 1500 ? Biomes.DEEP_LUKEWARM_OCEAN : Biomes.LUKEWARM_OCEAN;
            return depth > 2500 ? Biomes.DEEP_LUKEWARM_OCEAN : Biomes.WARM_OCEAN;
        }
        if (cls == EarthData.LAKE) return alat > 55 ? Biomes.FROZEN_RIVER : Biomes.RIVER;
        if (m < 22 && cls != EarthData.ICE) {
            Column c = column(x, z);
            if (c.height() <= SEA + 1) return alat > 58 ? Biomes.SNOWY_BEACH : Biomes.BEACH;
            if (alat < 25 && (cls == EarthData.FOREST || cls == EarthData.MIXED) && v > 0.25) return Biomes.MANGROVE_SWAMP;
            if (alat >= 25 && alat < 42 && cls == EarthData.FOREST && v > 0.35) return Biomes.SWAMP;
        }
        if (m > 4200) {
            boolean dry = cls == EarthData.DESERT || cls == EarthData.STEPPE || cls == EarthData.BARREN;
            return dry && alat < 40 ? Biomes.STONY_PEAKS : alat > 45 || v > 0.2 ? Biomes.FROZEN_PEAKS : Biomes.JAGGED_PEAKS;
        }
        if (m > 3000) return cls == EarthData.FOREST && alat < 35 ? Biomes.GROVE : Biomes.SNOWY_SLOPES;
        if (m > 2000 && alat > 25) {
            if (cls == EarthData.FOREST) return Biomes.GROVE;
            if (cls == EarthData.GRASS || cls == EarthData.MIXED) return Biomes.MEADOW;
            return Biomes.WINDSWEPT_HILLS;
        }
        boolean japan = lon > 128 && lon < 146 && lat > 30 && lat < 42;
        switch (cls) {
            case EarthData.ICE:
                return v > 0.55 ? Biomes.ICE_SPIKES : Biomes.SNOWY_PLAINS;
            case EarthData.TUNDRA:
                return alat < 64 && v > 0.15 ? Biomes.SNOWY_TAIGA : Biomes.SNOWY_PLAINS;
            case EarthData.FOREST:
                if (alat < 12) return v > 0.55 ? Biomes.BAMBOO_JUNGLE : Biomes.JUNGLE;
                if (alat < 24) return v > 0.25 ? Biomes.JUNGLE : Biomes.SPARSE_JUNGLE;
                if (japan && v > 0.35) return Biomes.CHERRY_GROVE;
                if (alat < 36) return v > 0.45 ? Biomes.DARK_FOREST : Biomes.FOREST;
                if (alat < 48) return v > 0.5 ? Biomes.BIRCH_FOREST : v < -0.55 ? Biomes.DARK_FOREST : m > 900 ? Biomes.WINDSWEPT_FOREST : Biomes.FOREST;
                if (alat < 55) return v > 0.1 ? Biomes.FOREST : Biomes.TAIGA;
                if (alat < 63) return v > 0.35 ? Biomes.OLD_GROWTH_SPRUCE_TAIGA : Biomes.TAIGA;
                return v > 0.1 ? Biomes.SNOWY_TAIGA : Biomes.TAIGA;
            case EarthData.MIXED:
                if (alat < 20) return v > 0.2 ? Biomes.SPARSE_JUNGLE : Biomes.SAVANNA;
                if (japan && v > 0.45) return Biomes.CHERRY_GROVE;
                if (alat < 30) return v > 0.4 ? Biomes.FOREST : Biomes.PLAINS;
                if (alat < 52) return v > 0.45 ? Biomes.FOREST : v > 0.3 ? Biomes.FLOWER_FOREST : v < -0.6 ? Biomes.SUNFLOWER_PLAINS : Biomes.PLAINS;
                if (alat < 62) return v > 0 ? Biomes.TAIGA : Biomes.PLAINS;
                return Biomes.SNOWY_PLAINS;
            case EarthData.GRASS:
                if (alat < 30) return m > 900 ? Biomes.SAVANNA_PLATEAU : Biomes.SAVANNA;
                if (alat < 58) return v > 0.6 ? Biomes.SUNFLOWER_PLAINS : Biomes.PLAINS;
                return Biomes.SNOWY_PLAINS;
            case EarthData.STEPPE:
                if (alat < 35) {
                    if (m > 300 && v > 0.45) return v > 0.75 ? Biomes.ERODED_BADLANDS : v > 0.6 ? Biomes.WOODED_BADLANDS : Biomes.BADLANDS;
                    return v < -0.3 ? Biomes.DESERT : Biomes.SAVANNA;
                }
                if (alat < 55) return v > 0.5 ? Biomes.DESERT : Biomes.PLAINS;
                return Biomes.SNOWY_PLAINS;
            case EarthData.DESERT:
                if (alat < 46) return m > 800 && v > 0.55 ? Biomes.BADLANDS : Biomes.DESERT;
                return Biomes.PLAINS;
            case EarthData.BARREN:
                if (m > 1500) return alat > 35 ? Biomes.SNOWY_SLOPES : Biomes.STONY_PEAKS;
                return alat > 50 ? Biomes.WINDSWEPT_GRAVELLY_HILLS : Biomes.BADLANDS;
            default:
                return Biomes.PLAINS;
        }
    }
}

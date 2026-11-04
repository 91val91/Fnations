package fr.fnations.earth;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Générateur du monde « Terre » au 1/1000 : relief réel, océans, lacs, surfaces selon les biomes. */
public final class EarthChunkGenerator extends ChunkGenerator {
    public static final MapCodec<EarthChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(
            i -> i.group(BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource))
                    .apply(i, i.stable(EarthChunkGenerator::new)));

    private static final BlockState STONE = Blocks.STONE.defaultBlockState(), DEEPSLATE = Blocks.DEEPSLATE.defaultBlockState(),
            BEDROCK = Blocks.BEDROCK.defaultBlockState(), WATER = Blocks.WATER.defaultBlockState(), AIR = Blocks.AIR.defaultBlockState(),
            GRASS = Blocks.GRASS_BLOCK.defaultBlockState(), DIRT = Blocks.DIRT.defaultBlockState(), SAND = Blocks.SAND.defaultBlockState(),
            SANDSTONE = Blocks.SANDSTONE.defaultBlockState(), GRAVEL = Blocks.GRAVEL.defaultBlockState(), CLAY = Blocks.CLAY.defaultBlockState(),
            RED_SAND = Blocks.RED_SAND.defaultBlockState(), MUD = Blocks.MUD.defaultBlockState(), SNOW = Blocks.SNOW_BLOCK.defaultBlockState(),
            PACKED_ICE = Blocks.PACKED_ICE.defaultBlockState(), CALCITE = Blocks.CALCITE.defaultBlockState(),
            COARSE = Blocks.COARSE_DIRT.defaultBlockState(), PODZOL = Blocks.PODZOL.defaultBlockState();
    private static final BlockState[] TERRACOTTA = {Blocks.TERRACOTTA.defaultBlockState(), Blocks.ORANGE_TERRACOTTA.defaultBlockState(),
            Blocks.YELLOW_TERRACOTTA.defaultBlockState(), Blocks.BROWN_TERRACOTTA.defaultBlockState(), Blocks.RED_TERRACOTTA.defaultBlockState(),
            Blocks.WHITE_TERRACOTTA.defaultBlockState(), Blocks.LIGHT_GRAY_TERRACOTTA.defaultBlockState(), Blocks.ORANGE_TERRACOTTA.defaultBlockState()};

    public EarthChunkGenerator(BiomeSource biomes) { super(biomes); }

    private static EarthTerrain t() { return EarthTerrain.get(); }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() { return CODEC; }

    private static int hash(int x, int y, int z) {
        int h = x * 73428767 ^ y * 912931 ^ z * 4382191;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        return h ^ (h >>> 15);
    }

    private static BlockState base(int x, int y, int z, int minY) {
        if (y == minY) return BEDROCK;
        if (y <= minY + 4 && (hash(x, y, z) & 7) < 5 - (y - minY)) return BEDROCK;
        return y < ((hash(x, 0, z) & 3) - 2) ? DEEPSLATE : STONE;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState rs, StructureManager sm, ChunkAccess chunk) {
        ChunkPos cp = chunk.getPos();
        int bx = cp.getMinBlockX(), bz = cp.getMinBlockZ(), minY = chunk.getMinY();
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        Heightmap floor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = bx + lx, z = bz + lz;
                EarthTerrain.Column c = t().column(x, z);
                int top = Math.max(c.height(), c.waterTop());
                for (int y = minY; y <= top; y++) {
                    BlockState st = y <= c.height() ? base(x, y, z, minY) : WATER;
                    chunk.setBlockState(mp.set(lx, y, lz), st, false);
                    floor.update(lx, y, lz, st);
                    surface.update(lx, y, lz, st);
                }
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager sm, RandomState rs, ChunkAccess chunk) {
        ChunkPos cp = chunk.getPos();
        int bx = cp.getMinBlockX(), bz = cp.getMinBlockZ(), minY = chunk.getMinY();
        EarthTerrain.Column[] cols = new EarthTerrain.Column[18 * 18];
        for (int i = -1; i <= 16; i++) for (int j = -1; j <= 16; j++) cols[(i + 1) * 18 + j + 1] = t().column(bx + i, bz + j);
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = bx + lx, z = bz + lz;
                EarthTerrain.Column c = cols[(lx + 1) * 18 + lz + 1];
                int h = c.height();
                int slope = 0;
                for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) slope = Math.max(slope, Math.abs(h - cols[(lx + 1 + o[0]) * 18 + lz + 1 + o[1]].height()));
                BlockState[] layers = layers(x, z, h, c, slope);
                for (int k = 0; k < layers.length; k++) {
                    int y = h - k;
                    if (y <= minY + 5) break;
                    mp.set(lx, y, lz);
                    BlockState cur = chunk.getBlockState(mp);
                    if (cur.is(Blocks.STONE) || cur.is(Blocks.DEEPSLATE)) chunk.setBlockState(mp, layers[k], false);
                }
            }
        }
    }

    /** Couches de surface d'une colonne (du sommet vers le bas). */
    private BlockState[] layers(int x, int z, int h, EarthTerrain.Column c, int slope) {
        int r = hash(x, h, z);
        boolean underwater = c.waterTop() > h;
        if (underwater) {
            int depth = c.waterTop() - h;
            if (depth <= 5) return new BlockState[]{SAND, SAND, SAND, SANDSTONE};
            if (depth <= 20) return (r & 3) == 0 ? new BlockState[]{GRAVEL, GRAVEL} : new BlockState[]{SAND, SAND, SANDSTONE};
            return (r & 15) == 0 ? new BlockState[]{CLAY, GRAVEL} : new BlockState[]{GRAVEL, GRAVEL};
        }
        ResourceKey<Biome> b = t().biome(x, z);
        int cover = t().coverAt(x, z);
        if (cover == EarthData.ICE && c.meters() > 50) return new BlockState[]{SNOW, SNOW, SNOW, PACKED_ICE, PACKED_ICE, PACKED_ICE, PACKED_ICE, PACKED_ICE};
        if (b == Biomes.BEACH || b == Biomes.SNOWY_BEACH) return new BlockState[]{SAND, SAND, SAND, SANDSTONE, SANDSTONE};
        if (b == Biomes.DESERT) return new BlockState[]{SAND, SAND, SAND, SAND, SANDSTONE, SANDSTONE, SANDSTONE};
        if (b == Biomes.BADLANDS || b == Biomes.ERODED_BADLANDS || b == Biomes.WOODED_BADLANDS) {
            BlockState[] out = new BlockState[9];
            out[0] = b == Biomes.WOODED_BADLANDS && h > 90 ? COARSE : RED_SAND;
            out[1] = RED_SAND;
            for (int k = 2; k < out.length; k++) out[k] = TERRACOTTA[Math.floorMod(h - k, TERRACOTTA.length)];
            return out;
        }
        if (b == Biomes.MANGROVE_SWAMP) return new BlockState[]{MUD, MUD, MUD, DIRT};
        if (b == Biomes.SNOWY_SLOPES || b == Biomes.FROZEN_PEAKS || b == Biomes.JAGGED_PEAKS)
            return slope >= 5 ? new BlockState[]{STONE} : new BlockState[]{SNOW, SNOW, b == Biomes.FROZEN_PEAKS ? PACKED_ICE : STONE};
        if (b == Biomes.STONY_PEAKS) return new BlockState[]{(r & 3) == 0 ? CALCITE : STONE};
        if (b == Biomes.WINDSWEPT_GRAVELLY_HILLS || b == Biomes.STONY_SHORE) return new BlockState[]{GRAVEL, GRAVEL, STONE};
        if (slope >= 5) return new BlockState[]{STONE};
        if (slope >= 4) return new BlockState[]{(r & 1) == 0 ? GRAVEL : STONE, STONE};
        if (b == Biomes.GROVE && h > 150) return new BlockState[]{SNOW, DIRT, DIRT};
        if ((b == Biomes.OLD_GROWTH_SPRUCE_TAIGA || b == Biomes.TAIGA) && (r & 7) == 0) return new BlockState[]{PODZOL, DIRT, DIRT};
        if (b == Biomes.SAVANNA_PLATEAU && (r & 7) == 0) return new BlockState[]{COARSE, DIRT, DIRT};
        return new BlockState[]{GRASS, DIRT, DIRT, DIRT};
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState rs, BiomeManager bm, StructureManager sm, ChunkAccess chunk) {}

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        ChunkPos cp = region.getCenter();
        Holder<Biome> biome = region.getBiome(cp.getWorldPosition().atY(region.getMaxY()));
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
        random.setDecorationSeed(region.getSeed(), cp.getMinBlockX(), cp.getMinBlockZ());
        NaturalSpawner.spawnMobsForChunkGeneration(region, biome, cp, random);
    }

    @Override
    public int getGenDepth() { return 384; }

    @Override
    public int getSeaLevel() { return EarthTerrain.SEA; }

    @Override
    public int getMinY() { return -64; }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState rs) {
        EarthTerrain.Column c = t().column(x, z);
        int top = c.height();
        if (c.waterTop() > top && type.isOpaque().test(WATER)) top = c.waterTop();
        return top + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState rs) {
        EarthTerrain.Column c = t().column(x, z);
        int minY = level.getMinY();
        BlockState[] states = new BlockState[level.getHeight()];
        for (int i = 0; i < states.length; i++) {
            int y = minY + i;
            states[i] = y <= c.height() ? base(x, y, z, minY) : y <= c.waterTop() ? WATER : AIR;
        }
        return new NoiseColumn(minY, states);
    }

    @Override
    public void addDebugScreenInfo(List<String> list, RandomState rs, BlockPos pos) {
        String[] place = EarthData.get().place(pos.getX(), pos.getZ());
        list.add("FNations Terre 1/1000 : " + EarthData.latLon(pos.getX(), pos.getZ()) + (place[0].isEmpty() ? " (océan)" : " — " + place[0] + ", " + place[1]));
    }
}

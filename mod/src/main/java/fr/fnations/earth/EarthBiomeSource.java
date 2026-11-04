package fr.fnations.earth;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.stream.Stream;

/** Biomes de la Terre : d'après la couverture du sol, la latitude et l'altitude réelles. */
public final class EarthBiomeSource extends BiomeSource {
    public static final MapCodec<EarthBiomeSource> CODEC = RecordCodecBuilder.mapCodec(
            i -> i.group(RegistryOps.retrieveGetter(Registries.BIOME)).apply(i, i.stable(EarthBiomeSource::new)));

    private final Map<ResourceKey<Biome>, Holder<Biome>> holders = new IdentityHashMap<>();
    /** Petit cache par fil (les biomes sont demandés par paquets de 4×4 blocs, à toutes les hauteurs). */
    private final ThreadLocal<long[]> cacheKeys = ThreadLocal.withInitial(() -> {
        long[] k = new long[4096];
        java.util.Arrays.fill(k, Long.MIN_VALUE);
        return k;
    });
    private final ThreadLocal<Object[]> cacheVals = ThreadLocal.withInitial(() -> new Object[4096]);

    public EarthBiomeSource(HolderGetter<Biome> biomes) {
        for (ResourceKey<Biome> k : EarthTerrain.BIOMES) holders.put(k, biomes.getOrThrow(k));
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() { return CODEC; }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() { return holders.values().stream(); }

    @Override
    @SuppressWarnings("unchecked")
    public Holder<Biome> getNoiseBiome(int qx, int qy, int qz, Climate.Sampler sampler) {
        long key = ((long) qx << 32) ^ (qz & 0xFFFFFFFFL);
        int slot = (int) ((qx * 31L + qz) & 4095);
        long[] keys = cacheKeys.get();
        Object[] vals = cacheVals.get();
        if (keys[slot] == key) return (Holder<Biome>) vals[slot];
        Holder<Biome> h = holders.get(EarthTerrain.get().biome((qx << 2) + 2, (qz << 2) + 2));
        if (h == null) h = holders.get(net.minecraft.world.level.biome.Biomes.PLAINS);
        keys[slot] = key;
        vals[slot] = h;
        return h;
    }
}

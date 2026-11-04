package fr.fnations.registry;

import com.mojang.serialization.Codec;
import fr.fnations.FNations;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;

/** Composants de données des items FNations (synchronisés avec le client). */
public final class ModComponents {
    /** Munitions dans le chargeur d'une arme. */
    public static DataComponentType<Integer> AMMO;
    /** Taille du chargeur (pour l'affichage client). */
    public static DataComponentType<Integer> MAGAZINE;
    /** Valeur d'un billet de banque. */
    public static DataComponentType<Double> NOTE_VALUE;
    /** Carburant restant d'un véhicule rangé. */
    public static DataComponentType<Double> FUEL;
    /** Type de mob et niveau d'un spawner FNations. */
    public static DataComponentType<SpawnerData> SPAWNER;

    /** mob = type de spawners.yml, entity = id de l'entité affichée (pour le client), level = 1 à 5. */
    public record SpawnerData(String mob, String entity, int level) {
        public static final Codec<SpawnerData> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("mob").forGetter(SpawnerData::mob),
                Codec.STRING.optionalFieldOf("entity", "").forGetter(SpawnerData::entity),
                Codec.INT.fieldOf("level").forGetter(SpawnerData::level)).apply(i, SpawnerData::new));
        public static final net.minecraft.network.codec.StreamCodec<io.netty.buffer.ByteBuf, SpawnerData> STREAM =
                net.minecraft.network.codec.StreamCodec.composite(ByteBufCodecs.STRING_UTF8, SpawnerData::mob, ByteBufCodecs.STRING_UTF8, SpawnerData::entity,
                        ByteBufCodecs.VAR_INT, SpawnerData::level, SpawnerData::new);

        /** Id de l'entité : celui enregistré, sinon minecraft:&lt;mob&gt;. */
        public String entityId() { return !entity.isEmpty() ? entity : mob.contains(":") ? mob : "minecraft:" + mob; }
    }

    private ModComponents() {}

    public static void init() {
        AMMO = register("ammo", Codec.INT, ByteBufCodecs.VAR_INT);
        MAGAZINE = register("magazine", Codec.INT, ByteBufCodecs.VAR_INT);
        NOTE_VALUE = register("note_value", Codec.DOUBLE, ByteBufCodecs.DOUBLE);
        FUEL = register("fuel", Codec.DOUBLE, ByteBufCodecs.DOUBLE);
        SPAWNER = register("spawner", SpawnerData.CODEC, SpawnerData.STREAM);
    }

    private static <T> DataComponentType<T> register(String id, Codec<T> codec, net.minecraft.network.codec.StreamCodec<? super io.netty.buffer.ByteBuf, T> stream) {
        return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, FNations.id(id),
                DataComponentType.<T>builder().persistent(codec).networkSynchronized(stream).build());
    }
}

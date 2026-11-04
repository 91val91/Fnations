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

    private ModComponents() {}

    public static void init() {
        AMMO = register("ammo", Codec.INT, ByteBufCodecs.VAR_INT);
        MAGAZINE = register("magazine", Codec.INT, ByteBufCodecs.VAR_INT);
        NOTE_VALUE = register("note_value", Codec.DOUBLE, ByteBufCodecs.DOUBLE);
        FUEL = register("fuel", Codec.DOUBLE, ByteBufCodecs.DOUBLE);
    }

    private static <T> DataComponentType<T> register(String id, Codec<T> codec, net.minecraft.network.codec.StreamCodec<? super io.netty.buffer.ByteBuf, T> stream) {
        return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, FNations.id(id),
                DataComponentType.<T>builder().persistent(codec).networkSynchronized(stream).build());
    }
}

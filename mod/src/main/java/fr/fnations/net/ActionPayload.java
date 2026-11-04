package fr.fnations.net;

import fr.fnations.FNations;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → serveur : action de jeu (tir, rechargement, tir de véhicule, charrue...).
 * flags : bit 0 = visée (accroupi), bit 1 = maintien (tir automatique).
 */
public record ActionPayload(Kind kind, int flags) implements CustomPacketPayload {
    public enum Kind { FIRE, RELOAD, VEHICLE_FIRE, VEHICLE_TOGGLE }

    public static final Type<ActionPayload> TYPE = new Type<>(FNations.id("action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ActionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(i -> Kind.values()[Math.max(0, Math.min(Kind.values().length - 1, i))], Kind::ordinal), ActionPayload::kind,
            ByteBufCodecs.VAR_INT, ActionPayload::flags,
            ActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

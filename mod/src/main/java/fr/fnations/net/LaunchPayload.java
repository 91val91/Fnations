package fr.fnations.net;

import fr.fnations.FNations;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → serveur : régler la cible d'un lanceur de missiles et éventuellement tirer. */
public record LaunchPayload(BlockPos pos, int x, int z, boolean fire) implements CustomPacketPayload {
    public static final Type<LaunchPayload> TYPE = new Type<>(FNations.id("launch"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LaunchPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, LaunchPayload::pos,
            ByteBufCodecs.VAR_INT, LaunchPayload::x,
            ByteBufCodecs.VAR_INT, LaunchPayload::z,
            ByteBufCodecs.BOOL, LaunchPayload::fire,
            LaunchPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

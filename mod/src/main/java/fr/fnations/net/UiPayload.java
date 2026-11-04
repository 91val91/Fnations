package fr.fnations.net;

import fr.fnations.FNations;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Serveur → client : page de la tablette (JSON compressé deflate). */
public record UiPayload(byte[] data) implements CustomPacketPayload {
    public static final Type<UiPayload> TYPE = new Type<>(FNations.id("ui"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UiPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.byteArray(1 << 20), UiPayload::data, UiPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

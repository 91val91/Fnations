package fr.fnations.net;

import fr.fnations.FNations;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Serveur → client : ouvrir une fenêtre de saisie. */
public record PromptOpenPayload(int id, String question, String initial) implements CustomPacketPayload {
    public static final Type<PromptOpenPayload> TYPE = new Type<>(FNations.id("prompt_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PromptOpenPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PromptOpenPayload::id,
            ByteBufCodecs.STRING_UTF8, PromptOpenPayload::question,
            ByteBufCodecs.STRING_UTF8, PromptOpenPayload::initial,
            PromptOpenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

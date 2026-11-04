package fr.fnations.net;

import fr.fnations.FNations;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → serveur : réponse à une saisie. */
public record PromptAnswerPayload(int id, String text, boolean cancelled) implements CustomPacketPayload {
    public static final Type<PromptAnswerPayload> TYPE = new Type<>(FNations.id("prompt_answer"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PromptAnswerPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PromptAnswerPayload::id,
            ByteBufCodecs.stringUtf8(256), PromptAnswerPayload::text,
            ByteBufCodecs.BOOL, PromptAnswerPayload::cancelled,
            PromptAnswerPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

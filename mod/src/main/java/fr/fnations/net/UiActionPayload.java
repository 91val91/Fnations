package fr.fnations.net;

import fr.fnations.FNations;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → serveur : action sur la tablette ("page.action:arg") et valeurs des champs (JSON). */
public record UiActionPayload(String action, String data) implements CustomPacketPayload {
    public static final Type<UiActionPayload> TYPE = new Type<>(FNations.id("ui_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UiActionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(256), UiActionPayload::action,
            ByteBufCodecs.stringUtf8(8192), UiActionPayload::data,
            UiActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

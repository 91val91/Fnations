package fr.fnations.server;

import fr.fnations.util.Txt;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** Envoi de messages, titres et sons aux joueurs. */
public final class Msg {
    private Msg() {}

    public static void send(ServerPlayer p, String mini) {
        p.sendSystemMessage(Txt.prefixed(mini));
        FnServer s = FnServer.get();
        if (s != null && s.ui() != null) s.ui().toast(p, mini); // affiché aussi sur la tablette si elle est ouverte
    }

    public static void ok(ServerPlayer p, String mini) { send(p, "<green>" + mini); }

    public static void err(ServerPlayer p, String mini) { send(p, "<red>" + mini); }

    public static void raw(ServerPlayer p, String mini) { p.sendSystemMessage(Txt.mm(mini)); }

    public static void bar(ServerPlayer p, String mini) { p.displayClientMessage(Txt.mm(mini), true); }

    public static void title(ServerPlayer p, String title, String sub, int fadeIn, int stay, int fadeOut) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Txt.mm(sub)));
        p.connection.send(new ClientboundSetTitleTextPacket(Txt.mm(title)));
    }

    public static void title(ServerPlayer p, String title, String sub) { title(p, title, sub, 10, 50, 15); }

    public static void broadcast(MinecraftServer s, String mini) {
        s.getPlayerList().broadcastSystemMessage(Txt.prefixed(mini), false);
    }

    public static void sound(ServerPlayer p, SoundEvent e, float vol, float pitch) {
        p.playNotifySound(e, SoundSource.MASTER, vol, pitch);
    }

    public static void sound(ServerPlayer p, Holder<SoundEvent> e, float vol, float pitch) { sound(p, e.value(), vol, pitch); }
}

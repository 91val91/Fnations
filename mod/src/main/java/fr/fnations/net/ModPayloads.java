package fr.fnations.net;

import fr.fnations.gui.Prompt;
import fr.fnations.machine.MachineBlockEntity;
import fr.fnations.server.FnServer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** Enregistrement des paquets réseau et des récepteurs côté serveur. */
public final class ModPayloads {
    private ModPayloads() {}

    public static void init() {
        PayloadTypeRegistry.playS2C().register(PromptOpenPayload.TYPE, PromptOpenPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PromptAnswerPayload.TYPE, PromptAnswerPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ActionPayload.TYPE, ActionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(LaunchPayload.TYPE, LaunchPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(UiPayload.TYPE, UiPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UiActionPayload.TYPE, UiActionPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(UiActionPayload.TYPE, (p, ctx) -> {
            FnServer s = FnServer.get();
            if (s != null) s.ui().handle(ctx.player(), p.action(), p.data());
        });

        ServerPlayNetworking.registerGlobalReceiver(PromptAnswerPayload.TYPE, (p, ctx) -> Prompt.answer(ctx.player(), p.id(), p.text(), p.cancelled()));
        ServerPlayNetworking.registerGlobalReceiver(ActionPayload.TYPE, (p, ctx) -> {
            FnServer s = FnServer.get();
            if (s == null) return;
            switch (p.kind()) {
                case FIRE -> s.weapons().fire(ctx.player(), (p.flags() & 1) != 0, (p.flags() & 2) != 0);
                case RELOAD -> s.weapons().reload(ctx.player());
                case VEHICLE_FIRE -> fr.fnations.vehicle.VehicleEntity.onFireRequest(ctx.player());
                case VEHICLE_TOGGLE -> fr.fnations.vehicle.VehicleEntity.onToggleRequest(ctx.player());
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(LaunchPayload.TYPE, (p, ctx) -> {
            var player = ctx.player();
            if (player.distanceToSqr(p.pos().getCenter()) > 64) return;
            if (player.level().getBlockEntity(p.pos()) instanceof MachineBlockEntity be) be.launcherCommand(player, p.x(), p.z(), p.fire());
        });
    }
}

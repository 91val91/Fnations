package fr.fnations.client;

import com.mojang.blaze3d.platform.InputConstants;
import fr.fnations.client.render.ItemModelRenderer;
import fr.fnations.client.render.MachineRenderer;
import fr.fnations.client.render.VehicleRenderer;
import fr.fnations.client.screen.MachineScreen;
import fr.fnations.client.screen.PanelScreen;
import fr.fnations.client.screen.PromptScreen;
import fr.fnations.client.ui.FnUiScreen;
import fr.fnations.net.UiActionPayload;
import fr.fnations.net.UiPayload;
import fr.fnations.machine.MachineType;
import fr.fnations.net.ActionPayload;
import fr.fnations.net.PromptOpenPayload;
import fr.fnations.registry.ModBlockEntities;
import fr.fnations.registry.ModBlocks;
import fr.fnations.registry.ModEntities;
import fr.fnations.registry.ModMenus;
import fr.fnations.util.Txt;
import fr.fnations.vehicle.VehicleEntity;
import fr.fnations.vehicle.VehicleKind;
import fr.fnations.weapon.GunItem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/** Partie client : écrans, rendus, touches, tir, conduite et HUD. */
public final class FNationsClient implements ClientModInitializer {
    public static KeyMapping RELOAD, TABLET;
    private boolean useWasDown, jumpWasDown;
    private int fireCooldown, vehicleFireCooldown;

    @Override
    public void onInitializeClient() {
        MenuScreens.register(ModMenus.PANEL, PanelScreen::new);
        MenuScreens.register(ModMenus.MACHINE, MachineScreen::new);
        ModEntities.VEHICLES.values().forEach(t -> EntityRendererRegistry.register(t, VehicleRenderer::new));
        EntityRendererRegistry.register(ModEntities.SHELL, ItemModelRenderer::new);
        EntityRendererRegistry.register(ModEntities.MISSILE, ItemModelRenderer::new);
        EntityRendererRegistry.register(ModEntities.ROCKET, ItemModelRenderer::new);
        BlockEntityRenderers.register(ModBlockEntities.MACHINE, MachineRenderer::new);
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.get(MachineType.CRAFTING_STATION), RenderType.translucent());

        RELOAD = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.fnations.reload", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "category.fnations"));
        TABLET = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.fnations.tablet", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, "category.fnations"));
        ClientPlayNetworking.registerGlobalReceiver(UiPayload.TYPE, (p, ctx) -> FnUiScreen.receive(p.data()));

        ClientPlayNetworking.registerGlobalReceiver(PromptOpenPayload.TYPE, (p, ctx) -> ctx.client().setScreen(new PromptScreen(p.id(), p.question(), p.initial())));

        ClientTickEvents.START_CLIENT_TICK.register(mc -> {
            LocalPlayer p = mc.player;
            if (p != null && p.getVehicle() instanceof VehicleEntity v && v.getControllingPassenger() == p) v.setInput(p.input.keyPresses);
        });
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        HudRenderCallback.EVENT.register((g, dt) -> Hud.render(g));
    }

    private void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) return;
        if (fireCooldown > 0) fireCooldown--;
        if (vehicleFireCooldown > 0) vehicleFireCooldown--;
        while (TABLET.consumeClick()) {
            if (mc.screen == null) ClientPlayNetworking.send(new UiActionPayload("nav:accueil", "{}"));
        }
        while (RELOAD.consumeClick()) {
            if (p.getMainHandItem().getItem() instanceof GunItem) ClientPlayNetworking.send(new ActionPayload(ActionPayload.Kind.RELOAD, 0));
        }
        boolean free = mc.screen == null;
        // ---- Véhicules
        if (p.getVehicle() instanceof VehicleEntity v && v.getControllingPassenger() == p) {
            if (free && mc.options.keyAttack.isDown() && v.kind.weapon != VehicleKind.Weapon.NONE && vehicleFireCooldown == 0) {
                ClientPlayNetworking.send(new ActionPayload(ActionPayload.Kind.VEHICLE_FIRE, 0));
                vehicleFireCooldown = v.kind.weapon == VehicleKind.Weapon.CANNON ? 10 : 2;
            }
            boolean jump = p.input.keyPresses.jump();
            if (v.kind == VehicleKind.TRACTOR && jump && !jumpWasDown) ClientPlayNetworking.send(new ActionPayload(ActionPayload.Kind.VEHICLE_TOGGLE, 0));
            jumpWasDown = jump;
            useWasDown = false;
            return;
        }
        // ---- Armes à feu
        ItemStack held = p.getMainHandItem();
        boolean down = free && mc.options.keyUse.isDown();
        if (held.getItem() instanceof GunItem g && down && fireCooldown == 0 && (!useWasDown || g.automatic)) {
            boolean aim = p.isShiftKeyDown();
            ClientPlayNetworking.send(new ActionPayload(ActionPayload.Kind.FIRE, (aim ? 1 : 0) | (useWasDown ? 2 : 0)));
            fireCooldown = Math.max(1, g.fireDelay);
            if (GunItem.ammo(held) > 0) {
                p.setXRot(p.getXRot() - g.recoil * (aim ? 0.5f : 1f));
                p.setYRot(p.getYRot() + (p.getRandom().nextFloat() - 0.5f) * g.recoil * 0.4f);
            }
        }
        useWasDown = down;
    }

    @SuppressWarnings("unused")
    private static String unused() { return Txt.PREFIX; }
}

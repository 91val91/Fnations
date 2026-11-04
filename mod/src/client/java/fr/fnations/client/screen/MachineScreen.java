package fr.fnations.client.screen;

import fr.fnations.FNations;
import fr.fnations.machine.MachineMenu;
import fr.fnations.machine.MachineType;
import fr.fnations.net.LaunchPayload;
import fr.fnations.util.Txt;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

/** Écran des machines : jauges d'énergie et de chaleur, progression, marche/arrêt, ciblage du lanceur. */
public final class MachineScreen extends AbstractContainerScreen<MachineMenu> {
    private static final ResourceLocation TEX = FNations.id("textures/gui/machine.png");
    private EditBox tx, tz;
    private Button toggle;

    public MachineScreen(MachineMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = 176;
        imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        MachineType t = menu.type;
        if (t != MachineType.MISSILE_LAUNCHER) {
            toggle = addRenderableWidget(Button.builder(Component.literal("⏻"), b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, MachineMenu.BUTTON_TOGGLE))
                    .bounds(leftPos + imageWidth - 26, topPos + 4, 20, 14).build());
        } else {
            tx = new EditBox(font, leftPos + 30, topPos + 46, 50, 14, Component.literal("X"));
            tz = new EditBox(font, leftPos + 96, topPos + 46, 50, 14, Component.literal("Z"));
            tx.setFilter(s -> s.matches("-?\\d{0,8}"));
            tz.setFilter(s -> s.matches("-?\\d{0,8}"));
            if (menu.open.hasTarget()) {
                tx.setValue(String.valueOf(menu.open.tx()));
                tz.setValue(String.valueOf(menu.open.tz()));
            }
            addRenderableWidget(tx);
            addRenderableWidget(tz);
            addRenderableWidget(Button.builder(Txt.mm("<aqua>Verrouiller"), b -> send(false)).bounds(leftPos + 22, topPos + 63, 64, 14).build());
            addRenderableWidget(Button.builder(Txt.mm("<red><b>LANCER"), b -> send(true)).bounds(leftPos + 92, topPos + 63, 56, 14).build());
        }
    }

    private void send(boolean fire) {
        try {
            int x = Integer.parseInt(tx.getValue()), z = Integer.parseInt(tz.getValue());
            ClientPlayNetworking.send(new LaunchPayload(menu.open.pos(), x, z, fire));
        } catch (NumberFormatException ignored) {
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if ((tx != null && tx.isFocused()) || (tz != null && tz.isFocused())) {
            if (key == 256) { onClose(); return true; }
            return (tx.isFocused() ? tx : tz).keyPressed(key, scan, mods) || true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
        int x = leftPos, y = topPos;
        if (mx >= x + 8 && mx < x + 20 && my >= y + 17 && my < y + 69) {
            g.renderComponentTooltip(font, List.of(Txt.mm("<yellow>⚡ Énergie"), Txt.mm("<white>" + menu.energy() + " / " + menu.capacity() + " E")), mx, my);
        }
        if (menu.type.heatRate > 0 && mx >= x + 156 && mx < x + 168 && my >= y + 17 && my < y + 69) {
            List<Component> l = new ArrayList<>();
            l.add(Txt.mm("<gold>🌡 Chaleur : " + (int) menu.heat() + "°C"));
            l.add(Txt.mm("<gray>Surchauffe : 100°C • Explosion : 150°C"));
            if (menu.overheated()) l.add(Txt.mm("<red><b>⚠ SURCHAUFFE ! Coupez l'interrupteur !"));
            g.renderComponentTooltip(font, l, mx, my);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos;
        g.blit(RenderType::guiTextured, TEX, x, y, 0, 0, imageWidth, imageHeight, 256, 256);
        // cases de la machine (dessinées à leur position réelle)
        for (int i = 0; i < menu.machineSlots(); i++) {
            var sl = menu.slots.get(i);
            int sx = x + sl.x - 1, sy = y + sl.y - 1;
            g.fill(sx, sy, sx + 18, sy + 18, 0xFF0E1013);
            g.fill(sx + 1, sy + 1, sx + 18, sy + 18, 0xFF4A515B);
            g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF1D2025);
        }
        // jauge d'énergie (verticale, gauche)
        double e = menu.capacity() <= 0 ? 0 : Math.min(1, menu.energy() / (double) menu.capacity());
        int h = (int) Math.round(e * 50);
        g.fill(x + 9, y + 18, x + 19, y + 68, 0xFF202020);
        g.fill(x + 9, y + 68 - h, x + 19, y + 68, 0xFFE8C31C);
        // jauge de chaleur (verticale, droite)
        if (menu.type.heatRate > 0) {
            double hr = Math.min(1, menu.heat() / 150.0);
            int hh = (int) Math.round(hr * 50);
            int col = menu.heat() >= 100 ? 0xFFE02020 : menu.heat() >= 70 ? 0xFFE08020 : menu.heat() >= 40 ? 0xFFE0C020 : 0xFF40C040;
            g.fill(x + 157, y + 18, x + 167, y + 68, 0xFF202020);
            g.fill(x + 157, y + 68 - hh, x + 167, y + 68, col);
            g.fill(x + 157, y + 68 - 33, x + 167, y + 68 - 32, 0xFFFFFFFF);
        }
        // progression
        if (menu.type.kind == MachineType.Kind.CONSUMER) {
            int w = menu.maxProgress() <= 0 ? 0 : (int) Math.round(22.0 * menu.progress() / menu.maxProgress());
            g.fill(x + 76, y + 38, x + 100, y + 46, 0xFF303030);
            g.fill(x + 77, y + 39, x + 77 + w, y + 45, 0xFF4FD04F);
        }
        if (menu.type == MachineType.COAL_GENERATOR && menu.burn() > 0) {
            g.fill(x + 81, y + 54, x + 95, y + 58, 0xFFFF8A1C);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, title, 24, 6, 0xFFE8C26A, false);
        if (menu.type != MachineType.MISSILE_LAUNCHER) g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFB8B8B8, false);
        String status;
        if (menu.overheated()) status = "<red><b>SURCHAUFFE";
        else if (menu.tripped()) status = "<red>Coupée (interrupteur)";
        else if (!menu.enabled()) status = "<gray>Arrêtée";
        else if (menu.working()) status = "<green>En marche";
        else status = "<yellow>En attente";
        if (menu.type.solar()) status += " <gray>• Soleil " + menu.solar() + "%";
        if (menu.type == MachineType.MISSILE_LAUNCHER) {
            g.drawString(font, Txt.mm("<gray>X"), 22, 49, 0xFFFFFFFF, false);
            g.drawString(font, Txt.mm("<gray>Z"), 88, 49, 0xFFFFFFFF, false);
            status = "<gray>Cible : " + (tx.getValue().isEmpty() ? "<red>aucune" : "<white>" + tx.getValue() + " / " + tz.getValue());
        }
        if (menu.type == MachineType.MISSILE_LAUNCHER) g.drawString(font, Txt.mm(status), 100, 27, 0xFFFFFFFF, false);
        else g.drawString(font, Txt.mm(status), 24, 60, 0xFFFFFFFF, false);
        if (toggle != null) toggle.setMessage(Txt.mm(menu.enabled() ? "<green>⏻" : "<red>⏻"));
    }
}

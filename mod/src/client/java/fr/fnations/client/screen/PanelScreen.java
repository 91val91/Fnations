package fr.fnations.client.screen;

import fr.fnations.FNations;
import fr.fnations.gui.PanelMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** Écran des panneaux FNations (thème sombre doré, mise en page façon coffre). */
public final class PanelScreen extends AbstractContainerScreen<PanelMenu> {
    private static final ResourceLocation TEX = FNations.id("textures/gui/panel.png");
    private final int rows;

    public PanelScreen(PanelMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.rows = menu.rows;
        this.imageHeight = 114 + rows * 18;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = (width - imageWidth) / 2, y = (height - imageHeight) / 2;
        g.blit(RenderType::guiTextured, TEX, x, y, 0, 0, imageWidth, rows * 18 + 17, 256, 256);
        g.blit(RenderType::guiTextured, TEX, x, y + rows * 18 + 17, 0, 126, imageWidth, 96, 256, 256);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, title, titleLabelX, titleLabelY, 0xFFE8C26A, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFB8B8B8, false);
    }
}

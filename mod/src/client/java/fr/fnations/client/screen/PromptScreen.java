package fr.fnations.client.screen;

import fr.fnations.net.PromptAnswerPayload;
import fr.fnations.util.Txt;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Fenêtre de saisie demandée par le serveur (montants, noms, coordonnées...). */
public final class PromptScreen extends Screen {
    private final int id;
    private final String initial;
    private EditBox box;
    private boolean answered;

    public PromptScreen(int id, String question, String initial) {
        super(Txt.mm(question));
        this.id = id;
        this.initial = initial;
    }

    @Override
    protected void init() {
        int w = 240, x = (width - w) / 2, y = height / 2 - 10;
        box = new EditBox(font, x, y, w, 20, Component.literal(""));
        box.setMaxLength(256);
        box.setValue(initial);
        addRenderableWidget(box);
        setInitialFocus(box);
        addRenderableWidget(Button.builder(Txt.mm("<green>Valider"), b -> send(false)).bounds(x, y + 30, 115, 20).build());
        addRenderableWidget(Button.builder(Txt.mm("<red>Annuler"), b -> send(true)).bounds(x + 125, y + 30, 115, 20).build());
    }

    private void send(boolean cancel) {
        if (answered) return;
        answered = true;
        ClientPlayNetworking.send(new PromptAnswerPayload(id, cancel ? "" : box.getValue(), cancel));
        onClose();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { send(false); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void removed() {
        if (!answered) {
            answered = true;
            ClientPlayNetworking.send(new PromptAnswerPayload(id, "", true));
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        int w = 260, x = (width - w) / 2, y = height / 2 - 44;
        g.fill(x, y, x + w, y + 98, 0xE0141820);
        g.fill(x, y, x + w, y + 1, 0xFFE0B81C);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        int y = height / 2 - 44;
        g.drawCenteredString(font, Txt.mm("<gold><b>FNations"), width / 2, y + 6, 0xFFFFFFFF);
        g.drawCenteredString(font, title, width / 2, y + 20, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}

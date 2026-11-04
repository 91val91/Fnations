package fr.fnations.gui;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import java.util.function.Consumer;

/** Confirmation Oui / Non. */
public final class ConfirmPanel extends Panel {
    public ConfirmPanel(String question, String detail, Consumer<ServerPlayer> yes, Consumer<ServerPlayer> no) {
        super(3, "<dark_red>Confirmation");
        set(13, Icons.of(Items.PAPER, "<yellow>" + question, detail == null ? new String[0] : new String[]{"<gray>" + detail}));
        button(11, Icons.of(Items.LIME_CONCRETE, "<green><b>Confirmer"), p -> { p.closeContainer(); yes.accept(p); });
        button(15, Icons.of(Items.RED_CONCRETE, "<red><b>Annuler"), p -> { if (no != null) no.accept(p); else p.closeContainer(); });
        fill(Items.GRAY_STAINED_GLASS_PANE);
    }
}

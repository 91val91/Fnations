package fr.fnations.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

/** Menu de confirmation Oui / Non. */
public final class ConfirmGui extends Gui {
    public ConfirmGui(String question, String detail, Consumer<Player> yes, Consumer<Player> no) {
        super(3, "<dark_red>Confirmation");
        fill(Material.GRAY_STAINED_GLASS_PANE);
        set(13, Icons.of(Material.PAPER, "<yellow>" + question, detail == null ? new String[0] : new String[]{"<gray>" + detail}));
        button(11, Icons.of(Material.LIME_CONCRETE, "<green><b>Confirmer"), p -> { p.closeInventory(); yes.accept(p); });
        button(15, Icons.of(Material.RED_CONCRETE, "<red><b>Annuler"), p -> { if (no != null) no.accept(p); else p.closeInventory(); });
    }
}

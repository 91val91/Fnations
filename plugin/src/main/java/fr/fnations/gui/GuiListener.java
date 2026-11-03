package fr.fnations.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/** Protège les menus et dispatch les clics. */
public final class GuiListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Gui gui)) return;
        int raw = e.getRawSlot();
        int topSize = e.getView().getTopInventory().getSize();
        boolean inTop = raw >= 0 && raw < topSize;
        if (inTop) {
            if (!gui.isEditable(raw)) {
                e.setCancelled(true);
                gui.handleClick(e);
            }
        } else {
            // Inventaire du joueur : shift-clic / double-clic interdits sauf si le menu a des slots éditables
            if ((e.isShiftClick() || e.getAction() == InventoryAction.COLLECT_TO_CURSOR) && !gui.hasEditable()) {
                e.setCancelled(true);
            }
            if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR) e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Gui gui)) return;
        int topSize = e.getView().getTopInventory().getSize();
        for (int raw : e.getRawSlots()) {
            if (raw < topSize && !gui.isEditable(raw)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof Gui gui && e.getPlayer() instanceof Player p) gui.handleClose(p);
    }
}

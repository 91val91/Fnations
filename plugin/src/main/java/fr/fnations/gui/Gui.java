package fr.fnations.gui;

import fr.fnations.util.Txt;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Inventaire-menu générique avec actions par slot. */
public class Gui implements InventoryHolder {
    protected final Inventory inventory;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();
    /** Slots où le joueur peut poser/retirer des items (machines, lanceur). */
    protected final Set<Integer> editable = new HashSet<>();
    private Consumer<Player> onClose;

    public Gui(int rows, String title) {
        this.inventory = Bukkit.createInventory(this, rows * 9, Txt.raw(title));
    }

    /** Permet à une sous-classe de fournir un inventaire déjà existant (machines persistantes). */
    protected Gui(Inventory existing) { this.inventory = existing; }

    @Override
    public @NotNull Inventory getInventory() { return inventory; }

    public Gui set(int slot, ItemStack item) {
        inventory.setItem(slot, item);
        actions.remove(slot);
        return this;
    }

    public Gui set(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        inventory.setItem(slot, item);
        actions.put(slot, action);
        return this;
    }

    /** Action simple qui reçoit le joueur. */
    public Gui button(int slot, ItemStack item, Consumer<Player> action) {
        return set(slot, item, e -> action.accept((Player) e.getWhoClicked()));
    }

    public void clear() {
        for (int i = 0; i < inventory.getSize(); i++) if (!editable.contains(i)) inventory.setItem(i, null);
        actions.clear();
    }

    public void fill(Material pane) {
        ItemStack it = Icons.pane(pane);
        for (int i = 0; i < inventory.getSize(); i++) {
            if (editable.contains(i)) continue;
            if (inventory.getItem(i) == null) inventory.setItem(i, it);
        }
    }

    public void border(Material pane) {
        int size = inventory.getSize();
        ItemStack it = Icons.pane(pane);
        for (int i = 0; i < size; i++) {
            if (i < 9 || i >= size - 9 || i % 9 == 0 || i % 9 == 8) inventory.setItem(i, it);
        }
    }

    public void onClose(Consumer<Player> c) { this.onClose = c; }

    void handleClick(InventoryClickEvent e) {
        Consumer<InventoryClickEvent> a = actions.get(e.getRawSlot());
        if (a != null) a.accept(e);
    }

    void handleClose(Player p) { if (onClose != null) onClose.accept(p); }

    boolean isEditable(int rawSlot) { return editable.contains(rawSlot); }

    public boolean isEditableSlot(int slot) { return editable.contains(slot); }

    boolean hasEditable() { return !editable.isEmpty(); }

    public void open(Player p) { p.openInventory(inventory); }

    /** Slots intérieurs (hors bordure) d'un inventaire de n lignes. */
    public static int[] innerSlots(int rows) {
        int[] out = new int[(rows - 2) * 7];
        int k = 0;
        for (int r = 1; r < rows - 1; r++) for (int c = 1; c < 8; c++) out[k++] = r * 9 + c;
        return out;
    }
}

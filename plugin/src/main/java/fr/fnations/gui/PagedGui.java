package fr.fnations.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.Consumer;

/** Menu paginé sur 6 lignes : contenu dans les slots intérieurs, navigation en bas. */
public class PagedGui extends Gui {
    public record Entry(ItemStack icon, Consumer<Player> action) {}

    private final List<Entry> entries;
    private final Consumer<Player> back;
    private int page;

    public PagedGui(String title, List<Entry> entries, Consumer<Player> back) {
        super(6, title);
        this.entries = entries;
        this.back = back;
        render();
    }

    private void render() {
        clear();
        border(Material.BLACK_STAINED_GLASS_PANE);
        int[] slots = innerSlots(6);
        int per = slots.length;
        int pages = Math.max(1, (entries.size() + per - 1) / per);
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < per; i++) {
            int idx = page * per + i;
            if (idx >= entries.size()) break;
            Entry en = entries.get(idx);
            if (en.action() == null) set(slots[i], en.icon());
            else button(slots[i], en.icon(), en.action());
        }
        if (page > 0) button(45, Icons.prev(), p -> { page--; render(); });
        if (page < pages - 1) button(53, Icons.next(), p -> { page++; render(); });
        if (back != null) button(49, Icons.back(), back);
        else button(49, Icons.close(), Player::closeInventory);
    }
}

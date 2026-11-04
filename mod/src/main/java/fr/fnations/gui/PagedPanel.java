package fr.fnations.gui;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.function.Consumer;

/** Panneau paginé sur 6 lignes. */
public class PagedPanel extends Panel {
    public record Entry(ItemStack icon, Consumer<ServerPlayer> action) {}

    private final List<Entry> entries;
    private final Consumer<ServerPlayer> back;
    private int page;

    public PagedPanel(String title, List<Entry> entries, Consumer<ServerPlayer> back) {
        super(6, title);
        this.entries = entries;
        this.back = back;
        render();
    }

    private void render() {
        clear();
        border(Items.BLACK_STAINED_GLASS_PANE);
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
        else button(49, Icons.close(), ServerPlayer::closeContainer);
    }
}

package fr.fnations.gui;

import fr.fnations.util.Txt;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Menu piloté par le serveur : icônes + actions par case (équivalent des GUIs du plugin). */
public class Panel {
    /** Informations sur le clic. */
    public record Click(int button, ClickType type) {
        public boolean shift() { return type == ClickType.QUICK_MOVE; }

        public boolean right() { return button == 1; }
    }

    public interface Action { void run(ServerPlayer player, Click click); }

    protected final int rows;
    protected final Component title;
    protected final SimpleContainer container;
    private final Map<Integer, Action> actions = new HashMap<>();
    private Consumer<ServerPlayer> onClose;

    public Panel(int rows, String title) {
        this.rows = Math.max(1, Math.min(6, rows));
        this.title = Txt.mm(title);
        this.container = new SimpleContainer(this.rows * 9);
    }

    public int size() { return rows * 9; }

    public Panel set(int slot, ItemStack item) {
        container.setItem(slot, item);
        actions.remove(slot);
        return this;
    }

    public Panel set(int slot, ItemStack item, Action action) {
        container.setItem(slot, item);
        actions.put(slot, action);
        return this;
    }

    public Panel button(int slot, ItemStack item, Consumer<ServerPlayer> action) {
        return set(slot, item, (p, c) -> action.accept(p));
    }

    public ItemStack get(int slot) { return container.getItem(slot); }

    public void clear() {
        container.clearContent();
        actions.clear();
    }

    public void fill(Item pane) {
        ItemStack it = Icons.pane(pane);
        for (int i = 0; i < size(); i++) if (container.getItem(i).isEmpty()) container.setItem(i, it.copy());
    }

    public void border(Item pane) {
        ItemStack it = Icons.pane(pane);
        int size = size();
        for (int i = 0; i < size; i++) if (i < 9 || i >= size - 9 || i % 9 == 0 || i % 9 == 8) container.setItem(i, it.copy());
    }

    public void onClose(Consumer<ServerPlayer> c) { this.onClose = c; }

    void click(ServerPlayer p, int slot, int button, ClickType type) {
        Action a = actions.get(slot);
        if (a != null) a.run(p, new Click(button, type));
    }

    void closed(Player p) {
        if (onClose != null && p instanceof ServerPlayer sp) onClose.accept(sp);
    }

    public void open(ServerPlayer p) {
        Panel self = this;
        p.openMenu(new ExtendedScreenHandlerFactory<Integer>() {
            @Override
            public Integer getScreenOpeningData(ServerPlayer player) { return rows; }

            @Override
            public Component getDisplayName() { return title; }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
                return new PanelMenu(id, inv, rows, container, self);
            }
        });
    }

    /** Cases intérieures (hors bordure) d'un panneau de n lignes. */
    public static int[] innerSlots(int rows) {
        int[] out = new int[(rows - 2) * 7];
        int k = 0;
        for (int r = 1; r < rows - 1; r++) for (int c = 1; c < 8; c++) out[k++] = r * 9 + c;
        return out;
    }
}

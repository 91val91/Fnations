package fr.fnations.gui;

import fr.fnations.registry.ModMenus;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Conteneur d'un {@link Panel} : cases d'icônes non manipulables + inventaire du joueur. */
public final class PanelMenu extends AbstractContainerMenu {
    public final int rows;
    private final Panel panel;

    /** Côté client. */
    public PanelMenu(int id, Inventory inv, Integer rows) {
        this(id, inv, rows, new SimpleContainer(rows * 9), null);
    }

    public PanelMenu(int id, Inventory inv, int rows, Container container, Panel panel) {
        super(ModMenus.PANEL, id);
        this.rows = rows;
        this.panel = panel;
        for (int r = 0; r < rows; r++) for (int c = 0; c < 9; c++) addSlot(new IconSlot(container, c + r * 9, 8 + c * 18, 18 + r * 18));
        int off = (rows - 4) * 18;
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, 103 + r * 18 + off));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 8 + c * 18, 161 + off));
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (slotId >= 0 && slotId < rows * 9) {
            if (panel != null && player instanceof ServerPlayer sp) panel.click(sp, slotId, button, type);
            return;
        }
        if (type == ClickType.QUICK_MOVE || type == ClickType.PICKUP_ALL || type == ClickType.QUICK_CRAFT) return;
        super.clicked(slotId, button, type, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int i) { return ItemStack.EMPTY; }

    @Override
    public boolean stillValid(Player player) { return true; }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (panel != null) panel.closed(player);
    }

    /** Case d'icône : rien ne peut y être pris ni posé. */
    static final class IconSlot extends Slot {
        IconSlot(Container c, int i, int x, int y) { super(c, i, x, y); }

        @Override
        public boolean mayPickup(Player player) { return false; }

        @Override
        public boolean mayPlace(ItemStack stack) { return false; }
    }
}

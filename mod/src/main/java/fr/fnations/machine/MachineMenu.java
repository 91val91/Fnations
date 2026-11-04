package fr.fnations.machine;

import fr.fnations.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** Menu d'une machine : cases selon le type + données synchronisées (énergie, chaleur, progression). */
public final class MachineMenu extends AbstractContainerMenu {
    public static final int DATA_COUNT = 8;
    public static final int BUTTON_TOGGLE = 0;

    /** Données d'ouverture envoyées au client. */
    public record OpenData(BlockPos pos, boolean hasTarget, int tx, int tz) {
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenData> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, OpenData::pos, ByteBufCodecs.BOOL, OpenData::hasTarget,
                ByteBufCodecs.VAR_INT, OpenData::tx, ByteBufCodecs.VAR_INT, OpenData::tz, OpenData::new);
    }

    public final MachineType type;
    public final OpenData open;
    private final Container container;
    private final ContainerData data;
    private final MachineBlockEntity be;

    /** Côté client. */
    public MachineMenu(int id, Inventory inv, OpenData open) {
        this(id, inv, new SimpleContainer(2), new SimpleContainerData(DATA_COUNT), typeAt(inv, open.pos()), open, null);
    }

    public MachineMenu(int id, Inventory inv, MachineBlockEntity be, ContainerData data, MachineType type) {
        this(id, inv, be, data, type, be.getScreenOpeningData(null), be);
    }

    private MachineMenu(int id, Inventory inv, Container c, ContainerData data, MachineType type, OpenData open, MachineBlockEntity be) {
        super(ModMenus.MACHINE, id);
        this.type = type;
        this.open = open;
        this.container = c;
        this.data = data;
        this.be = be;
        switch (type.kind) {
            case CONSUMER -> {
                addSlot(new FilteredSlot(c, MachineBlockEntity.SLOT_A, 52, 35));
                addSlot(new OutputSlot(c, MachineBlockEntity.SLOT_B, 112, 35));
            }
            case LAUNCHER -> addSlot(new FilteredSlot(c, MachineBlockEntity.SLOT_A, 80, 22));
            default -> { if (type == MachineType.COAL_GENERATOR) addSlot(new FilteredSlot(c, MachineBlockEntity.SLOT_A, 80, 35)); }
        }
        for (int r = 0; r < 3; r++) for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col + r * 9 + 9, 8 + col * 18, 84 + r * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, 8 + col * 18, 142));
        addDataSlots(data);
    }

    private static MachineType typeAt(Inventory inv, BlockPos pos) {
        BlockState s = inv.player.level().getBlockState(pos);
        return s.getBlock() instanceof MachineBlock mb ? mb.type : MachineType.COMPRESSOR;
    }

    public int machineSlots() { return slots.size() - 36; }

    public int get(int i) { return data.get(i); }

    public int energy() { return data.get(0); }

    public int capacity() { return data.get(1); }

    public double heat() { return data.get(2) / 10.0; }

    public int progress() { return data.get(3); }

    public int maxProgress() { return data.get(4); }

    public boolean enabled() { return (data.get(5) & 1) != 0; }

    public boolean overheated() { return (data.get(5) & 2) != 0; }

    public boolean tripped() { return (data.get(5) & 4) != 0; }

    public boolean working() { return (data.get(5) & 8) != 0; }

    public int burn() { return data.get(6); }

    public int solar() { return data.get(7); }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (be != null && id == BUTTON_TOGGLE) {
            be.toggle();
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int m = machineSlots();
        if (index < m) {
            if (!moveItemStackTo(stack, m, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            for (int i = 0; i < m && !moved; i++) {
                if (slots.get(i).mayPlace(stack)) moved = moveItemStackTo(stack, i, i + 1, false);
            }
            if (!moved) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) { return container.stillValid(player); }

    /** Case d'entrée filtrée par la machine. */
    static final class FilteredSlot extends Slot {
        FilteredSlot(Container c, int i, int x, int y) { super(c, i, x, y); }

        @Override
        public boolean mayPlace(ItemStack s) { return container.canPlaceItem(getContainerSlot(), s); }
    }

    /** Case de sortie : on ne peut que prendre. */
    static final class OutputSlot extends Slot {
        OutputSlot(Container c, int i, int x, int y) { super(c, i, x, y); }

        @Override
        public boolean mayPlace(ItemStack s) { return false; }
    }
}

package fr.fnations.machine;

import fr.fnations.registry.ModBlockEntities;
import fr.fnations.server.FnServer;
import fr.fnations.util.Explosions;
import fr.fnations.util.Inv;
import fr.fnations.util.Txt;
import fr.fnations.util.Yaml;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Logique d'une machine : énergie, chaleur, recettes, surchauffe, disjoncteur, lanceur. */
public class MachineBlockEntity extends BlockEntity implements WorldlyContainer, ExtendedScreenHandlerFactory<MachineMenu.OpenData> {
    public static final double OVERHEAT = 100, MELTDOWN = 150;
    public static final int SLOT_A = 0, SLOT_B = 1;

    private final NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
    private double energy, heat;
    private boolean enabled = true, overheated, auto = true, working;
    private int progress, maxProgress, burn, solar, ticks;
    private Integer targetX, targetZ;
    private UUID owner;
    private String status = "";

    /** Valeurs synchronisées avec le menu client (shorts). */
    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> (int) energy;
                case 1 -> (int) type().capacity;
                case 2 -> (int) Math.round(heat * 10);
                case 3 -> progress;
                case 4 -> maxProgress;
                case 5 -> (enabled ? 1 : 0) | (overheated ? 2 : 0) | (level instanceof ServerLevel sl && MachineNetwork.of(sl).tripped(MachineBlockEntity.this) ? 4 : 0) | (working ? 8 : 0);
                case 6 -> burn;
                case 7 -> solar;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {}

        @Override
        public int getCount() { return MachineMenu.DATA_COUNT; }
    };

    public MachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MACHINE, pos, state);
    }

    public MachineType type() { return ((MachineBlock) getBlockState().getBlock()).type; }

    public double energy() { return energy; }

    public double heat() { return heat; }

    public boolean enabled() { return enabled; }

    public boolean auto() { return auto; }

    public boolean overheated() { return overheated; }

    public String status() { return status; }

    public Integer targetX() { return targetX; }

    public Integer targetZ() { return targetZ; }

    public UUID owner() { return owner; }

    public void setOwner(UUID u) { owner = u; setChanged(); }

    public void useEnergy(double e) { energy = Math.max(0, energy - e); setChanged(); }

    public void toggle() { enabled = !enabled; setChanged(); }

    public void toggleAuto() { auto = !auto; setChanged(); }

    public void setTarget(int x, int z) { targetX = x; targetZ = z; setChanged(); }

    public Vec3 center() { return Vec3.atCenterOf(worldPosition); }

    // ------------------------------------------------------------ registre du réseau
    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (level instanceof ServerLevel sl && !isRemoved()) MachineNetwork.of(sl).add(this);
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        if (level instanceof ServerLevel sl) MachineNetwork.of(sl).add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel sl) MachineNetwork.of(sl).remove(this);
    }

    // ------------------------------------------------------------ logique
    public static void serverTick(Level level, BlockPos pos, BlockState state, MachineBlockEntity be) {
        if (++be.ticks % 20 != 0 || !(level instanceof ServerLevel sl)) return;
        FnServer s = FnServer.get();
        if (s == null) return;
        be.second(sl, s);
    }

    private void second(ServerLevel level, FnServer s) {
        Yaml cfg = s.config();
        MachineNetwork net = MachineNetwork.of(level);
        boolean cut = net.tripped(this);
        working = false;
        MachineType t = type();
        switch (t.kind) {
            case GENERATOR -> generate(level, cut);
            case CONSUMER -> consume(level, s, cut, net, cfg);
            case LAUNCHER -> pull(net, cfg);  // charge lente, uniquement sur le surplus des générateurs
            case SWITCH -> switchTick(level, net, cfg);
            default -> {}
        }
        if (t.heatRate > 0 && heatTick(level, cfg, cut)) return;
        boolean active = working || (t == MachineType.COAL_GENERATOR && burn > 0 && !cut && enabled);
        BlockState st = getBlockState();
        if (st.getValue(MachineBlock.ACTIVE) != active) level.setBlock(worldPosition, st.setValue(MachineBlock.ACTIVE, active), 3);
        setChanged();
    }

    private void generate(ServerLevel level, boolean cut) {
        MachineType t = type();
        boolean off = !enabled || overheated || cut;
        if (t == MachineType.COAL_GENERATOR) {
            if (!off && burn <= 0 && energy < t.capacity) {
                ItemStack f = items.get(SLOT_A);
                int sec = fuelSeconds(f);
                if (sec > 0) { f.shrink(1); burn = sec; }
            }
            if (!off && burn > 0) {
                burn--;
                energy = Math.min(t.capacity, energy + t.production);
                working = true;
                status = "Combustion en cours";
                if (burn % 3 == 0) level.sendParticles(ParticleTypes.LARGE_SMOKE, center().x, center().y + 0.7, center().z, 1, 0.1, 0.1, 0.1, 0.01);
            } else status = off ? "Arrêté" : "En attente de charbon";
        } else if (t.solar()) {
            double f = solarFactor(level);
            solar = (int) Math.round(f * 100);
            status = f <= 0 ? "Nuit / pas de soleil" : "Rendement " + solar + "%";
            if (!off && f > 0) energy = Math.min(t.capacity, energy + t.production * f);
        }
    }

    private void pull(MachineNetwork net, Yaml cfg) {
        double need = type().capacity - energy;
        if (need <= 0 || net.tripped(this)) return;
        boolean launcher = type().kind == MachineType.Kind.LAUNCHER;
        double max = launcher ? cfg.getDouble("machines.charge-lanceur", 40) : cfg.getDouble("machines.debit-max", 200);
        need = Math.min(need, max);
        for (MachineBlockEntity g : net.inRange(this)) {
            if (g.type().kind != MachineType.Kind.GENERATOR || g.energy <= 0 || net.tripped(g)) continue;
            // les machines de production sont prioritaires : le lanceur ne prend que le surplus (> 25 % du stock)
            double available = launcher ? g.energy - g.type().capacity * 0.25 : g.energy;
            if (available <= 0) continue;
            double tr = Math.min(need, Math.min(available, max));
            g.energy -= tr;
            g.setChanged();
            energy += tr;
            need -= tr;
            if (need <= 0) break;
        }
    }

    private void consume(ServerLevel level, FnServer s, boolean cut, MachineNetwork net, Yaml cfg) {
        MachineRecipe r = recipe(level, s);
        maxProgress = r == null ? 0 : r.seconds();
        if (r == null) { progress = 0; status = "En attente"; return; }
        // une machine ne tire de l'énergie du réseau que lorsqu'elle a du travail
        if (enabled && !overheated && !cut && canOutput(r)) pull(net, cfg);
        if (!enabled || overheated || cut) { status = overheated ? "Surchauffe !" : cut ? "Coupée par interrupteur" : "Arrêtée"; return; }
        if (energy < type().consumption) { status = "Énergie insuffisante"; return; }
        if (!canOutput(r)) { status = "Sortie pleine"; return; }
        energy -= type().consumption;
        progress++;
        working = true;
        status = "En production";
        if (progress % 2 == 0) level.playSound(null, worldPosition, type() == MachineType.CRUSHER ? SoundEvents.GRINDSTONE_USE : SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.3f, 1.5f);
        if (progress >= r.seconds()) {
            progress = 0;
            craft(r);
        }
    }

    private MachineRecipe recipe(ServerLevel level, FnServer s) {
        ItemStack in = items.get(SLOT_A);
        if (in.isEmpty()) return null;
        for (MachineRecipe r : s.machineRecipes(type())) if (Inv.matches(in, r.input()) && in.getCount() >= r.amount()) return r;
        if (type() == MachineType.ELECTRIC_FURNACE) {
            var holder = level.recipeAccess().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(in), level);
            if (holder.isPresent()) {
                ItemStack res = holder.get().value().assemble(new SingleRecipeInput(in), level.registryAccess());
                String key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(res.getItem()).toString();
                return new MachineRecipe(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(in.getItem()).toString(), 1,
                        List.of(Map.entry(key, res.getCount())), s.config().getInt("machines.four-electrique-duree", 3));
            }
        }
        return null;
    }

    private boolean canOutput(MachineRecipe r) {
        ItemStack out = items.get(SLOT_B);
        var first = r.outputs().get(0);
        if (out.isEmpty()) return true;
        ItemStack res = Inv.stack(first.getKey(), first.getValue());
        return ItemStack.isSameItemSameComponents(out, res) && out.getCount() + res.getCount() <= out.getMaxStackSize();
    }

    /** Sorties secondaires (concasseur) : éjectées au-dessus de la machine si la sortie est occupée. */
    private void craft(MachineRecipe r) {
        items.get(SLOT_A).shrink(r.amount());
        boolean first = true;
        for (var o : r.outputs()) {
            ItemStack res = Inv.stack(o.getKey(), o.getValue());
            ItemStack out = items.get(SLOT_B);
            if (first && out.isEmpty()) items.set(SLOT_B, res);
            else if (ItemStack.isSameItemSameComponents(out, res) && out.getCount() + res.getCount() <= out.getMaxStackSize()) out.grow(res.getCount());
            else net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.1, worldPosition.getZ() + 0.5, res);
            first = false;
        }
    }

    private void switchTick(ServerLevel level, MachineNetwork net, Yaml cfg) {
        if (!enabled || !auto) return;
        double threshold = cfg.getDouble("machines.seuil-disjoncteur", 90);
        for (MachineBlockEntity o : net.inRange(this)) {
            if (o != this && o.heat >= threshold) {
                enabled = false;
                level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 1f, 0.6f);
                alert(level, "<gold>⚡ Disjoncteur déclenché : surchauffe détectée, circuit coupé.");
                break;
            }
        }
    }

    /** @return true si la machine a explosé. */
    private boolean heatTick(ServerLevel level, Yaml cfg, boolean cut) {
        if (cut) heat = Math.max(0, heat - cfg.getDouble("machines.refroidissement-coupure", 5));
        else if (overheated) heat += cfg.getDouble("machines.emballement", 1);
        else heat = Math.max(0, heat - cfg.getDouble("machines.refroidissement-passif", 0.6) + (working ? type().heatRate : 0));
        if (!overheated && heat >= OVERHEAT) {
            overheated = true;
            alert(level, "<red>⚠ <item:" + type().itemKey() + "> <red>est en SURCHAUFFE ! Coupez l'interrupteur de sécurité !");
        }
        if (overheated) {
            Vec3 c = center();
            level.sendParticles(ParticleTypes.FLAME, c.x, c.y + 0.6, c.z, 3, 0.3, 0.2, 0.3, 0.01);
            if (heat <= 0) overheated = false;
        }
        if (heat >= MELTDOWN) {
            meltdown(level, cfg);
            return true;
        }
        return false;
    }

    private void meltdown(ServerLevel level, Yaml cfg) {
        alert(level, "<dark_red><b>💥 <item:" + type().itemKey() + "> a explosé !");
        Vec3 c = center();
        level.removeBlock(worldPosition, false);
        Explosions.explode(level, c.x, c.y, c.z, (float) cfg.getDouble("machines.explosion-surchauffe", 3), true, true, null, null);
    }

    public void alert(ServerLevel level, String msg) {
        Component c = Txt.prefixed(msg);
        for (ServerPlayer p : level.players()) if (p.distanceToSqr(center()) < 48 * 48) p.sendSystemMessage(c);
        if (owner != null) {
            ServerPlayer o = level.getServer().getPlayerList().getPlayer(owner);
            if (o != null && o.level() != level) o.sendSystemMessage(c);
        }
    }

    private static int fuelSeconds(ItemStack f) {
        if (f.isEmpty()) return 0;
        if (f.is(Items.COAL) || f.is(Items.CHARCOAL)) return 16;
        if (f.is(Items.COAL_BLOCK)) return 160;
        if (f.is(Items.BLAZE_ROD)) return 24;
        return 0;
    }

    private double solarFactor(ServerLevel level) {
        if (!level.canSeeSky(worldPosition.above())) return 0;
        boolean day = level.isDay();
        double f = day ? 1 : (type() == MachineType.SOLAR_PANEL_T3 ? 0.25 : 0);
        if (level.isRaining()) f *= 0.5;
        return f;
    }

    // ------------------------------------------------------------ lanceur de missiles
    public void launcherCommand(ServerPlayer p, int x, int z, boolean fire) {
        if (type() != MachineType.MISSILE_LAUNCHER) return;
        setTarget(x, z);
        if (fire) {
            FnServer s = FnServer.get();
            if (s != null) s.missiles().launch(p, this);
        } else {
            p.sendSystemMessage(Txt.prefixed("<green>Cible verrouillée : X " + x + " Z " + z));
        }
    }

    // ------------------------------------------------------------ conteneur
    @Override
    public int getContainerSize() { return items.size(); }

    @Override
    public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }

    @Override
    public ItemStack getItem(int i) { return items.get(i); }

    @Override
    public ItemStack removeItem(int i, int n) {
        ItemStack r = ContainerHelper.removeItem(items, i, n);
        if (!r.isEmpty()) setChanged();
        return r;
    }

    @Override
    public ItemStack removeItemNoUpdate(int i) { return ContainerHelper.takeItem(items, i); }

    @Override
    public void setItem(int i, ItemStack s) {
        items.set(i, s);
        if (s.getCount() > getMaxStackSize()) s.setCount(getMaxStackSize());
        setChanged();
    }

    @Override
    public boolean stillValid(Player p) { return !isRemoved() && p.distanceToSqr(center()) <= 64; }

    @Override
    public void clearContent() { items.clear(); }

    public boolean usesSlot(int i) {
        MachineType t = type();
        if (t.kind == MachineType.Kind.CONSUMER) return true;
        return i == SLOT_A && (t == MachineType.COAL_GENERATOR || t.kind == MachineType.Kind.LAUNCHER);
    }

    @Override
    public boolean canPlaceItem(int i, ItemStack s) {
        if (!usesSlot(i)) return false;
        MachineType t = type();
        if (t == MachineType.COAL_GENERATOR) return fuelSeconds(s) > 0;
        if (t.kind == MachineType.Kind.LAUNCHER) return fr.fnations.missile.MissileManager.isMissile(s);
        return i == SLOT_A;
    }

    @Override
    public int[] getSlotsForFace(Direction d) {
        if (type().kind == MachineType.Kind.CONSUMER) return d == Direction.DOWN ? new int[]{SLOT_B} : new int[]{SLOT_A};
        return usesSlot(SLOT_A) ? new int[]{SLOT_A} : new int[0];
    }

    @Override
    public boolean canPlaceItemThroughFace(int i, ItemStack s, @Nullable Direction d) { return canPlaceItem(i, s); }

    @Override
    public boolean canTakeItemThroughFace(int i, ItemStack s, Direction d) {
        return type().kind == MachineType.Kind.CONSUMER ? i == SLOT_B : false;
    }

    // ------------------------------------------------------------ menu
    @Override
    public MachineMenu.OpenData getScreenOpeningData(ServerPlayer player) {
        return new MachineMenu.OpenData(worldPosition, targetX != null, targetX == null ? 0 : targetX, targetZ == null ? 0 : targetZ);
    }

    @Override
    public Component getDisplayName() { return Component.translatable(getBlockState().getBlock().getDescriptionId()); }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new MachineMenu(id, inv, this, data, type());
    }

    // ------------------------------------------------------------ sauvegarde
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        ContainerHelper.saveAllItems(tag, items, reg);
        tag.putDouble("energy", energy);
        tag.putDouble("heat", heat);
        tag.putBoolean("enabled", enabled);
        tag.putBoolean("overheated", overheated);
        tag.putBoolean("auto", auto);
        tag.putInt("progress", progress);
        tag.putInt("burn", burn);
        if (targetX != null) { tag.putInt("tx", targetX); tag.putInt("tz", targetZ); }
        if (owner != null) tag.putUUID("owner", owner);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        items.clear();
        ContainerHelper.loadAllItems(tag, items, reg);
        energy = tag.getDouble("energy");
        heat = tag.getDouble("heat");
        enabled = !tag.contains("enabled") || tag.getBoolean("enabled");
        overheated = tag.getBoolean("overheated");
        auto = !tag.contains("auto") || tag.getBoolean("auto");
        progress = tag.getInt("progress");
        burn = tag.getInt("burn");
        if (tag.contains("tx")) { targetX = tag.getInt("tx"); targetZ = tag.getInt("tz"); }
        if (tag.hasUUID("owner")) owner = tag.getUUID("owner");
    }
}

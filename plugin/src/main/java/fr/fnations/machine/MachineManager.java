package fr.fnations.machine;

import fr.fnations.FNations;
import fr.fnations.item.ItemRegistry;
import fr.fnations.util.Explosions;
import fr.fnations.util.Inv;
import fr.fnations.util.Locs;
import fr.fnations.util.Txt;
import fr.fnations.util.YamlStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Système de machines : réseau électrique par rayon, recettes, surchauffe et interrupteurs. */
public final class MachineManager implements Listener {
    private final FNations plugin;
    private final YamlStore store;
    private final Map<String, Machine> machines = new HashMap<>();
    private final Map<MachineType, List<MachineRecipe>> recipes = new HashMap<>();
    private final List<FurnaceRecipe> vanillaFurnace = new ArrayList<>();

    public MachineManager(FNations plugin) {
        this.plugin = plugin;
        this.store = new YamlStore(plugin, "data/machines.yml");
        reloadRecipes();
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    FNations plugin() { return plugin; }

    public double radius() { return plugin.getConfig().getDouble("machines.rayon-reseau", 16); }

    public double autoThreshold() { return plugin.getConfig().getDouble("machines.seuil-disjoncteur", 90); }

    public Collection<Machine> all() { return machines.values(); }

    public boolean isMachine(Block b) { return machines.containsKey(Locs.blockKey(b)); }

    public Machine at(Block b) { return machines.get(Locs.blockKey(b)); }

    // ------------------------------------------------------------ recettes
    public void reloadRecipes() {
        recipes.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "recipes.yml"));
        Map<String, MachineType> map = Map.of("compresseur", MachineType.COMPRESSOR, "four-electrique", MachineType.ELECTRIC_FURNACE, "concasseur", MachineType.CRUSHER);
        for (var e : map.entrySet()) {
            List<MachineRecipe> list = new ArrayList<>();
            for (Map<?, ?> r : y.getMapList("machines." + e.getKey())) {
                List<Map.Entry<String, Integer>> outs = new ArrayList<>();
                Object o = r.get("sortie");
                if (o instanceof List<?> l) for (Object x : l) outs.add(Inv.parse(String.valueOf(x)));
                else if (o != null) outs.add(Inv.parse(String.valueOf(o)));
                Map.Entry<String, Integer> in = Inv.parse(String.valueOf(r.get("entree")));
                Object d = r.get("duree");
                list.add(new MachineRecipe(in.getKey(), in.getValue(), outs, d instanceof Number n ? n.intValue() : 5));
            }
            recipes.put(e.getValue(), list);
        }
        vanillaFurnace.clear();
        Iterator<Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) if (it.next() instanceof FurnaceRecipe fr) vanillaFurnace.add(fr);
    }

    MachineRecipe recipeFor(Machine m) {
        ItemStack in = m.gui == null ? null : m.gui.slot(MachineGui.INPUT);
        if (in == null || in.getType().isAir()) return null;
        for (MachineRecipe r : recipes.getOrDefault(m.type, List.of())) if (Inv.matches(in, r.input()) && in.getAmount() >= r.amount()) return r;
        if (m.type == MachineType.ELECTRIC_FURNACE && ItemRegistry.idOf(in) == null) {
            for (FurnaceRecipe fr : vanillaFurnace) if (fr.getInputChoice().test(in)) {
                return new MachineRecipe(in.getType().name(), 1, List.of(Map.entry(fr.getResult().getType().name(), fr.getResult().getAmount())),
                        plugin.getConfig().getInt("machines.four-electrique-duree", 3));
            }
        }
        return null;
    }

    public List<MachineRecipe> recipes(MachineType t) { return recipes.getOrDefault(t, List.of()); }

    // ------------------------------------------------------------ pose / casse / interaction
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        MachineType t = MachineType.byItem(ItemRegistry.idOf(e.getItemInHand()));
        if (t == null) return;
        Block b = e.getBlockPlaced();
        Machine m = new Machine(Locs.blockKey(b), t, e.getPlayer().getUniqueId(), b);
        if (t.hasInventory()) m.gui = new MachineGui(m, this);
        machines.put(m.key, m);
        spawnDisplay(m);
        Txt.ok(e.getPlayer(), ItemRegistry.def(t.item).name() + " <green>installé(e).");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Machine m = at(e.getBlock());
        if (m == null) return;
        e.setDropItems(false);
        remove(m, true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        Machine m = at(e.getClickedBlock());
        if (m == null) return;
        if (e.getPlayer().isSneaking() && e.getItem() != null && e.getItem().getType().isBlock()) return; // poser un bloc contre
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        if (!plugin.territory().canBuild(p, m.block.getLocation())) { Txt.err(p, "Cette machine ne vous appartient pas."); return; }
        if (m.type == MachineType.CRAFTING_STATION) { plugin.station().open(p); return; }
        if (m.type == MachineType.SWITCH && m.gui == null) m.gui = new MachineGui(m, this);
        if (m.gui != null) m.gui.openFor(p);
        else {
            Txt.send(p, ItemRegistry.def(m.type.item).name() + " <gray>— Énergie <yellow>" + (int) m.energy + "/" + (int) m.type.capacity
                    + " <gray>| " + m.status);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) { explode(e.blockList()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(BlockExplodeEvent e) { explode(e.blockList()); }

    private void explode(List<Block> blocks) {
        blocks.removeIf(b -> {
            Machine m = at(b);
            if (m == null) return false;
            remove(m, true);
            b.setType(Material.AIR);
            return true;
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onPiston(BlockPistonExtendEvent e) { if (e.getBlocks().stream().anyMatch(this::isMachine)) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void onPiston(BlockPistonRetractEvent e) { if (e.getBlocks().stream().anyMatch(this::isMachine)) e.setCancelled(true); }

    private void remove(Machine m, boolean drop) {
        machines.remove(m.key);
        if (m.display != null) m.display.remove();
        Location c = m.center();
        if (m.gui != null) {
            for (var v : new ArrayList<>(m.gui.getInventory().getViewers())) v.closeInventory();
            if (drop) for (int s : new int[]{MachineGui.INPUT, MachineGui.OUTPUT, MachineGui.FUEL}) {
                ItemStack it = m.gui.slot(s);
                if (it != null && !it.getType().isAir() && m.gui.isEditableSlot(s)) c.getWorld().dropItemNaturally(c, it);
            }
        }
        if (drop) c.getWorld().dropItemNaturally(c, ItemRegistry.create(m.type.item));
    }

    // ------------------------------------------------------------ affichage 3D
    private void spawnDisplay(Machine m) {
        if (m.display != null && m.display.isValid()) return;
        m.display = m.block.getWorld().spawn(m.center(), ItemDisplay.class, d -> {
            d.setItemStack(ItemRegistry.create(m.type.item));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBillboard(Display.Billboard.FIXED);
            d.setPersistent(false);
            d.setBrightness(new Display.Brightness(15, 15));
            float s = 1.003f;
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(s, s, s), new AxisAngle4f()));
        });
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        String w = e.getWorld().getName();
        int cx = e.getChunk().getX(), cz = e.getChunk().getZ();
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Machine m : machines.values())
                if (m.block.getWorld().getName().equals(w) && (m.block.getX() >> 4) == cx && (m.block.getZ() >> 4) == cz) spawnDisplay(m);
        });
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent e) {
        for (Machine m : machines.values()) {
            if (m.display != null && m.block.getWorld() == e.getWorld() && (m.block.getX() >> 4) == e.getChunk().getX() && (m.block.getZ() >> 4) == e.getChunk().getZ()) {
                m.display.remove();
                m.display = null;
            }
        }
    }

    // ------------------------------------------------------------ réseau
    public List<Machine> network(Machine m) {
        List<Machine> l = new ArrayList<>();
        double r2 = radius() * radius();
        for (Machine o : machines.values()) {
            if (o.block.getWorld() != m.block.getWorld()) continue;
            if (o.block.getLocation().distanceSquared(m.block.getLocation()) <= r2) l.add(o);
        }
        return l;
    }

    /** La machine est coupée par un interrupteur ouvert à portée. */
    public boolean tripped(Machine m) {
        if (m.type == MachineType.SWITCH) return false;
        for (Machine o : network(m)) if (o.type == MachineType.SWITCH && !o.enabled) return true;
        return false;
    }

    private boolean loaded(Machine m) { return m.block.getWorld().isChunkLoaded(m.block.getX() >> 4, m.block.getZ() >> 4); }

    private void tick() {
        List<Machine> list = new ArrayList<>(machines.values());
        // 1. Production
        for (Machine m : list) {
            if (!loaded(m) || m.type.kind != MachineType.Kind.GENERATOR) continue;
            boolean off = !m.enabled || m.overheated || tripped(m);
            if (m.type == MachineType.COAL_GENERATOR) {
                if (!off && m.burn <= 0 && m.energy < m.type.capacity) {
                    ItemStack f = m.gui.slot(MachineGui.FUEL);
                    int sec = fuelSeconds(f);
                    if (sec > 0) { f.setAmount(f.getAmount() - 1); m.burn = sec; }
                }
                if (!off && m.burn > 0) {
                    m.burn--;
                    m.energy = Math.min(m.type.capacity, m.energy + m.type.production);
                    m.heat += m.type.heatRate;
                    m.status = "Combustion en cours";
                    if (m.burn % 3 == 0) m.block.getWorld().spawnParticle(Particle.LARGE_SMOKE, m.center().add(0, 0.7, 0), 1, 0.1, 0.1, 0.1, 0.01);
                } else m.status = off ? "Arrêté" : "En attente de charbon";
            } else if (m.type.solar()) {
                double f = solarFactor(m);
                m.status = f <= 0 ? "Nuit / pas de soleil" : "Rendement " + (int) (f * 100) + "%";
                if (!off) m.energy = Math.min(m.type.capacity, m.energy + m.type.production * f);
            }
        }
        // 2. Distribution vers les consommateurs et lanceurs
        for (Machine m : list) {
            if (!loaded(m) || (m.type.kind != MachineType.Kind.CONSUMER && m.type.kind != MachineType.Kind.LAUNCHER)) continue;
            double need = m.type.capacity - m.energy;
            if (need <= 0 || tripped(m)) continue;
            for (Machine g : network(m)) {
                if (g.type.kind != MachineType.Kind.GENERATOR || g.energy <= 0 || tripped(g)) continue;
                double t = Math.min(need, Math.min(g.energy, plugin.getConfig().getDouble("machines.debit-max", 200)));
                g.energy -= t;
                m.energy += t;
                need -= t;
                if (need <= 0) break;
            }
        }
        // 3. Travail des consommateurs
        for (Machine m : list) {
            if (!loaded(m) || m.type.kind != MachineType.Kind.CONSUMER) continue;
            boolean working = false;
            MachineRecipe r = recipeFor(m);
            if (m.enabled && !m.overheated && !tripped(m) && r != null) {
                if (m.energy >= m.type.consumption) {
                    if (canOutput(m, r)) {
                        m.energy -= m.type.consumption;
                        m.progress++;
                        working = true;
                        m.status = "En production";
                        if (m.progress >= r.seconds()) {
                            m.progress = 0;
                            craft(m, r);
                        }
                    } else m.status = "Sortie pleine";
                } else m.status = "Énergie insuffisante";
            } else {
                m.progress = r == null ? 0 : m.progress;
                m.status = r == null ? "En attente" : m.status;
            }
            if (working) {
                m.heat += m.type.heatRate;
                if (m.progress % 2 == 0) m.block.getWorld().playSound(m.center(), m.type == MachineType.CRUSHER ? Sound.BLOCK_GRINDSTONE_USE : Sound.BLOCK_PISTON_EXTEND, 0.3f, 1.5f);
            }
        }
        // 4. Chaleur, surchauffe, disjoncteurs
        for (Machine m : list) {
            if (!loaded(m)) continue;
            if (m.type.kind == MachineType.Kind.SWITCH) {
                if (m.enabled && m.auto) {
                    for (Machine o : network(m)) if (o != m && o.heat >= autoThreshold()) {
                        m.enabled = false;
                        m.block.getWorld().playSound(m.center(), Sound.BLOCK_LEVER_CLICK, 1f, 0.6f);
                        alert(m, "<gold>⚡ Disjoncteur déclenché : surchauffe détectée, circuit coupé.");
                        break;
                    }
                }
                if (m.gui != null && !m.gui.getInventory().getViewers().isEmpty()) m.gui.render();
                continue;
            }
            if (m.type.heatRate <= 0) continue;
            boolean cut = tripped(m);
            if (cut) m.heat = Math.max(0, m.heat - plugin.getConfig().getDouble("machines.refroidissement-coupure", 5));
            else if (m.overheated) m.heat += plugin.getConfig().getDouble("machines.emballement", 1);
            else m.heat = Math.max(0, m.heat - plugin.getConfig().getDouble("machines.refroidissement-passif", 0.6));
            if (!m.overheated && m.heat >= Machine.OVERHEAT) {
                m.overheated = true;
                alert(m, "<red>⚠ " + ItemRegistry.def(m.type.item).name() + " <red>est en SURCHAUFFE ! Coupez l'interrupteur de sécurité !");
            }
            if (m.overheated) {
                m.block.getWorld().spawnParticle(Particle.FLAME, m.center().add(0, 0.6, 0), 3, 0.3, 0.2, 0.3, 0.01);
                if (m.heat <= 0) m.overheated = false;
            }
            if (m.heat >= Machine.MELTDOWN) {
                meltdown(m);
                continue;
            }
            if (m.gui != null && !m.gui.getInventory().getViewers().isEmpty()) m.gui.render();
        }
        for (Machine m : list) if (m.type.kind == MachineType.Kind.LAUNCHER && m.gui != null && !m.gui.getInventory().getViewers().isEmpty()) m.gui.render();
    }

    private void alert(Machine m, String msg) {
        for (Player p : m.block.getWorld().getPlayers()) if (p.getLocation().distanceSquared(m.center()) < 48 * 48) Txt.send(p, msg);
        Player o = Bukkit.getPlayer(m.owner);
        if (o != null && o.getLocation().getWorld() != m.block.getWorld()) Txt.send(o, msg);
    }

    private void meltdown(Machine m) {
        alert(m, "<dark_red><b>💥 " + Txt.plain(Txt.raw(ItemRegistry.def(m.type.item).name())) + " a explosé !");
        Location c = m.center();
        remove(m, false);
        m.block.setType(Material.AIR);
        Explosions.explode(c, (float) plugin.getConfig().getDouble("machines.explosion-surchauffe", 3), true, true, null, null);
    }

    private int fuelSeconds(ItemStack f) {
        if (f == null || ItemRegistry.idOf(f) != null) return 0;
        return switch (f.getType()) {
            case COAL, CHARCOAL -> 16;
            case COAL_BLOCK -> 160;
            case BLAZE_ROD -> 24;
            default -> 0;
        };
    }

    private double solarFactor(Machine m) {
        var w = m.block.getWorld();
        Block above = m.block.getRelative(0, 1, 0);
        if (above.getLightFromSky() < 15 && !above.getType().isAir()) return 0;
        if (m.block.getRelative(0, 1, 0).getLightFromSky() < 14) return 0;
        long t = w.getTime();
        boolean day = t < 12300 || t > 23850;
        double f = day ? 1 : (m.type == MachineType.SOLAR_PANEL_T3 ? 0.25 : 0);
        if (w.hasStorm()) f *= 0.5;
        return f;
    }

    private boolean canOutput(Machine m, MachineRecipe r) {
        ItemStack out = m.gui.slot(MachineGui.OUTPUT);
        if (r.outputs().size() > 1) return out == null || out.getType().isAir() || roomInOutput(m, r);
        var o = r.outputs().get(0);
        if (out == null || out.getType().isAir()) return true;
        ItemStack res = Inv.stack(o.getKey(), o.getValue());
        return out.isSimilar(res) && out.getAmount() + res.getAmount() <= out.getMaxStackSize();
    }

    private boolean roomInOutput(Machine m, MachineRecipe r) {
        ItemStack out = m.gui.slot(MachineGui.OUTPUT);
        var first = r.outputs().get(0);
        ItemStack res = Inv.stack(first.getKey(), first.getValue());
        return out.isSimilar(res) && out.getAmount() + res.getAmount() <= out.getMaxStackSize();
    }

    /** Applique la recette. Les sorties secondaires (concasseur) tombent au-dessus de la machine si la sortie est occupée. */
    private void craft(Machine m, MachineRecipe r) {
        ItemStack in = m.gui.slot(MachineGui.INPUT);
        in.setAmount(in.getAmount() - r.amount());
        boolean first = true;
        for (var o : r.outputs()) {
            ItemStack res = Inv.stack(o.getKey(), o.getValue());
            ItemStack out = m.gui.slot(MachineGui.OUTPUT);
            if (first && (out == null || out.getType().isAir())) m.gui.setSlot(MachineGui.OUTPUT, res);
            else if (out != null && out.isSimilar(res) && out.getAmount() + res.getAmount() <= out.getMaxStackSize()) out.setAmount(out.getAmount() + res.getAmount());
            else m.block.getWorld().dropItem(m.center().add(0, 0.8, 0), res);
            first = false;
        }
    }

    // ------------------------------------------------------------ persistance
    public void load() {
        YamlConfiguration y = store.get();
        ConfigurationSection root = y.getConfigurationSection("machines");
        if (root == null) return;
        for (String k : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(k);
            Block b = Locs.block(s.getString("block"));
            MachineType t;
            try { t = MachineType.valueOf(s.getString("type")); } catch (Exception e) { continue; }
            if (b == null) continue;
            Machine m = new Machine(Locs.blockKey(b), t, UUID.fromString(s.getString("owner")), b);
            m.energy = s.getDouble("energy");
            m.heat = s.getDouble("heat");
            m.enabled = s.getBoolean("enabled", true);
            m.overheated = s.getBoolean("overheated");
            m.auto = s.getBoolean("auto", true);
            m.burn = s.getInt("burn");
            if (s.contains("target-x")) m.setTarget(s.getInt("target-x"), s.getInt("target-z"));
            if (t.hasInventory() || t == MachineType.SWITCH) {
                m.gui = new MachineGui(m, this);
                for (int slot : new int[]{MachineGui.INPUT, MachineGui.OUTPUT, MachineGui.FUEL}) {
                    ItemStack it = s.getItemStack("slots." + slot);
                    if (it != null && m.gui.isEditableSlot(slot)) m.gui.setSlot(slot, it);
                }
            }
            machines.put(m.key, m);
            if (loaded(m)) spawnDisplay(m);
        }
    }

    public void save() {
        YamlConfiguration y = store.fresh();
        int i = 0;
        for (Machine m : machines.values()) {
            String p = "machines.m" + (i++) + ".";
            y.set(p + "block", m.key);
            y.set(p + "type", m.type.name());
            y.set(p + "owner", m.owner.toString());
            y.set(p + "energy", m.energy);
            y.set(p + "heat", m.heat);
            y.set(p + "enabled", m.enabled);
            y.set(p + "overheated", m.overheated);
            y.set(p + "auto", m.auto);
            y.set(p + "burn", m.burn);
            if (m.targetX != null) { y.set(p + "target-x", m.targetX); y.set(p + "target-z", m.targetZ); }
            if (m.gui != null) for (int slot : new int[]{MachineGui.INPUT, MachineGui.OUTPUT, MachineGui.FUEL}) {
                if (m.gui.isEditableSlot(slot)) y.set(p + "slots." + slot, m.gui.slot(slot));
            }
        }
        store.save();
    }

    public void shutdown() {
        save();
        for (Machine m : machines.values()) if (m.display != null) m.display.remove();
    }
}

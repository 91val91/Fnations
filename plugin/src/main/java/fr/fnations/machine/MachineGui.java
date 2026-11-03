package fr.fnations.machine;

import fr.fnations.gui.Gui;
import fr.fnations.gui.Icons;
import fr.fnations.item.ItemRegistry;
import fr.fnations.util.Txt;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Interface d'une machine : slots d'entrée/sortie, jauges d'énergie et de chaleur. */
public final class MachineGui extends Gui {
    public static final int INPUT = 10, FUEL = 13, OUTPUT = 16, MISSILE = 13;
    private final Machine m;
    private final MachineManager mm;

    MachineGui(Machine m, MachineManager mm) {
        super(4, "<dark_gray>" + Txt.plain(Txt.raw(ItemRegistry.def(m.type.item).name())));
        this.m = m;
        this.mm = mm;
        switch (m.type.kind) {
            case CONSUMER -> { editable.add(INPUT); editable.add(OUTPUT); }
            case LAUNCHER -> editable.add(MISSILE);
            default -> { if (m.type == MachineType.COAL_GENERATOR) editable.add(FUEL); }
        }
        render();
    }

    public ItemStack slot(int i) { return inventory.getItem(i); }

    public void setSlot(int i, ItemStack it) { inventory.setItem(i, it); }

    /** Met à jour les éléments décoratifs (appelé chaque seconde si ouvert). */
    public void render() {
        clear();
        ItemStack pane = Icons.pane(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < 18; i++) if (!editable.contains(i)) set(i, pane);
        set(4, Icons.of(ItemRegistry.def(m.type.item).material(), ItemRegistry.def(m.type.item).name(), statusLore()));
        if (m.type.kind != MachineType.Kind.SWITCH) {
            button(8, Icons.of(m.enabled ? Material.LIME_DYE : Material.GRAY_DYE, m.enabled ? "<green>Machine en marche" : "<red>Machine arrêtée",
                    "<yellow>Clic : marche / arrêt"), p -> { m.enabled = !m.enabled; render(); });
        }
        switch (m.type.kind) {
            case CONSUMER -> {
                MachineRecipe r = mm.recipeFor(m);
                double ratio = r == null ? 0 : m.progress / (double) r.seconds();
                int filled = (int) Math.round(ratio * 3);
                for (int i = 0; i < 3; i++) set(12 + i, Icons.of(i < filled ? Material.LIME_STAINED_GLASS_PANE : Material.WHITE_STAINED_GLASS_PANE,
                        "<white>Progression : " + (int) (ratio * 100) + "%"));
                set(9, Icons.of(Material.HOPPER, "<gray>Entrée →"));
                set(17, Icons.of(Material.CHEST, "<gray>← Sortie"));
            }
            case GENERATOR -> {
                if (m.type == MachineType.COAL_GENERATOR) {
                    set(12, Icons.of(Material.COAL, "<gray>Combustible →", "<gray>Charbon, charbon de bois", "<gray>ou bloc de charbon"));
                    set(14, Icons.of(m.burn > 0 ? Material.FIRE_CHARGE : Material.FLINT, m.burn > 0 ? "<gold>Combustion : " + m.burn + "s" : "<gray>Éteint"));
                } else {
                    set(13, Icons.of(Material.SUNFLOWER, "<yellow>Ensoleillement", "<gray>" + m.status));
                }
            }
            case SWITCH -> {
                button(11, Icons.of(m.enabled ? Material.LEVER : Material.REDSTONE_TORCH, m.enabled ? "<green><b>CIRCUIT FERMÉ" : "<red><b>CIRCUIT COUPÉ",
                        m.enabled ? "<gray>Les machines fonctionnent." : "<gray>Les machines refroidissent.", "", "<yellow>Clic : basculer"), p -> {
                    m.enabled = !m.enabled;
                    p.playSound(p.getLocation(), "minecraft:block.lever.click", 1f, m.enabled ? 1.2f : 0.8f);
                    render();
                });
                button(15, Icons.of(m.auto ? Material.REPEATER : Material.STICK, m.auto ? "<green>Disjoncteur automatique : ON" : "<gray>Disjoncteur automatique : OFF",
                        "<gray>Coupe le circuit dès qu'une machine", "<gray>dépasse " + (int) mm.autoThreshold() + "°C.", "", "<yellow>Clic : basculer"),
                        p -> { m.auto = !m.auto; render(); });
                List<String> lore = new ArrayList<>();
                for (Machine o : mm.network(m)) if (o != m) lore.add(heatColor(o.heat) + Txt.plain(Txt.raw(ItemRegistry.def(o.type.item).name())) + " <gray>" + (int) o.heat + "°C" + (o.overheated ? " <red>⚠ SURCHAUFFE" : ""));
                if (lore.isEmpty()) lore.add("<gray>Aucune machine dans le rayon.");
                set(13, Icons.of(Material.OBSERVER, "<white>Machines du réseau (" + (int) mm.radius() + " blocs)", lore));
            }
            case LAUNCHER -> mm.plugin().missiles().renderLauncher(this, m);
            default -> {}
        }
        // Jauges
        double e = m.type.capacity <= 0 ? 0 : m.energy / m.type.capacity;
        for (int i = 0; i < 9; i++) {
            boolean on = e > i / 9.0;
            set(18 + i, Icons.of(on ? Material.YELLOW_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE,
                    "<yellow>⚡ Énergie : " + (int) m.energy + "/" + (int) m.type.capacity + " E"));
        }
        double h = m.heat / Machine.MELTDOWN;
        for (int i = 0; i < 9; i++) {
            boolean on = h > i / 9.0;
            Material mat = !on ? Material.BLACK_STAINED_GLASS_PANE : i < 4 ? Material.ORANGE_STAINED_GLASS_PANE : i < 6 ? Material.RED_STAINED_GLASS_PANE : Material.MAGENTA_STAINED_GLASS_PANE;
            set(27 + i, Icons.of(mat, heatColor(m.heat) + "🌡 Chaleur : " + (int) m.heat + "°C", "<gray>Surchauffe : " + (int) Machine.OVERHEAT + "°C",
                    "<gray>Explosion : " + (int) Machine.MELTDOWN + "°C", m.overheated ? "<red><b>⚠ SURCHAUFFE ! Coupez l'interrupteur !" : ""));
        }
    }

    private static String heatColor(double h) { return h >= Machine.OVERHEAT ? "<red>" : h >= 70 ? "<gold>" : h >= 40 ? "<yellow>" : "<green>"; }

    private List<String> statusLore() {
        List<String> l = new ArrayList<>();
        l.add("<gray>État : " + (m.overheated ? "<red><b>SURCHAUFFE" : mm.tripped(m) ? "<red>Coupée par interrupteur" : m.enabled ? "<green>Active" : "<gray>Arrêtée"));
        if (m.type.production > 0) l.add("<gray>Production : <yellow>" + (int) m.type.production + " E/s");
        if (m.type.consumption > 0) l.add("<gray>Consommation : <yellow>" + (int) m.type.consumption + " E/s");
        if (m.type.capacity > 0) l.add("<gray>Énergie : <yellow>" + (int) m.energy + "/" + (int) m.type.capacity);
        if (m.type.kind != MachineType.Kind.SWITCH) l.add("<gray>Chaleur : <white>" + (int) m.heat + "°C");
        if (!m.status.isEmpty()) l.add("<gray>" + m.status);
        return l;
    }

    public void openFor(Player p) {
        render();
        open(p);
    }
}

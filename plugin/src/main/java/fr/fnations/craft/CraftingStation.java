package fr.fnations.craft;

import fr.fnations.FNations;
import fr.fnations.gui.Gui;
import fr.fnations.gui.Icons;
import fr.fnations.item.ItemDef;
import fr.fnations.item.ItemRegistry;
import fr.fnations.research.Technology;
import fr.fnations.util.Inv;
import fr.fnations.util.Txt;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Station de Craft : fabrication de tout le contenu "moddé" (armes, véhicules, machines, missiles, fusée...). */
public final class CraftingStation {
    private static final Map<String, Object[]> CATS = new LinkedHashMap<>();
    static {
        CATS.put("composant", new Object[]{Material.IRON_INGOT, "<gray>Composants"});
        CATS.put("arme", new Object[]{Material.CROSSBOW, "<white>Armes à feu"});
        CATS.put("munition", new Object[]{Material.ARROW, "<yellow>Munitions"});
        CATS.put("vehicule", new Object[]{Material.MINECART, "<green>Véhicules"});
        CATS.put("machine", new Object[]{Material.BLAST_FURNACE, "<aqua>Machines"});
        CATS.put("missile", new Object[]{Material.FIREWORK_ROCKET, "<red>Missiles"});
        CATS.put("fusee", new Object[]{Material.END_CRYSTAL, "<gold>Spatial"});
    }

    private final FNations plugin;
    private final Map<String, StationRecipe> recipes = new LinkedHashMap<>();

    public CraftingStation(FNations plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        recipes.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "recipes.yml"));
        ConfigurationSection root = y.getConfigurationSection("station");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            String result = s.getString("resultat", id);
            ItemDef d = ItemRegistry.def(result);
            if (d == null) { plugin.getLogger().warning("Recette " + id + " : item inconnu " + result); continue; }
            Map<String, Integer> ing = new LinkedHashMap<>();
            for (String spec : s.getStringList("ingredients")) {
                var e = Inv.parse(spec);
                ing.merge(e.getKey(), e.getValue(), Integer::sum);
            }
            recipes.put(id, new StationRecipe(id, result, s.getInt("nombre", 1), s.getString("categorie", d.category()), ing));
        }
        plugin.getLogger().info(recipes.size() + " recettes de station chargées.");
    }

    public List<StationRecipe> all() { return new ArrayList<>(recipes.values()); }

    public void open(Player p) {
        Gui g = new Gui(5, "<gold>Station de Craft");
        g.border(Material.ORANGE_STAINED_GLASS_PANE);
        g.set(4, Icons.of(Material.SMITHING_TABLE, "<gold><b>Station de Craft", "<gray>Fabriquez le matériel de votre nation.",
                "<gray>Certaines recettes demandent une", "<gray>technologie de R&D."));
        int[] slots = {20, 21, 22, 23, 24, 30, 32};
        int i = 0;
        for (var c : CATS.entrySet()) {
            long n = recipes.values().stream().filter(r -> r.category().equals(c.getKey())).count();
            g.button(slots[i++], Icons.of((Material) c.getValue()[0], (String) c.getValue()[1], "<gray>" + n + " recette(s)", "", "<yellow>Clic : ouvrir"),
                    pl -> category(pl, c.getKey(), 0));
        }
        g.button(40, Icons.close(), Player::closeInventory);
        g.fill(Material.GRAY_STAINED_GLASS_PANE);
        g.open(p);
    }

    private void category(Player p, String cat, int page) {
        List<StationRecipe> list = recipes.values().stream().filter(r -> r.category().equals(cat)).toList();
        Gui g = new Gui(6, "<gold>Station » " + CATS.get(cat)[1]);
        g.border(Material.ORANGE_STAINED_GLASS_PANE);
        int[] slots = Gui.innerSlots(6);
        int start = page * slots.length;
        for (int i = 0; i < slots.length && start + i < list.size(); i++) {
            StationRecipe r = list.get(start + i);
            g.set(slots[i], icon(p, r), e -> {
                Player pl = (Player) e.getWhoClicked();
                int times = e.isShiftClick() ? 5 : 1;
                for (int k = 0; k < times; k++) if (!craft(pl, r, k == 0)) break;
                category(pl, cat, page);
            });
        }
        if (page > 0) g.button(45, Icons.prev(), pl -> category(pl, cat, page - 1));
        if (start + slots.length < list.size()) g.button(53, Icons.next(), pl -> category(pl, cat, page + 1));
        g.button(49, Icons.back(), this::open);
        g.fill(Material.GRAY_STAINED_GLASS_PANE);
        g.open(p);
    }

    private ItemStack icon(Player p, StationRecipe r) {
        ItemStack base = ItemRegistry.create(r.result(), r.amount());
        ItemDef d = ItemRegistry.def(r.result());
        List<String> lore = new ArrayList<>(d.lore());
        lore.add("");
        lore.add("<gold>Ingrédients :");
        boolean ok = true;
        for (var e : r.ingredients().entrySet()) {
            int have = Inv.count(p, e.getKey());
            boolean enough = have >= e.getValue();
            ok &= enough;
            lore.add((enough ? " <green>✔ " : " <red>✘ ") + "<white>" + e.getValue() + "× " + Txt.plain(Txt.raw(Inv.display(e.getKey()))) + " <dark_gray>(" + have + ")");
        }
        Technology t = plugin.research().requiredFor(r.result());
        boolean researched = t == null || plugin.research().playerCanUse(p, t.id());
        if (t != null) lore.add((researched ? "<green>✔ Recherche : " : "<red>✘ Recherche requise : ") + Txt.plain(Txt.raw(t.name())));
        lore.add("");
        lore.add(ok && researched ? "<green>Clic : fabriquer <gray>(Maj+clic : ×5)" : "<red>Impossible de fabriquer");
        ItemStack it = Icons.decorate(base, d.name() + (r.amount() > 1 ? " <gray>×" + r.amount() : ""), lore);
        return ok && researched ? Icons.glow(it) : it;
    }

    private boolean craft(Player p, StationRecipe r, boolean feedback) {
        Technology t = plugin.research().requiredFor(r.result());
        if (t != null && !plugin.research().playerCanUse(p, t.id())) {
            if (feedback) Txt.err(p, "Votre nation doit rechercher : " + t.name());
            return false;
        }
        if (!Inv.takeAll(p, r.ingredients())) {
            if (feedback) Txt.err(p, "Ingrédients manquants.");
            return false;
        }
        Inv.give(p, ItemRegistry.create(r.result(), r.amount()));
        p.playSound(p.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1f, 1f);
        p.getWorld().spawnParticle(Particle.WAX_OFF, p.getLocation().add(0, 1, 0), 10, 0.4, 0.4, 0.4, 0);
        if (feedback) Txt.ok(p, "Fabriqué : " + ItemRegistry.def(r.result()).name() + (r.amount() > 1 ? " ×" + r.amount() : ""));
        return true;
    }
}

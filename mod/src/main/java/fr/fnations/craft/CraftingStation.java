package fr.fnations.craft;

import fr.fnations.gui.Icons;
import fr.fnations.gui.Panel;
import fr.fnations.registry.Category;
import fr.fnations.registry.ModItems;
import fr.fnations.research.Technology;
import fr.fnations.server.FnServer;
import fr.fnations.server.Msg;
import fr.fnations.util.Inv;
import fr.fnations.util.Yaml;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Station de Craft : fabrication de tout le contenu du mod (armes, véhicules, machines, missiles, fusée...). */
public final class CraftingStation {
    private record Cat(Item icon, String name) {}

    private static final Map<Category, Cat> CATS = new LinkedHashMap<>();
    static {
        CATS.put(Category.COMPOSANT, new Cat(Items.IRON_INGOT, "<gray>Composants"));
        CATS.put(Category.ARME, new Cat(Items.CROSSBOW, "<white>Armes à feu"));
        CATS.put(Category.MUNITION, new Cat(Items.ARROW, "<yellow>Munitions"));
        CATS.put(Category.VEHICULE, new Cat(Items.MINECART, "<green>Véhicules"));
        CATS.put(Category.MACHINE, new Cat(Items.BLAST_FURNACE, "<aqua>Machines"));
        CATS.put(Category.MISSILE, new Cat(Items.FIREWORK_ROCKET, "<red>Missiles"));
        CATS.put(Category.FUSEE, new Cat(Items.END_CRYSTAL, "<gold>Spatial"));
    }

    private final FnServer plugin;
    private final Map<String, StationRecipe> recipes = new LinkedHashMap<>();

    public CraftingStation(FnServer plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        recipes.clear();
        Yaml root = plugin.yaml("recipes.yml").getSection("station");
        if (root == null) return;
        for (String id : root.getKeys()) {
            Yaml s = root.getSection(id);
            String result = s.getString("resultat", id);
            if (ModItems.get(result) == null) { fr.fnations.FNations.LOG.warn("Recette {} : item inconnu {}", id, result); continue; }
            Map<String, Integer> ing = new LinkedHashMap<>();
            for (String spec : s.getStringList("ingredients")) {
                var e = Inv.parse(spec);
                ing.merge(e.getKey(), e.getValue(), Integer::sum);
            }
            Category cat = s.contains("categorie") ? Category.byName(s.getString("categorie")) : ModItems.CATEGORY.getOrDefault(result, Category.COMPOSANT);
            recipes.put(id, new StationRecipe(id, result, s.getInt("nombre", 1), cat.name(), ing));
        }
    }

    public List<StationRecipe> all() { return new ArrayList<>(recipes.values()); }

    public void open(ServerPlayer p) {
        Panel g = new Panel(5, "<gold>Station de Craft");
        g.border(Items.ORANGE_STAINED_GLASS_PANE);
        g.set(4, Icons.of(Items.SMITHING_TABLE, "<gold><b>Station de Craft", "<gray>Fabriquez le matériel de votre nation.",
                "<gray>Certaines recettes demandent une", "<gray>technologie de R&D."));
        int[] slots = {20, 21, 22, 23, 24, 30, 32};
        int i = 0;
        for (var c : CATS.entrySet()) {
            long n = recipes.values().stream().filter(r -> r.category().equals(c.getKey().name())).count();
            Category cat = c.getKey();
            g.button(slots[i++], Icons.of(c.getValue().icon(), c.getValue().name(), "<gray>" + n + " recette(s)", "", "<yellow>Clic : ouvrir"),
                    pl -> category(pl, cat, 0));
        }
        g.button(40, Icons.close(), ServerPlayer::closeContainer);
        g.fill(Items.GRAY_STAINED_GLASS_PANE);
        g.open(p);
    }

    private void category(ServerPlayer p, Category cat, int page) {
        List<StationRecipe> list = recipes.values().stream().filter(r -> r.category().equals(cat.name())).toList();
        Panel g = new Panel(6, "<gold>Station » " + CATS.get(cat).name());
        g.border(Items.ORANGE_STAINED_GLASS_PANE);
        int[] slots = Panel.innerSlots(6);
        int start = page * slots.length;
        for (int i = 0; i < slots.length && start + i < list.size(); i++) {
            StationRecipe r = list.get(start + i);
            g.set(slots[i], icon(p, r), (pl, click) -> {
                int times = click.shift() ? 5 : 1;
                for (int k = 0; k < times; k++) if (!craft(pl, r, k == 0)) break;
                category(pl, cat, page);
            });
        }
        if (page > 0) g.button(45, Icons.prev(), pl -> category(pl, cat, page - 1));
        if (start + slots.length < list.size()) g.button(53, Icons.next(), pl -> category(pl, cat, page + 1));
        g.button(49, Icons.back(), this::open);
        g.fill(Items.GRAY_STAINED_GLASS_PANE);
        g.open(p);
    }

    private ItemStack icon(ServerPlayer p, StationRecipe r) {
        ItemStack base = ModItems.stack(r.result(), r.amount());
        List<String> lore = new ArrayList<>();
        lore.add("<gold>Ingrédients :");
        boolean ok = true;
        for (var e : r.ingredients().entrySet()) {
            int have = Inv.count(p, e.getKey());
            boolean enough = have >= e.getValue();
            ok &= enough;
            lore.add((enough ? " <green>✔ " : " <red>✘ ") + "<white>" + e.getValue() + "× " + Inv.display(e.getKey()) + " <dark_gray>(" + have + ")");
        }
        Technology t = plugin.research().requiredFor(r.result());
        boolean researched = t == null || plugin.research().playerCanUse(p, t.id());
        if (t != null) lore.add((researched ? "<green>✔ Recherche : " : "<red>✘ Recherche requise : ") + t.name());
        lore.add("");
        lore.add(ok && researched ? "<green>Clic : fabriquer <gray>(Maj+clic : ×5)" : "<red>Impossible de fabriquer");
        ItemStack it = Icons.decorate(base, null, lore);
        return ok && researched ? Icons.glow(it) : it;
    }

    private boolean craft(ServerPlayer p, StationRecipe r, boolean feedback) {
        Technology t = plugin.research().requiredFor(r.result());
        if (t != null && !plugin.research().playerCanUse(p, t.id())) {
            if (feedback) Msg.err(p, "Votre nation doit rechercher : " + t.name());
            return false;
        }
        if (!Inv.takeAll(p, r.ingredients())) {
            if (feedback) Msg.err(p, "Ingrédients manquants.");
            return false;
        }
        Inv.give(p, ModItems.stack(r.result(), r.amount()));
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 1f, 1f);
        p.serverLevel().sendParticles(ParticleTypes.WAX_OFF, p.getX(), p.getY() + 1, p.getZ(), 10, 0.4, 0.4, 0.4, 0);
        if (feedback) Msg.ok(p, "Fabriqué : <item:fnations:" + r.result() + ">" + (r.amount() > 1 ? " ×" + r.amount() : ""));
        return true;
    }
}

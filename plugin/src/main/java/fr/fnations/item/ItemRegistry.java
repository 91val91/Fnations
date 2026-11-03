package fr.fnations.item;

import fr.fnations.util.Keys;
import fr.fnations.util.Txt;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/** Registre de tous les items custom FNations. Les modèles viennent du resource pack (composant item_model). */
public final class ItemRegistry {
    private static final Map<String, ItemDef> DEFS = new LinkedHashMap<>();
    private static final Map<String, String> CATEGORY_NAMES = Map.of(
            "composant", "Composants", "munition", "Munitions", "arme", "Armes",
            "vehicule", "Véhicules", "machine", "Machines", "missile", "Missiles",
            "fusee", "Spatial", "economie", "Économie");

    private ItemRegistry() {}

    public static void load(YamlConfiguration yaml, Logger log) {
        DEFS.clear();
        ConfigurationSection sec = yaml.getConfigurationSection("items");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(id);
            Material m = Material.matchMaterial(s.getString("material", "PAPER"));
            if (m == null) {
                log.warning("Matériau invalide pour l'item " + id);
                m = Material.PAPER;
            }
            DEFS.put(id, new ItemDef(id, s.getString("name", id), m, s.getString("model", "fnations:" + id),
                    s.getString("category", "composant"), s.getStringList("lore"), s.getInt("stack", 64)));
        }
        log.info(DEFS.size() + " items custom chargés.");
    }

    public static ItemDef def(String id) { return DEFS.get(id); }

    public static Collection<ItemDef> all() { return DEFS.values(); }

    public static String categoryName(String cat) { return CATEGORY_NAMES.getOrDefault(cat, cat); }

    public static ItemStack create(String id) { return create(id, 1); }

    public static ItemStack create(String id, int amount) {
        ItemDef d = DEFS.get(id);
        if (d == null) throw new IllegalArgumentException("Item FNations inconnu : " + id);
        ItemStack it = new ItemStack(d.material(), amount);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Txt.c(d.name()));
        List<String> lore = new ArrayList<>(d.lore());
        lore.add("<dark_gray>FNations • " + categoryName(d.category()));
        meta.lore(Txt.lines(lore));
        meta.setItemModel(NamespacedKey.fromString(d.model()));
        if (d.stack() != d.material().getMaxStackSize()) meta.setMaxStackSize(Math.max(1, Math.min(99, d.stack())));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        meta.getPersistentDataContainer().set(Keys.ITEM, PersistentDataType.STRING, id);
        it.setItemMeta(meta);
        return it;
    }

    /** Identifiant FNations d'un item, ou null si item vanilla. */
    public static String idOf(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(Keys.ITEM, PersistentDataType.STRING);
    }

    public static boolean is(ItemStack it, String id) { return id.equals(idOf(it)); }

    /**
     * Reconstruit un item créé par le datapack (qui ne porte que custom_data + item_model)
     * pour lui donner le nom, la lore et les propriétés définis ici.
     */
    public static ItemStack normalize(ItemStack it) {
        String id = idOf(it);
        if (id == null || !DEFS.containsKey(id)) return it;
        ItemStack fresh = create(id, it.getAmount());
        return fresh;
    }
}

package fr.fnations.util;

import fr.fnations.item.ItemRegistry;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Map;

/**
 * Gestion des ingrédients. Un ingrédient est soit un matériau vanilla ("IRON_INGOT"),
 * soit un item custom ("fnations:steel_plate"). Les items custom ne comptent jamais comme vanilla.
 */
public final class Inv {
    private Inv() {}

    public static boolean matches(ItemStack stack, String key) {
        if (stack == null || stack.getType().isAir()) return false;
        String id = ItemRegistry.idOf(stack);
        if (key.startsWith("fnations:")) return key.substring(9).equals(id);
        if (id != null) return false;
        Material m = Material.matchMaterial(key);
        return m != null && stack.getType() == m;
    }

    public static int count(Player p, String key) {
        int n = 0;
        for (ItemStack s : p.getInventory().getStorageContents()) if (matches(s, key)) n += s.getAmount();
        return n;
    }

    public static boolean has(Player p, Map<String, Integer> needs) {
        for (Map.Entry<String, Integer> e : needs.entrySet()) if (count(p, e.getKey()) < e.getValue()) return false;
        return true;
    }

    public static boolean take(Player p, String key, int amount) {
        if (count(p, key) < amount) return false;
        PlayerInventory inv = p.getInventory();
        int size = inv.getStorageContents().length;
        for (int i = 0; i < size && amount > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (!matches(s, key)) continue;
            int t = Math.min(amount, s.getAmount());
            amount -= t;
            if (s.getAmount() - t <= 0) inv.setItem(i, null);
            else { s.setAmount(s.getAmount() - t); inv.setItem(i, s); }
        }
        return true;
    }

    public static boolean takeAll(Player p, Map<String, Integer> needs) {
        if (!has(p, needs)) return false;
        needs.forEach((k, v) -> take(p, k, v));
        return true;
    }

    public static void give(Player p, ItemStack item) {
        p.getInventory().addItem(item).values().forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
    }

    /** Nom lisible d'un ingrédient. */
    public static String display(String key) {
        if (key.startsWith("fnations:")) {
            var def = ItemRegistry.def(key.substring(9));
            return def != null ? def.name() : key;
        }
        String s = key.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Crée un ItemStack depuis une clé d'ingrédient. */
    public static ItemStack stack(String key, int amount) {
        if (key.startsWith("fnations:")) return ItemRegistry.create(key.substring(9), amount);
        Material m = Material.matchMaterial(key);
        return new ItemStack(m == null ? Material.STONE : m, amount);
    }

    /** "IRON_INGOT:4" / "fnations:steel_plate:2" -> [clé, quantité] */
    public static Map.Entry<String, Integer> parse(String spec) {
        int i = spec.lastIndexOf(':');
        if (i > 0 && spec.substring(i + 1).matches("\\d+")) return Map.entry(spec.substring(0, i), Integer.parseInt(spec.substring(i + 1)));
        return Map.entry(spec, 1);
    }
}

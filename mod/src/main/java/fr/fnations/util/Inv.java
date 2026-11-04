package fr.fnations.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;
import java.util.Map;

/**
 * Ingrédients : "minecraft:iron_ingot", "fnations:steel_plate", ou l'ancien format du plugin "IRON_INGOT".
 * Suffixe ":n" pour la quantité ("fnations:steel_plate:2").
 */
public final class Inv {
    private Inv() {}

    /** Normalise une clé d'item ("IRON_INGOT" -> "minecraft:iron_ingot"). */
    public static String norm(String key) {
        String k = key.trim();
        if (!k.contains(":")) k = "minecraft:" + k.toLowerCase(Locale.ROOT);
        return k.toLowerCase(Locale.ROOT);
    }

    public static Item item(String key) {
        ResourceLocation id = ResourceLocation.tryParse(norm(key));
        if (id == null) return Items.AIR;
        return BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
    }

    public static boolean matches(ItemStack stack, String key) {
        if (stack == null || stack.isEmpty()) return false;
        Item it = item(key);
        return it != Items.AIR && stack.is(it);
    }

    public static int count(ServerPlayer p, String key) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (matches(s, key)) n += s.getCount();
        return n;
    }

    public static boolean has(ServerPlayer p, Map<String, Integer> needs) {
        for (Map.Entry<String, Integer> e : needs.entrySet()) if (count(p, e.getKey()) < e.getValue()) return false;
        return true;
    }

    public static boolean take(ServerPlayer p, String key, int amount) {
        if (count(p, key) < amount) return false;
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.items.size() && amount > 0; i++) {
            ItemStack s = inv.items.get(i);
            if (!matches(s, key)) continue;
            int t = Math.min(amount, s.getCount());
            s.shrink(t);
            amount -= t;
        }
        inv.setChanged();
        return true;
    }

    public static boolean takeAll(ServerPlayer p, Map<String, Integer> needs) {
        if (!has(p, needs)) return false;
        needs.forEach((k, v) -> take(p, k, v));
        return true;
    }

    public static void give(ServerPlayer p, ItemStack stack) {
        if (!p.getInventory().add(stack)) p.drop(stack, false);
    }

    public static ItemStack stack(String key, int amount) {
        Item it = item(key);
        return new ItemStack(it == Items.AIR ? Items.STONE : it, amount);
    }

    /** Nom affichable (traduit côté client) : balise <item:...> pour Txt. */
    public static String display(String key) { return "<item:" + norm(key) + ">"; }

    /** "IRON_INGOT:4" / "fnations:steel_plate:2" -> [clé normalisée, quantité] */
    public static Map.Entry<String, Integer> parse(String spec) {
        int i = spec.lastIndexOf(':');
        if (i > 0 && spec.substring(i + 1).matches("\\d+")) return Map.entry(norm(spec.substring(0, i)), Integer.parseInt(spec.substring(i + 1)));
        return Map.entry(norm(spec), 1);
    }
}

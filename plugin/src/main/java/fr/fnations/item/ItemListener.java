package fr.fnations.item;

import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

/**
 * Empêche les items custom (souvent basés sur PAPER) d'être utilisés comme leur matériau vanilla,
 * et normalise les résultats des recettes du datapack "fnations".
 */
public final class ItemListener implements Listener {

    @EventHandler
    public void onPrepare(PrepareItemCraftEvent e) {
        Recipe r = e.getRecipe();
        boolean fnRecipe = r instanceof CraftingRecipe cr && cr.getKey().getNamespace().equals("fnations");
        if (!fnRecipe) {
            for (ItemStack s : e.getInventory().getMatrix()) {
                if (ItemRegistry.idOf(s) != null) {
                    e.getInventory().setResult(null);
                    return;
                }
            }
            return;
        }
        ItemStack res = e.getInventory().getResult();
        if (ItemRegistry.idOf(res) != null) e.getInventory().setResult(ItemRegistry.normalize(res));
    }

    @EventHandler(ignoreCancelled = true)
    public void onSmelt(FurnaceSmeltEvent e) {
        if (ItemRegistry.idOf(e.getSource()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(FurnaceBurnEvent e) {
        if (ItemRegistry.idOf(e.getFuel()) != null) e.setCancelled(true);
    }

    /** Les items custom basés sur un bloc ne se posent que s'ils sont gérés (machines). */
    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        String id = ItemRegistry.idOf(e.getItemInHand());
        if (id == null) return;
        ItemDef d = ItemRegistry.def(id);
        if (d != null && !"machine".equals(d.category())) e.setCancelled(true);
    }

    @SuppressWarnings("unused")
    private static boolean isFn(NamespacedKey k) { return k.getNamespace().equals("fnations"); }
}

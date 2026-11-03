package fr.fnations.gui;

import fr.fnations.util.Txt;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Fabrique d'icônes de menus. */
public final class Icons {
    private Icons() {}

    public static ItemStack of(Material m, String name, String... lore) {
        return of(m, name, Arrays.asList(lore));
    }

    public static ItemStack of(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Txt.c(name));
        if (!lore.isEmpty()) meta.lore(Txt.lines(lore));
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    /** Ajoute nom + lore à un item existant (copie). */
    public static ItemStack decorate(ItemStack base, String name, List<String> lore) {
        ItemStack it = base.clone();
        ItemMeta meta = it.getItemMeta();
        if (name != null) meta.displayName(Txt.c(name));
        List<String> all = new ArrayList<>(lore);
        meta.lore(Txt.lines(all));
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    public static ItemStack glow(ItemStack it) {
        ItemMeta meta = it.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        it.setItemMeta(meta);
        return it;
    }

    public static ItemStack pane(Material m) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Txt.c(" "));
        meta.setHideTooltip(true);
        it.setItemMeta(meta);
        return it;
    }

    public static ItemStack head(OfflinePlayer p, String name, List<String> lore) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) it.getItemMeta();
        meta.setOwningPlayer(p);
        meta.displayName(Txt.c(name));
        meta.lore(Txt.lines(lore));
        it.setItemMeta(meta);
        return it;
    }

    public static ItemStack back() { return of(Material.ARROW, "<yellow>← Retour"); }

    public static ItemStack close() { return of(Material.BARRIER, "<red>Fermer"); }

    public static ItemStack prev() { return of(Material.SPECTRAL_ARROW, "<yellow>Page précédente"); }

    public static ItemStack next() { return of(Material.SPECTRAL_ARROW, "<yellow>Page suivante"); }
}

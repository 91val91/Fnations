package fr.fnations.gui;

import com.mojang.authlib.GameProfile;
import fr.fnations.util.Txt;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.Arrays;
import java.util.List;

/** Fabrique d'icônes pour les panneaux. */
public final class Icons {
    private Icons() {}

    public static ItemStack of(Item item, String name, String... lore) { return of(item, name, Arrays.asList(lore)); }

    public static ItemStack of(Item item, String name, List<String> lore) { return decorate(new ItemStack(item), name, lore); }

    public static ItemStack decorate(ItemStack base, String name, List<String> lore) {
        ItemStack it = base.copy();
        if (name != null) it.set(DataComponents.CUSTOM_NAME, Txt.c(name));
        if (lore != null && !lore.isEmpty()) it.set(DataComponents.LORE, new ItemLore(Txt.lines(lore.stream().filter(s -> s != null).toList())));
        it.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
        return it;
    }

    public static ItemStack glow(ItemStack it) {
        it.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return it;
    }

    public static ItemStack pane(Item pane) {
        ItemStack it = new ItemStack(pane);
        it.set(DataComponents.HIDE_TOOLTIP, Unit.INSTANCE);
        return it;
    }

    public static ItemStack head(GameProfile profile, String name, List<String> lore) {
        ItemStack it = decorate(new ItemStack(Items.PLAYER_HEAD), name, lore);
        if (profile != null) it.set(DataComponents.PROFILE, new ResolvableProfile(profile));
        return it;
    }

    public static ItemStack back() { return of(Items.ARROW, "<yellow>← Retour"); }

    public static ItemStack close() { return of(Items.BARRIER, "<red>Fermer"); }

    public static ItemStack prev() { return of(Items.SPECTRAL_ARROW, "<yellow>Page précédente"); }

    public static ItemStack next() { return of(Items.SPECTRAL_ARROW, "<yellow>Page suivante"); }
}

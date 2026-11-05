package fr.fnations.mixin;

import fr.fnations.server.FnServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Crafts par palier de métier à l'établi (et dans l'inventaire) : le résultat reste vide tant que
 * le joueur n'a pas le palier demandé dans metiers.yml (section crafts).
 */
@Mixin(CraftingMenu.class)
public abstract class CraftingMenuMixin {
    /**
     * Le résultat est remplacé à sa première lecture (passage à ResultContainer.setItem), donc avant setItem,
     * setRemoteSlot et le paquet envoyé au client : les trois voient le même résultat.
     */
    @ModifyVariable(method = "slotChangedCraftingGrid", ordinal = 0, at = @At(value = "LOAD", ordinal = 0))
    private static ItemStack fnations$jobTier(ItemStack result, AbstractContainerMenu menu, ServerLevel level, Player player) {
        FnServer s = FnServer.get();
        if (s == null || !(player instanceof ServerPlayer sp)) return result;
        return s.jobs().craftLock(sp, result);
    }
}

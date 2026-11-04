package fr.fnations.weapon;

import fr.fnations.registry.Category;
import fr.fnations.registry.ModComponents;
import fr.fnations.registry.ModItems;
import fr.fnations.util.Txt;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Arme à feu. Le tir est déclenché par le client (clic droit maintenu, voir FNationsClient) et validé par le serveur
 * (cadence, munitions, dégâts dans weapons.yml).
 */
public final class GunItem extends ModItems.FnItem {
    public final String gunId;
    public final String ammo;
    public final int magazine;
    public final boolean automatic;
    public final float recoil;
    public final int fireDelay;

    public GunItem(Properties p, String gunId, String ammo, int magazine, boolean automatic, float recoil, int fireDelay) {
        super(p, Category.ARME, List.of("<gray>Clic droit : tirer" + (automatic ? " (maintenir)" : ""), "<gray>R : recharger", "<gray>Accroupi : viser"));
        this.gunId = gunId;
        this.ammo = ammo;
        this.magazine = magazine;
        this.automatic = automatic;
        this.recoil = recoil;
        this.fireDelay = fireDelay;
    }

    public static int ammo(ItemStack s) {
        Integer v = s.get(ModComponents.AMMO);
        return v == null ? 0 : v;
    }

    public int magazine(ItemStack s) {
        Integer v = s.get(ModComponents.MAGAZINE);
        return v == null ? magazine : v;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        // Le tir passe par un paquet dédié : on bloque l'usage vanilla.
        return hand == InteractionHand.MAIN_HAND ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> out, TooltipFlag flag) {
        int a = ammo(stack);
        out.add(Txt.c("<gray>Chargeur : " + (a == 0 ? "<red>" : "<white>") + a + "<gray>/" + magazine(stack)));
        out.add(Txt.c("<gray>Munition : <item:fnations:" + ammo + ">"));
        super.appendHoverText(stack, ctx, out, flag);
    }
}

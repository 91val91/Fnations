package fr.fnations.bank;

import fr.fnations.registry.Category;
import fr.fnations.registry.ModComponents;
import fr.fnations.registry.ModItems;
import fr.fnations.server.FnServer;
import fr.fnations.server.Msg;
import fr.fnations.util.Txt;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Billet de banque : clic droit pour le déposer sur son compte. */
public final class BanknoteItem extends ModItems.FnItem {
    public BanknoteItem(Properties p) { super(p, Category.ECONOMIE, List.of("<gray>Clic droit pour déposer en banque.")); }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        FnServer s = FnServer.get();
        ItemStack it = player.getItemInHand(hand);
        if (s == null || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        double v = s.bank().noteValue(it);
        if (v <= 0) return InteractionResult.PASS;
        player.setItemInHand(hand, ItemStack.EMPTY);
        s.bank().deposit(sp.getUUID(), v, "Dépôt de billets");
        Msg.ok(sp, Txt.money(v) + " déposés sur votre compte.");
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> out, TooltipFlag flag) {
        Double v = stack.get(ModComponents.NOTE_VALUE);
        if (v != null) out.add(Txt.c("<gray>Valeur : <green>" + Txt.money(v)));
        super.appendHoverText(stack, ctx, out, flag);
    }
}

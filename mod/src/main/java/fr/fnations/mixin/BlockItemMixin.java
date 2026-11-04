package fr.fnations.mixin;

import fr.fnations.server.FnServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Blocs posés par les joueurs : XP de Builder, anti-abus des métiers de récolte. */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @Inject(method = "place", at = @At("RETURN"))
    private void fnations$placed(BlockPlaceContext ctx, CallbackInfoReturnable<InteractionResult> cir) {
        if (!cir.getReturnValue().consumesAction()) return;
        if (!(ctx.getPlayer() instanceof ServerPlayer p) || !(ctx.getLevel() instanceof ServerLevel level)) return;
        FnServer s = FnServer.get();
        if (s == null) return;
        BlockPos pos = ctx.getClickedPos();
        s.jobs().onPlace(p, level, pos, level.getBlockState(pos), (BlockItem) (Object) this);
    }
}

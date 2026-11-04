package fr.fnations.mixin;

import fr.fnations.server.FnServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/** Protège les territoires et les chambres des explosions (sauf frappes de guerre autorisées). */
@Mixin(ServerExplosion.class)
public abstract class ServerExplosionMixin {
    @Shadow @Final private ServerLevel level;

    @Inject(method = "calculateExplodedPositions", at = @At("RETURN"), cancellable = true)
    private void fnations$protect(CallbackInfoReturnable<List<BlockPos>> cir) {
        FnServer s = FnServer.get();
        if (s == null) return;
        List<BlockPos> list = new ArrayList<>(cir.getReturnValue());
        s.territory().filterExplosion(level, list);
        cir.setReturnValue(list);
    }
}

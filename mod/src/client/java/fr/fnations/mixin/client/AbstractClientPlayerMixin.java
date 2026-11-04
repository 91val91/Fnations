package fr.fnations.mixin.client;

import fr.fnations.weapon.GunItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zoom de la lunette du fusil de précision (accroupi, vue à la première personne). */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
    @Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
    private void fnations$scope(boolean firstPerson, float scale, CallbackInfoReturnable<Float> cir) {
        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        if (self != Minecraft.getInstance().player || !firstPerson) return;
        if (self.isShiftKeyDown() && self.getMainHandItem().getItem() instanceof GunItem g) {
            if (g.gunId.equals("sniper")) cir.setReturnValue(0.18f);
            else if (g.gunId.equals("assault_rifle")) cir.setReturnValue(cir.getReturnValue() * 0.75f);
        }
    }
}

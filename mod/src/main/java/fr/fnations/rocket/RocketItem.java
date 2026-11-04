package fr.fnations.rocket;

import fr.fnations.registry.Category;
import fr.fnations.registry.ModEntities;
import fr.fnations.registry.ModItems;
import fr.fnations.server.FnServer;
import fr.fnations.server.Msg;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Fusée : clic droit au sol pour l'installer. */
public final class RocketItem extends ModItems.FnItem {
    public RocketItem(Properties p) {
        super(p, Category.FUSEE, List.of("<gray>Clic droit au sol pour l'installer.", "<gray>Remplir avec de l'Ergol de fusée."));
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel level) || !(ctx.getPlayer() instanceof ServerPlayer p)) return InteractionResult.SUCCESS;
        FnServer s = FnServer.get();
        BlockPos base = ctx.getClickedPos().above();
        if (s != null && !s.territory().canBuild(p, level, base)) { Msg.err(p, "Vous ne pouvez pas installer de fusée ici."); return InteractionResult.FAIL; }
        RocketEntity r = new RocketEntity(ModEntities.ROCKET, level);
        Vec3 at = Vec3.atBottomCenterOf(base);
        r.moveTo(at.x, at.y, at.z, p.getYRot(), 0);
        if (!level.noCollision(r, r.getBoundingBox())) { Msg.err(p, "Il faut 9 blocs d'espace libre au-dessus."); return InteractionResult.FAIL; }
        r.setOwner(p.getUUID());
        level.addFreshEntity(r);
        ctx.getItemInHand().consume(1, p);
        Msg.ok(p, "Fusée installée ! Remplissez-la avec " + RocketEntity.fuelNeeded() + " Ergols de fusée (clic droit), puis montez à bord.");
        return InteractionResult.SUCCESS_SERVER;
    }
}

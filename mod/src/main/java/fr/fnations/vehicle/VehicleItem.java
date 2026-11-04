package fr.fnations.vehicle;

import fr.fnations.registry.Category;
import fr.fnations.registry.ModComponents;
import fr.fnations.registry.ModEntities;
import fr.fnations.registry.ModItems;
import fr.fnations.server.FnServer;
import fr.fnations.server.Msg;
import fr.fnations.util.Txt;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Item de véhicule : clic droit au sol pour le déployer. */
public final class VehicleItem extends ModItems.FnItem {
    private final VehicleKind kind;

    public VehicleItem(Properties p, VehicleKind kind) {
        super(p, Category.VEHICULE, kind == VehicleKind.TRACTOR
                ? List.of("<gray>Clic droit au sol pour déployer.", "<gray>Récolte et replante les cultures.")
                : List.of("<gray>Clic droit au sol pour déployer."));
        this.kind = kind;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel level) || !(ctx.getPlayer() instanceof ServerPlayer p)) return InteractionResult.SUCCESS;
        FnServer s = FnServer.get();
        if (s != null && !s.territory().canBuild(p, level, ctx.getClickedPos())) {
            Msg.err(p, "Vous ne pouvez pas déployer de véhicule ici.");
            return InteractionResult.FAIL;
        }
        ItemStack stack = ctx.getItemInHand();
        Vec3 at = Vec3.atBottomCenterOf(ctx.getClickedPos().relative(ctx.getClickedFace()));
        VehicleEntity v = new VehicleEntity(ModEntities.VEHICLES.get(kind), level, kind);
        v.moveTo(at.x, at.y, at.z, p.getYRot(), 0);
        if (!level.noCollision(v, v.getBoundingBox())) {
            Msg.err(p, "Pas assez de place pour déployer ce véhicule ici.");
            return InteractionResult.FAIL;
        }
        Double fuel = stack.get(ModComponents.FUEL);
        v.setup(p.getUUID(), fuel == null ? kind.fuelCapacity * 0.25 : fuel);
        level.addFreshEntity(v);
        stack.consume(1, p);
        level.playSound(null, v.blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.NEUTRAL, 0.6f, 0.7f);
        Msg.ok(p, "<item:fnations:" + kind.id + "> <green>déployé. Clic droit pour monter, accroupi + clic droit pour ranger.");
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> out, TooltipFlag flag) {
        Double fuel = stack.get(ModComponents.FUEL);
        if (fuel != null) out.add(Txt.c("<gray>Carburant : <yellow>" + (int) (double) fuel + "/" + (int) kind.fuelCapacity));
        super.appendHoverText(stack, ctx, out, flag);
    }
}

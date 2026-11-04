package fr.fnations.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.fnations.FNations;
import fr.fnations.registry.ModItems;
import fr.fnations.vehicle.VehicleEntity;
import fr.fnations.vehicle.VehicleKind;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Rendu des véhicules à partir des modèles 3D du mod (mêmes modèles que les items). */
public final class VehicleRenderer extends EntityRenderer<VehicleEntity, ModelRenderState> {
    private static final ItemStack ROTOR = new ItemStack(Items.PAPER);
    static { ROTOR.set(DataComponents.ITEM_MODEL, FNations.id("helicopter_rotor")); }

    private final ItemModelResolver items;

    public VehicleRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.items = ctx.getItemModelResolver();
        this.shadowRadius = 1.2f;
    }

    @Override
    public ModelRenderState createRenderState() { return new ModelRenderState(); }

    @Override
    public void extractRenderState(VehicleEntity e, ModelRenderState s, float pt) {
        super.extractRenderState(e, s, pt);
        s.yaw = Mth.rotLerp(pt, e.yRotO, e.getYRot());
        s.pitch = e.kind == VehicleKind.PLANE ? Mth.lerp(pt, e.xRotO, e.getXRot()) : 0;
        if (e.kind == VehicleKind.HELICOPTER) {
            double dx = e.getX() - e.xo, dz = e.getZ() - e.zo;
            double yr = Math.toRadians(s.yaw);
            double fwd = -Math.sin(yr) * dx + Math.cos(yr) * dz;
            s.pitch = (float) Mth.clamp(fwd * 25, -15, 15);
        }
        s.scale = e.kind.renderScale;
        s.lift = 0.5f * s.scale;
        items.updateForNonLiving(s.model, ModItems.stack(e.kind.id), ItemDisplayContext.NONE, e);
        s.hasRotor = e.kind == VehicleKind.HELICOPTER;
        if (s.hasRotor) {
            items.updateForNonLiving(s.rotor, ROTOR, ItemDisplayContext.NONE, e);
            s.rotorAngle = Mth.lerp(pt, e.rotorO, e.rotor);
        }
    }

    @Override
    public void render(ModelRenderState s, PoseStack pose, MultiBufferSource buf, int light) {
        pose.pushPose();
        pose.translate(0, s.lift, 0);
        pose.mulPose(Axis.YP.rotationDegrees(180f - s.yaw));
        pose.mulPose(Axis.XP.rotationDegrees(-s.pitch));
        pose.scale(s.scale, s.scale, s.scale);
        s.model.render(pose, buf, light, OverlayTexture.NO_OVERLAY);
        if (s.hasRotor) {
            pose.mulPose(Axis.YP.rotationDegrees(s.rotorAngle));
            s.rotor.render(pose, buf, light, OverlayTexture.NO_OVERLAY);
        }
        pose.popPose();
        super.render(s, pose, buf, light);
    }
}

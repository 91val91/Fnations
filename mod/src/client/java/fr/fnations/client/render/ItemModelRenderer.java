package fr.fnations.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.fnations.missile.MissileEntity;
import fr.fnations.registry.ModItems;
import fr.fnations.rocket.RocketEntity;
import fr.fnations.weapon.ShellEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Rendu d'une entité par un modèle 3D d'item (missiles, roquettes, obus, fusée). */
public final class ItemModelRenderer<T extends Entity> extends EntityRenderer<T, ModelRenderState> {
    private final ItemModelResolver items;

    public ItemModelRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.items = ctx.getItemModelResolver();
    }

    @Override
    public ModelRenderState createRenderState() { return new ModelRenderState(); }

    @Override
    public void extractRenderState(T e, ModelRenderState s, float pt) {
        super.extractRenderState(e, s, pt);
        ItemStack stack;
        if (e instanceof RocketEntity) {
            stack = ModItems.stack("rocket");
            s.scale = 3f;
            s.lift = 1.5f * s.scale;
            s.yaw = Mth.rotLerp(pt, e.yRotO, e.getYRot());
            s.pitch = 0;
        } else if (e instanceof MissileEntity m) {
            stack = m.getItem();
            s.scale = 2.5f;
            s.lift = 0;
            s.yaw = Mth.rotLerp(pt, e.yRotO, e.getYRot());
            s.pitch = Mth.lerp(pt, e.xRotO, e.getXRot());
        } else {
            stack = e instanceof ShellEntity sh && ModItems.is(sh.getItem(), "tank_shell") ? ModItems.stack("missile_cruise") : ModItems.stack("missile_tactical");
            s.scale = 0.5f;
            s.lift = 0;
            Vec3 v = e.getDeltaMovement();
            if (v.lengthSqr() > 1e-6) {
                s.yaw = (float) Math.toDegrees(Math.atan2(-v.x, v.z));
                s.pitch = (float) -Math.toDegrees(Math.atan2(v.y, v.horizontalDistance()));
            }
        }
        items.updateForNonLiving(s.model, stack, ItemDisplayContext.NONE, e);
    }

    @Override
    public void render(ModelRenderState s, PoseStack pose, MultiBufferSource buf, int light) {
        pose.pushPose();
        pose.translate(0, s.lift, 0);
        pose.mulPose(Axis.YP.rotationDegrees(180f - s.yaw));
        pose.mulPose(Axis.XP.rotationDegrees(-s.pitch));
        pose.scale(s.scale, s.scale, s.scale);
        s.model.render(pose, buf, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        super.render(s, pose, buf, light);
    }
}

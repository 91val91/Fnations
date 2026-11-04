package fr.fnations.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.fnations.FNations;
import fr.fnations.machine.MachineBlockEntity;
import fr.fnations.machine.MachineType;
import fr.fnations.registry.ModItems;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Parties animées des machines :
 * - panneaux solaires : le panneau 2x3 cellules suit la course du soleil (est -> ouest) et se replie la nuit ;
 * - Station de Craft : hologramme tournant des créations du mod, faisceau de projection et anneaux.
 */
public final class MachineRenderer implements BlockEntityRenderer<MachineBlockEntity> {
    /** Créations présentées par l'hologramme ; les armes, plus longues, sont réduites pour rester entre les vitres. */
    private static final List<String> SHOWCASE = List.of("assault_rifle", "tank", "plane", "rocket", "helicopter", "missile_nuclear",
            "sniper", "tractor", "rpg", "jet_engine", "missile_cruise", "shotgun");
    private static final List<String> LONG = List.of("assault_rifle", "sniper", "rpg", "shotgun");
    private static ItemStack[] showcaseStacks;
    private static final int CYCLE = 100; // ticks par objet présenté
    private static final ItemStack[] ARRAYS = {part("solar_array_t1"), part("solar_array_t2"), part("solar_array_t3")};
    private static final ItemStack BEAM = part("holo_beam");
    private static final ItemStack RING = part("holo_ring");

    private final ItemModelResolver items;
    private final ItemStackRenderState state = new ItemStackRenderState();

    public MachineRenderer(BlockEntityRendererProvider.Context ctx) { this.items = ctx.getItemModelResolver(); }

    private static ItemStack part(String model) {
        ItemStack s = new ItemStack(Items.PAPER);
        s.set(DataComponents.ITEM_MODEL, FNations.id(model));
        return s;
    }

    @Override
    public void render(MachineBlockEntity be, float pt, PoseStack pose, MultiBufferSource buf, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) return;
        MachineType t = be.type();
        if (t.solar()) solar(be, t, level, pt, pose, buf);
        else if (t == MachineType.CRAFTING_STATION) station(level, pt, pose, buf);
    }

    /** Inclinaison maximale : au-delà, le bas du panneau toucherait le boîtier. */
    private static final float MAX_TILT = 50f;
    /** Hauteur de l'axe de rotation du panneau (sommet du mât), en blocs. */
    private static final double PIVOT_Y = 27 / 16.0;

    /** Inclinaison du panneau vers le soleil (degrés autour de l'axe nord-sud). */
    public static float sunTilt(Level level, float pt) {
        float deg = (float) Math.toDegrees(level.getSunAngle(pt));
        deg = Mth.wrapDegrees(deg); // 0 = midi, négatif = matin (est), positif = après-midi (ouest)
        float a = Math.abs(deg);
        if (a <= 90) return Mth.clamp(deg, -MAX_TILT, MAX_TILT);
        if (a < 120) return Math.signum(deg) * MAX_TILT * (120 - a) / 30f;
        return 0f; // nuit : panneau à plat
    }

    private void solar(MachineBlockEntity be, MachineType t, Level level, float pt, PoseStack pose, MultiBufferSource buf) {
        float tilt = sunTilt(level, pt);
        int tier = t == MachineType.SOLAR_PANEL_T1 ? 0 : t == MachineType.SOLAR_PANEL_T2 ? 1 : 2;
        float deg = Math.abs(Mth.wrapDegrees((float) Math.toDegrees(level.getSunAngle(pt))));
        int light = tier == 2 && deg > 100 ? LightTexture.FULL_BRIGHT : LevelRenderer.getLightColor(level, be.getBlockPos().above());
        pose.pushPose();
        pose.translate(0.5, PIVOT_Y, 0.5);
        pose.mulPose(Axis.ZP.rotationDegrees(tilt));
        render(ARRAYS[tier], ItemDisplayContext.NONE, level, pose, buf, light);
        pose.popPose();
    }

    private void station(Level level, float pt, PoseStack pose, MultiBufferSource buf) {
        float time = (level.getGameTime() % 24000L) + pt;
        int full = LightTexture.FULL_BRIGHT;
        // faisceau de projection (part de l'émetteur)
        pose.pushPose();
        pose.translate(0.5, 6.5 / 16.0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(time * 1.5f));
        pose.scale(1f, 0.85f, 1f);
        render(BEAM, ItemDisplayContext.NONE, level, pose, buf, full);
        pose.popPose();
        // objet présenté : rotation, flottement, apparition / disparition
        int index = (int) ((time / CYCLE) % SHOWCASE.size());
        float phase = (time % CYCLE) / CYCLE;
        float pop = Math.min(1f, phase * 8f) * Math.min(1f, (1f - phase) * 8f);
        float bob = Mth.sin(time * 0.08f) * 0.6f;
        pose.pushPose();
        pose.translate(0.5, (12.5 + bob) / 16.0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(time * 2.2f));
        float s = (LONG.contains(SHOWCASE.get(index)) ? 0.42f : 0.55f) * (0.15f + 0.85f * pop);
        pose.scale(s, s, s);
        render(showcase(index), ItemDisplayContext.FIXED, level, pose, buf, full);
        pose.popPose();
        // anneaux holographiques
        for (int i = 0; i < 2; i++) {
            pose.pushPose();
            pose.translate(0.5, (12.5 + bob * 0.5) / 16.0, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(time * (i == 0 ? 3.0f : -2.2f)));
            pose.mulPose(Axis.XP.rotationDegrees(i == 0 ? 68f : -68f + Mth.sin(time * 0.05f) * 10f));
            float r = 0.72f + 0.04f * Mth.sin(time * 0.1f + i);
            pose.scale(r, r, r);
            render(RING, ItemDisplayContext.NONE, level, pose, buf, full);
            pose.popPose();
        }
    }

    private static ItemStack showcase(int index) {
        if (showcaseStacks == null) {
            showcaseStacks = new ItemStack[SHOWCASE.size()];
            for (int i = 0; i < showcaseStacks.length; i++) showcaseStacks[i] = ModItems.stack(SHOWCASE.get(i));
        }
        return showcaseStacks[index];
    }

    private void render(ItemStack stack, ItemDisplayContext ctx, Level level, PoseStack pose, MultiBufferSource buf, int light) {
        items.updateForTopItem(state, stack, ctx, false, level, null, 0);
        state.render(pose, buf, light, OverlayTexture.NO_OVERLAY);
    }

    @Override
    public boolean shouldRenderOffScreen(MachineBlockEntity be) {
        MachineType t = be.type();
        return t.solar() || t == MachineType.CRAFTING_STATION;
    }

    @Override
    public int getViewDistance() { return 96; }

    @Override
    public boolean shouldRender(MachineBlockEntity be, Vec3 camera) {
        MachineType t = be.type();
        return (t.solar() || t == MachineType.CRAFTING_STATION) && BlockEntityRenderer.super.shouldRender(be, camera);
    }
}

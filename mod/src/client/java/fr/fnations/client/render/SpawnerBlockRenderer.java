package fr.fnations.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.fnations.spawner.SpawnerBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.SpawnerRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

/** Spawner FNations : le mob tourne dans la cage, comme un spawner classique. */
public final class SpawnerBlockRenderer implements BlockEntityRenderer<SpawnerBlockEntity> {
    private final EntityRenderDispatcher dispatcher;
    /** Une entité d'affichage par type de mob et par monde. */
    private static final Map<String, Entity> DISPLAY = new HashMap<>();
    private static Level displayLevel;

    public SpawnerBlockRenderer(BlockEntityRendererProvider.Context ctx) { this.dispatcher = ctx.getEntityRenderer(); }

    private static Entity display(Level level, String id) {
        if (level != displayLevel) {
            DISPLAY.clear();
            displayLevel = level;
        }
        return DISPLAY.computeIfAbsent(id, k -> {
            ResourceLocation rl = ResourceLocation.tryParse(k);
            var type = rl == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(rl).orElse(null);
            return type == null ? null : type.create(level, EntitySpawnReason.SPAWNER);
        });
    }

    @Override
    public void render(SpawnerBlockEntity be, float pt, PoseStack pose, MultiBufferSource buf, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) return;
        Entity e = display(level, be.entity());
        if (e == null) return;
        SpawnerRenderer.renderEntityInSpawner(pt, pose, buf, light, e, dispatcher, be.oSpin, be.spin);
    }
}

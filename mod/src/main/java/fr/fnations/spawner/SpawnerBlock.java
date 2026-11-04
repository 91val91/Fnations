package fr.fnations.spawner;

import com.mojang.serialization.MapCodec;
import fr.fnations.registry.ModBlockEntities;
import fr.fnations.server.FnServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** Bloc de spawner FNations (cage en métal, mob tournant à l'intérieur). */
public class SpawnerBlock extends BaseEntityBlock {
    public static final MapCodec<SpawnerBlock> CODEC = simpleCodec(SpawnerBlock::new);

    public SpawnerBlock(Properties p) { super(p); }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SpawnerBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> t) {
        return level.isClientSide ? createTickerHelper(t, ModBlockEntities.SPAWNER, SpawnerBlockEntity::clientTick)
                : createTickerHelper(t, ModBlockEntities.SPAWNER, SpawnerBlockEntity::serverTick);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof ServerPlayer p && level.getBlockEntity(pos) instanceof SpawnerBlockEntity be) {
            be.setOwner(p.getUUID(), p.getScoreboardName());
            be.sync();
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof SpawnerBlockEntity) {
            FnServer s = FnServer.get();
            if (s != null) s.ui().open(sp, "spawner", fr.fnations.ui.pages.SpawnerPage.at(sp, pos));
        }
        return InteractionResult.SUCCESS;
    }
}

package fr.fnations.machine;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.fnations.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** Bloc de machine (orientable, avec entité de bloc). */
public class MachineBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final MapCodec<MachineBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            MachineType.CODEC.fieldOf("machine").forGetter(b -> b.type),
            propertiesCodec()).apply(i, (t, p) -> new MachineBlock(p, t)));

    public final MachineType type;

    public MachineBlock(Properties p, MachineType type) {
        super(p);
        this.type = type;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ACTIVE, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, ACTIVE); }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState s, Rotation r) { return s.setValue(FACING, r.rotate(s.getValue(FACING))); }

    @Override
    protected BlockState mirror(BlockState s, Mirror m) { return s.rotate(m.getRotation(s.getValue(FACING))); }

    @Override
    protected RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new MachineBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> t) {
        return level.isClientSide ? null : createTickerHelper(t, ModBlockEntities.MACHINE, MachineBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof MachineBlockEntity be) {
            MachineInteractions.open(sp, be);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof MachineBlockEntity be) {
            Containers.dropContents(level, pos, be);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    /** La Station de Craft fait 1,5 bloc de haut (socle, piliers, auvent). */
    private static final VoxelShape STATION_SHAPE = Block.box(0, 0, 0, 16, 24, 16);

    @Override
    protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return type == MachineType.CRAFTING_STATION ? STATION_SHAPE : super.getShape(s, level, pos, ctx);
    }

    /** Particules d'ambiance (côté client). */
    @Override
    public void animateTick(BlockState s, Level level, BlockPos pos, RandomSource r) {
        // Les particules naissent hors de la hitbox : à l'intérieur, la collision les bloquerait sur place.
        if (type == MachineType.CRAFTING_STATION) {
            if (r.nextInt(2) == 0) // données (lueurs cyan) qui montent de l'écran supérieur ; vitesse x0,01 pour SCRAPE
                level.addParticle(ParticleTypes.SCRAPE, pos.getX() + 0.5 + (r.nextDouble() - 0.5) * 0.7, pos.getY() + 1.53,
                        pos.getZ() + 0.5 + (r.nextDouble() - 0.5) * 0.7, 0, 1.5 + r.nextDouble() * 1.5, 0);
            if (r.nextInt(3) == 0) { // étincelles devant la console
                Direction f = s.getValue(FACING);
                double side = (r.nextDouble() - 0.5) * 0.5; // le long de la console
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5 + f.getStepX() * 0.62 + f.getStepZ() * side,
                        pos.getY() + 0.35, pos.getZ() + 0.5 + f.getStepZ() * 0.62 + f.getStepX() * side,
                        f.getStepX() * 0.02, 0.03, f.getStepZ() * 0.02);
            }
        } else if (type.solar() && s.getValue(ACTIVE) && r.nextInt(2) == 0) {
            // reflets de soleil au-dessus du panneau
            level.addParticle(ParticleTypes.WAX_OFF, pos.getX() + 0.5 + (r.nextDouble() - 0.5) * 1.6, pos.getY() + 1.9,
                    pos.getZ() + 0.5 + (r.nextDouble() - 0.5) * 1.6, 0, 0.01, 0);
        }
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState s) { return true; }

    @Override
    protected int getAnalogOutputSignal(BlockState s, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof MachineBlockEntity be ? (int) Math.round(be.heat() / MachineBlockEntity.MELTDOWN * 15) : 0;
    }
}

package fr.fnations.registry;

import fr.fnations.FNations;
import fr.fnations.machine.MachineBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Entités de bloc. */
public final class ModBlockEntities {
    public static BlockEntityType<MachineBlockEntity> MACHINE;
    public static BlockEntityType<fr.fnations.spawner.SpawnerBlockEntity> SPAWNER;

    private ModBlockEntities() {}

    public static void init() {
        MACHINE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, FNations.id("machine"),
                FabricBlockEntityTypeBuilder.create(MachineBlockEntity::new, ModBlocks.MACHINES.values().toArray(new Block[0])).build());
        SPAWNER = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, FNations.id("spawner"),
                FabricBlockEntityTypeBuilder.create(fr.fnations.spawner.SpawnerBlockEntity::new, ModBlocks.SPAWNER).build());
    }
}

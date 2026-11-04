package fr.fnations.registry;

import fr.fnations.FNations;
import fr.fnations.machine.MachineBlock;
import fr.fnations.machine.MachineType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.EnumMap;
import java.util.Map;

/** Blocs-machines. */
public final class ModBlocks {
    public static final Map<MachineType, MachineBlock> MACHINES = new EnumMap<>(MachineType.class);
    public static fr.fnations.spawner.SpawnerBlock SPAWNER;

    private ModBlocks() {}

    public static void init() {
        for (MachineType t : MachineType.values()) {
            ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, FNations.id(t.id));
            BlockBehaviour.Properties p = BlockBehaviour.Properties.of().setId(key)
                    .mapColor(MapColor.METAL).strength(3.5f, 8f).requiresCorrectToolForDrops()
                    .sound(SoundType.METAL).noOcclusion();
            if (t == MachineType.ELECTRIC_FURNACE || t == MachineType.COAL_GENERATOR) p = p.lightLevel(s -> s.getValue(MachineBlock.ACTIVE) ? 12 : 0);
            if (t == MachineType.CRAFTING_STATION) p = p.lightLevel(s -> 7); // écrans et hologramme
            MachineBlock b = Registry.register(BuiltInRegistries.BLOCK, key, new MachineBlock(p, t));
            MACHINES.put(t, b);
        }
        ResourceKey<Block> sk = ResourceKey.create(Registries.BLOCK, FNations.id("spawner"));
        SPAWNER = Registry.register(BuiltInRegistries.BLOCK, sk, new fr.fnations.spawner.SpawnerBlock(BlockBehaviour.Properties.of().setId(sk)
                .mapColor(MapColor.METAL).strength(5f, 12f).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion().lightLevel(s -> 6)));
    }

    public static MachineBlock get(MachineType t) { return MACHINES.get(t); }
}

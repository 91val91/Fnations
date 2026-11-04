package fr.fnations.spawner;

import fr.fnations.registry.ModComponents;
import fr.fnations.util.Txt;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/** Objet spawner : son nom et sa description indiquent le mob et le niveau. */
public class SpawnerItem extends BlockItem {
    public SpawnerItem(Block block, Properties p) { super(block, p); }

    private static ModComponents.SpawnerData data(ItemStack st) {
        ModComponents.SpawnerData d = st.get(ModComponents.SPAWNER);
        return d == null ? new ModComponents.SpawnerData("zombie", "minecraft:zombie", 1) : d;
    }

    @Override
    public Component getName(ItemStack st) {
        ModComponents.SpawnerData d = data(st);
        ResourceLocation rl = ResourceLocation.tryParse(d.entityId());
        Component mob = rl == null ? Component.literal(d.mob())
                : BuiltInRegistries.ENTITY_TYPE.getOptional(rl).map(e -> (Component) e.getDescription()).orElse(Component.literal(d.mob()));
        return Component.translatable("item.fnations.spawner.named", mob, d.level());
    }

    @Override
    public void appendHoverText(ItemStack st, TooltipContext ctx, List<Component> out, TooltipFlag flag) {
        ModComponents.SpawnerData d = data(st);
        out.add(Txt.c("<#8C96FF>Niveau " + d.level() + " / 5"));
        out.add(Txt.c("<gray>Posez-le puis clic droit pour l'améliorer."));
        out.add(Txt.c("<dark_gray>FNations • Spawner"));
    }
}

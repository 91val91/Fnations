package fr.fnations.registry;

import fr.fnations.FNations;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;

/** Onglet du mode créatif. */
public final class ModTab {
    private ModTab() {}

    public static void init() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, FNations.id("main"), FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.fnations"))
                .icon(() -> ModItems.stack("assault_rifle"))
                .displayItems((params, out) -> {
                    for (Item i : ModItems.ALL.values()) out.accept(i);
                })
                .build());
    }
}

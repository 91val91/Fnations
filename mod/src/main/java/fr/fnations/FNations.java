package fr.fnations;

import fr.fnations.net.ModPayloads;
import fr.fnations.registry.ModBlockEntities;
import fr.fnations.registry.ModBlocks;
import fr.fnations.registry.ModComponents;
import fr.fnations.registry.ModEntities;
import fr.fnations.registry.ModItems;
import fr.fnations.registry.ModMenus;
import fr.fnations.registry.ModTab;
import fr.fnations.server.ServerEvents;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FNations — mod Fabric : nations, R&D, guerres, banque, chambres, armes à feu,
 * véhicules, machines, missiles et conquête spatiale.
 */
public final class FNations implements ModInitializer {
    public static final String MOD_ID = "fnations";
    public static final Logger LOG = LoggerFactory.getLogger("FNations");

    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(MOD_ID, path); }

    @Override
    public void onInitialize() {
        ModComponents.init();
        ModBlocks.init();
        ModItems.init();
        ModBlockEntities.init();
        ModEntities.init();
        ModMenus.init();
        ModTab.init();
        ModPayloads.init();
        fr.fnations.earth.EarthWorld.init();
        ServerEvents.init();
        LOG.info("FNations initialisé : {} items, {} machines.", ModItems.ALL.size(), ModBlocks.MACHINES.size());
    }
}

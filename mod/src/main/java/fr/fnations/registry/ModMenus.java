package fr.fnations.registry;

import fr.fnations.FNations;
import fr.fnations.gui.PanelMenu;
import fr.fnations.machine.MachineMenu;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;

/** Types de menus. */
public final class ModMenus {
    public static ExtendedScreenHandlerType<PanelMenu, Integer> PANEL;
    public static ExtendedScreenHandlerType<MachineMenu, MachineMenu.OpenData> MACHINE;

    private ModMenus() {}

    public static void init() {
        PANEL = Registry.register(BuiltInRegistries.MENU, FNations.id("panel"), new ExtendedScreenHandlerType<>(PanelMenu::new, ByteBufCodecs.VAR_INT));
        MACHINE = Registry.register(BuiltInRegistries.MENU, FNations.id("machine"), new ExtendedScreenHandlerType<>(MachineMenu::new, MachineMenu.OpenData.CODEC));
    }
}

package fr.fnations.machine;

import fr.fnations.gui.Icons;
import fr.fnations.gui.Panel;
import fr.fnations.server.FnServer;
import fr.fnations.util.Txt;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Ouverture des interfaces des machines (menus à cases ou panneaux). */
public final class MachineInteractions {
    private MachineInteractions() {}

    public static void open(ServerPlayer p, MachineBlockEntity be) {
        FnServer s = FnServer.get();
        if (s == null) return;
        if (!s.territory().canBuild(p, (ServerLevel) be.getLevel(), be.getBlockPos())) {
            p.sendSystemMessage(Txt.prefixed("<red>Cette machine ne vous appartient pas."));
            return;
        }
        switch (be.type()) {
            case CRAFTING_STATION -> s.ui().open(p, "station", fr.fnations.ui.pages.StationPage.at(p, be.getBlockPos()));
            case SWITCH -> switchPanel(p, be);
            default -> p.openMenu(be);
        }
    }

    private static String heatColor(double h) {
        return h >= MachineBlockEntity.OVERHEAT ? "<red>" : h >= 70 ? "<gold>" : h >= 40 ? "<yellow>" : "<green>";
    }

    /** Interrupteur de sécurité : panneau dynamique (mis à jour à chaque ouverture/clic). */
    public static void switchPanel(ServerPlayer p, MachineBlockEntity be) {
        Panel g = new Panel(3, "<dark_red>Interrupteur de sécurité");
        render(g, p, be);
        g.open(p);
    }

    private static void render(Panel g, ServerPlayer p, MachineBlockEntity be) {
        g.clear();
        g.button(11, Icons.of(be.enabled() ? Items.LEVER : Items.REDSTONE_TORCH, be.enabled() ? "<green><b>CIRCUIT FERMÉ" : "<red><b>CIRCUIT COUPÉ",
                be.enabled() ? "<gray>Les machines fonctionnent." : "<gray>Les machines refroidissent.", "", "<yellow>Clic : basculer"), pl -> {
            be.toggle();
            pl.level().playSound(null, be.getBlockPos(), SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 1f, be.enabled() ? 1.2f : 0.8f);
            render(g, pl, be);
        });
        double threshold = FnServer.get().config().getDouble("machines.seuil-disjoncteur", 90);
        g.button(15, Icons.of(be.auto() ? Items.REPEATER : Items.STICK, be.auto() ? "<green>Disjoncteur automatique : ON" : "<gray>Disjoncteur automatique : OFF",
                "<gray>Coupe le circuit dès qu'une machine", "<gray>dépasse " + (int) threshold + "°C.", "", "<yellow>Clic : basculer"), pl -> {
            be.toggleAuto();
            render(g, pl, be);
        });
        List<String> lore = new ArrayList<>();
        for (MachineBlockEntity o : MachineNetwork.of((ServerLevel) be.getLevel()).inRange(be)) {
            if (o == be) continue;
            lore.add(heatColor(o.heat()) + "<item:" + o.type().itemKey() + "> <gray>" + (int) o.heat() + "°C" + (o.overheated() ? " <red>⚠ SURCHAUFFE" : ""));
        }
        if (lore.isEmpty()) lore.add("<gray>Aucune machine dans le rayon.");
        g.button(13, Icons.of(Items.OBSERVER, "<white>Machines du réseau (" + (int) MachineNetwork.radius() + " blocs)", lore), pl -> render(g, pl, be));
        g.fill(Items.GRAY_STAINED_GLASS_PANE);
    }
}

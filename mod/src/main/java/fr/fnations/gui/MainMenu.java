package fr.fnations.gui;

import fr.fnations.gui.ConfirmPanel;
import fr.fnations.gui.Icons;
import fr.fnations.gui.PagedPanel;
import fr.fnations.gui.Panel;
import fr.fnations.gui.Prompt;
import fr.fnations.server.FnServer;
import fr.fnations.server.Msg;
import fr.fnations.util.Txt;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import fr.fnations.nation.Nation;

/** Menu principal /menu : accès à tous les systèmes. */
public final class MainMenu {
    private final FnServer plugin;

    public MainMenu(FnServer plugin) { this.plugin = plugin; }

    public void open(ServerPlayer p) {
        Nation n = plugin.nations().of(p);
        Panel g = new Panel(5, "<gold><b>FNations</b> <dark_gray>» Menu principal");
        g.border(Items.ORANGE_STAINED_GLASS_PANE);
        g.set(4, Icons.head(p.getGameProfile(), "<gold>" + p.getScoreboardName(), java.util.List.of(
                "<gray>Nation : " + (n == null ? "<red>aucune" : n.colored()),
                "<gray>Banque : <green>" + Txt.money(plugin.bank().balance(p.getUUID())))));
        g.button(20, Icons.of(Items.WHITE_BANNER, "<aqua><b>Nation", "<gray>Membres, territoire, diplomatie"), pl -> plugin.nationGui().open(pl));
        g.button(21, Icons.of(Items.BREWING_STAND, "<light_purple><b>Recherche & Développement", "<gray>Arbre technologique"), pl -> plugin.researchGui().open(pl));
        g.button(22, Icons.of(Items.NETHERITE_SWORD, "<red><b>Guerres", "<gray>Conflits, captures, paix"), pl -> plugin.warGui().open(pl));
        g.button(23, Icons.of(Items.GOLD_INGOT, "<gold><b>Banque", "<gray>Compte personnel & national"), pl -> plugin.bankGui().open(pl));
        g.button(24, Icons.of(Items.RED_BED, "<dark_purple><b>Chambres", "<gray>Louer ou acheter une chambre"), pl -> plugin.roomGui().open(pl, null));
        g.button(31, Icons.of(Items.SMITHING_TABLE, "<gold>Station de Craft", "<gray>(utilisez une station posée)", "<gray>Aperçu des recettes"), pl -> {
            if (pl.hasPermissions(2)) plugin.station().open(pl);
            else Msg.err(pl, "Rendez-vous à une Station de Craft pour fabriquer.");
        });
        g.button(40, Icons.close(), ServerPlayer::closeContainer);
        g.fill(Items.GRAY_STAINED_GLASS_PANE);
        g.open(p);
    }
}

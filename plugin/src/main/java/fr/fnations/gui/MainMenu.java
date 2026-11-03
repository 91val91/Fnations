package fr.fnations.gui;

import fr.fnations.FNations;
import fr.fnations.nation.Nation;
import fr.fnations.util.Txt;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Menu principal /menu : accès à tous les systèmes. */
public final class MainMenu {
    private final FNations plugin;

    public MainMenu(FNations plugin) { this.plugin = plugin; }

    public void open(Player p) {
        Nation n = plugin.nations().of(p);
        Gui g = new Gui(5, "<gold><b>FNations</b> <dark_gray>» Menu principal");
        g.border(Material.ORANGE_STAINED_GLASS_PANE);
        g.set(4, Icons.head(p, "<gold>" + p.getName(), java.util.List.of(
                "<gray>Nation : " + (n == null ? "<red>aucune" : n.colored()),
                "<gray>Banque : <green>" + Txt.money(plugin.bank().balance(p.getUniqueId())))));
        g.button(20, Icons.of(Material.WHITE_BANNER, "<aqua><b>Nation", "<gray>Membres, territoire, diplomatie"), pl -> plugin.nationGui().open(pl));
        g.button(21, Icons.of(Material.BREWING_STAND, "<light_purple><b>Recherche & Développement", "<gray>Arbre technologique"), pl -> plugin.researchGui().open(pl));
        g.button(22, Icons.of(Material.NETHERITE_SWORD, "<red><b>Guerres", "<gray>Conflits, captures, paix"), pl -> plugin.warGui().open(pl));
        g.button(23, Icons.of(Material.GOLD_INGOT, "<gold><b>Banque", "<gray>Compte personnel & national"), pl -> plugin.bankGui().open(pl));
        g.button(24, Icons.of(Material.RED_BED, "<dark_purple><b>Chambres", "<gray>Louer ou acheter une chambre"), pl -> plugin.roomGui().open(pl, null));
        g.button(31, Icons.of(Material.SMITHING_TABLE, "<gold>Station de Craft", "<gray>(utilisez une station posée)", "<gray>Aperçu des recettes"), pl -> {
            if (pl.hasPermission("fnations.station.distance")) plugin.station().open(pl);
            else Txt.err(pl, "Rendez-vous à une Station de Craft pour fabriquer.");
        });
        g.button(40, Icons.close(), Player::closeInventory);
        g.fill(Material.GRAY_STAINED_GLASS_PANE);
        g.open(p);
    }
}

package fr.fnations.nation;

import fr.fnations.FNations;
import fr.fnations.gui.ConfirmGui;
import fr.fnations.gui.Gui;
import fr.fnations.gui.Icons;
import fr.fnations.gui.PagedGui;
import fr.fnations.util.ChatPrompt;
import fr.fnations.util.Locs;
import fr.fnations.util.Txt;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Tous les menus du système de nations. */
public final class NationGui {
    private static final List<String> COLORS = List.of("aqua", "red", "green", "blue", "yellow", "gold", "light_purple",
            "dark_aqua", "dark_green", "dark_red", "white", "gray");
    private static final Map<String, Material> WOOL = Map.ofEntries(
            Map.entry("aqua", Material.LIGHT_BLUE_WOOL), Map.entry("red", Material.RED_WOOL), Map.entry("green", Material.LIME_WOOL),
            Map.entry("blue", Material.BLUE_WOOL), Map.entry("yellow", Material.YELLOW_WOOL), Map.entry("gold", Material.ORANGE_WOOL),
            Map.entry("light_purple", Material.MAGENTA_WOOL), Map.entry("dark_aqua", Material.CYAN_WOOL),
            Map.entry("dark_green", Material.GREEN_WOOL), Map.entry("dark_red", Material.BROWN_WOOL),
            Map.entry("white", Material.WHITE_WOOL), Map.entry("gray", Material.GRAY_WOOL));

    private final FNations plugin;
    private final NationActions act;

    public NationGui(FNations plugin, NationActions act) {
        this.plugin = plugin;
        this.act = act;
    }

    public static Material wool(Nation n) { return WOOL.getOrDefault(n.color(), Material.WHITE_WOOL); }

    public List<String> describe(Nation n) {
        NationManager nm = plugin.nations();
        List<String> l = new ArrayList<>();
        l.add("<gray>Tag : <white>[" + n.tag() + "]");
        l.add("<gray>Chef : <gold>" + Bukkit.getOfflinePlayer(n.leader()).getName());
        l.add("<gray>Citoyens : <white>" + n.members().size());
        l.add("<gray>Territoire : <white>" + n.claims().size() + "/" + nm.maxClaims(n) + " chunks");
        l.add("<gray>Trésor : <green>" + Txt.money(n.balance()));
        l.add("<gray>Technologies : <aqua>" + n.researched().size() + "/" + plugin.research().all().size());
        l.add("<gray>Guerres : <green>" + n.wins() + " victoires <gray>/ <red>" + n.losses() + " défaites");
        l.add("<gray>Fondée le : <white>" + new SimpleDateFormat("dd/MM/yyyy").format(new Date(n.createdAt())));
        l.add(n.open() ? "<green>Recrutement ouvert" : "<red>Sur invitation");
        return l;
    }

    // ================================================================ menu principal
    public void open(Player p) {
        Nation n = plugin.nations().of(p);
        if (n == null) { openNoNation(p); return; }
        Gui g = new Gui(6, "<dark_blue>Nation » " + n.colored());
        g.border(Material.BLUE_STAINED_GLASS_PANE);
        g.set(4, Icons.glow(Icons.of(wool(n), n.colored() + " <dark_gray>[" + n.tag() + "]", describe(n))));
        Role r = n.role(p.getUniqueId());
        g.button(19, Icons.of(Material.PLAYER_HEAD, "<yellow>Citoyens", "<gray>" + n.members().size() + " membres", "", "<yellow>Clic : gérer"), this::members);
        g.button(20, Icons.of(Material.FILLED_MAP, "<green>Territoire", "<gray>Carte des chunks alentour", "<gray>Revendiquer / abandonner"), this::territory);
        g.button(21, Icons.of(Material.WRITABLE_BOOK, "<aqua>Diplomatie", "<gray>Alliances et relations", "<gray>avec les autres nations"), this::diplomacy);
        g.button(22, Icons.of(Material.GOLD_BLOCK, "<gold>Trésor national", "<gray>Solde : <green>" + Txt.money(n.balance()), "<yellow>Clic : banque nationale"),
                pl -> plugin.bankGui().openNation(pl));
        g.button(23, Icons.of(Material.BREWING_STAND, "<light_purple>Recherche & Développement",
                "<gray>Débloquez armes, véhicules,", "<gray>machines, missiles et fusées."), pl -> plugin.researchGui().open(pl));
        g.button(24, Icons.of(Material.IRON_SWORD, "<red>Guerres", "<gray>Déclarer, suivre et négocier", "<gray>Guerres actives : <white>" + plugin.wars().of(n).size()),
                pl -> plugin.warGui().open(pl));
        g.button(25, Icons.of(Material.RED_BED, "<white>Capitale", "<gray>Clic gauche : s'y téléporter", "<gray>Clic droit : définir ici (Ministre)"), pl -> {});
        g.set(25, g.getInventory().getItem(25), e -> {
            Player pl = (Player) e.getWhoClicked();
            pl.closeInventory();
            if (e.isRightClick()) act.setHome(pl); else act.home(pl);
        });
        g.button(30, Icons.of(Material.OAK_SIGN, "<green>Inviter un joueur", "<gray>Saisie du pseudo dans le chat"),
                pl -> ChatPrompt.ask(pl, "Pseudo du joueur à inviter :", s -> act.invite(pl, s)));
        g.button(32, Icons.of(Material.EMERALD, "<green>Faire un don au trésor", "<gray>Depuis votre compte bancaire"),
                pl -> ChatPrompt.ask(pl, "Montant du don :", s -> { Double v = Txt.parseAmount(s); if (v == null) Txt.err(pl, "Montant invalide."); else act.deposit(pl, v); }));
        if (r == Role.LEADER) g.button(40, Icons.of(Material.COMPARATOR, "<gold>Paramètres de la nation", "<gray>Tag, couleur, impôts, recrutement..."), this::settings);
        else g.button(40, Icons.of(Material.OAK_DOOR, "<red>Quitter la nation"),
                pl -> new ConfirmGui("Quitter " + n.name() + " ?", null, act::leave, this::open).open(pl));
        g.button(49, Icons.close(), Player::closeInventory);
        g.fill(Material.GRAY_STAINED_GLASS_PANE);
        g.open(p);
    }

    private void openNoNation(Player p) {
        Gui g = new Gui(3, "<dark_blue>Nations");
        g.fill(Material.GRAY_STAINED_GLASS_PANE);
        double cost = plugin.getConfig().getDouble("nations.cout-creation", 5000);
        g.button(11, Icons.of(Material.WHITE_BANNER, "<green><b>Fonder une nation", "<gray>Coût : <gold>" + Txt.money(cost), "<gray>Saisie du nom dans le chat"),
                pl -> ChatPrompt.ask(pl, "Nom de votre nation :", s -> act.create(pl, s)));
        g.button(13, Icons.of(Material.BOOK, "<aqua>Liste des nations", "<gray>Rejoindre une nation ouverte", "<gray>ou à laquelle vous êtes invité"), this::listToJoin);
        g.button(15, Icons.of(Material.GOLD_INGOT, "<gold>Ma banque"), pl -> plugin.bankGui().open(pl));
        g.open(p);
    }

    private void listToJoin(Player p) {
        List<PagedGui.Entry> list = new ArrayList<>();
        for (Nation n : plugin.nations().all()) {
            boolean can = n.open() || n.invites().contains(p.getUniqueId());
            List<String> lore = describe(n);
            lore.add("");
            lore.add(can ? "<green>Clic : rejoindre" : "<red>Invitation requise");
            list.add(new PagedGui.Entry(Icons.of(wool(n), n.colored(), lore), can ? pl -> { pl.closeInventory(); act.join(pl, n.id()); } : null));
        }
        new PagedGui("<dark_blue>Nations du monde", list, this::open).open(p);
    }

    // ================================================================ membres
    public void members(Player p) {
        Nation n = plugin.nations().of(p);
        if (n == null) return;
        List<PagedGui.Entry> list = new ArrayList<>();
        n.members().entrySet().stream().sorted((a, b) -> b.getValue().power - a.getValue().power).forEach(e -> {
            OfflinePlayer op = Bukkit.getOfflinePlayer(e.getKey());
            List<String> lore = new ArrayList<>(List.of(e.getValue().display, op.isOnline() ? "<green>● En ligne" : "<gray>● Hors ligne",
                    "<gray>Solde : <green>" + Txt.money(plugin.bank().balance(e.getKey()))));
            if (n.can(p.getUniqueId(), Role.OFFICER) && !e.getKey().equals(p.getUniqueId())) { lore.add(""); lore.add("<yellow>Clic : gérer"); }
            list.add(new PagedGui.Entry(Icons.head(op, "<white>" + op.getName(), lore), pl -> member(pl, e.getKey())));
        });
        new PagedGui("<dark_blue>Citoyens de " + n.colored(), list, this::open).open(p);
    }

    private void member(Player p, UUID target) {
        Nation n = plugin.nations().of(p);
        if (n == null || !n.can(p.getUniqueId(), Role.OFFICER) || target.equals(p.getUniqueId())) return;
        OfflinePlayer op = Bukkit.getOfflinePlayer(target);
        Gui g = new Gui(3, "<dark_blue>Gérer " + op.getName());
        g.fill(Material.GRAY_STAINED_GLASS_PANE);
        g.set(4, Icons.head(op, "<white>" + op.getName(), List.of(n.role(target) == null ? "" : n.role(target).display)));
        g.button(10, Icons.of(Material.LIME_DYE, "<green>Promouvoir Ministre"), pl -> { act.promote(pl, target); members(pl); });
        g.button(12, Icons.of(Material.GRAY_DYE, "<yellow>Rétrograder Citoyen"), pl -> { act.demote(pl, target); members(pl); });
        g.button(14, Icons.of(Material.GOLDEN_HELMET, "<gold>Transférer la direction"),
                pl -> new ConfirmGui("Céder la nation à " + op.getName() + " ?", null, x -> act.transfer(x, target), this::members).open(pl));
        g.button(16, Icons.of(Material.TNT, "<red>Expulser"),
                pl -> new ConfirmGui("Expulser " + op.getName() + " ?", null, x -> act.kick(x, target), this::members).open(pl));
        g.button(22, Icons.back(), this::members);
        g.open(p);
    }

    // ================================================================ territoire
    public void territory(Player p) {
        Nation mine = plugin.nations().of(p);
        if (mine == null) return;
        Gui g = new Gui(6, "<dark_green>Carte du territoire");
        int cx = p.getLocation().getBlockX() >> 4, cz = p.getLocation().getBlockZ() >> 4;
        String w = p.getWorld().getName();
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 9; col++) {
                int x = cx + col - 4, z = cz + row - 2;
                String key = Locs.chunkKey(w, x, z);
                Nation o = plugin.nations().atChunk(key);
                Material m;
                String name;
                if (o == null) { m = Material.LIGHT_GRAY_STAINED_GLASS_PANE; name = "<gray>Terres sauvages"; }
                else if (o == mine) { m = Material.LIME_STAINED_GLASS_PANE; name = "<green>" + o.name() + " (vous)"; }
                else if (plugin.nations().allied(mine, o)) { m = Material.LIGHT_BLUE_STAINED_GLASS_PANE; name = "<aqua>" + o.name() + " (allié)"; }
                else if (plugin.wars().between(mine, o) != null) { m = Material.RED_STAINED_GLASS_PANE; name = "<red>" + o.name() + " (ennemi)"; }
                else { m = Material.ORANGE_STAINED_GLASS_PANE; name = "<gold>" + o.name(); }
                if (plugin.rooms().intersectsChunk(w, x, z)) { m = Material.PURPLE_STAINED_GLASS_PANE; name = "<light_purple>Hôtel (chambres)"; }
                if (col == 4 && row == 2) { m = Material.COMPASS; name = "<yellow>Vous êtes ici <gray>— " + name; }
                g.set(row * 9 + col, Icons.of(m, name, "<dark_gray>Chunk " + x + ", " + z));
            }
        }
        g.set(45, Icons.of(Material.MAP, "<white>Légende", "<green>■ Votre nation", "<aqua>■ Alliés", "<red>■ Ennemis (guerre)",
                "<gold>■ Autres nations", "<light_purple>■ Hôtel", "<gray>■ Libre", "",
                "<gray>Territoire : " + mine.claims().size() + "/" + plugin.nations().maxClaims(mine)));
        g.button(47, Icons.of(Material.GREEN_BANNER, "<green>Revendiquer ce chunk", "<gray>Coût : <gold>" + Txt.money(plugin.getConfig().getDouble("nations.cout-claim", 500))),
                pl -> { act.claim(pl); territory(pl); });
        g.button(48, Icons.of(Material.RED_BANNER, "<red>Abandonner ce chunk"), pl -> { act.unclaim(pl); territory(pl); });
        g.button(50, Icons.of(Material.SUNFLOWER, "<yellow>Actualiser"), this::territory);
        g.button(53, Icons.back(), this::open);
        g.fill(Material.BLACK_STAINED_GLASS_PANE);
        g.open(p);
    }

    // ================================================================ diplomatie
    public void diplomacy(Player p) {
        Nation mine = plugin.nations().of(p);
        if (mine == null) return;
        List<PagedGui.Entry> list = new ArrayList<>();
        for (Nation o : plugin.nations().all()) {
            if (o == mine) continue;
            List<String> lore = describe(o);
            lore.add("");
            if (mine.allies().contains(o.id())) lore.add("<aqua>★ Allié");
            else if (plugin.wars().between(mine, o) != null) lore.add("<red>⚔ En guerre");
            else if (mine.allyRequests().contains(o.id())) lore.add("<yellow>✉ Vous propose une alliance");
            else lore.add("<gray>Neutre");
            lore.add("<yellow>Clic : relations");
            list.add(new PagedGui.Entry(Icons.of(wool(o), o.colored(), lore), pl -> relation(pl, o)));
        }
        new PagedGui("<dark_aqua>Diplomatie", list, this::open).open(p);
    }

    private void relation(Player p, Nation o) {
        Nation mine = plugin.nations().of(p);
        if (mine == null) return;
        Gui g = new Gui(3, "<dark_aqua>Relations avec " + o.colored());
        g.fill(Material.GRAY_STAINED_GLASS_PANE);
        g.set(4, Icons.of(wool(o), o.colored(), describe(o)));
        boolean allied = mine.allies().contains(o.id());
        String allyLabel = allied ? "<red>Rompre l'alliance" : mine.allyRequests().contains(o.id()) ? "<green>Accepter l'alliance" : "<aqua>Proposer une alliance";
        g.button(11, Icons.of(allied ? Material.SHEARS : Material.LEAD, allyLabel), pl -> { act.ally(pl, o.id()); diplomacy(pl); });
        g.button(15, Icons.of(Material.NETHERITE_SWORD, "<dark_red><b>Déclarer la guerre",
                        "<gray>Coût : <gold>" + Txt.money(plugin.getConfig().getDouble("guerres.cout-declaration", 10000))),
                pl -> plugin.warGui().confirmDeclare(pl, o));
        g.button(22, Icons.back(), this::diplomacy);
        g.open(p);
    }

    // ================================================================ paramètres
    public void settings(Player p) {
        Nation n = plugin.nations().of(p);
        if (n == null || !n.can(p.getUniqueId(), Role.LEADER)) return;
        Gui g = new Gui(4, "<gold>Paramètres de " + n.colored());
        g.fill(Material.GRAY_STAINED_GLASS_PANE);
        g.button(10, Icons.of(Material.NAME_TAG, "<white>Tag : [" + n.tag() + "]", "<yellow>Clic : modifier"),
                pl -> ChatPrompt.ask(pl, "Nouveau tag (2-5 caractères) :", s -> { act.setTag(pl, s); settings(pl); }));
        g.button(12, Icons.of(wool(n), "<" + n.color() + ">Couleur : " + n.color(), "<yellow>Clic : couleur suivante"), pl -> {
            int i = (COLORS.indexOf(n.color()) + 1) % COLORS.size();
            n.setColor(COLORS.get(i));
            settings(pl);
        });
        g.button(14, Icons.of(Material.GOLD_NUGGET, "<gold>Impôt : " + Txt.money(n.taxPerHour()) + "/h", "<gray>Prélevé sur chaque citoyen", "<yellow>Clic : modifier"),
                pl -> ChatPrompt.ask(pl, "Impôt par heure et par citoyen :", s -> {
                    try { act.setTax(pl, Double.parseDouble(s.replace(',', '.'))); } catch (NumberFormatException ex) { Txt.err(pl, "Nombre invalide."); }
                    settings(pl);
                }));
        g.button(16, Icons.of(n.open() ? Material.LIME_DYE : Material.RED_DYE, n.open() ? "<green>Recrutement ouvert" : "<red>Sur invitation", "<yellow>Clic : basculer"),
                pl -> { n.setOpen(!n.open()); settings(pl); });
        g.button(22, Icons.of(Material.TNT, "<dark_red><b>Dissoudre la nation", "<gray>Le trésor vous est reversé.", "<red>Irréversible !"),
                pl -> new ConfirmGui("Dissoudre " + n.name() + " ?", "Tous les territoires seront perdus.", act::disband, this::settings).open(pl));
        g.button(31, Icons.back(), this::open);
        g.open(p);
    }
}

package fr.fnations.room;

import fr.fnations.FNations;
import fr.fnations.gui.ConfirmGui;
import fr.fnations.gui.Gui;
import fr.fnations.gui.Icons;
import fr.fnations.gui.PagedGui;
import fr.fnations.util.ChatPrompt;
import fr.fnations.util.Txt;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/** GUI de la réception : liste des chambres, location, achat, gestion. */
public final class RoomGui {
    private final FNations plugin;
    private final SimpleDateFormat fmt = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    public RoomGui(FNations plugin) { this.plugin = plugin; }

    private RoomManager rm() { return plugin.rooms(); }

    private Material icon(Room r, Player viewer) {
        if (viewer.getUniqueId().equals(r.owner())) return Material.LIGHT_BLUE_BED;
        if (r.members().contains(viewer.getUniqueId())) return Material.CYAN_BED;
        return switch (r.mode()) {
            case FREE -> Material.LIME_BED;
            case RENTED -> Material.YELLOW_BED;
            case OWNED -> Material.RED_BED;
        };
    }

    private List<String> describe(Room r) {
        List<String> l = new ArrayList<>();
        l.add("<gray>Groupe : <white>" + r.group());
        l.add("<gray>Taille : <white>" + r.size() + " <dark_gray>(" + r.volume() + " blocs)");
        l.add("<gray>Achat : " + (r.buyPrice() > 0 ? "<gold>" + Txt.money(r.buyPrice()) : "<dark_gray>non disponible"));
        l.add("<gray>Location : " + (r.rentPrice() > 0 ? "<gold>" + Txt.money(r.rentPrice()) + "<gray>/jour" : "<dark_gray>non disponible"));
        l.add("");
        switch (r.mode()) {
            case FREE -> l.add("<green><b>✔ LIBRE");
            case RENTED -> {
                l.add("<yellow>Louée par <white>" + Bukkit.getOfflinePlayer(r.owner()).getName());
                l.add("<gray>Jusqu'au <white>" + fmt.format(new Date(r.expiry())) + " <dark_gray>(" + Txt.duration((r.expiry() - System.currentTimeMillis()) / 1000) + ")");
            }
            case OWNED -> l.add("<red>Propriété de <white>" + Bukkit.getOfflinePlayer(r.owner()).getName());
        }
        if (!r.members().isEmpty()) l.add("<gray>Colocataires : <white>" + r.members().size());
        return l;
    }

    /** Liste des chambres (group = null → toutes). */
    public void open(Player p, String group) {
        List<PagedGui.Entry> list = new ArrayList<>();
        list.add(new PagedGui.Entry(Icons.glow(Icons.of(Material.ENDER_CHEST, "<aqua><b>Mes chambres", "<gray>" + rm().ownedBy(p.getUniqueId()).size()
                + " chambre(s)", "<gray>Solde : <gold>" + Txt.money(plugin.bank().balance(p.getUniqueId())))), this::mine));
        for (Room r : rm().all()) {
            if (group != null && !group.isEmpty() && !group.equalsIgnoreCase(r.group())) continue;
            List<String> lore = describe(r);
            lore.add("");
            lore.add("<yellow>Clic : détails");
            list.add(new PagedGui.Entry(Icons.of(icon(r, p), "<white>Chambre <b>" + r.id(), lore), pl -> detail(pl, r, group)));
        }
        new PagedGui("<dark_purple>Réception" + (group == null || group.isEmpty() ? "" : " » " + group), list, null).open(p);
    }

    private void mine(Player p) {
        List<PagedGui.Entry> list = new ArrayList<>();
        for (Room r : rm().all()) {
            if (!r.canUse(p.getUniqueId())) continue;
            list.add(new PagedGui.Entry(Icons.of(icon(r, p), "<white>Chambre <b>" + r.id(), describe(r)), pl -> detail(pl, r, null)));
        }
        if (list.isEmpty()) list.add(new PagedGui.Entry(Icons.of(Material.BARRIER, "<gray>Vous n'avez aucune chambre"), null));
        new PagedGui("<dark_purple>Mes chambres", list, pl -> open(pl, null)).open(p);
    }

    public void detail(Player p, Room r, String group) {
        Gui g = new Gui(5, "<dark_purple>Chambre " + r.id());
        g.border(Material.PURPLE_STAINED_GLASS_PANE);
        g.set(4, Icons.of(icon(r, p), "<white><b>Chambre " + r.id(), describe(r)));
        UUID u = p.getUniqueId();
        boolean owner = u.equals(r.owner());
        if (r.mode() == Room.Mode.FREE) {
            if (r.buyPrice() > 0) g.button(20, Icons.of(Material.GOLD_BLOCK, "<gold><b>Acheter", "<gray>Prix : <gold>" + Txt.money(r.buyPrice()), "<gray>Propriété permanente"),
                    pl -> new ConfirmGui("Acheter la chambre " + r.id() + " ?", Txt.money(r.buyPrice()), x -> { rm().buy(x, r); detail(x, r, group); }, x -> detail(x, r, group)).open(pl));
            if (r.rentPrice() > 0) {
                g.button(22, Icons.of(Material.CLOCK, "<yellow>Louer 1 jour", "<gray>Prix : <gold>" + Txt.money(r.rentPrice())), pl -> { rm().rent(pl, r, 1); detail(pl, r, group); });
                g.button(23, Icons.of(Material.CLOCK, "<yellow>Louer 7 jours", "<gray>Prix : <gold>" + Txt.money(r.rentPrice() * 7)), pl -> { rm().rent(pl, r, 7); detail(pl, r, group); });
                g.button(24, Icons.of(Material.WRITABLE_BOOK, "<yellow>Louer X jours", "<gray>Saisie du nombre de jours"), pl -> ChatPrompt.ask(pl, "Nombre de jours :", s -> {
                    try { rm().rent(pl, r, Math.max(1, Integer.parseInt(s.trim()))); } catch (NumberFormatException e) { Txt.err(pl, "Nombre invalide."); }
                }));
            }
        }
        if (owner && r.mode() == Room.Mode.RENTED) {
            g.button(22, Icons.of(Material.CLOCK, "<yellow>Prolonger d'1 jour", "<gray>Prix : <gold>" + Txt.money(r.rentPrice())), pl -> { rm().rent(pl, r, 1); detail(pl, r, group); });
            g.button(23, Icons.of(Material.CLOCK, "<yellow>Prolonger de 7 jours", "<gray>Prix : <gold>" + Txt.money(r.rentPrice() * 7)), pl -> { rm().rent(pl, r, 7); detail(pl, r, group); });
        }
        if (r.canUse(u) || p.hasPermission("fnations.admin")) {
            g.button(19, Icons.of(Material.ENDER_PEARL, "<aqua>Se téléporter"), pl -> {
                if (r.spawn() != null) { pl.closeInventory(); pl.teleportAsync(r.spawn()); }
            });
        }
        if (owner) {
            g.button(29, Icons.of(Material.PLAYER_HEAD, "<green>Ajouter un colocataire", "<gray>Saisie du pseudo"), pl -> ChatPrompt.ask(pl, "Pseudo du colocataire :", s -> {
                Player t = Bukkit.getPlayerExact(s);
                if (t == null) { Txt.err(pl, "Joueur introuvable (doit être en ligne)."); return; }
                r.members().add(t.getUniqueId());
                Txt.ok(pl, t.getName() + " peut maintenant utiliser la chambre.");
                Txt.send(t, "<green>" + pl.getName() + " vous a ajouté à sa chambre " + r.id() + ".");
            }));
            g.button(30, Icons.of(Material.SKELETON_SKULL, "<red>Retirer un colocataire"), pl -> roommates(pl, r, group));
            String label = r.mode() == Room.Mode.OWNED ? "<red>Revendre (" + (int) plugin.getConfig().getDouble("chambres.revente-pourcent", 50) + "%)" : "<red>Résilier la location";
            g.button(33, Icons.of(Material.LAVA_BUCKET, label, "<gray>La chambre sera réinitialisée", "<red>Le contenu sera perdu !"),
                    pl -> new ConfirmGui("Libérer la chambre " + r.id() + " ?", "Son contenu sera effacé.", x -> rm().release(x, r), x -> detail(x, r, group)).open(pl));
        }
        if (p.hasPermission("fnations.admin")) {
            g.button(40, Icons.of(Material.COMMAND_BLOCK, "<dark_red>[Admin] Réinitialiser", "<gray>Restaure le schematic WorldEdit"), pl -> {
                Txt.send(pl, rm().reset(r) ? "<green>Chambre réinitialisée." : "<red>Échec de la restauration.");
            });
            if (r.mode() != Room.Mode.FREE) g.button(41, Icons.of(Material.BARRIER, "<dark_red>[Admin] Expulser l'occupant"),
                    pl -> new ConfirmGui("Expulser l'occupant ?", null, x -> rm().release(x, r), x -> detail(x, r, group)).open(pl));
        }
        g.button(36, Icons.back(), pl -> open(pl, group));
        g.fill(Material.GRAY_STAINED_GLASS_PANE);
        g.open(p);
    }

    private void roommates(Player p, Room r, String group) {
        List<PagedGui.Entry> list = new ArrayList<>();
        for (UUID m : new ArrayList<>(r.members())) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(m);
            list.add(new PagedGui.Entry(Icons.head(op, "<white>" + op.getName(), List.of("<red>Clic : retirer")), pl -> {
                r.members().remove(m);
                roommates(pl, r, group);
            }));
        }
        new PagedGui("<dark_purple>Colocataires", list, pl -> detail(pl, r, group)).open(p);
    }
}

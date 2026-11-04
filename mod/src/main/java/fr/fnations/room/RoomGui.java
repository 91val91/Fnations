package fr.fnations.room;

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


import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/** GUI de la réception : liste des chambres, location, achat, gestion. */
public final class RoomGui {
    private final FnServer plugin;
    private final SimpleDateFormat fmt = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    public RoomGui(FnServer plugin) { this.plugin = plugin; }

    private RoomManager rm() { return plugin.rooms(); }

    private net.minecraft.world.item.Item icon(Room r, ServerPlayer viewer) {
        if (viewer.getUUID().equals(r.owner())) return Items.LIGHT_BLUE_BED;
        if (r.members().contains(viewer.getUUID())) return Items.CYAN_BED;
        return switch (r.mode()) {
            case FREE -> Items.LIME_BED;
            case RENTED -> Items.YELLOW_BED;
            case OWNED -> Items.RED_BED;
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
                l.add("<yellow>Louée par <white>" + plugin.name(r.owner()));
                l.add("<gray>Jusqu'au <white>" + fmt.format(new Date(r.expiry())) + " <dark_gray>(" + Txt.duration((r.expiry() - System.currentTimeMillis()) / 1000) + ")");
            }
            case OWNED -> l.add("<red>Propriété de <white>" + plugin.name(r.owner()));
        }
        if (!r.members().isEmpty()) l.add("<gray>Colocataires : <white>" + r.members().size());
        return l;
    }

    /** Liste des chambres (group = null → toutes). */
    public void open(ServerPlayer p, String group) {
        List<PagedPanel.Entry> list = new ArrayList<>();
        list.add(new PagedPanel.Entry(Icons.glow(Icons.of(Items.ENDER_CHEST, "<aqua><b>Mes chambres", "<gray>" + rm().ownedBy(p.getUUID()).size()
                + " chambre(s)", "<gray>Solde : <gold>" + Txt.money(plugin.bank().balance(p.getUUID())))), this::mine));
        for (Room r : rm().all()) {
            if (group != null && !group.isEmpty() && !group.equalsIgnoreCase(r.group())) continue;
            List<String> lore = describe(r);
            lore.add("");
            lore.add("<yellow>Clic : détails");
            list.add(new PagedPanel.Entry(Icons.of(icon(r, p), "<white>Chambre <b>" + r.id(), lore), pl -> detail(pl, r, group)));
        }
        new PagedPanel("<dark_purple>Réception" + (group == null || group.isEmpty() ? "" : " » " + group), list, null).open(p);
    }

    private void mine(ServerPlayer p) {
        List<PagedPanel.Entry> list = new ArrayList<>();
        for (Room r : rm().all()) {
            if (!r.canUse(p.getUUID())) continue;
            list.add(new PagedPanel.Entry(Icons.of(icon(r, p), "<white>Chambre <b>" + r.id(), describe(r)), pl -> detail(pl, r, null)));
        }
        if (list.isEmpty()) list.add(new PagedPanel.Entry(Icons.of(Items.BARRIER, "<gray>Vous n'avez aucune chambre"), null));
        new PagedPanel("<dark_purple>Mes chambres", list, pl -> open(pl, null)).open(p);
    }

    public void detail(ServerPlayer p, Room r, String group) {
        Panel g = new Panel(5, "<dark_purple>Chambre " + r.id());
        g.border(Items.PURPLE_STAINED_GLASS_PANE);
        g.set(4, Icons.of(icon(r, p), "<white><b>Chambre " + r.id(), describe(r)));
        UUID u = p.getUUID();
        boolean owner = u.equals(r.owner());
        if (r.mode() == Room.Mode.FREE) {
            if (r.buyPrice() > 0) g.button(20, Icons.of(Items.GOLD_BLOCK, "<gold><b>Acheter", "<gray>Prix : <gold>" + Txt.money(r.buyPrice()), "<gray>Propriété permanente"),
                    pl -> new ConfirmPanel("Acheter la chambre " + r.id() + " ?", Txt.money(r.buyPrice()), x -> { rm().buy(x, r); detail(x, r, group); }, x -> detail(x, r, group)).open(pl));
            if (r.rentPrice() > 0) {
                g.button(22, Icons.of(Items.CLOCK, "<yellow>Louer 1 jour", "<gray>Prix : <gold>" + Txt.money(r.rentPrice())), pl -> { rm().rent(pl, r, 1); detail(pl, r, group); });
                g.button(23, Icons.of(Items.CLOCK, "<yellow>Louer 7 jours", "<gray>Prix : <gold>" + Txt.money(r.rentPrice() * 7)), pl -> { rm().rent(pl, r, 7); detail(pl, r, group); });
                g.button(24, Icons.of(Items.WRITABLE_BOOK, "<yellow>Louer X jours", "<gray>Saisie du nombre de jours"), pl -> Prompt.ask(pl, "Nombre de jours :", s -> {
                    try { rm().rent(pl, r, Math.max(1, Integer.parseInt(s.trim()))); } catch (NumberFormatException e) { Msg.err(pl, "Nombre invalide."); }
                }));
            }
        }
        if (owner && r.mode() == Room.Mode.RENTED) {
            g.button(22, Icons.of(Items.CLOCK, "<yellow>Prolonger d'1 jour", "<gray>Prix : <gold>" + Txt.money(r.rentPrice())), pl -> { rm().rent(pl, r, 1); detail(pl, r, group); });
            g.button(23, Icons.of(Items.CLOCK, "<yellow>Prolonger de 7 jours", "<gray>Prix : <gold>" + Txt.money(r.rentPrice() * 7)), pl -> { rm().rent(pl, r, 7); detail(pl, r, group); });
        }
        if (r.canUse(u) || p.hasPermissions(2)) {
            g.button(19, Icons.of(Items.ENDER_PEARL, "<aqua>Se téléporter"), pl -> {
                if (r.spawn() != null) { pl.closeContainer(); rm().teleport(pl, r); }
            });
        }
        if (owner) {
            g.button(29, Icons.of(Items.PLAYER_HEAD, "<green>Ajouter un colocataire", "<gray>Saisie du pseudo"), pl -> Prompt.ask(pl, "Pseudo du colocataire :", s -> {
                ServerPlayer t = plugin.playerByName(s);
                if (t == null) { Msg.err(pl, "Joueur introuvable (doit être en ligne)."); return; }
                r.members().add(t.getUUID());
                Msg.ok(pl, t.getScoreboardName() + " peut maintenant utiliser la chambre.");
                Msg.send(t, "<green>" + pl.getScoreboardName() + " vous a ajouté à sa chambre " + r.id() + ".");
            }));
            g.button(30, Icons.of(Items.SKELETON_SKULL, "<red>Retirer un colocataire"), pl -> roommates(pl, r, group));
            String label = r.mode() == Room.Mode.OWNED ? "<red>Revendre (" + (int) plugin.config().getDouble("chambres.revente-pourcent", 50) + "%)" : "<red>Résilier la location";
            g.button(33, Icons.of(Items.LAVA_BUCKET, label, "<gray>La chambre sera réinitialisée", "<red>Le contenu sera perdu !"),
                    pl -> new ConfirmPanel("Libérer la chambre " + r.id() + " ?", "Son contenu sera effacé.", x -> rm().release(x, r), x -> detail(x, r, group)).open(pl));
        }
        if (p.hasPermissions(2)) {
            g.button(40, Icons.of(Items.COMMAND_BLOCK, "<dark_red>[Admin] Réinitialiser", "<gray>Restaure le schematic WorldEdit"), pl -> {
                Msg.send(pl, rm().reset(r) ? "<green>Chambre réinitialisée." : "<red>Échec de la restauration.");
            });
            if (r.mode() != Room.Mode.FREE) g.button(41, Icons.of(Items.BARRIER, "<dark_red>[Admin] Expulser l'occupant"),
                    pl -> new ConfirmPanel("Expulser l'occupant ?", null, x -> rm().release(x, r), x -> detail(x, r, group)).open(pl));
        }
        g.button(36, Icons.back(), pl -> open(pl, group));
        g.fill(Items.GRAY_STAINED_GLASS_PANE);
        g.open(p);
    }

    private void roommates(ServerPlayer p, Room r, String group) {
        List<PagedPanel.Entry> list = new ArrayList<>();
        for (UUID m : new ArrayList<>(r.members())) {
            list.add(new PagedPanel.Entry(Icons.head(plugin.profile(m), "<white>" + plugin.name(m), List.of("<red>Clic : retirer")), pl -> {
                r.members().remove(m);
                roommates(pl, r, group);
            }));
        }
        new PagedPanel("<dark_purple>Colocataires", list, pl -> detail(pl, r, group)).open(p);
    }
}

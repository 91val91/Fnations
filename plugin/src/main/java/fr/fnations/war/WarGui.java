package fr.fnations.war;

import fr.fnations.FNations;
import fr.fnations.gui.ConfirmGui;
import fr.fnations.gui.Gui;
import fr.fnations.gui.Icons;
import fr.fnations.gui.PagedGui;
import fr.fnations.nation.Nation;
import fr.fnations.nation.NationGui;
import fr.fnations.util.Txt;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** GUI du système de guerres. */
public final class WarGui {
    private final FNations plugin;

    public WarGui(FNations plugin) { this.plugin = plugin; }

    private WarManager wm() { return plugin.wars(); }

    private String name(String id) {
        Nation n = wm().nation(id);
        return n == null ? id : n.colored();
    }

    private List<String> describe(War w) {
        List<String> l = new ArrayList<>();
        long now = System.currentTimeMillis();
        if (w.state() == War.State.PREPARATION) l.add("<yellow>⌛ Préparation : " + Txt.duration((w.activeAt() - now) / 1000));
        else l.add("<red>⚔ Hostilités en cours");
        int goal = wm().goal();
        l.add("");
        l.add(name(w.attacker()) + " <gray>(attaquant)");
        l.add(Txt.progress(w.attackerScore() / (double) goal, 25, "red", "dark_gray") + " <white>" + w.attackerScore() + "/" + goal + " <gray>• " + w.kills(w.attacker()) + " kills");
        l.add(name(w.defender()) + " <gray>(défenseur)");
        l.add(Txt.progress(w.defenderScore() / (double) goal, 25, "blue", "dark_gray") + " <white>" + w.defenderScore() + "/" + goal + " <gray>• " + w.kills(w.defender()) + " kills");
        l.add("");
        l.add("<gray>Durée : <white>" + Txt.duration((now - w.declaredAt()) / 1000) + " <dark_gray>/ max " + plugin.getConfig().getLong("guerres.duree-max-heures", 72) + "h");
        if (w.peaceProposedBy() != null) l.add("<green>☮ Paix proposée par " + name(w.peaceProposedBy()));
        return l;
    }

    public void open(Player p) {
        Nation n = plugin.nations().of(p);
        Gui g = new Gui(6, "<dark_red>Guerres");
        g.border(Material.RED_STAINED_GLASS_PANE);
        g.set(4, Icons.of(Material.NETHERITE_SWORD, "<dark_red><b>Ministère de la Guerre",
                "<gray>Score de victoire : <white>" + wm().goal(),
                "<gray>Kill : <white>+" + plugin.getConfig().getInt("guerres.points-kill", 5) + " <gray>• Capture : <white>+" + plugin.getConfig().getInt("guerres.points-capture", 15)
                        + " <gray>• Missile : <white>+" + plugin.getConfig().getInt("guerres.points-missile", 10),
                "<gray>Réparations du vaincu : <white>" + plugin.getConfig().getInt("guerres.reparations-pourcent", 30) + "% du trésor"));
        int[] slots = Gui.innerSlots(6);
        int i = 0;
        if (n != null) {
            for (War w : wm().of(n)) {
                if (i >= slots.length - 7) break;
                List<String> lore = describe(w);
                lore.add("");
                lore.add("<yellow>Clic : détails & négociations");
                g.button(slots[i++], Icons.of(w.state() == War.State.ACTIVE ? Material.TNT : Material.CLOCK,
                        name(w.attacker()) + " <dark_red>⚔ " + name(w.defender()), lore), pl -> detail(pl, w));
            }
            if (i == 0) g.set(22, Icons.of(Material.WHITE_BANNER, "<green>☮ Votre nation est en paix"));
            g.button(46, Icons.of(Material.IRON_SWORD, "<red><b>Déclarer une guerre", "<gray>Choisir une nation cible",
                    "<gray>Coût : <gold>" + Txt.money(plugin.getConfig().getDouble("guerres.cout-declaration", 10000))), this::chooseTarget);
            g.button(47, Icons.of(Material.RED_BANNER, "<red>Capturer ce territoire", "<gray>Debout sur un chunk ennemi",
                    "<gray>pendant " + plugin.getConfig().getInt("guerres.capture-secondes", 60) + " secondes"), pl -> { pl.closeInventory(); wm().startCapture(pl); });
        } else {
            g.set(22, Icons.of(Material.BARRIER, "<gray>Vous n'avez pas de nation"));
        }
        g.button(51, Icons.of(Material.FILLED_MAP, "<white>Toutes les guerres du monde", "<gray>" + wm().all().size() + " conflit(s)"), this::world);
        g.button(49, Icons.back(), pl -> plugin.nationGui().open(pl));
        g.fill(Material.GRAY_STAINED_GLASS_PANE);
        g.open(p);
    }

    private void detail(Player p, War w) {
        Nation n = plugin.nations().of(p);
        if (n == null || !wm().all().contains(w)) { open(p); return; }
        Gui g = new Gui(4, "<dark_red>Guerre : " + Txt.plain(Txt.raw(name(w.attacker()))) + " vs " + Txt.plain(Txt.raw(name(w.defender()))));
        g.border(Material.RED_STAINED_GLASS_PANE);
        g.set(4, Icons.of(Material.TNT, name(w.attacker()) + " <dark_red>⚔ " + name(w.defender()), describe(w)));
        boolean theyProposed = w.peaceProposedBy() != null && !w.peaceProposedBy().equals(n.id());
        g.button(11, Icons.of(Material.WHITE_BANNER, theyProposed ? "<green><b>Accepter la paix" : "<green>Proposer une paix blanche",
                "<gray>Aucune réparation, fin du conflit"), pl -> { wm().proposePeace(pl, w); open(pl); });
        g.button(13, Icons.of(Material.RED_BANNER, "<red>Capturer le chunk actuel"), pl -> { pl.closeInventory(); wm().startCapture(pl); });
        g.button(15, Icons.of(Material.SKELETON_SKULL, "<dark_red>Capituler", "<gray>L'ennemi gagne la guerre", "<gray>et reçoit des réparations."),
                pl -> new ConfirmGui("Capituler ?", "Réservé au Chef d'État.", x -> wm().surrender(x, w), x -> detail(x, w)).open(pl));
        g.button(31, Icons.back(), this::open);
        g.fill(Material.GRAY_STAINED_GLASS_PANE);
        g.open(p);
    }

    private void chooseTarget(Player p) {
        Nation n = plugin.nations().of(p);
        if (n == null) return;
        List<PagedGui.Entry> list = new ArrayList<>();
        for (Nation o : plugin.nations().all()) {
            if (o == n || plugin.nations().allied(n, o) || wm().between(n, o) != null) continue;
            List<String> lore = plugin.nationGui().describe(o);
            lore.add("");
            lore.add("<gray>En ligne : <white>" + plugin.nations().online(o).size());
            lore.add("<red>Clic : déclarer la guerre");
            list.add(new PagedGui.Entry(Icons.of(NationGui.wool(o), o.colored(), lore), pl -> confirmDeclare(pl, o)));
        }
        new PagedGui("<dark_red>Choisir une cible", list, this::open).open(p);
    }

    public void confirmDeclare(Player p, Nation target) {
        new ConfirmGui("Déclarer la guerre à " + target.name() + " ?",
                "Coût : " + Txt.money(plugin.getConfig().getDouble("guerres.cout-declaration", 10000)),
                pl -> wm().declare(pl, target), this::open).open(p);
    }

    private void world(Player p) {
        List<PagedGui.Entry> list = new ArrayList<>();
        for (War w : wm().all()) list.add(new PagedGui.Entry(Icons.of(Material.TNT, name(w.attacker()) + " <dark_red>⚔ " + name(w.defender()), describe(w)), null));
        if (list.isEmpty()) list.add(new PagedGui.Entry(Icons.of(Material.WHITE_BANNER, "<green>☮ Le monde est en paix"), null));
        new PagedGui("<dark_red>Conflits mondiaux", list, this::open).open(p);
    }
}

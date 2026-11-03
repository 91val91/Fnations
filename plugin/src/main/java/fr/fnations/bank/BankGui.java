package fr.fnations.bank;

import fr.fnations.FNations;
import fr.fnations.gui.Gui;
import fr.fnations.gui.Icons;
import fr.fnations.gui.PagedGui;
import fr.fnations.nation.Nation;
import fr.fnations.nation.Role;
import fr.fnations.util.ChatPrompt;
import fr.fnations.util.Inv;
import fr.fnations.util.Txt;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** GUI de la banque personnelle et de la banque nationale. */
public final class BankGui {
    private final FNations plugin;

    public BankGui(FNations plugin) { this.plugin = plugin; }

    private BankManager bank() { return plugin.bank(); }

    public void open(Player p) {
        Gui g = new Gui(6, "<dark_green>Banque » Compte personnel");
        g.border(Material.GREEN_STAINED_GLASS_PANE);
        double bal = bank().balance(p.getUniqueId());
        g.set(4, Icons.head(p, "<green><b>" + p.getName(), List.of("<gray>Solde : <gold><b>" + Txt.money(bal),
                "<gray>Intérêts : <white>" + plugin.getConfig().getDouble("economie.interets-pourcent-par-heure", 0.5) + "%/h",
                "<dark_gray>(plafond " + Txt.money(plugin.getConfig().getDouble("economie.interets-max", 500)) + "/h)")));
        g.button(19, Icons.of(Material.HOPPER, "<green>Déposer mes billets", "<gray>Dépose tous les billets", "<gray>de votre inventaire."), pl -> {
            double v = bank().depositAllNotes(pl);
            if (v > 0) Txt.ok(pl, Txt.money(v) + " déposés."); else Txt.err(pl, "Aucun billet dans votre inventaire.");
            open(pl);
        });
        int slot = 20;
        for (double amount : new double[]{100, 1000, 10000}) {
            g.button(slot++, Icons.of(Material.PAPER, "<yellow>Retirer " + Txt.money(amount), "<gray>Reçu sous forme de billet"), pl -> withdrawNote(pl, amount));
        }
        g.button(23, Icons.of(Material.WRITABLE_BOOK, "<yellow>Retrait personnalisé", "<gray>Saisie du montant dans le chat"),
                pl -> ChatPrompt.ask(pl, "Montant à retirer :", s -> { Double v = Txt.parseAmount(s); if (v == null) Txt.err(pl, "Montant invalide."); else withdrawNote(pl, v); }));
        g.button(24, Icons.of(Material.ENDER_PEARL, "<aqua>Virement", "<gray>Envoyer de l'argent à un joueur"), this::transferPrompt);
        g.button(25, Icons.of(Material.BOOK, "<white>Historique", "<gray>15 dernières opérations"), pl -> history(pl, pl.getUniqueId().toString(), this::open));
        Nation n = plugin.nations().of(p);
        if (n != null) g.button(31, Icons.of(Material.GOLD_BLOCK, "<gold>Banque nationale : " + n.colored(), "<gray>Trésor : <green>" + Txt.money(n.balance())), this::openNation);
        g.button(40, Icons.of(Material.DIAMOND, "<aqua>Classement des fortunes"), this::top);
        g.button(49, Icons.close(), Player::closeInventory);
        g.fill(Material.GRAY_STAINED_GLASS_PANE);
        g.open(p);
    }

    private void withdrawNote(Player p, double amount) {
        if (!bank().withdraw(p.getUniqueId(), amount, "Retrait en billets")) { Txt.err(p, "Fonds insuffisants."); open(p); return; }
        Inv.give(p, bank().banknote(amount));
        Txt.ok(p, "Vous avez retiré " + Txt.money(amount) + ".");
        open(p);
    }

    private void transferPrompt(Player p) {
        ChatPrompt.ask(p, "Pseudo du destinataire :", name -> {
            @SuppressWarnings("deprecation") OfflinePlayer t = Bukkit.getOfflinePlayer(name);
            if (!t.hasPlayedBefore() && !t.isOnline()) { Txt.err(p, "Joueur inconnu."); return; }
            if (t.getUniqueId().equals(p.getUniqueId())) { Txt.err(p, "Impossible de se virer de l'argent."); return; }
            ChatPrompt.ask(p, "Montant à envoyer à " + t.getName() + " :", s -> {
                Double v = Txt.parseAmount(s);
                if (v == null) { Txt.err(p, "Montant invalide."); return; }
                if (!bank().transfer(p.getUniqueId(), t.getUniqueId(), v)) { Txt.err(p, "Fonds insuffisants."); return; }
                Txt.ok(p, "Virement de " + Txt.money(v) + " envoyé à " + t.getName() + ".");
                if (t.getPlayer() != null) Txt.ok(t.getPlayer(), "Vous avez reçu " + Txt.money(v) + " de " + p.getName() + ".");
            });
        });
    }

    public void openNation(Player p) {
        Nation n = plugin.nations().of(p);
        if (n == null) { Txt.err(p, "Vous n'avez pas de nation."); return; }
        boolean officer = n.can(p.getUniqueId(), Role.OFFICER);
        Gui g = new Gui(4, "<gold>Banque nationale » " + n.colored());
        g.border(Material.YELLOW_STAINED_GLASS_PANE);
        g.set(4, Icons.glow(Icons.of(Material.GOLD_BLOCK, "<gold><b>Trésor de " + n.name(), "<gray>Solde : <green><b>" + Txt.money(n.balance()),
                "<gray>Impôt : <white>" + Txt.money(n.taxPerHour()) + "/h par citoyen", "<gray>Votre compte : <white>" + Txt.money(bank().balance(p.getUniqueId())))));
        int slot = 10;
        for (double amount : new double[]{100, 1000, 10000}) {
            g.button(slot++, Icons.of(Material.LIME_DYE, "<green>Verser " + Txt.money(amount), "<gray>Depuis votre compte"), pl -> natDeposit(pl, amount));
        }
        g.button(13, Icons.of(Material.WRITABLE_BOOK, "<green>Versement personnalisé"),
                pl -> ChatPrompt.ask(pl, "Montant à verser au trésor :", s -> { Double v = Txt.parseAmount(s); if (v == null) Txt.err(pl, "Montant invalide."); else natDeposit(pl, v); }));
        if (officer) {
            g.button(15, Icons.of(Material.RED_DYE, "<red>Retirer du trésor", "<gray>Réservé aux Ministres", "<gray>Saisie du montant"),
                    pl -> ChatPrompt.ask(pl, "Montant à retirer du trésor :", s -> {
                        Double v = Txt.parseAmount(s);
                        if (v == null) { Txt.err(pl, "Montant invalide."); return; }
                        if (!bank().nationWithdraw(n, v, "Retrait par " + pl.getName())) { Txt.err(pl, "Trésor insuffisant."); return; }
                        bank().deposit(pl.getUniqueId(), v, "Retrait du trésor de " + n.name());
                        plugin.nations().broadcast(n, "<yellow>" + pl.getName() + " a retiré " + Txt.money(v) + " du trésor.");
                    }));
        } else {
            g.set(15, Icons.of(Material.GRAY_DYE, "<dark_gray>Retrait réservé aux Ministres"));
        }
        g.button(16, Icons.of(Material.BOOK, "<white>Historique du trésor"), pl -> history(pl, "nation:" + n.id(), this::openNation));
        g.button(27, Icons.back(), this::open);
        g.button(31, Icons.of(Material.EMERALD_BLOCK, "<green>Nations les plus riches"), this::topNations);
        g.fill(Material.GRAY_STAINED_GLASS_PANE);
        g.open(p);
    }

    private void natDeposit(Player p, double v) {
        Nation n = plugin.nations().of(p);
        if (n == null) return;
        if (!bank().withdraw(p.getUniqueId(), v, "Versement au trésor de " + n.name())) { Txt.err(p, "Fonds insuffisants."); openNation(p); return; }
        bank().nationDeposit(n, v, "Versement de " + p.getName());
        Txt.ok(p, Txt.money(v) + " versés au trésor national.");
        openNation(p);
    }

    private void history(Player p, String key, java.util.function.Consumer<Player> back) {
        List<PagedGui.Entry> list = new ArrayList<>();
        for (String line : bank().history(key)) list.add(new PagedGui.Entry(Icons.of(Material.PAPER, line), null));
        if (list.isEmpty()) list.add(new PagedGui.Entry(Icons.of(Material.BARRIER, "<gray>Aucune opération"), null));
        new PagedGui("<dark_green>Historique", list, back).open(p);
    }

    private void top(Player p) {
        List<PagedGui.Entry> list = new ArrayList<>();
        List<OfflinePlayer> players = new ArrayList<>(List.of(Bukkit.getOfflinePlayers()));
        players.sort((a, b) -> Double.compare(bank().balance(b.getUniqueId()), bank().balance(a.getUniqueId())));
        int rank = 1;
        for (OfflinePlayer op : players.subList(0, Math.min(28, players.size()))) {
            list.add(new PagedGui.Entry(Icons.head(op, "<gold>#" + rank++ + " <white>" + op.getName(),
                    List.of("<green>" + Txt.money(bank().balance(op.getUniqueId())))), null));
        }
        new PagedGui("<dark_green>Fortunes", list, this::open).open(p);
    }

    private void topNations(Player p) {
        List<PagedGui.Entry> list = new ArrayList<>();
        List<Nation> ns = new ArrayList<>(plugin.nations().all());
        ns.sort((a, b) -> Double.compare(b.balance(), a.balance()));
        int rank = 1;
        for (Nation n : ns) list.add(new PagedGui.Entry(Icons.of(fr.fnations.nation.NationGui.wool(n), "<gold>#" + rank++ + " " + n.colored(),
                List.of("<green>" + Txt.money(n.balance()))), null));
        new PagedGui("<gold>Nations les plus riches", list, this::openNation).open(p);
    }
}

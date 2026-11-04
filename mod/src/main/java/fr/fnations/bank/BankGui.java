package fr.fnations.bank;

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
import fr.fnations.nation.Role;
import fr.fnations.util.Inv;

import java.util.ArrayList;
import java.util.List;

/** GUI de la banque personnelle et de la banque nationale. */
public final class BankGui {
    private final FnServer plugin;

    public BankGui(FnServer plugin) { this.plugin = plugin; }

    private BankManager bank() { return plugin.bank(); }

    public void open(ServerPlayer p) {
        Panel g = new Panel(6, "<dark_green>Banque » Compte personnel");
        g.border(Items.GREEN_STAINED_GLASS_PANE);
        double bal = bank().balance(p.getUUID());
        g.set(4, Icons.head(p.getGameProfile(), "<green><b>" + p.getScoreboardName(), List.of("<gray>Solde : <gold><b>" + Txt.money(bal),
                "<gray>Intérêts : <white>" + plugin.config().getDouble("economie.interets-pourcent-par-heure", 0.5) + "%/h",
                "<dark_gray>(plafond " + Txt.money(plugin.config().getDouble("economie.interets-max", 500)) + "/h)")));
        g.button(19, Icons.of(Items.HOPPER, "<green>Déposer mes billets", "<gray>Dépose tous les billets", "<gray>de votre inventaire."), pl -> {
            double v = bank().depositAllNotes(pl);
            if (v > 0) Msg.ok(pl, Txt.money(v) + " déposés."); else Msg.err(pl, "Aucun billet dans votre inventaire.");
            open(pl);
        });
        int slot = 20;
        for (double amount : new double[]{100, 1000, 10000}) {
            g.button(slot++, Icons.of(Items.PAPER, "<yellow>Retirer " + Txt.money(amount), "<gray>Reçu sous forme de billet"), pl -> withdrawNote(pl, amount));
        }
        g.button(23, Icons.of(Items.WRITABLE_BOOK, "<yellow>Retrait personnalisé", "<gray>Saisie du montant dans le chat"),
                pl -> Prompt.ask(pl, "Montant à retirer :", s -> { Double v = Txt.parseAmount(s); if (v == null) Msg.err(pl, "Montant invalide."); else withdrawNote(pl, v); }));
        g.button(24, Icons.of(Items.ENDER_PEARL, "<aqua>Virement", "<gray>Envoyer de l'argent à un joueur"), this::transferPrompt);
        g.button(25, Icons.of(Items.BOOK, "<white>Historique", "<gray>15 dernières opérations"), pl -> history(pl, pl.getUUID().toString(), this::open));
        Nation n = plugin.nations().of(p);
        if (n != null) g.button(31, Icons.of(Items.GOLD_BLOCK, "<gold>Banque nationale : " + n.colored(), "<gray>Trésor : <green>" + Txt.money(n.balance())), this::openNation);
        g.button(40, Icons.of(Items.DIAMOND, "<aqua>Classement des fortunes"), this::top);
        g.button(49, Icons.close(), ServerPlayer::closeContainer);
        g.fill(Items.GRAY_STAINED_GLASS_PANE);
        g.open(p);
    }

    private void withdrawNote(ServerPlayer p, double amount) {
        if (!bank().withdraw(p.getUUID(), amount, "Retrait en billets")) { Msg.err(p, "Fonds insuffisants."); open(p); return; }
        Inv.give(p, bank().banknote(amount));
        Msg.ok(p, "Vous avez retiré " + Txt.money(amount) + ".");
        open(p);
    }

    private void transferPrompt(ServerPlayer p) {
        Prompt.ask(p, "Pseudo du destinataire :", name -> {
            java.util.UUID t = plugin.uuidByName(name);
            if (t == null) { Msg.err(p, "Joueur inconnu."); return; }
            if (t.equals(p.getUUID())) { Msg.err(p, "Impossible de se virer de l'argent."); return; }
            String tn = plugin.name(t);
            Prompt.ask(p, "Montant à envoyer à " + tn + " :", s -> {
                Double v = Txt.parseAmount(s);
                if (v == null) { Msg.err(p, "Montant invalide."); return; }
                if (!bank().transfer(p.getUUID(), t, v)) { Msg.err(p, "Fonds insuffisants."); return; }
                Msg.ok(p, "Virement de " + Txt.money(v) + " envoyé à " + tn + ".");
                ServerPlayer tp = plugin.player(t);
                if (tp != null) Msg.ok(tp, "Vous avez reçu " + Txt.money(v) + " de " + p.getScoreboardName() + ".");
            });
        });
    }

    public void openNation(ServerPlayer p) {
        Nation n = plugin.nations().of(p);
        if (n == null) { Msg.err(p, "Vous n'avez pas de nation."); return; }
        boolean officer = n.can(p.getUUID(), Role.OFFICER);
        Panel g = new Panel(4, "<gold>Banque nationale » " + n.colored());
        g.border(Items.YELLOW_STAINED_GLASS_PANE);
        g.set(4, Icons.glow(Icons.of(Items.GOLD_BLOCK, "<gold><b>Trésor de " + n.name(), "<gray>Solde : <green><b>" + Txt.money(n.balance()),
                "<gray>Impôt : <white>" + Txt.money(n.taxPerHour()) + "/h par citoyen", "<gray>Votre compte : <white>" + Txt.money(bank().balance(p.getUUID())))));
        int slot = 10;
        for (double amount : new double[]{100, 1000, 10000}) {
            g.button(slot++, Icons.of(Items.LIME_DYE, "<green>Verser " + Txt.money(amount), "<gray>Depuis votre compte"), pl -> natDeposit(pl, amount));
        }
        g.button(13, Icons.of(Items.WRITABLE_BOOK, "<green>Versement personnalisé"),
                pl -> Prompt.ask(pl, "Montant à verser au trésor :", s -> { Double v = Txt.parseAmount(s); if (v == null) Msg.err(pl, "Montant invalide."); else natDeposit(pl, v); }));
        if (officer) {
            g.button(15, Icons.of(Items.RED_DYE, "<red>Retirer du trésor", "<gray>Réservé aux Ministres", "<gray>Saisie du montant"),
                    pl -> Prompt.ask(pl, "Montant à retirer du trésor :", s -> {
                        Double v = Txt.parseAmount(s);
                        if (v == null) { Msg.err(pl, "Montant invalide."); return; }
                        if (!bank().nationWithdraw(n, v, "Retrait par " + pl.getScoreboardName())) { Msg.err(pl, "Trésor insuffisant."); return; }
                        bank().deposit(pl.getUUID(), v, "Retrait du trésor de " + n.name());
                        plugin.nations().broadcast(n, "<yellow>" + pl.getScoreboardName() + " a retiré " + Txt.money(v) + " du trésor.");
                    }));
        } else {
            g.set(15, Icons.of(Items.GRAY_DYE, "<dark_gray>Retrait réservé aux Ministres"));
        }
        g.button(16, Icons.of(Items.BOOK, "<white>Historique du trésor"), pl -> history(pl, "nation:" + n.id(), this::openNation));
        g.button(27, Icons.back(), this::open);
        g.button(31, Icons.of(Items.EMERALD_BLOCK, "<green>Nations les plus riches"), this::topNations);
        g.fill(Items.GRAY_STAINED_GLASS_PANE);
        g.open(p);
    }

    private void natDeposit(ServerPlayer p, double v) {
        Nation n = plugin.nations().of(p);
        if (n == null) return;
        if (!bank().withdraw(p.getUUID(), v, "Versement au trésor de " + n.name())) { Msg.err(p, "Fonds insuffisants."); openNation(p); return; }
        bank().nationDeposit(n, v, "Versement de " + p.getScoreboardName());
        Msg.ok(p, Txt.money(v) + " versés au trésor national.");
        openNation(p);
    }

    private void history(ServerPlayer p, String key, java.util.function.Consumer<ServerPlayer> back) {
        List<PagedPanel.Entry> list = new ArrayList<>();
        for (String line : bank().history(key)) list.add(new PagedPanel.Entry(Icons.of(Items.PAPER, line), null));
        if (list.isEmpty()) list.add(new PagedPanel.Entry(Icons.of(Items.BARRIER, "<gray>Aucune opération"), null));
        new PagedPanel("<dark_green>Historique", list, back).open(p);
    }

    private void top(ServerPlayer p) {
        List<PagedPanel.Entry> list = new ArrayList<>();
        List<java.util.UUID> players = bank().accounts();
        players.sort((a, b) -> Double.compare(bank().balance(b), bank().balance(a)));
        int rank = 1;
        for (java.util.UUID op : players.subList(0, Math.min(28, players.size()))) {
            list.add(new PagedPanel.Entry(Icons.head(plugin.profile(op), "<gold>#" + rank++ + " <white>" + plugin.name(op),
                    List.of("<green>" + Txt.money(bank().balance(op)))), null));
        }
        new PagedPanel("<dark_green>Fortunes", list, this::open).open(p);
    }

    private void topNations(ServerPlayer p) {
        List<PagedPanel.Entry> list = new ArrayList<>();
        List<Nation> ns = new ArrayList<>(plugin.nations().all());
        ns.sort((a, b) -> Double.compare(b.balance(), a.balance()));
        int rank = 1;
        for (Nation n : ns) list.add(new PagedPanel.Entry(Icons.of(fr.fnations.nation.NationGui.wool(n), "<gold>#" + rank++ + " " + n.colored(),
                List.of("<green>" + Txt.money(n.balance()))), null));
        new PagedPanel("<gold>Nations les plus riches", list, this::openNation).open(p);
    }
}

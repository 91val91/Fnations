package fr.fnations.ui.pages;

import fr.fnations.company.Company;
import fr.fnations.nation.Nation;
import fr.fnations.nation.Role;
import fr.fnations.server.FnServer;
import fr.fnations.server.Msg;
import fr.fnations.ui.UiNode;
import fr.fnations.ui.UiPage;
import fr.fnations.ui.UiSession;
import fr.fnations.util.Inv;
import fr.fnations.util.Txt;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static fr.fnations.ui.UiNode.*;
import static fr.fnations.ui.UiTheme.*;

/** Banque : compte personnel (billets, virements, historique) et banque nationale. */
public final class BankPage extends UiPage {
    public BankPage(FnServer s) { super(s); }

    @Override
    public String id() { return "banque"; }

    private static UiNode historyList(String id, List<String> lines) {
        UiNode l = list(id).gap(2).flex(1);
        for (String line : lines) l.add(text(line).pad(3, 2));
        if (lines.isEmpty()) l.add(empty("Aucune opération."));
        return l;
    }

    @Override
    public UiNode build(ServerPlayer p, UiSession ss) {
        double bal = s.bank().balance(p.getUUID());
        double rate = s.config().getDouble("economie.interets-pourcent-par-heure", 0.5);
        double pending = s.jobs().pending(p.getUUID());
        UiNode account = col(
                row(icon("bank").size(14).color(TEXT), text(T2 + "Compte personnel").flex(1), chip(TA + rate + " %/h").bg("#33000000").tip(T2 + "Intérêts horaires plafonnés")).gap(6),
                text("<b>" + money(bal)).scale(2.2),
                pending >= 0.01 ? text(TM + "+ " + money(pending) + " de revenus de métier en attente (versés chaque minute)") : text(TM + "Revenus de métier versés chaque minute"),
                row(btn("100 $", a("note", 100)).withIcon("coin"), btn("1 000 $", a("note", 1000)).withIcon("coin"), btn("10 000 $", a("note", 10000)).withIcon("coin"),
                        flexSpace(), btn("Déposer", a("depositnotes")).withIcon("arrow_down").tip(T2 + "Dépose tous les billets de l'inventaire")).gap(5),
                row(input("note").placeholder("Montant à retirer en billet").numeric().flex(1).act(a("notecustom")), btn("Retirer", a("notecustom")).primary()).gap(6))
                .bg(ACCENT_SOFT).r(8).pad(10).gap(6);
        UiNode transfer = card(sectionTitle("Virement"),
                row(input("to").placeholder("Destinataire").flex(1), input("amount").placeholder("Montant").numeric().w(90).act(a("transfer")),
                        btn("Envoyer", a("transfer")).primary().withIcon("send")).gap(6));
        UiNode history = card(row(sectionTitle("Historique"), flexSpace(), btn("Classement des fortunes", "nav:classements").ghost().withIcon("trophy")),
                historyList("hist", s.bank().history(p.getUUID().toString()))).flex(1);
        UiNode left = col(account, transfer, history).gap(8).flex(3);

        Nation n = s.nations().of(p);
        UiNode right;
        if (n == null) {
            right = card(text("<b>Banque nationale").scale(1.25), empty("Rejoignez ou fondez une nation pour accéder à son trésor."),
                    btn("Mon pays", "nav:pays").primary().withIcon("flag"), flexSpace());
        } else {
            boolean officer = n.can(p.getUUID(), Role.OFFICER);
            right = card(
                    row(icon("flag").size(12).color(NationPage.hex(n.color())), text("<b>Trésor de " + Txt.esc(n.name())).flex(1)).gap(6),
                    text("<b>" + TG + money(n.balance())).scale(1.6),
                    text(T2 + "Impôt : " + money(n.taxPerHour()) + " par heure et par citoyen"),
                    row(input("ndep").placeholder("Montant").numeric().flex(1).act(a("ndeposit")), btn("Verser", a("ndeposit")).success()).gap(6),
                    officer ? row(input("nwit").placeholder("Montant").numeric().flex(1).act(a("nwithdraw")), btn("Retirer", a("nwithdraw")).danger()).gap(6)
                            : text(TM + "Retraits réservés aux Ministres."),
                    sectionTitle("Historique du trésor"),
                    historyList("nhist", s.bank().history("nation:" + n.id())));
        }
        Company c = s.companies().of(p.getUUID());
        UiNode comp = c == null ? null : row(icon("briefcase").size(12).color(BLUE), col(text(T2 + "Capital de " + Txt.esc(c.name())), text("<b>" + money(c.balance()))).gap(2).flex(1),
                btn("Ouvrir", "nav:entreprise")).bg(PANEL).r(6).pad(8).gap(8);
        return row(left, col(right.flex(1), comp).gap(8).flex(2)).gap(8);
    }

    @Override
    public void action(ServerPlayer p, UiSession ss, String act, String arg, Map<String, String> in) {
        switch (act) {
            case "note" -> {
                try { withdrawNote(p, Double.parseDouble(arg)); } catch (NumberFormatException ignored) {}
            }
            case "notecustom" -> {
                Double v = amount(in, "note");
                if (v == null) Msg.err(p, "Montant invalide."); else withdrawNote(p, v);
            }
            case "depositnotes" -> {
                double v = s.bank().depositAllNotes(p);
                if (v > 0) Msg.ok(p, money(v) + " déposés."); else Msg.err(p, "Aucun billet dans votre inventaire.");
            }
            case "transfer" -> {
                String name = in.getOrDefault("to", "").trim();
                Double v = amount(in, "amount");
                UUID t = name.isEmpty() ? null : s.uuidByName(name);
                if (t == null) { Msg.err(p, "Destinataire inconnu."); return; }
                if (t.equals(p.getUUID())) { Msg.err(p, "Impossible de se virer de l'argent."); return; }
                if (v == null) { Msg.err(p, "Montant invalide."); return; }
                if (!s.bank().transfer(p.getUUID(), t, v)) { Msg.err(p, "Fonds insuffisants."); return; }
                Msg.ok(p, "Virement de " + money(v) + " envoyé à " + s.name(t) + ".");
                ServerPlayer tp = s.player(t);
                if (tp != null) Msg.ok(tp, "Vous avez reçu " + money(v) + " de " + p.getScoreboardName() + ".");
            }
            case "ndeposit" -> {
                Double v = amount(in, "ndep");
                if (v == null) Msg.err(p, "Montant invalide."); else s.nationActions().deposit(p, v);
            }
            case "nwithdraw" -> {
                Double v = amount(in, "nwit");
                if (v == null) Msg.err(p, "Montant invalide."); else s.nationActions().withdraw(p, v);
            }
            default -> {
            }
        }
    }

    private void withdrawNote(ServerPlayer p, double amount) {
        if (amount <= 0) return;
        if (!s.bank().withdraw(p.getUUID(), amount, "Retrait en billets")) { Msg.err(p, "Fonds insuffisants."); return; }
        Inv.give(p, s.bank().banknote(amount));
        Msg.ok(p, "Vous avez retiré " + money(amount) + ".");
    }
}

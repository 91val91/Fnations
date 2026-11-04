package fr.fnations.ui.pages;

import fr.fnations.company.Company;
import fr.fnations.company.CompanyManager;
import fr.fnations.server.FnServer;
import fr.fnations.server.Msg;
import fr.fnations.ui.UiNode;
import fr.fnations.ui.UiPage;
import fr.fnations.ui.UiSession;
import fr.fnations.util.Txt;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;

import static fr.fnations.ui.UiNode.*;
import static fr.fnations.ui.UiTheme.*;

/** Entreprise : création (4 types), employés, capital, salaires, bonus et paramètres. */
public final class CompanyPage extends UiPage {
    public CompanyPage(FnServer s) { super(s); }

    @Override
    public String id() { return "entreprise"; }

    private CompanyManager cm() { return s.companies(); }

    static UiNode typeChip(Company.Type t) { return chip("<" + t.color + ">" + t.display).bg(ROW); }

    @Override
    public UiNode build(ServerPlayer p, UiSession ss) {
        Company c = cm().of(p.getUUID());
        return c == null ? noCompany(p, ss) : company(p, c);
    }

    // ================================================================ sans entreprise
    private UiNode noCompany(ServerPlayer p, UiSession ss) {
        Company.Type sel = Company.Type.byName(ss.get("type", "TRADING"));
        if (sel == null) sel = Company.Type.TRADING;
        UiNode types = grid(2).gap(6);
        int need = s.config().getInt("entreprises.palier-requis", 1);
        for (Company.Type t : Company.Type.values()) {
            boolean on = t == sel;
            boolean ok = t.job == null || s.jobs().palier(p.getUUID(), t.job) >= need;
            types.add(col(row(icon(t.icon).size(14).color(t.color), text("<b>" + t.display).flex(1), on ? icon("check").size(10).color(TEXT) : null).gap(6),
                    text(T2 + t.description).wrap(),
                    text(t.job == null ? TM + "Aucun prérequis" : (ok ? TG : TR) + "Palier " + need + " en " + t.job.display + " requis").wrap())
                    .bg(on ? ACCENT_SOFT : ROW).border(on ? ACCENT : null).r(6).pad(7).gap(4).act(a("type", t.name())));
        }
        UiNode form = card(text("<b>Fonder une entreprise").scale(1.5),
                text(T2 + "Une entreprise regroupe des joueurs autour d'une activité. Son niveau monte avec le travail de ses employés et débloque des bonus.").wrap(),
                list("types").add(types).flex(1),
                row(input("cname").placeholder("Nom de l'entreprise").maxLen(20).flex(1).act(a("create")),
                        text(T2 + "Coût : " + TO + money(cm().creationCost())), btn("Fonder", a("create")).primary().withIcon("briefcase")).gap(8))
                .flex(1);
        UiNode offers = list("offers").gap(4).h(80);
        for (Company c : cm().invitesFor(p.getUUID()))
            offers.add(row(icon(c.type().icon).size(12).color(c.type().color), col(text("<b>" + Txt.esc(c.name())), text(TM + c.type().display + " • niveau " + cm().level(c))).gap(2).flex(1),
                    btn("", a("accept", c.id())).withIcon("check").success().w(20), btn("", a("decline", c.id())).withIcon("close").w(20)).bg(ROW).r(5).pad(5).gap(5));
        if (offers.isEmpty()) offers.add(empty("Aucune offre d'emploi."));
        UiNode all = list("companies").gap(3).flex(1);
        cm().all().stream().sorted((x, y) -> Double.compare(y.xp(), x.xp())).forEach(c -> all.add(
                row(icon(c.type().icon).size(12).color(c.type().color), col(text(Txt.esc(c.name())), text(TM + c.type().display + " • " + c.members().size() + " employé(s)")).gap(2).flex(1),
                        chip(TA + "Niv. " + cm().level(c)).bg(ACCENT_SOFT)).pad(4).gap(6).r(4).hover()
                        .tip("<b>" + Txt.esc(c.name()), T2 + "PDG : " + Txt.esc(s.name(c.ceo())), T2 + "Capital : " + money(c.balance()))));
        if (all.isEmpty()) all.add(empty("Aucune entreprise pour l'instant."));
        UiNode side = col(card(sectionTitle("Offres d'emploi"), offers), card(sectionTitle("Entreprises du serveur"), all).flex(1)).gap(8).w(176);
        return col(header("briefcase", "Entreprises", "Trading, Ingénierie, Agriculture ou Construction"), row(form, side).gap(8).flex(1)).gap(8);
    }

    // ================================================================ mon entreprise
    private UiNode company(ServerPlayer p, Company c) {
        int lvl = cm().level(c);
        double from = cm().levelStart(lvl), to = cm().levelNext(lvl);
        double ratio = lvl >= cm().maxLevel() ? 1 : (c.xp() - from) / Math.max(1, to - from);
        boolean manager = c.can(p.getUUID(), Company.Rank.DIRECTEUR), ceo = c.can(p.getUUID(), Company.Rank.PDG);
        UiNode head = row(
                col(row(text("<b>" + Txt.esc(c.name())).scale(1.6), typeChip(c.type())).gap(8),
                        text(c.description().isEmpty() ? TM + "Aucune description." : T2 + Txt.esc(c.description())).wrap().lines(2),
                        row(text(TA + "<b>Niveau " + lvl), bar(ratio).h(5).flex(1), text(TM + Math.round(c.xp()) + " / " + Math.round(to) + " XP")).gap(8)).gap(5).flex(1),
                col(icon(c.type().icon).size(28).color(c.type().color)).w(46).h(46).bg("#33000000").r(8).pad(9))
                .bg(ACCENT_SOFT).r(8).pad(10).gap(10);
        UiNode stats = row(stat("coin", "Capital", money(c.balance())).flex(1), stat("users", "Employés", c.members().size() + " / " + cm().maxMembers(c)).flex(1),
                stat("chart", "Contribution", c.contribution() + " %").flex(1), stat("star", "Gains cumulés", money(c.earned())).flex(1)).gap(6);

        UiNode staff = list("staff").gap(4).flex(1);
        c.members().entrySet().stream().sorted((x, y) -> y.getValue().power - x.getValue().power).forEach(e -> {
            UUID u = e.getKey();
            boolean self = u.equals(p.getUUID());
            UiNode acts = row().gap(3);
            if (manager) acts.add(btn("", a("salary", u)).withIcon("coin").w(20).tip(T2 + "Définir le salaire"));
            if (ceo && !self && e.getValue() == Company.Rank.EMPLOYE) acts.add(btn("", a("promote", u)).withIcon("arrow_up").w(20).tip(T2 + "Promouvoir Directeur"));
            if (ceo && !self && e.getValue() == Company.Rank.DIRECTEUR) acts.add(btn("", a("demote", u)).withIcon("arrow_down").w(20).tip(T2 + "Rétrograder"));
            if (ceo && !self) acts.add(btn("", a("transfer", u)).withIcon("crown").w(20).tip(T2 + "Nommer PDG"));
            if (manager && !self && e.getValue().power < c.rank(p.getUUID()).power) acts.add(btn("", a("kick", u)).withIcon("close").danger().w(20).tip(T2 + "Licencier"));
            staff.add(row(head(u, s.name(u)).size(14), col(text("<b>" + Txt.esc(s.name(u))), text(TM + e.getValue().display)).gap(2).flex(1),
                    text(c.salary(u) > 0 ? TG + money(c.salary(u)) + "/h" : TM + "Sans salaire"), acts).bg(ROW).r(5).pad(5).gap(6));
        });
        UiNode recruit = manager ? row(input("hire").placeholder("Pseudo à recruter").flex(1).act(a("invite")), btn("Recruter", a("invite")).primary().withIcon("plus")).gap(6) : null;
        UiNode left = card(row(sectionTitle("Employés"), flexSpace(), text(TM + "Salaires versés chaque heure")), recruit, staff).flex(1);

        UiNode bonus = card(sectionTitle("Bonus du niveau " + lvl));
        for (String b : cm().bonuses(c)) bonus.add(row(icon("check").size(8).color(GREEN), text(T2 + b).wrap().flex(1)).gap(5));
        UiNode money = card(sectionTitle("Capital"),
                row(input("cdep").placeholder("Montant").numeric().flex(1).act(a("deposit")), btn("Verser", a("deposit")).success()).gap(5),
                manager ? row(input("cwit").placeholder("Montant").numeric().flex(1).act(a("withdraw")), btn("Retirer", a("withdraw")).danger()).gap(5) : null);
        UiNode settings = ceo ? card(sectionTitle("Paramètres"),
                row(text(T2 + "Contribution").flex(1), input("contrib").value(String.valueOf(c.contribution())).numeric().w(36), btn("OK", a("contrib"))).gap(5),
                row(input("cdesc").placeholder("Description").value(c.description()).maxLen(100).flex(1), btn("OK", a("desc"))).gap(5),
                btn("Dissoudre l'entreprise", a("disband")).danger().flag("fill"))
                : card(btn("Quitter l'entreprise", a("leave")).danger().flag("fill"));
        UiNode right = list("cright").add(bonus, money, settings).gap(8).w(184);
        return col(head, stats, row(left, right).gap(8).flex(1)).gap(8);
    }

    // ================================================================ actions
    @Override
    public void action(ServerPlayer p, UiSession ss, String act, String arg, Map<String, String> in) {
        Company c = cm().of(p.getUUID());
        switch (act) {
            case "type" -> { if (Company.Type.byName(arg) != null) ss.set("type", arg); }
            case "create" -> {
                Company.Type t = Company.Type.byName(ss.get("type", "TRADING"));
                cm().create(p, in.getOrDefault("cname", "").trim(), t == null ? Company.Type.TRADING : t);
            }
            case "accept" -> cm().accept(p, arg);
            case "decline" -> cm().decline(p, arg);
            case "invite" -> {
                String t = in.getOrDefault("hire", "").trim();
                if (t.isEmpty()) Msg.err(p, "Saisissez un pseudo."); else cm().invite(p, t);
            }
            case "salary" -> withUuid(arg, u -> ss.dialog = card(text("<b>Salaire de " + Txt.esc(s.name(u))).scale(1.25),
                    text(T2 + "Montant versé chaque heure depuis le capital de l'entreprise (0 = aucun).").wrap(),
                    input("sal").value(c == null ? "0" : String.valueOf(c.salary(u))).numeric().force().act(a("salary_ok", u)),
                    row(flexSpace(), btn("Annuler", "dialog:close"), btn("Valider", a("salary_ok", u)).primary()).gap(6)).w(240).pad(12));
            case "salary_ok" -> withUuid(arg, u -> {
                Double v = amount(in, "sal");
                cm().setSalary(p, u, v == null ? 0 : v);
                ss.dialog = null;
            });
            case "promote" -> withUuid(arg, u -> cm().promote(p, u));
            case "demote" -> withUuid(arg, u -> cm().demote(p, u));
            case "transfer" -> withUuid(arg, u -> ss.dialog = confirm("Nommer " + s.name(u) + " PDG ?", "Vous deviendrez Directeur.", "Confirmer", a("transfer_ok", u), true));
            case "transfer_ok" -> { withUuid(arg, u -> cm().transfer(p, u)); ss.dialog = null; }
            case "kick" -> withUuid(arg, u -> ss.dialog = confirm("Licencier " + s.name(u) + " ?", null, "Licencier", a("kick_ok", u), true));
            case "kick_ok" -> { withUuid(arg, u -> cm().kick(p, u)); ss.dialog = null; }
            case "deposit" -> {
                Double v = amount(in, "cdep");
                if (v == null) Msg.err(p, "Montant invalide."); else cm().depositFrom(p, v);
            }
            case "withdraw" -> {
                Double v = amount(in, "cwit");
                if (v == null) Msg.err(p, "Montant invalide."); else cm().withdrawTo(p, v);
            }
            case "contrib" -> cm().setContribution(p, intIn(in, "contrib", c == null ? 10 : c.contribution()));
            case "desc" -> {
                if (c == null || !c.can(p.getUUID(), Company.Rank.PDG)) return;
                String d = in.getOrDefault("cdesc", "").trim();
                c.setDescription(d.length() > 100 ? d.substring(0, 100) : d);
                Msg.ok(p, "Description mise à jour.");
            }
            case "leave" -> ss.dialog = confirm("Quitter l'entreprise ?", null, "Quitter", a("leave_ok"), true);
            case "leave_ok" -> { ss.dialog = null; cm().leave(p); }
            case "disband" -> ss.dialog = confirm("Dissoudre l'entreprise ?", "Le capital vous est reversé, les annonces HDV de l'entreprise vous reviennent.",
                    "Dissoudre", a("disband_ok"), true);
            case "disband_ok" -> { ss.dialog = null; cm().disband(p); }
            default -> {
            }
        }
    }

    private static void withUuid(String s, java.util.function.Consumer<UUID> c) {
        try { c.accept(UUID.fromString(s)); } catch (IllegalArgumentException ignored) {}
    }
}

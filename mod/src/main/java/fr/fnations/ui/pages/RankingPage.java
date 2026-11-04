package fr.fnations.ui.pages;

import fr.fnations.company.Company;
import fr.fnations.job.Job;
import fr.fnations.job.JobManager;
import fr.fnations.nation.Nation;
import fr.fnations.server.FnServer;
import fr.fnations.ui.UiNode;
import fr.fnations.ui.UiPage;
import fr.fnations.ui.UiSession;
import fr.fnations.util.Txt;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static fr.fnations.ui.UiNode.*;
import static fr.fnations.ui.UiTheme.*;

/** Classements : fortunes, nations, métiers, entreprises et vendeurs de la semaine. */
public final class RankingPage extends UiPage {
    public RankingPage(FnServer s) { super(s); }

    @Override
    public String id() { return "classements"; }

    private static String medal(int r) { return r == 1 ? TY : r == 2 ? "<#C0C4CC>" : r == 3 ? "<#D08A4E>" : TM; }

    private static UiNode line(int r, UiNode icon, String name, String sub, String value, boolean me) {
        return row(text(medal(r) + "<b>#" + r).w(30), icon, col(text("<b>" + name), sub == null ? null : text(TM + sub)).gap(2).flex(1), text(value).right())
                .bg(me ? ACCENT_SOFT : ROW).r(5).pad(6).gap(8);
    }

    @Override
    public UiNode build(ServerPlayer p, UiSession ss) {
        String tab = ss.get("tab", "fortunes");
        UiNode tabs = row(
                btn("Fortunes", a("tab", "fortunes")).withIcon("coin").style("tab").selected(tab.equals("fortunes")),
                btn("Nations", a("tab", "nations")).withIcon("flag").style("tab").selected(tab.equals("nations")),
                btn("Métiers", a("tab", "metiers")).withIcon("pickaxe").style("tab").selected(tab.equals("metiers")),
                btn("Entreprises", a("tab", "entreprises")).withIcon("briefcase").style("tab").selected(tab.equals("entreprises")),
                btn("HDV (semaine)", a("tab", "hdv")).withIcon("shop").style("tab").selected(tab.equals("hdv"))).gap(2);
        UiNode list = list("rank").gap(4).flex(1);
        UiNode extra = null;
        int r = 1;
        switch (tab) {
            case "nations" -> {
                List<Nation> ns = new ArrayList<>(s.nations().all());
                ns.sort((a, b) -> s.nations().power(b) - s.nations().power(a));
                Nation mine = s.nations().of(p);
                for (Nation n : ns) list.add(line(r++, space(4, 20).bg(NationPage.hex(n.color())).r(2), Txt.esc(n.name()) + " " + TM + "[" + n.tag() + "]",
                        n.members().size() + " citoyens • " + n.claims().size() + " chunks • " + money(n.balance()), TA + "<b>" + s.nations().power(n) + " pts", n == mine));
            }
            case "metiers" -> {
                Job j = Job.byId(ss.get("job", "mineur"));
                if (j == null) j = Job.MINEUR;
                extra = row().gap(2);
                for (Job x : Job.values()) extra.add(btn(x.display, a("job", x.id)).withIcon(x.icon).selected(x == j));
                for (JobManager.Rank e : s.jobs().top(j, 30)) list.add(line(r++, head(e.player(), s.name(e.player())).size(16), Txt.esc(s.name(e.player())),
                        "Palier " + s.jobs().palier(e.xp()), TA + "<b>" + Math.round(e.xp()) + " XP", e.player().equals(p.getUUID())));
            }
            case "entreprises" -> {
                List<Company> cs = new ArrayList<>(s.companies().all());
                cs.sort((a, b) -> Double.compare(b.xp(), a.xp()));
                Company mine = s.companies().of(p.getUUID());
                for (Company c : cs) list.add(line(r++, icon(c.type().icon).size(16).color(c.type().color), Txt.esc(c.name()),
                        c.type().display + " • " + c.members().size() + " employé(s) • " + money(c.balance()), TA + "<b>Niveau " + s.companies().level(c), c == mine));
            }
            case "hdv" -> {
                for (Map.Entry<UUID, Double> e : s.market().weeklyTop(30)) list.add(line(r++, head(e.getKey(), s.name(e.getKey())).size(16), Txt.esc(s.name(e.getKey())),
                        "Ventes de la semaine", TG + "<b>" + money(e.getValue()), e.getKey().equals(p.getUUID())));
            }
            default -> {
                List<UUID> players = s.bank().accounts();
                players.sort((a, b) -> Double.compare(s.bank().balance(b), s.bank().balance(a)));
                for (UUID u : players.subList(0, Math.min(50, players.size()))) list.add(line(r++, head(u, s.name(u)).size(16), Txt.esc(s.name(u)), null,
                        TG + "<b>" + money(s.bank().balance(u)), u.equals(p.getUUID())));
            }
        }
        if (list.isEmpty()) list.add(empty("Aucune donnée pour ce classement."));
        return col(header("trophy", "Classements", "Les meilleurs joueurs, nations et entreprises du serveur"), tabs, extra, card(list).flex(1)).gap(8);
    }

    @Override
    public void action(ServerPlayer p, UiSession ss, String act, String arg, Map<String, String> in) {
        if (act.equals("tab")) ss.set("tab", arg);
        if (act.equals("job") && Job.byId(arg) != null) ss.set("job", arg);
    }
}

package fr.fnations.ui.pages;

import fr.fnations.company.Company;
import fr.fnations.job.Job;
import fr.fnations.nation.Nation;
import fr.fnations.server.FnServer;
import fr.fnations.ui.UiNode;
import fr.fnations.ui.UiPage;
import fr.fnations.ui.UiSession;
import fr.fnations.util.Txt;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

import static fr.fnations.ui.UiNode.*;
import static fr.fnations.ui.UiTheme.*;

/** Accueil : profil du joueur, résumé de sa nation, de son entreprise et de ses métiers. */
public final class HomePage extends UiPage {
    public HomePage(FnServer s) { super(s); }

    @Override
    public String id() { return "accueil"; }

    @Override
    public UiNode build(ServerPlayer p, UiSession ss) {
        Nation n = s.nations().of(p);
        Company c = s.companies().of(p.getUUID());
        UiNode profile = card(
                player().h(120),
                text("<b>" + Txt.esc(p.getScoreboardName())).scale(1.25).center(),
                n == null ? chip(T2 + "Sans nation").bg(ROW) : chip("<" + n.color() + ">" + Txt.esc(n.name()) + " " + TM + "[" + n.tag() + "]").bg(ACCENT_SOFT),
                c == null ? chip(T2 + "Sans entreprise").bg(ROW) : chip(TB + Txt.esc(c.name())).bg(ROW),
                flexSpace(),
                btn("Mon pays", "nav:pays").withIcon("flag").primary().flag("fill"))
                .w(140).str("align", "center");

        UiNode stats = row(
                stat("bank", "Solde", money(s.bank().balance(p.getUUID()))).flex(1),
                stat("flag", "Nation", n == null ? "Aucune" : Txt.esc(n.name()) + " " + TM + n.role(p.getUUID()).display.replaceAll("<[^>]+>", "")).flex(1),
                stat("briefcase", "Entreprise", c == null ? "Aucune" : Txt.esc(c.name())).flex(1),
                stat("shop", "Ventes HDV", String.valueOf(s.market().listingsOf(fr.fnations.market.MarketManager.player(p.getUUID())).size()))).gap(6);

        UiNode jobs = grid(3).gap(6);
        for (Job j : Job.values()) {
            double xp = s.jobs().xp(p.getUUID(), j);
            int pal = s.jobs().palier(xp);
            double from = s.jobs().threshold(pal), to = s.jobs().next(xp);
            double ratio = pal >= 6 ? 1 : (xp - from) / Math.max(1, to - from);
            jobs.add(col(
                    row(icon(j.icon).size(12).color(ACCENT), text("<b>" + j.display).flex(1), chip(TA + "P" + pal).bg(ACCENT_SOFT)).gap(5),
                    bar(ratio).h(4),
                    text(TM + Math.round(xp) + " / " + Math.round(to) + " XP")).bg(ROW).r(5).pad(6).gap(4)
                    .act("nav:metiers").tip("<b>" + j.display, T2 + "Palier " + pal + " / 6", TA + "Clic : ouvrir les compétences"));
        }

        UiNode shortcuts = row(
                btn("Hôtel des ventes", "nav:hdv").withIcon("shop").flex(1),
                btn("Banque", "nav:banque").withIcon("bank").flex(1),
                btn("Entreprise", "nav:entreprise").withIcon("briefcase").flex(1),
                btn("Classements", "nav:classements").withIcon("trophy").flex(1)).gap(6);

        UiNode right = col(
                header("home", "Accueil", "Bienvenue sur FNations, " + Txt.esc(p.getScoreboardName()) + " !"),
                stats,
                card(sectionTitle("Mes compétences"), jobs),
                card(sectionTitle("Raccourcis"), shortcuts),
                flexSpace(),
                text(TM + "Touche M : ouvrir la tablette • Échap : fermer").center()).gap(8).flex(1);
        return row(profile, right).gap(8);
    }

    @Override
    public void action(ServerPlayer p, UiSession ss, String act, String arg, Map<String, String> in) {}
}

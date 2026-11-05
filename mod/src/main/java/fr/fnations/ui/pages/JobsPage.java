package fr.fnations.ui.pages;

import fr.fnations.company.Company;
import fr.fnations.craft.StationRecipe;
import fr.fnations.job.Job;
import fr.fnations.job.JobManager;
import fr.fnations.rating.RatingManager;
import fr.fnations.research.Technology;
import fr.fnations.server.FnServer;
import fr.fnations.ui.UiNode;
import fr.fnations.ui.UiPage;
import fr.fnations.ui.UiSession;
import fr.fnations.util.Inv;
import fr.fnations.util.Txt;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static fr.fnations.ui.UiNode.*;
import static fr.fnations.ui.UiTheme.*;

/**
 * Métiers : liste des six compétences, progression par palier, onglets Paliers et bonus, Crafts débloqués
 * (Station de Craft et établi), Comment monter et Notation (Builder, Ingénieur).
 */
public final class JobsPage extends UiPage {
    public JobsPage(FnServer s) { super(s); }

    @Override
    public String id() { return "metiers"; }

    private Job selected(UiSession ss) {
        Job j = Job.byId(ss.get("job", "builder"));
        return j == null ? Job.BUILDER : j;
    }

    private static boolean hasNotation(Job j) { return j == Job.BUILDER || j == Job.INGENIEUR; }

    @Override
    public UiNode build(ServerPlayer p, UiSession ss) {
        Job sel = selected(ss);
        JobManager jm = s.jobs();
        String tab = ss.get("tab", "paliers");
        if (tab.equals("notation") && !hasNotation(sel)) tab = "paliers";

        // ---------- liste des compétences
        UiNode left = card(text(T2 + "<b>COMPÉTENCES")).w(124);
        int total = 0;
        for (Job j : Job.values()) {
            double x = jm.xp(p.getUUID(), j);
            int pal = jm.palier(x);
            total += pal;
            double from = jm.threshold(pal), to = jm.threshold(Math.min(6, pal + 1));
            double prog = pal >= 6 ? 1 : (x - from) / Math.max(1, to - from);
            boolean on = j == sel;
            left.add(row(icon(j.icon).size(12).color(on ? TEXT : TEXT2),
                    col(text((on ? "<b>" : T2) + j.display), bar(prog).h(3).color(on ? "#D6D9FF" : ACCENT)).gap(3).flex(1),
                    text((on ? "<b>" : TA) + "P" + pal).right().w(16))
                    .pad(6, 5).r(5).bg(on ? ACCENT : null).act(a("job", j.id)).gap(6).hover());
        }
        left.add(flexSpace(),
                col(text(T2 + "Paliers cumulés"), row(text("<b>" + total).scale(1.5), text(TM + "/ 36")).gap(4).valign("bottom")).gap(2).bg(ROW).r(5).pad(6),
                text(TM + "Vos métiers progressent quand vous jouez.").wrap());

        // ---------- en-tête
        double xp = jm.xp(p.getUUID(), sel);
        int pal = jm.palier(xp);
        double next = jm.next(xp);
        UiNode headRow = row(row(icon(sel.icon).size(18).color(TEXT)).bg(ACCENT).r(6).pad(6),
                col(row(text("<b>" + sel.display.toUpperCase()).scale(1.4), chip(TA + "<b>Palier " + pal + "</b>" + TM + " / 6").bg(ACCENT_SOFT)).gap(6),
                        text(T2 + sel.description).wrap().lines(2).tip(T2 + sel.description)).gap(3).flex(1),
                col(row(head(p.getUUID(), p.getScoreboardName()).size(12), text("<b>#" + jm.rank(p.getUUID(), sel)), text(TM + "du serveur")).gap(4),
                        btn("Classement", a("top")).withIcon("trophy").flag("fill")).gap(4).w(96)).gap(8).valign("top");

        // ---------- piste des paliers
        UiNode track = row().gap(0).h(16);
        for (int i = 0; i <= 6; i++) {
            boolean reached = i <= pal;
            List<String> tip = new ArrayList<>();
            if (i == 0) tip.add("<b>Départ");
            else {
                tip.add("<b>Palier " + i + " " + TM + "(" + Math.round(jm.threshold(i)) + " XP)");
                tip.add(TG + "+" + money(jm.reward(i)));
                for (String perk : sel.perks(i)) tip.add(T2 + "• " + perk);
                int n = jm.craftsAt(sel, i).size();
                if (n > 0) tip.add(TA + "• " + n + " craft(s) débloqué(s)");
            }
            track.add(row(icon(reached ? (i == 0 ? "dot" : "check") : "lock").size(8).color(reached ? TEXT : MUTED))
                    .w(16).h(16).r(8).bg(reached ? ACCENT : ROW).pad(4).tip(tip).hover());
            if (i < 6) {
                double from = jm.threshold(i), to = jm.threshold(i + 1);
                double v = xp >= to ? 1 : xp <= from ? 0 : (xp - from) / (to - from);
                track.add(bar(v).h(4).flex(1));
            }
        }
        UiNode progress = col(
                row(text(T2 + "<b>PROGRESSION"), flexSpace(), text("<b>" + Math.round(xp) + "</b>" + TM + " / " + Math.round(next) + " XP")),
                track,
                text(pal >= 6 ? TG + "Palier maximal atteint !" : TM + "Prochain palier : " + Math.round(next - xp) + " XP restants")).gap(5);

        // ---------- onglets
        int crafts = jm.craftsOf(sel).size();
        UiNode tabs = row(
                btn("Paliers et bonus", a("tab", "paliers")).withIcon("medal").style("tab").selected(tab.equals("paliers")),
                btn("Crafts (" + crafts + ")", a("tab", "crafts")).withIcon("hammer").style("tab").selected(tab.equals("crafts")),
                btn("Comment monter", a("tab", "gains")).withIcon("info").style("tab").selected(tab.equals("gains")),
                hasNotation(sel) ? btn("Notation", a("tab", "notation")).withIcon("star").style("tab").selected(tab.equals("notation")) : null).gap(2);
        UiNode body = switch (tab) {
            case "crafts" -> crafts(p, sel, pal);
            case "gains" -> gains(sel);
            case "notation" -> notation(p, sel);
            default -> tiers(p, sel, pal);
        };
        return row(left, card(headRow, progress, tabs, body.flex(1)).flex(1)).gap(8);
    }

    // ------------------------------------------------------------ onglets
    private UiNode tiers(ServerPlayer p, Job sel, int pal) {
        JobManager jm = s.jobs();
        UiNode list = list("rewards").gap(4).flex(1);
        for (int i = 1; i <= 6; i++) {
            boolean got = i <= pal;
            UiNode lines = col(text((got ? "<b>" : T2 + "<b>") + "Palier " + i + " " + TM + "— " + Math.round(jm.threshold(i)) + " XP")).gap(2).flex(1);
            for (String perk : sel.perks(i)) lines.add(text((got ? T2 : TM) + perk).wrap());
            List<String> cr = jm.craftsAt(sel, i);
            if (!cr.isEmpty()) {
                UiNode icons = row(text((got ? TG : TM) + "Crafts :")).gap(3);
                for (String id : cr) icons.add(item(Inv.stack(id, 1)).size(14));
                lines.add(icons);
            }
            list.add(row(icon(got ? "check" : "lock").size(12).color(got ? GREEN : MUTED), lines,
                    text((got ? TG : TM) + "+" + money(jm.reward(i))).right())
                    .bg(ROW).r(5).pad(6).gap(8).valign("top"));
        }
        Company c = s.companies().of(p.getUUID());
        if (c != null && c.type().job == sel)
            list.add(row(icon("briefcase").size(12).color(BLUE), text(TB + "Bonus d'entreprise (" + Txt.esc(c.name()) + ") : XP +" + s.companies().level(c)
                    + " %, revenus +" + (2 * s.companies().level(c)) + " %").wrap().flex(1)).bg(ROW).r(5).pad(6).gap(8));
        return list;
    }

    private UiNode crafts(ServerPlayer p, Job sel, int pal) {
        List<Map.Entry<String, JobManager.CraftReq>> all = s.jobs().craftsOf(sel);
        if (all.isEmpty()) return col(empty("Ce métier ne débloque pas de craft."));
        UiNode grid = grid(2).gap(4);
        int unlocked = 0;
        for (var e : all) {
            String id = e.getKey();
            int need = e.getValue().palier();
            boolean ok = need <= 0 || pal >= need || p.isCreative();
            if (ok) unlocked++;
            boolean station = id.startsWith("fnations:") && s.station().forResult(id.substring(9)) != null;
            ItemStack st = Inv.stack(id, 1);
            grid.add(row(item(st).size(16), col(itemName(st), text((ok ? TG + "✔ " : TR + "🔒 ") + (need <= 0 ? "Dès le départ" : "Palier " + need)
                            + TM + " • " + (station ? "Station" : "Établi"))).gap(2).flex(1))
                    .bg(ok ? "#1F3A2C" : ROW).r(5).pad(5).gap(6).act(a("craft", id)).hover());
        }
        UiNode info = row(text(T2 + all.size() + " crafts • <white>" + unlocked + "</white> débloqué(s)"), flexSpace(),
                chip(TG + "Débloqué").bg("#1F3A2C"), chip(TM + "Verrouillé").bg(ROW), text(TM + "Clic : recette")).gap(5);
        return col(info, list("crafts").add(grid).flex(1)).gap(5);
    }

    private UiNode gains(Job sel) {
        UiNode list = list("gains").gap(4).flex(1);
        list.add(text(T2 + sel.description).wrap());
        for (String h : sel.howTo) list.add(row(icon("dot").size(8).color(ACCENT), text(T2 + h).wrap().flex(1)).gap(6).bg(ROW).r(5).pad(6));
        list.add(row(icon("clock").size(10).color(TEXT2), text(TM + "L'argent des métiers est versé chaque minute. En créatif, aucun métier ne progresse.").wrap().flex(1)).gap(6).pad(4));
        return list;
    }

    private UiNode notation(ServerPlayer p, Job sel) {
        RatingManager rm = s.ratings();
        RatingManager.Score sc = rm.playerScore(p.getUUID());
        String reward = sel == Job.BUILDER
                ? "Chaque construction notée rapporte jusqu'à " + Math.round(s.config().getDouble("notation.recompenses.xp-builder", 2000)) + " XP de Builder selon sa note globale."
                : "La note R&D de vos constructions rapporte jusqu'à " + Math.round(s.config().getDouble("notation.recompenses.xp-ingenieur", 800)) + " XP d'Ingénieur.";
        UiNode last = col().gap(3);
        int k = 0;
        for (RatingManager.Build b : rm.byAuthor(p.getUUID())) {
            if (k++ >= 3) break;
            last.add(row(icon(b.rated() ? "medal" : "clock").size(10).color(b.rated() ? YELLOW : TEXT2), text("<b>" + Txt.esc(b.name)).lines(1).flex(1),
                    NotationPage.scoreBadge(b.global(), b.rated())).bg(ROW).r(4).pad(4).gap(6).act("nav:notation"));
        }
        if (k == 0) last.add(text(TM + "Aucune construction soumise pour l'instant.").wrap());
        return row(col(sectionTitle("Ma moyenne"),
                        row(text(sc.count() == 0 ? TM + "—" : "<b><" + NotationPage.color(sc.global()) + ">" + RatingManager.fmt(sc.global())).scale(2),
                                text(TM + "/10 • " + sc.count() + " notée(s)")).gap(5).valign("bottom"),
                        NotationPage.criteria(sc.criteria(), sc.count())).gap(5).w(200),
                col(text(T2 + reward).wrap(), sectionTitle("Dernières constructions"), last, flexSpace(),
                        btn("Notation des constructions", "nav:notation").primary().withIcon("medal").flag("fill")).gap(5).flex(1)).gap(12).valign("top");
    }

    // ------------------------------------------------------------ actions
    @Override
    public void action(ServerPlayer p, UiSession ss, String act, String arg, Map<String, String> in) {
        switch (act) {
            case "job" -> { if (Job.byId(arg) != null) ss.set("job", arg); }
            case "tab" -> ss.set("tab", arg);
            case "top" -> ss.dialog = leaderboard(p, selected(ss));
            case "craft" -> ss.dialog = craftDialog(p, arg);
            default -> {
            }
        }
    }

    /** Fiche d'un craft : condition de métier, recherche et ingrédients (Station) ou établi. */
    private UiNode craftDialog(ServerPlayer p, String id) {
        JobManager jm = s.jobs();
        JobManager.CraftReq req = jm.requirement(id);
        ItemStack st = Inv.stack(id, 1);
        StationRecipe r = id.startsWith("fnations:") ? s.station().forResult(id.substring(9)) : null;
        boolean ok = jm.canCraft(p, id);
        UiNode reqRow = req == null || req.palier() <= 0 ? row(icon("check").size(10).color(GREEN), text(T2 + "Métier : aucun palier requis" + (req == null ? "" : " (" + req.job().display + ")"))).gap(5)
                : row(icon(ok ? "check" : "lock").size(10).color(ok ? GREEN : RED),
                text((ok ? T2 : TR) + "Métier : " + JobManager.label(req) + (ok ? "" : " (vous : palier " + jm.palier(p.getUUID(), req.job()) + ")")).wrap().flex(1)).gap(5);
        UiNode tech = null, ings = null;
        if (r != null) {
            Technology t = s.research().requiredFor(r.result());
            if (t != null) {
                boolean res = s.research().playerCanUse(p, t.id());
                tech = row(icon(res ? "check" : "lock").size(10).color(res ? GREEN : RED), text((res ? T2 : TR) + "Recherche : " + Txt.plain(t.name())).wrap().flex(1)).gap(5);
            }
            ings = col().gap(3);
            for (var e : r.ingredients().entrySet()) {
                int have = Inv.count(p, e.getKey());
                ings.add(row(item(Inv.stack(e.getKey(), 1)).size(14), text(Inv.display(e.getKey())).flex(1),
                        text((have >= e.getValue() ? TG : TR) + Math.min(have, 999) + "/" + e.getValue())).bg(ROW).r(4).pad(3).gap(6));
            }
        }
        return card(row(item(st).size(28), col(itemName(st).bold(), text(T2 + (r != null ? "Station de Craft" + (r.amount() > 1 ? " • ×" + r.amount() : "") : "Établi (recette Minecraft)"))).gap(3).flex(1),
                        btn("", "dialog:close").withIcon("close").ghost().w(18)).gap(8),
                reqRow, tech,
                r != null ? sectionTitle("Ingrédients") : null, ings,
                r == null ? text(TM + "La recette s'affiche dans le livre de recettes de l'établi. Sans le palier, le résultat reste vide.").wrap() : null)
                .w(270).pad(12).gap(6);
    }

    private UiNode leaderboard(ServerPlayer p, Job j) {
        UiNode l = list("top").h(170).gap(3);
        int r = 1;
        for (JobManager.Rank e : s.jobs().top(j, 15)) {
            String medal = r == 1 ? TY : r == 2 ? "<#C0C4CC>" : r == 3 ? "<#D08A4E>" : TM;
            l.add(row(text(medal + "<b>#" + r).w(24), head(e.player(), s.name(e.player())).size(12), text(Txt.esc(s.name(e.player()))).flex(1),
                    chip(TA + "P" + s.jobs().palier(e.xp())).bg(ACCENT_SOFT), text(T2 + Math.round(e.xp()) + " XP").right().w(70))
                    .bg(e.player().equals(p.getUUID()) ? ACCENT_SOFT : ROW).r(4).pad(5).gap(6));
            r++;
        }
        if (l.isEmpty()) l.add(empty("Personne n'a encore d'expérience dans ce métier."));
        return card(row(icon(j.icon).size(14).color(ACCENT), text("<b>Classement " + j.display).scale(1.25).flex(1), btn("", "dialog:close").withIcon("close").ghost().w(18)).gap(6),
                l).w(300).pad(10);
    }
}

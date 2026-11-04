package fr.fnations.ui.pages;

import fr.fnations.company.Company;
import fr.fnations.job.Job;
import fr.fnations.job.JobManager;
import fr.fnations.server.FnServer;
import fr.fnations.ui.UiNode;
import fr.fnations.ui.UiPage;
import fr.fnations.ui.UiSession;
import fr.fnations.util.Txt;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static fr.fnations.ui.UiNode.*;
import static fr.fnations.ui.UiTheme.*;

/** Compétences (métiers) : liste à gauche, détail, paliers et récompenses. */
public final class JobsPage extends UiPage {
    public JobsPage(FnServer s) { super(s); }

    @Override
    public String id() { return "metiers"; }

    private Job selected(UiSession ss) {
        Job j = Job.byId(ss.get("job", "builder"));
        return j == null ? Job.BUILDER : j;
    }

    @Override
    public UiNode build(ServerPlayer p, UiSession ss) {
        Job sel = selected(ss);
        JobManager jm = s.jobs();
        // ---------- liste des compétences
        UiNode left = card(text(T2 + "<b>COMPÉTENCES")).w(122);
        for (Job j : Job.values()) {
            int pal = jm.palier(p.getUUID(), j);
            boolean on = j == sel;
            left.add(row(icon(j.icon).size(12).color(on ? TEXT : TEXT2), text((on ? "<b>" : T2) + j.display).flex(1),
                    text((on ? "" : TM) + pal).right().w(10))
                    .pad(6, 5).r(5).bg(on ? ACCENT : null).act(a("job", j.id)).gap(6));
        }
        left.add(flexSpace(), text(TM + "Vos métiers progressent\nquand vous jouez.").wrap());

        // ---------- détail
        double xp = jm.xp(p.getUUID(), sel);
        int pal = jm.palier(xp);
        double next = jm.next(xp);
        boolean how = "1".equals(ss.get("how"));
        UiNode info = col(
                text("<b>" + sel.display.toUpperCase()).scale(1.5).color(TEXT),
                text(T2 + sel.description).wrap(),
                row(icon("info").size(10).color(TA), text(TA + "<u>Comment monter cette compétence ?")).gap(4).act(a("how")).pad(2),
                how ? col(sel.howTo.stream().map(h -> row(icon("dot").size(8).color(TEXT2), text(T2 + h).wrap().flex(1)).gap(4)).toArray(UiNode[]::new)).gap(3) : null)
                .gap(6).flex(1);
        Company c = s.companies().of(p.getUUID());
        UiNode portrait = col(
                player().h(86),
                text("<b>" + Txt.esc(p.getScoreboardName())).center(),
                text(TM + "#" + jm.rank(p.getUUID(), sel) + " du serveur").center(),
                btn("Classement", a("top")).primary().flag("fill"))
                .bg(ROW).r(6).pad(6).gap(4).w(96).str("align", "center");

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
            }
            UiNode dot = row(icon(reached ? (i == 0 ? "dot" : "check") : "lock").size(8).color(reached ? TEXT : MUTED))
                    .w(16).h(16).r(8).bg(reached ? ACCENT : ROW).pad(4).tip(tip).hover();
            track.add(dot);
            if (i < 6) {
                double from = jm.threshold(i), to = jm.threshold(i + 1);
                double v = xp >= to ? 1 : xp <= from ? 0 : (xp - from) / (to - from);
                track.add(bar(v).h(4).flex(1));
            }
        }
        UiNode palier = col(
                row(text(T2 + "<b>PALIER"), flexSpace(), text("<b>" + Math.round(xp) + "</b>" + TM + " / " + Math.round(next) + " XP")),
                track,
                text(pal >= 6 ? TG + "Palier maximal atteint !" : TM + "Prochain palier : " + Math.round(next - xp) + " XP restants")).gap(6);

        // ---------- récompenses
        UiNode rewards = list("rewards").gap(4).flex(1);
        for (int i = 1; i <= 6; i++) {
            boolean got = i <= pal;
            UiNode lines = col(text((got ? "<b>" : T2 + "<b>") + "Palier " + i + " " + TM + "— " + Math.round(jm.threshold(i)) + " XP")).gap(2).flex(1);
            for (String perk : sel.perks(i)) lines.add(text((got ? T2 : TM) + perk).wrap());
            rewards.add(row(icon(got ? "check" : "lock").size(12).color(got ? GREEN : MUTED), lines,
                    text((got ? TG : TM) + "+" + money(jm.reward(i))).right())
                    .bg(ROW).r(5).pad(6).gap(8).valign("top"));
        }
        if (c != null && c.type().job == sel)
            rewards.add(row(icon("briefcase").size(12).color(BLUE), text(TB + "Bonus d'entreprise (" + Txt.esc(c.name()) + ") : XP +" + s.companies().level(c)
                    + " %, revenus +" + (2 * s.companies().level(c)) + " %").wrap().flex(1)).bg(ROW).r(5).pad(6).gap(8));

        UiNode main = card(row(info, portrait).gap(10).valign("top"), sep(), palier, sep(), rewards).flex(1);
        return row(left, main).gap(8);
    }

    @Override
    public void action(ServerPlayer p, UiSession ss, String act, String arg, Map<String, String> in) {
        switch (act) {
            case "job" -> { if (Job.byId(arg) != null) ss.set("job", arg); }
            case "how" -> ss.set("how", "1".equals(ss.get("how")) ? "0" : "1");
            case "top" -> ss.dialog = leaderboard(p, selected(ss));
            default -> {
            }
        }
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

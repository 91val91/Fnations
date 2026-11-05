package fr.fnations.ui.pages;

import fr.fnations.nation.Nation;
import fr.fnations.rating.RatingManager;
import fr.fnations.rating.RatingManager.Build;
import fr.fnations.rating.RatingManager.Criterion;
import fr.fnations.server.FnServer;
import fr.fnations.server.Msg;
import fr.fnations.ui.UiNode;
import fr.fnations.ui.UiPage;
import fr.fnations.ui.UiSession;
import fr.fnations.util.Txt;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

import static fr.fnations.ui.UiNode.*;
import static fr.fnations.ui.UiTheme.*;

/** Notation des constructions : mes constructions, meilleures constructions, classement des pays, jury. */
public final class NotationPage extends UiPage {
    public NotationPage(FnServer s) { super(s); }

    @Override
    public String id() { return "notation"; }

    private RatingManager rm() { return s.ratings(); }

    private static String fmt(double v) { return RatingManager.fmt(v); }

    private static String medal(int r) { return r == 1 ? TY : r == 2 ? "<#C0C4CC>" : r == 3 ? "<#D08A4E>" : TM; }

    /** Couleur d'une note sur 10. */
    static String color(double v) { return v >= 8 ? "#43D17A" : v >= 6 ? "#8C96FF" : v >= 4 ? "#F5A623" : "#F04747"; }

    /** Cinq petites barres (une par critère), avec le détail en info-bulle. */
    static UiNode miniBars(double[] values) {
        UiNode r = row().gap(2).w(70);
        String[] tip = new String[Criterion.values().length];
        for (Criterion c : Criterion.values()) {
            double v = values[c.ordinal()];
            r.add(bar(v / 10).color(color(v)).h(4).flex(1));
            tip[c.ordinal()] = T2 + c.display + " : <white>" + fmt(v) + "/10";
        }
        return r.valign("center").tip(tip);
    }

    static double[] values(Build b) {
        double[] v = new double[Criterion.values().length];
        for (Criterion c : Criterion.values()) v[c.ordinal()] = b.avg(c);
        return v;
    }

    /** Bloc « note détaillée » : un critère par ligne. */
    static UiNode criteria(double[] values, int count) {
        UiNode col = col().gap(4);
        for (Criterion c : Criterion.values()) {
            double v = values[c.ordinal()];
            col.add(row(icon(c.icon).size(10).color(TEXT2), text(T2 + c.display).w(88), bar(count == 0 ? 0 : v / 10).color(color(v)).h(5).flex(1),
                    text(count == 0 ? TM + "—" : "<b>" + fmt(v)).right().w(22)).gap(5).tip("<b>" + c.display, T2 + c.hint));
        }
        return col;
    }

    static UiNode scoreBadge(double v, boolean rated) {
        return chip(rated ? "<b><" + color(v) + ">" + fmt(v) + "</b>" + TM + "/10" : TM + "En attente").bg(rated ? "#1C1D27" : ROW);
    }

    @Override
    public UiNode build(ServerPlayer p, UiSession ss) {
        boolean jury = rm().isJury(p);
        String tab = ss.get("tab", "mes");
        if (tab.equals("jury") && !jury) tab = "mes";
        int pending = rm().pending().size();
        UiNode head = row(row(icon("medal").size(18).color(YELLOW)).bg(ROW).r(6).pad(5),
                col(text("<b>Notation des constructions").scale(1.4),
                        text(T2 + "Le jury note vos constructions de 0 à 10 : Build, Skill, Architecture, Respect du thème et R&D.").wrap()).gap(3).flex(1),
                btn("Soumettre une construction", a("submitdlg")).primary().withIcon("plus")).gap(8);
        UiNode tabs = row(
                btn("Mes constructions", a("tab", "mes")).withIcon("hammer").style("tab").selected(tab.equals("mes")),
                btn("Meilleures", a("tab", "top")).withIcon("star").style("tab").selected(tab.equals("top")),
                btn("Pays", a("tab", "pays")).withIcon("flag").style("tab").selected(tab.equals("pays")),
                jury ? btn("Jury" + (pending > 0 ? " (" + pending + ")" : ""), a("tab", "jury")).withIcon("shield").style("tab").selected(tab.equals("jury")) : null,
                flexSpace(), jury ? chip(TY + "Vous êtes juré").bg(ROW) : null).gap(2);
        UiNode body = switch (tab) {
            case "top" -> top(p);
            case "pays" -> nations(p);
            case "jury" -> juryTab(p);
            default -> mine(p);
        };
        return col(head, tabs, body.flex(1)).gap(8);
    }

    // ------------------------------------------------------------ onglets
    private UiNode buildRow(Build b, boolean showAuthor, int rank) {
        Nation n = b.nation == null ? null : s.nations().get(b.nation);
        String sub = TM + Txt.esc(b.theme) + (showAuthor ? " • " + Txt.esc(b.authorName) : "") + (n == null ? "" : " • [" + n.tag() + "]");
        UiNode status = b.rated() ? text(T2 + "Notée par " + b.notes.size() + " juré" + (b.notes.size() > 1 ? "s" : "")) : text(TY + "En attente du jury");
        return row(rank > 0 ? text(medal(rank) + "<b>#" + rank).w(24) : null,
                showAuthor ? head(b.author, b.authorName).size(14) : row(icon(b.rated() ? "medal" : "clock").size(12).color(b.rated() ? YELLOW : TEXT2)).w(14),
                col(text("<b>" + Txt.esc(b.name)).lines(1), text(sub).lines(1)).gap(2).flex(1),
                showAuthor ? null : status,
                b.rated() ? miniBars(values(b)) : null,
                scoreBadge(b.global(), b.rated()).w(52))
                .bg(ROW).r(5).pad(6).gap(8).act(a("detail", b.id)).hover();
    }

    private UiNode mine(ServerPlayer p) {
        UiNode list = list("mes").gap(4).flex(1);
        for (Build b : rm().byAuthor(p.getUUID())) list.add(buildRow(b, false, 0));
        if (list.isEmpty()) list.add(col(icon("hammer").size(24).color(TEXT2), text("<b>Aucune construction soumise").center(),
                text(T2 + "Placez-vous dans votre plus belle construction, puis cliquez sur « Soumettre une construction ».").wrap().center()).gap(6).pad(16).str("align", "center"));
        RatingManager.Score sc = rm().playerScore(p.getUUID());
        double min = s.config().getDouble("notation.note-minimale", 4);
        UiNode side = card(sectionTitle("Ma moyenne"),
                row(text(sc.count() == 0 ? TM + "—" : "<b><" + color(sc.global()) + ">" + fmt(sc.global())).scale(2), text(TM + "/10 • " + sc.count() + " notée(s)")).gap(5).valign("bottom"),
                criteria(sc.criteria(), sc.count()), sep(),
                sectionTitle("Récompenses"),
                text(T2 + "À la première note, si elle atteint " + fmt(min) + "/10 :").wrap(),
                row(icon("hammer").size(10).color(TEXT2), text(T2 + "jusqu'à <white>" + Math.round(s.config().getDouble("notation.recompenses.xp-builder", 2000)) + " XP</white> de Builder").wrap().flex(1)).gap(5),
                row(icon("flask").size(10).color(TEXT2), text(T2 + "jusqu'à <white>" + Math.round(s.config().getDouble("notation.recompenses.xp-ingenieur", 800)) + " XP</white> d'Ingénieur (R&D)").wrap().flex(1)).gap(5),
                row(icon("coin").size(10).color(TEXT2), text(T2 + "jusqu'à <white>" + money(s.config().getDouble("notation.recompenses.argent", 15000))).wrap().flex(1)).gap(5)).w(190);
        return row(card(list).flex(1), side).gap(8);
    }

    private UiNode top(ServerPlayer p) {
        UiNode list = list("top").gap(4).flex(1);
        int r = 1;
        for (Build b : rm().ranked()) {
            if (r > 50) break;
            list.add(buildRow(b, true, r++));
        }
        if (list.isEmpty()) list.add(empty("Aucune construction notée pour l'instant."));
        return card(list);
    }

    private UiNode nations(ServerPlayer p) {
        UiNode list = list("pays").gap(4).flex(1);
        Nation mine = s.nations().of(p);
        int r = 1;
        for (Map.Entry<Nation, RatingManager.Score> e : rm().nationRanking()) {
            Nation n = e.getKey();
            RatingManager.Score sc = e.getValue();
            list.add(row(text(medal(r) + "<b>#" + r++).w(24), space(4, 22).bg(NationPage.hex(n.color())).r(2),
                    col(text("<b>" + Txt.esc(n.name()) + " " + TM + "[" + n.tag() + "]"), text(TM + sc.count() + " construction(s) notée(s)")).gap(2).flex(1),
                    miniBars(sc.criteria()), scoreBadge(sc.global(), true).w(52))
                    .bg(n == mine ? ACCENT_SOFT : ROW).r(5).pad(6).gap(8));
        }
        if (list.isEmpty()) list.add(empty("Aucun pays n'a encore de construction notée. Soumettez une construction « pour mon pays » !"));
        return card(list);
    }

    private UiNode juryTab(ServerPlayer p) {
        UiNode list = list("jury").gap(4).flex(1);
        long now = System.currentTimeMillis();
        for (Build b : rm().pending()) {
            Nation n = b.nation == null ? null : s.nations().get(b.nation);
            list.add(row(head(b.author, b.authorName).size(14),
                    col(text("<b>" + Txt.esc(b.name)).lines(1), text(TM + Txt.esc(b.theme) + " • " + Txt.esc(b.authorName) + (n == null ? "" : " • [" + n.tag() + "]")
                            + " • il y a " + Txt.duration((now - b.created) / 1000)).lines(1)).gap(2).flex(1),
                    btn("Visiter", a("tp", b.id)).withIcon("teleport"),
                    btn("Noter", a("ratedlg", b.id)).primary().withIcon("star").disabled(b.author.equals(p.getUUID())))
                    .bg(ROW).r(5).pad(6).gap(6));
        }
        if (list.isEmpty()) list.add(empty("Aucune construction n'attend le jury."));
        return card(row(sectionTitle("Constructions en attente"), flexSpace(), text(TM + "Les plus anciennes d'abord")), list);
    }

    // ------------------------------------------------------------ dialogues
    private UiNode submitDialog(ServerPlayer p) {
        Nation n = s.nations().of(p);
        return card(text("<b>Soumettre une construction").scale(1.25),
                text(T2 + "Placez-vous au cœur de votre construction : votre position est enregistrée pour la visite du jury.").wrap(),
                input("bname").placeholder("Nom de la construction").maxLen(32),
                input("btheme").placeholder("Thème : ville japonaise, base militaire...").maxLen(40),
                input("bdesc").placeholder("Description (facultatif)").maxLen(120),
                n == null ? null : check("bnation", "Pour mon pays (" + Txt.esc(n.name()) + ")", true),
                text(TM + "Position : " + p.getBlockX() + ", " + p.getBlockY() + ", " + p.getBlockZ()),
                row(flexSpace(), btn("Annuler", "dialog:close").w(80), btn("Soumettre", a("submit")).primary().withIcon("send").w(100)).gap(6))
                .w(300).pad(12).gap(7);
    }

    private UiNode detailDialog(ServerPlayer p, Build b) {
        boolean jury = rm().isJury(p);
        Nation n = b.nation == null ? null : s.nations().get(b.nation);
        UiNode notes = list("notes").gap(3).h(64);
        for (RatingManager.Note no : b.notes.values())
            notes.add(row(text("<b>" + Txt.esc(no.jurorName())).w(70), text(no.comment().isEmpty() ? TM + "Sans commentaire" : T2 + "« " + Txt.esc(no.comment()) + " »").wrap().flex(1),
                    text("<b><" + color(no.global()) + ">" + fmt(no.global())).right().w(26)).bg(ROW).r(4).pad(4).gap(6).valign("top"));
        if (notes.isEmpty()) notes.add(empty("Pas encore de note."));
        boolean canRemove = (b.author.equals(p.getUUID()) && !b.rated()) || jury;
        return card(row(col(text("<b>" + Txt.esc(b.name)).scale(1.3), text(T2 + "Thème : " + Txt.esc(b.theme))).gap(3).flex(1), scoreBadge(b.global(), b.rated()),
                        btn("", "dialog:close").withIcon("close").ghost().w(18)).gap(6),
                b.description.isEmpty() ? null : text(T2 + Txt.esc(b.description)).wrap(),
                row(head(b.author, b.authorName).size(12), text(Txt.esc(b.authorName) + (n == null ? "" : TM + " • " + Txt.esc(n.name()) + " [" + n.tag() + "]")).flex(1),
                        text(TM + b.spot.block().getX() + ", " + b.spot.block().getY() + ", " + b.spot.block().getZ())).gap(6),
                sep(), criteria(values(b), b.notes.size()), sep(),
                sectionTitle("Avis du jury"), notes,
                row(canRemove ? btn("Retirer", a("remove", b.id)).danger().withIcon("trash") : null, flexSpace(),
                        jury ? btn("Visiter", a("tp", b.id)).withIcon("teleport") : null,
                        jury && !b.author.equals(p.getUUID()) ? btn(b.notes.containsKey(p.getUUID()) ? "Modifier ma note" : "Noter", a("ratedlg", b.id)).primary().withIcon("star") : null).gap(6))
                .w(340).pad(12).gap(6);
    }

    private int value(UiSession ss, Criterion c) {
        try { return Integer.parseInt(ss.get("v_" + c.id, "-1")); } catch (NumberFormatException e) { return -1; }
    }

    private UiNode rateDialog(ServerPlayer p, UiSession ss, Build b) {
        Nation n = b.nation == null ? null : s.nations().get(b.nation);
        UiNode rows = col().gap(5);
        boolean complete = true;
        double total = 0;
        for (Criterion c : Criterion.values()) {
            int v = value(ss, c);
            complete &= v >= 0;
            total += Math.max(0, v);
            UiNode scale = row().gap(1);
            for (int k = 0; k <= 10; k++)
                scale.add(btn(String.valueOf(k), a("val", c.id + ":" + k)).w(17).h(15).style(k == v ? "primary" : "secondary"));
            rows.add(row(icon(c.icon).size(10).color(TEXT2), col(text("<b>" + c.display), text(TM + c.hint).lines(1)).gap(1).w(124), scale).gap(6).tip("<b>" + c.display, T2 + c.hint));
        }
        String rd = n == null ? TM + "Construction personnelle : jugez la technologie visible (machines, énergie)."
                : T2 + "Recherche de " + Txt.esc(n.name()) + " : <white>" + n.researched().size() + "/" + s.research().all().size() + "</white> technologies.";
        return card(row(text("<b>Noter « " + Txt.esc(b.name) + " »").scale(1.25).flex(1), btn("", "dialog:close").withIcon("close").ghost().w(18)).gap(6),
                text(T2 + "Thème annoncé : <white>" + Txt.esc(b.theme) + "</white> • par " + Txt.esc(b.authorName)).wrap(),
                row(icon("flask").size(10).color(TEXT2), text(rd).wrap().flex(1)).gap(5),
                rows,
                input("ncomment").placeholder("Commentaire pour le joueur (facultatif)").maxLen(120),
                row(text(complete ? "Note globale : <b><" + color(total / 5) + ">" + fmt(total / 5) + "</b>/10" : TM + "Choisissez une note pour chaque critère.").flex(1),
                        btn("Annuler", "dialog:close").w(70), btn("Valider la note", a("rate", b.id)).primary().disabled(!complete).w(110)).gap(6))
                .w(372).pad(12).gap(7);
    }

    @Override
    public void action(ServerPlayer p, UiSession ss, String act, String arg, Map<String, String> in) {
        switch (act) {
            case "tab" -> ss.set("tab", arg);
            case "submitdlg" -> ss.dialog = submitDialog(p);
            case "submit" -> {
                boolean forNation = "true".equals(in.getOrDefault("bnation", "false"));
                String err = rm().submit(p, in.get("bname"), in.get("btheme"), in.get("bdesc"), forNation);
                if (err != null) { Msg.err(p, err); return; }
                ss.dialog = null;
                ss.set("tab", "mes");
                Msg.ok(p, "Construction soumise au jury !");
            }
            case "detail" -> {
                Build b = rm().get(parse(arg));
                ss.dialog = b == null ? null : detailDialog(p, b);
            }
            case "ratedlg" -> {
                Build b = rm().get(parse(arg));
                if (b == null || !rm().isJury(p)) return;
                RatingManager.Note mine = b.notes.get(p.getUUID());
                for (Criterion c : Criterion.values()) ss.set("v_" + c.id, mine == null ? "-1" : String.valueOf(mine.values()[c.ordinal()]));
                ss.set("rid", String.valueOf(b.id));
                ss.dialog = rateDialog(p, ss, b);
            }
            case "val" -> {
                String[] v = arg.split(":");
                Build b = rm().get(parse(ss.get("rid", "-1")));
                if (v.length != 2 || b == null) return;
                ss.set("v_" + v[0], v[1]);
                ss.dialog = rateDialog(p, ss, b);
            }
            case "rate" -> {
                Build b = rm().get(parse(arg));
                if (b == null) return;
                int[] vals = new int[Criterion.values().length];
                for (Criterion c : Criterion.values()) vals[c.ordinal()] = value(ss, c);
                String err = rm().rate(p, b.id, vals, in.get("ncomment"));
                if (err != null) { Msg.err(p, err); return; }
                ss.dialog = null;
                Msg.ok(p, "Note enregistrée : " + fmt(b.global()) + "/10 pour « " + b.name + " ».");
            }
            case "tp" -> {
                if (rm().visit(p, parse(arg))) s.ui().close(p);
                else Msg.err(p, "Visite impossible.");
            }
            case "remove" -> {
                String err = rm().remove(p, parse(arg));
                if (err != null) Msg.err(p, err);
                else { ss.dialog = null; Msg.ok(p, "Construction retirée."); }
            }
            default -> {
            }
        }
    }

    private static int parse(String s) {
        try { return Integer.parseInt(s.trim()); } catch (RuntimeException e) { return -1; }
    }
}

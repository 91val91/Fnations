package fr.fnations.ui;

import fr.fnations.server.FnServer;
import fr.fnations.util.Txt;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

import static fr.fnations.ui.UiNode.btn;
import static fr.fnations.ui.UiNode.col;
import static fr.fnations.ui.UiNode.row;
import static fr.fnations.ui.UiNode.text;

/** Une page de la tablette FNations : construction de la vue et traitement des actions. */
public abstract class UiPage {
    protected final FnServer s;

    protected UiPage(FnServer s) { this.s = s; }

    /** Identifiant de la page (préfixe des actions : "id.action:arg"). */
    public abstract String id();

    /** Entrée de la barre de navigation à mettre en évidence. */
    public String nav() { return id(); }

    /** Rafraîchissement automatique côté client, en secondes (0 = jamais). */
    public int refresh(ServerPlayer p, UiSession ss) { return 0; }

    public abstract UiNode build(ServerPlayer p, UiSession ss);

    public abstract void action(ServerPlayer p, UiSession ss, String act, String arg, Map<String, String> in);

    /** Appelé quand le joueur arrive sur la page. */
    public void enter(ServerPlayer p, UiSession ss) {}

    // ------------------------------------------------------------ aides de mise en page
    protected String a(String act) { return id() + "." + act; }

    protected String a(String act, Object arg) { return id() + "." + act + ":" + arg; }

    protected static String money(double v) { return Txt.money(v); }

    /** Titre de page avec sous-titre. */
    protected static UiNode header(String icon, String title, String subtitle) {
        UiNode t = col(text("<b>" + title).scale(1.5), subtitle == null ? null : text(UiTheme.T2 + subtitle)).gap(3).flex(1);
        return row(UiNode.icon(icon).size(16).color(UiTheme.ACCENT), t).gap(8);
    }

    protected static UiNode sectionTitle(String t) { return text(UiTheme.T2 + "<b>" + t.toUpperCase()); }

    /** Petite carte de statistique (libellé + valeur). */
    protected static UiNode stat(String icon, String label, String value) {
        return row(UiNode.icon(icon).size(12).color(UiTheme.TEXT2),
                col(text(UiTheme.T2 + label), text("<b>" + value)).gap(2).flex(1))
                .bg(UiTheme.ROW).r(5).pad(6).gap(6);
    }

    protected static UiNode empty(String msg) { return text(UiTheme.TM + msg).center().wrap(); }

    /** Dialogue de confirmation. */
    protected static UiNode confirm(String title, String detail, String okLabel, String okAction, boolean danger) {
        UiNode ok = btn(okLabel, okAction).w(96);
        if (danger) ok.danger(); else ok.primary();
        return UiNode.card(text("<b>" + title).scale(1.25).wrap(), detail == null ? null : text(UiTheme.T2 + detail).wrap(),
                row(UiNode.flexSpace(), btn("Annuler", "dialog:close").w(80), ok).gap(6)).w(260).pad(12).gap(10);
    }

    /** Valeur numérique saisie (null si invalide). */
    protected static Double amount(Map<String, String> in, String key) {
        String v = in.get(key);
        if (v == null || v.isBlank()) return null;
        String t = v.trim().toLowerCase().replace(" ", "");
        double mult = 1;
        if (t.endsWith("k")) { mult = 1000; t = t.substring(0, t.length() - 1); }
        else if (t.endsWith("m")) { mult = 1_000_000; t = t.substring(0, t.length() - 1); }
        Double d = Txt.parseAmount(t);
        return d == null ? null : Math.floor(d * mult * 100) / 100.0;
    }

    protected static int intIn(Map<String, String> in, String key, int def) {
        try { return Integer.parseInt(in.getOrDefault(key, "").trim()); } catch (NumberFormatException e) { return def; }
    }
}

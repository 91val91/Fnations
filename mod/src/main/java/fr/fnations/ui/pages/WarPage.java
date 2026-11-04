package fr.fnations.ui.pages;

import fr.fnations.nation.Nation;
import fr.fnations.server.FnServer;
import fr.fnations.ui.UiNode;
import fr.fnations.ui.UiPage;
import fr.fnations.ui.UiSession;
import fr.fnations.util.Txt;
import fr.fnations.war.War;
import fr.fnations.war.WarManager;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

import static fr.fnations.ui.UiNode.*;
import static fr.fnations.ui.UiTheme.*;

/** Guerres : conflits de la nation, déclaration, paix, capitulation, captures. */
public final class WarPage extends UiPage {
    public WarPage(FnServer s) { super(s); }

    @Override
    public String id() { return "guerres"; }

    @Override
    public int refresh(ServerPlayer p, UiSession ss) {
        Nation n = s.nations().of(p);
        return n != null && !s.wars().of(n).isEmpty() ? 3 : 0;
    }

    private WarManager wm() { return s.wars(); }

    private String name(String id) {
        Nation n = wm().nation(id);
        return n == null ? id : Txt.esc(n.name());
    }

    private String color(String id) {
        Nation n = wm().nation(id);
        return n == null ? TEXT2 : NationPage.hex(n.color());
    }

    private UiNode warCard(War w, Nation mine) {
        int goal = wm().goal();
        long now = System.currentTimeMillis();
        String state = w.state() == War.State.PREPARATION ? TY + "⌛ Préparation : " + Txt.duration((w.activeAt() - now) / 1000) : TR + "⚔ Hostilités en cours";
        UiNode side = row(
                col(text("<b>" + name(w.attacker())).color(color(w.attacker())), bar(w.attackerScore() / (double) goal).h(6).color(RED),
                        text(T2 + w.attackerScore() + "/" + goal + " pts • " + w.kills(w.attacker()) + " kills")).gap(3).flex(1),
                text("<b>VS").scale(1.25),
                col(text("<b>" + name(w.defender())).color(color(w.defender())).right(), bar(w.defenderScore() / (double) goal).h(6).color(BLUE),
                        text(T2 + w.defenderScore() + "/" + goal + " pts • " + w.kills(w.defender()) + " kills").right()).gap(3).flex(1)).gap(10);
        UiNode actions = null;
        if (mine != null && w.involves(mine.id())) {
            boolean theyProposed = w.peaceProposedBy() != null && !w.peaceProposedBy().equals(mine.id());
            actions = row(text(state).flex(1),
                    w.peaceProposedBy() != null ? chip(TG + "☮ Paix proposée par " + name(w.peaceProposedBy())).bg(FIELD) : null,
                    btn(theyProposed ? "Accepter la paix" : "Proposer la paix", a("peace", w.id())).style(theyProposed ? "success" : "secondary").withIcon("handshake"),
                    btn("Capturer ce chunk", a("capture")).withIcon("flag"),
                    btn("Capituler", a("surrender", w.id())).danger()).gap(6);
        } else {
            actions = row(text(state).flex(1), text(TM + "Durée : " + Txt.duration((now - w.declaredAt()) / 1000))).gap(6);
        }
        return card(side, actions).bg(ROW).pad(8);
    }

    @Override
    public UiNode build(ServerPlayer p, UiSession ss) {
        Nation n = s.nations().of(p);
        UiNode rules = row(stat("trophy", "Score de victoire", String.valueOf(wm().goal())).flex(1),
                stat("sword", "Kill", "+" + s.config().getInt("guerres.points-kill", 5) + " pts").flex(1),
                stat("flag", "Capture", "+" + s.config().getInt("guerres.points-capture", 15) + " pts").flex(1),
                stat("arrow_up", "Missile", "+" + s.config().getInt("guerres.points-missile", 10) + " pts").flex(1),
                stat("coin", "Réparations", s.config().getInt("guerres.reparations-pourcent", 30) + " % du trésor").flex(1)).gap(6);
        UiNode mine = list("wars").gap(6).flex(1);
        if (n != null) for (War w : wm().of(n)) mine.add(warCard(w, n));
        if (mine.isEmpty()) mine.add(col(icon("shield").size(24).color(GREEN), text(TG + "<b>Votre nation est en paix").center(),
                text(TM + (n == null ? "Vous n'avez pas de nation." : "Déclarez une guerre depuis la colonne de droite.")).center()).str("align", "center").gap(6).pad(20));
        UiNode targets = list("targets").gap(4).flex(1);
        if (n != null) for (Nation o : s.nations().all()) {
            if (o == n || s.nations().allied(n, o) || wm().between(n, o) != null) continue;
            targets.add(row(space(3, 22).bg(NationPage.hex(o.color())).r(1), col(text("<b>" + Txt.esc(o.name())), text(TM + s.nations().online(o).size() + " en ligne • puissance " + s.nations().power(o))).gap(2).flex(1),
                    btn("", a("declare", o.id())).withIcon("swords").danger().w(22).tip(T2 + "Déclarer la guerre")).bg(ROW).r(5).pad(5).gap(6));
        }
        if (targets.isEmpty()) targets.add(empty(n == null ? "Sans nation, pas de guerre." : "Aucune cible possible."));
        UiNode world = list("world").gap(3).h(84);
        for (War w : wm().all()) world.add(row(text("<b>" + name(w.attacker())).color(color(w.attacker())).flex(1), text(TR + w.attackerScore() + " ⚔ " + w.defenderScore()),
                text("<b>" + name(w.defender())).color(color(w.defender())).right().flex(1)).gap(6).pad(3));
        if (world.isEmpty()) world.add(text(TG + "☮ Le monde est en paix.").center());
        UiNode right = col(card(text("<b>Déclarer la guerre"), text(TM + "Coût : " + money(s.config().getDouble("guerres.cout-declaration", 10000)) + " (trésor)"), targets).flex(1),
                card(text("<b>Conflits mondiaux"), world)).gap(8).w(180);
        return col(header("swords", "Ministère de la Guerre", "Kills, captures de chunks et missiles font gagner des points"), rules,
                row(card(text("<b>Guerres de votre nation"), mine).flex(1), right).gap(8).flex(1)).gap(8);
    }

    @Override
    public void action(ServerPlayer p, UiSession ss, String act, String arg, Map<String, String> in) {
        War w = null;
        for (War x : wm().all()) if (x.id().equals(arg)) w = x;
        switch (act) {
            case "peace" -> { if (w != null) wm().proposePeace(p, w); }
            case "capture" -> { s.ui().close(p); wm().startCapture(p); }
            case "surrender" -> { if (w != null) ss.dialog = confirm("Capituler ?", "L'ennemi gagne la guerre et reçoit des réparations. Réservé au Chef d'État.",
                    "Capituler", a("surrender_ok", arg), true); }
            case "surrender_ok" -> { ss.dialog = null; if (w != null) wm().surrender(p, w); }
            case "declare" -> {
                Nation o = s.nations().get(arg);
                if (o != null) ss.dialog = confirm("Déclarer la guerre à " + o.name() + " ?",
                        "Coût : " + money(s.config().getDouble("guerres.cout-declaration", 10000)) + ". Une phase de préparation précède les hostilités.", "Déclarer", a("declare_ok", arg), true);
            }
            case "declare_ok" -> {
                ss.dialog = null;
                Nation o = s.nations().get(arg);
                if (o != null) wm().declare(p, o);
            }
            default -> {
            }
        }
    }
}

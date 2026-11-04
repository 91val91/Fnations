package fr.fnations.ui.pages;

import fr.fnations.nation.Nation;
import fr.fnations.nation.Role;
import fr.fnations.research.ResearchManager;
import fr.fnations.research.Technology;
import fr.fnations.server.FnServer;
import fr.fnations.ui.UiNode;
import fr.fnations.ui.UiPage;
import fr.fnations.ui.UiSession;
import fr.fnations.util.Txt;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;

import static fr.fnations.ui.UiNode.*;
import static fr.fnations.ui.UiTheme.*;

/** Recherche & Développement : catégories, arbre technologique, recherche en cours. */
public final class ResearchPage extends UiPage {
    private static final Map<String, String> ICONS = Map.of("economie", "coin", "industrie", "factory", "militaire", "sword",
            "vehicules", "cart", "missiles", "arrow_up", "spatial", "globe");

    public ResearchPage(FnServer s) { super(s); }

    @Override
    public String id() { return "recherche"; }

    @Override
    public int refresh(ServerPlayer p, UiSession ss) {
        Nation n = s.nations().of(p);
        return n != null && n.currentResearch() != null ? 2 : 0;
    }

    private ResearchManager rm() { return s.research(); }

    @Override
    public UiNode build(ServerPlayer p, UiSession ss) {
        Nation n = s.nations().of(p);
        if (n == null) return col(header("flask", "Recherche & Développement", "Il faut appartenir à une nation"),
                card(empty("La recherche est financée par le trésor national. Rejoignez ou fondez une nation."), btn("Mon pays", "nav:pays").primary(), flexSpace()).flex(1)).gap(8);
        String cat = ss.get("cat", ResearchManager.CATEGORIES.keySet().iterator().next());
        UiNode left = card(text(T2 + "<b>CATÉGORIES")).w(140);
        for (var c : ResearchManager.CATEGORIES.entrySet()) {
            List<Technology> l = rm().byCategory(c.getKey());
            long done = l.stream().filter(t -> n.researched().contains(t.id())).count();
            boolean on = c.getKey().equals(cat);
            left.add(row(icon(ICONS.getOrDefault(c.getKey(), "flask")).size(12).color(on ? TEXT : TEXT2),
                    text((on ? "<b>" : T2) + Txt.plain(c.getValue())).flex(1), text((on ? "" : TM) + done + "/" + l.size()))
                    .pad(6, 5).r(5).bg(on ? ACCENT : null).act(a("cat", c.getKey())).gap(6));
        }
        left.add(flexSpace(), stat("bank", "Trésor national", money(n.balance())),
                stat("medal", "Technologies", n.researched().size() + " / " + rm().all().size()));

        Technology cur = rm().get(n.currentResearch());
        UiNode current;
        if (cur == null) {
            current = row(icon("clock").size(14).color(TEXT2), col(text("<b>Aucune recherche en cours"), text(T2 + "Choisissez une technologie disponible ci-dessous.")).gap(2).flex(1))
                    .bg(ROW).r(6).pad(8).gap(8);
        } else {
            long left2 = Math.max(0, (n.researchEnd() - System.currentTimeMillis()) / 1000);
            double total = Math.max(1, cur.seconds() * rm().speed());
            double ratio = Math.max(0, Math.min(1, 1 - left2 / total));
            current = row(item(BuiltInRegistries.ITEM.getKey(cur.icon()).toString()).size(24),
                    col(row(text("<b>" + Txt.plain(cur.name())), flexSpace(), text(T2 + "Temps restant : " + Txt.duration(left2))),
                            bar(ratio).h(9).str("label", Math.round(ratio * 100) + " %")).gap(4).flex(1),
                    n.can(p.getUUID(), Role.OFFICER) ? btn("Annuler", a("cancel")).danger() : null)
                    .bg(ACCENT_SOFT).r(6).pad(8).gap(10);
        }
        UiNode grid = grid(2).gap(6);
        for (Technology t : rm().byCategory(cat)) {
            ResearchManager.Status st = rm().status(n, t);
            String badge = switch (st) {
                case DONE -> TG + "✔ Recherchée";
                case IN_PROGRESS -> TY + "⌛ En cours";
                case AVAILABLE -> TA + "Disponible";
                case LOCKED -> TM + "Verrouillée";
            };
            grid.add(row(item(BuiltInRegistries.ITEM.getKey(t.icon()).toString()).size(16),
                    col(text((st == ResearchManager.Status.LOCKED ? TM : "<b>") + Txt.plain(t.name())), text(T2 + money(t.cost()) + " • " + Txt.duration((long) (t.seconds() * rm().speed()))),
                            text(badge)).gap(2).flex(1))
                    .bg(st == ResearchManager.Status.DONE ? "#1F3A2C" : st == ResearchManager.Status.IN_PROGRESS ? ACCENT_SOFT : ROW).r(6).pad(6).gap(6).act(a("tech", t.id())));
        }
        UiNode techs = list("techs").flex(1).add(grid);
        return row(left, col(header("flask", "Recherche & Développement", Txt.plain(ResearchManager.CATEGORIES.get(cat))), current, techs).gap(8).flex(1)).gap(8);
    }

    private UiNode techDialog(ServerPlayer p, Nation n, Technology t) {
        ResearchManager.Status st = rm().status(n, t);
        UiNode d = card(row(item(BuiltInRegistries.ITEM.getKey(t.icon()).toString()).size(32), col(text("<b>" + Txt.plain(t.name())).scale(1.25),
                text(T2 + money(t.cost()) + " • " + Txt.duration((long) (t.seconds() * rm().speed())))).gap(3).flex(1),
                btn("", "dialog:close").withIcon("close").ghost().w(18)).gap(8));
        for (String line : t.description()) d.add(text(line).wrap());
        if (!t.requires().isEmpty()) {
            d.add(sectionTitle("Prérequis"));
            for (String r : t.requires()) {
                Technology rt = rm().get(r);
                boolean ok = n.researched().contains(r);
                d.add(row(icon(ok ? "check" : "lock").size(10).color(ok ? GREEN : RED), text(rt == null ? r : Txt.plain(rt.name()))).gap(5));
            }
        }
        if (!t.unlocks().isEmpty()) {
            d.add(sectionTitle("Débloque"));
            UiNode r = row().gap(4);
            for (String u : t.unlocks()) r.add(row(item("fnations:" + u).size(16)).bg(ROW).r(4).pad(2));
            d.add(r);
        }
        if (t.bonusClaims() > 0) d.add(text(TG + "+" + t.bonusClaims() + " chunks de territoire"));
        boolean can = st == ResearchManager.Status.AVAILABLE && n.currentResearch() == null && n.can(p.getUUID(), Role.OFFICER);
        String why = st == ResearchManager.Status.DONE ? "Déjà recherchée" : st == ResearchManager.Status.LOCKED ? "Prérequis manquants"
                : n.currentResearch() != null ? "Une recherche est déjà en cours" : !n.can(p.getUUID(), Role.OFFICER) ? "Réservé aux Ministres" : "";
        d.add(row(text(TM + why).flex(1), btn("Lancer la recherche", a("start", t.id())).primary().disabled(!can)).gap(8));
        return d.w(290).pad(12);
    }

    @Override
    public void action(ServerPlayer p, UiSession ss, String act, String arg, Map<String, String> in) {
        Nation n = s.nations().of(p);
        if (n == null) return;
        switch (act) {
            case "cat" -> { if (ResearchManager.CATEGORIES.containsKey(arg)) ss.set("cat", arg); }
            case "tech" -> {
                Technology t = rm().get(arg);
                if (t != null) ss.dialog = techDialog(p, n, t);
            }
            case "start" -> {
                Technology t = rm().get(arg);
                if (t != null) rm().start(p, t);
                ss.dialog = null;
            }
            case "cancel" -> ss.dialog = confirm("Annuler la recherche en cours ?", "Remboursement : "
                    + (int) (s.config().getDouble("recherche.remboursement-annulation", 0.5) * 100) + " % du coût.", "Annuler la recherche", a("cancel_ok"), true);
            case "cancel_ok" -> { ss.dialog = null; rm().cancel(p); }
            default -> {
            }
        }
    }
}

package fr.fnations.ui.pages;

import fr.fnations.loot.LootManager;
import fr.fnations.rank.Rank;
import fr.fnations.rank.RankManager;
import fr.fnations.registry.ModItems;
import fr.fnations.server.FnServer;
import fr.fnations.spawner.SpawnerManager;
import fr.fnations.ui.UiNode;
import fr.fnations.ui.UiPage;
import fr.fnations.ui.UiSession;
import fr.fnations.util.Txt;
import fr.fnations.util.Yaml;
import net.minecraft.server.level.ServerPlayer;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import static fr.fnations.ui.UiNode.*;
import static fr.fnations.ui.UiTheme.*;

/** Boutique : grades, LootBoxs, dollars, spawners (achat en ligne) et avantages du joueur. */
public final class BoutiquePage extends UiPage {
    private final SimpleDateFormat fmt = new SimpleDateFormat("dd/MM/yyyy");

    public BoutiquePage(FnServer s) { super(s); }

    @Override
    public String id() { return "boutique"; }

    private String price(String key, String def) { return s.config().getString("boutique.prix." + key, def); }

    private UiNode buy(String label) { return btn(label, "url:" + s.store().url()).primary().withIcon("cart").flag("fill"); }

    @Override
    public UiNode build(ServerPlayer p, UiSession ss) {
        String tab = ss.get("tab", "grades");
        Rank rank = s.ranks().rank(p.getUUID());
        UiNode tabs = row(
                btn("Grades", a("tab", "grades")).withIcon("crown").style("tab").selected(tab.equals("grades")),
                btn("LootBoxs", a("tab", "lootboxs")).withIcon("box").style("tab").selected(tab.equals("lootboxs")),
                btn("Dollars", a("tab", "dollars")).withIcon("coin").style("tab").selected(tab.equals("dollars")),
                btn("Spawners", a("tab", "spawners")).withIcon("factory").style("tab").selected(tab.equals("spawners")),
                btn("Mes avantages", a("tab", "avantages")).withIcon("star").style("tab").selected(tab.equals("avantages")),
                flexSpace(), chip(rank == Rank.NONE ? T2 + "Sans grade" : rank.tag()).bg(ROW)).gap(2);
        UiNode body = switch (tab) {
            case "lootboxs" -> lootboxes(p);
            case "dollars" -> dollars();
            case "spawners" -> spawners();
            case "avantages" -> perks(p, rank);
            default -> ranks(rank);
        };
        UiNode banner = row(icon("cart").size(16).color(TEXT), col(text("<b>Boutique FNations").scale(1.4),
                text(T2 + "Achetez en ligne : livraison automatique en jeu, même hors ligne.")).gap(3).flex(1),
                btn("Ouvrir la boutique", "url:" + s.store().url()).primary().withIcon("send")).bg(ACCENT_SOFT).r(8).pad(8).gap(10);
        return col(banner, tabs, body.flex(1)).gap(8);
    }

    private UiNode ranks(Rank current) {
        UiNode row = row().gap(8);
        for (Rank r : new Rank[]{Rank.PREMIUM, Rank.SUPER_PREMIUM, Rank.ULTRA}) {
            boolean own = current == r;
            UiNode perks = col().gap(3);
            for (String l : r.perks()) perks.add(row(icon("check").size(8).color(r.color), text(T2 + l).wrap().flex(1)).gap(5).valign("top"));
            row.add(col(row(icon("crown").size(14).color(r.color), text("<b><" + r.color + ">" + r.display).scale(1.3)).gap(5),
                    row(text("<b>" + price(r.id(), "?")).scale(1.5), text(TM + "sur la boutique")).gap(5).valign("bottom"),
                    sep(), perks, flexSpace(),
                    own ? btn("Votre grade actuel", "noop").disabled(true).flag("fill") : buy("Acheter " + r.display))
                    .bg(PANEL).border(own ? r.color : LINE).r(8).pad(9).gap(5).flex(1));
        }
        return row;
    }

    private UiNode lootboxes(ServerPlayer p) {
        LootManager lm = s.loot();
        UiNode row = row().gap(8);
        for (LootManager.Box b : lm.boxes().values()) {
            int have = lm.count(p, b.id());
            UiNode best = col().gap(3);
            b.rewards().stream().sorted((x, y) -> Integer.compare(x.weight(), y.weight())).limit(4).forEach(r -> best.add(
                    row(item(lm.icon(r)).size(16), text("<" + LootManager.rarityColor(r.rarity()) + ">" + lm.label(r)).lines(1).flex(1)).gap(5)));
            row.add(col(row(row(item(ModItems.stack("lootbox_" + b.id())).size(32)).bg(ROW).r(6).pad(4),
                            col(text("<b><" + b.color() + ">" + b.name()).scale(1.4), text("<b>" + price(b.id().equals("ultra") ? "lootbox_ultra" : b.id(), "?"))).gap(4)).gap(8),
                    text(TM + "Meilleurs gains :"), best, flexSpace(),
                    text(T2 + "Vous en avez " + have),
                    have > 0 ? btn("Ouvrir", a("open", b.id())).success().withIcon("box").flag("fill") : null,
                    buy("Acheter"))
                    .bg(PANEL).border(LINE).r(8).pad(10).gap(6).flex(1));
        }
        return row;
    }

    private UiNode dollars() {
        UiNode g = grid(4).gap(8);
        Yaml y = s.config();
        int i = 0;
        for (Map<String, Object> d : y.getMapList("boutique.dollars")) {
            Yaml e = new Yaml(d);
            double amount = e.getDouble("montant", 0);
            i++;
            g.add(col(row(icon("coin").size(14).color(YELLOW), text("<b>" + money(amount)).scale(1.15)).gap(5), text("<b>" + e.getString("prix", "?")).scale(1.5),
                    text(TM + (i == 1 ? "Pour bien démarrer" : i == 2 ? "Le plus populaire" : i == 3 ? "Pour fonder un pays" : "Pour les empires")), buy("Acheter"))
                    .bg(PANEL).border(i == 2 ? ACCENT : LINE).r(8).pad(10).gap(6));
        }
        return col(g, text(TM + "Les dollars sont crédités sur votre compte bancaire dès que la boutique confirme le paiement.").wrap(), flexSpace()).gap(8);
    }

    private UiNode spawners() {
        SpawnerManager sm = s.spawners();
        UiNode g = grid(3).gap(8);
        for (Map<String, Object> d : s.config().getMapList("boutique.spawners")) {
            Yaml e = new Yaml(d);
            String type = e.getString("type", "zombie");
            if (!sm.exists(type)) continue;
            g.add(row(row(item(sm.item(type, 1)).size(24)).bg(ROW).r(6).pad(4),
                    col(text("<b>" + Txt.esc(sm.display(type))), text("<b>" + e.getString("prix", "?")), text(TM + "Niveau 1 à 5")).gap(2).flex(1),
                    btn("", "url:" + s.store().url()).withIcon("cart").primary().w(24)).bg(PANEL).border(LINE).r(8).pad(8).gap(8));
        }
        UiNode levels = row().gap(6);
        for (int l = 1; l <= 5; l++) {
            SpawnerManager.Level lv = sm.info(l);
            levels.add(col(text("<b>Niveau " + l), text(T2 + "~" + lv.delaySeconds() + " s"), text(T2 + lv.count() + " mob(s)"), text(TM + (l == 1 ? "—" : money(lv.upgradeCost())))).bg(ROW).r(6).pad(6).gap(2).flex(1));
        }
        return list("spawners").add(g, card(sectionTitle("Niveaux d'amélioration (en jeu, payés en dollars)"), levels)).gap(8);
    }

    private UiNode perks(ServerPlayer p, Rank rank) {
        RankManager rm = s.ranks();
        RankManager.Entry e = rm.entry(p.getUUID());
        String until = rank == Rank.NONE ? "—" : e == null || e.expires() == 0 ? "permanent" : "jusqu'au " + fmt.format(new Date(e.expires()));
        UiNode rewards = col().gap(5);
        for (RankManager.Reward r : RankManager.Reward.values()) {
            long next = rm.nextClaim(p.getUUID(), r);
            String[] c = RankManager.content(rank, r);
            String what = !c[1].isEmpty() ? "1× LootBox " + s.loot().display(c[1]) : money(Double.parseDouble(c[0]));
            UiNode b = next < 0 ? btn("Grade requis", "noop").disabled(true)
                    : next == 0 ? btn("Récupérer", a("claim", r.name())).success().withIcon("check")
                    : btn("Dans " + Txt.duration((next - System.currentTimeMillis()) / 1000), "noop").disabled(true);
            rewards.add(row(icon(r == RankManager.Reward.DAILY ? "clock" : r == RankManager.Reward.WEEKLY ? "star" : "medal").size(12).color(TEXT2),
                    col(text("<b>" + r.display), text(T2 + (next < 0 ? "Réservé aux grades" : what))).gap(2).flex(1), b).bg(ROW).r(6).pad(6).gap(8));
        }
        UiNode hist = list("hist").gap(2).flex(1);
        for (String h : s.store().history(p.getUUID())) hist.add(text(h));
        if (hist.isEmpty()) hist.add(text(TM + "Aucun achat pour l'instant."));
        int pend = s.store().pendingCount(p);
        return row(col(card(row(icon("crown").size(16).color(rank.color), col(text("<b>" + (rank == Rank.NONE ? "Aucun grade" : rank.tag())).scale(1.3), text(T2 + "Validité : " + until)).gap(3).flex(1)).gap(8),
                                rank == Rank.NONE ? text(TM + "Découvrez les avantages des grades dans l'onglet Grades.").wrap() : null),
                        card(sectionTitle("Récompenses"), rewards)).gap(8).flex(1),
                card(sectionTitle("Mes achats"), pend > 0 ? text(TY + pend + " livraison(s) en attente") : null, hist).w(220)).gap(8);
    }

    @Override
    public void action(ServerPlayer p, UiSession ss, String act, String arg, Map<String, String> in) {
        switch (act) {
            case "tab" -> ss.set("tab", arg);
            case "open" -> s.loot().open(p, arg);
            case "claim" -> {
                try { s.ranks().claim(p, RankManager.Reward.valueOf(arg)); } catch (IllegalArgumentException ignored) {}
            }
            default -> {
            }
        }
    }
}

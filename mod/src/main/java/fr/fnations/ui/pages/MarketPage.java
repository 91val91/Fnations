package fr.fnations.ui.pages;

import fr.fnations.company.Company;
import fr.fnations.market.MarketCategories;
import fr.fnations.market.MarketManager;
import fr.fnations.nation.Nation;
import fr.fnations.nation.Role;
import fr.fnations.server.FnServer;
import fr.fnations.server.Msg;
import fr.fnations.ui.UiNode;
import fr.fnations.ui.UiPage;
import fr.fnations.ui.UiSession;
import fr.fnations.util.Txt;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static fr.fnations.ui.UiNode.*;
import static fr.fnations.ui.UiTheme.*;

/** Hôtel des ventes : ventes, offres d'achat, espace personnel, espace pays et entreprise. */
public final class MarketPage extends UiPage {
    public MarketPage(FnServer s) { super(s); }

    @Override
    public String id() { return "hdv"; }

    private MarketManager mk() { return s.market(); }

    private static String price(double v) { return "<b>" + Txt.money(v).replace(" $", "") + "</b> " + TG + "$"; }

    private static String qty(int n) { return n >= 1000 ? (n / 1000) + "k+" : String.valueOf(n); }

    private String nationOwner(ServerPlayer p) {
        Nation n = s.nations().of(p);
        return n != null && n.can(p.getUUID(), Role.OFFICER) ? MarketManager.nation(n) : null;
    }

    private String companyOwner(ServerPlayer p) {
        Company c = s.companies().of(p.getUUID());
        return c != null && c.type() == Company.Type.TRADING && c.can(p.getUUID(), Company.Rank.DIRECTEUR) ? c.owner() : null;
    }

    @Override
    public UiNode build(ServerPlayer p, UiSession ss) {
        String tab = ss.get("tab", "ventes");
        Company comp = s.companies().of(p.getUUID());
        UiNode tabs = row(
                icon("shop").size(14).color(ACCENT), text("<b>Hôtel des ventes").scale(1.25), space(10, 0),
                btn("Ventes", a("tab", "ventes")).withIcon("coin").style("tab").selected(tab.equals("ventes")),
                btn("Offres", a("tab", "offres")).withIcon("handshake").style("tab").selected(tab.equals("offres")),
                btn("Mon espace", a("tab", "espace")).withIcon("user").style("tab").selected(tab.equals("espace")),
                btn("Espace pays", a("tab", "pays")).withIcon("flag").style("tab").selected(tab.equals("pays")),
                comp != null && comp.type() == Company.Type.TRADING ? btn("Entreprise", a("tab", "entreprise")).withIcon("briefcase").style("tab").selected(tab.equals("entreprise")) : null,
                flexSpace()).gap(4).bg(PANEL).r(6).pad(6, 3);
        UiNode center = switch (tab) {
            case "offres" -> browse(p, ss, true);
            case "espace" -> ownerSpace(p, MarketManager.player(p.getUUID()), "Mon espace");
            case "pays" -> {
                Nation n = s.nations().of(p);
                yield n == null ? card(empty("Vous n'avez pas de nation."), flexSpace()).flex(1)
                        : ownerSpace(p, MarketManager.nation(n), "Espace de " + Txt.esc(n.name()));
            }
            case "entreprise" -> comp == null ? card(empty("Vous n'avez pas d'entreprise."), flexSpace()).flex(1) : ownerSpace(p, comp.owner(), "Espace de " + Txt.esc(comp.name()));
            default -> browse(p, ss, false);
        };
        boolean browsing = tab.equals("ventes") || tab.equals("offres");
        UiNode body = row(browsing ? sidebar(ss) : null, center.flex(1), rightColumn(p)).gap(6);
        return col(tabs, body.flex(1)).gap(6);
    }

    // ------------------------------------------------------------ colonne de gauche : recherche et catégories
    private UiNode sidebar(UiSession ss) {
        String cat = ss.get("cat", ""), sub = ss.get("sub", "");
        UiNode cats = list("cats").gap(1).flex(1);
        for (MarketCategories.Cat c : MarketCategories.CATS) {
            boolean on = c.id().equals(cat);
            cats.add(row(text(on ? TO + "<b>" + c.name() : c.name()).flex(1), icon(on ? "minus" : "plus").size(8).color(on ? ORANGE : TEXT2))
                    .pad(5, 5).act(a("cat", c.id())).r(4));
            if (on) for (MarketCategories.Sub sc : c.subs()) {
                boolean son = sc.id().equals(sub);
                cats.add(row(space(6, 0), text((son ? TO + "<b>" : T2) + sc.name()).flex(1)).pad(4, 3).act(a("sub", sc.id())).r(4).bg(son ? "#22F5A623" : null));
            }
            cats.add(sep());
        }
        return card(input("hdv_q").placeholder("Rechercher").withIcon("search"), cats,
                btn("Réinitialiser", a("reset")).withIcon("refresh").flag("fill")).w(108).pad(6).gap(5);
    }

    // ------------------------------------------------------------ centre : annonces ou offres groupées
    private UiNode browse(ServerPlayer p, UiSession ss, boolean orders) {
        String cat = ss.get("cat", ""), sub = ss.get("sub", ""), open = ss.get("open", "");
        List<MarketManager.Group> groups = orders ? mk().orderGroups() : mk().groups();
        UiNode list = list("groups").gap(4).filter("hdv_q").flex(1);
        int shown = 0;
        for (MarketManager.Group g : groups) {
            if (!cat.isEmpty() && !g.cat().equals(cat)) continue;
            if (!sub.isEmpty() && !g.sub().equals(sub)) continue;
            shown++;
            boolean on = g.key().equals(open);
            UiNode r = row(item(g.item()).size(16), col(itemName(g.item()).bold(), text(TM + MarketCategories.label(g.cat(), g.sub()))).gap(2).flex(1),
                    text(T2 + qty(g.qty())).w(30).center(), text(price(orders ? g.min() : g.avg())).w(58).right(),
                    icon(on ? "chevron_up" : "chevron_down").size(8).color(TEXT2))
                    .bg(ROW).r(5).pad(5, 4).gap(5).act(a("open", g.key())).keys(g.item().getItem().toString());
            list.add(on ? col(r, orders ? orderDetail(p, g) : listingDetail(p, g)).gap(0).keys(g.item().getItem().toString()) : r);
        }
        if (shown == 0) list.add(empty(orders ? "Aucune offre d'achat dans cette catégorie." : "Aucun objet en vente dans cette catégorie."));
        UiNode headerRow = row(space(16, 0), text(T2 + "Nom").flex(1), text(T2 + "Stock").w(30).center(),
                text(T2 + (orders ? "Meilleure" : "Prix moyen")).w(58).right(), space(8, 0)).pad(5, 0).gap(5);
        return card(headerRow, list, text(TM + shown + " résultat(s)")).pad(6).gap(5);
    }

    private UiNode listingDetail(ServerPlayer p, MarketManager.Group g) {
        UiNode d = col().bg(FIELD).r(5).pad(6).gap(4);
        List<Double> last = mk().lastSales(g.key());
        d.add(row(text(T2 + "Quantité"), input("qty").value("1").numeric().w(40).act(a("buybest", g.key())),
                btn("Acheter au meilleur prix", a("buybest", g.key())).primary().withIcon("cart").flex(1),
                btn("", a("offerdlg", g.key())).withIcon("handshake").w(22).tip("<b>Faire une offre d'achat", T2 + "Proposez votre prix aux vendeurs")).gap(5));
        d.add(text(TM + (last.isEmpty() ? "Aucune vente récente" : "Dernière vente : " + Txt.money(last.getFirst())
                + (last.size() > 1 ? " • moyenne des " + last.size() + " dernières : " + Txt.money(last.stream().mapToDouble(Double::doubleValue).average().orElse(0)) : ""))));
        int k = 0;
        for (MarketManager.Listing l : mk().group(g.key())) {
            if (k++ >= 8) { d.add(text(TM + "… et d'autres vendeurs")); break; }
            boolean mine = l.seller.equals(p.getUUID());
            String who = l.owner.startsWith("player:") ? Txt.esc(l.sellerName) : Txt.esc(mk().ownerName(l.owner)) + TM + " (" + Txt.esc(l.sellerName) + ")";
            d.add(row(head(l.seller, l.sellerName).size(12), text(who).flex(1), text(T2 + l.qty + " ×"), text(price(l.price) + TM + "/u").w(80).right(),
                    mine ? btn("Retirer", a("cancel", l.id)).w(58) : btn("Acheter", a("buy", l.id)).success().w(58)).gap(6).pad(2));
        }
        return d;
    }

    private UiNode orderDetail(ServerPlayer p, MarketManager.Group g) {
        UiNode d = col().bg(FIELD).r(5).pad(6).gap(4);
        int have = 0;
        for (ItemStack st : p.getInventory().items) if (ItemStack.isSameItemSameComponents(st, g.item())) have += st.getCount();
        d.add(row(text(T2 + "Quantité à vendre"), input("fqty").value(String.valueOf(Math.max(1, have))).numeric().w(40), text(TM + "Vous en avez " + have).flex(1),
                btn("", a("offerdlg", g.key())).withIcon("handshake").w(22).tip("<b>Faire une offre d'achat", T2 + "Pour acheter vous aussi cet objet")).gap(5));
        for (MarketManager.Order o : mk().orderGroup(g.key())) {
            boolean mine = o.buyer.equals(p.getUUID());
            d.add(row(head(o.buyer, o.buyerName).size(12), text(Txt.esc(o.owner.startsWith("player:") ? o.buyerName : mk().ownerName(o.owner))).flex(1),
                    text(T2 + "veut " + o.qty), text(price(o.price) + TM + "/u").w(80).right(),
                    mine ? btn("Annuler", a("cancelorder", o.id)).w(58) : btn("Vendre", a("fulfill", o.id)).success().w(58).disabled(have <= 0)).gap(6).pad(2));
        }
        return d;
    }

    // ------------------------------------------------------------ centre : espace (joueur, pays, entreprise)
    private UiNode ownerSpace(ServerPlayer p, String owner, String title) {
        boolean allowed = mk().canAct(p, owner);
        UiNode l = list("espace").gap(6).flex(1);
        List<MarketManager.Listing> mine = mk().listingsOf(owner);
        List<MarketManager.Order> ord = mk().ordersOf(owner);
        List<MarketManager.Mail> box = mk().mail(owner);
        int boxCount = box.stream().mapToInt(m -> m.count).sum();
        l.add(row(stat("coin", "Annonces", owner.startsWith("player:") ? mine.size() + " / " + s.companies().marketSlots(p.getUUID()) : String.valueOf(mine.size())).flex(1),
                stat("handshake", "Offres d'achat", String.valueOf(ord.size())).flex(1), stat("mail", "Boîte de réception", boxCount + " objet(s)").flex(1)).gap(6));
        if (!allowed && !owner.startsWith("player:")) l.add(text(TY + "Consultation seule : il faut être Ministre (pays) ou Directeur (entreprise) pour gérer cet espace.").wrap());
        if (!owner.startsWith("player:")) l.add(text(TM + "Pour vendre au nom de " + Txt.esc(mk().ownerName(owner)) + ", cochez l'option dans la fenêtre de vente (clic sur un objet de l'inventaire).").wrap());
        // annonces
        UiNode sec = col(sectionTitle("Annonces en ligne")).gap(3);
        for (MarketManager.Listing x : mine) {
            long left = Math.max(0, (x.expires - System.currentTimeMillis()) / 1000);
            sec.add(row(item(x.item).size(16), itemName(x.item).flex(1), text(T2 + x.qty + " ×"), text(price(x.price) + TM + "/u").w(80).right(),
                    text(TM + Txt.duration(left)).w(54).right(), allowed ? btn("Retirer", a("cancel", x.id)).w(54) : null).bg(ROW).r(5).pad(5).gap(6));
        }
        if (mine.isEmpty()) sec.add(text(TM + "Aucune annonce. Cliquez sur un objet de l'inventaire pour le vendre."));
        l.add(sec);
        // offres
        UiNode so = col(sectionTitle("Offres d'achat")).gap(3);
        for (MarketManager.Order o : ord)
            so.add(row(item(o.item).size(16), itemName(o.item).flex(1), text(T2 + o.qty + " voulus"), text(price(o.price) + TM + "/u").w(80).right(),
                    allowed ? btn("Annuler", a("cancelorder", o.id)).w(54) : null).bg(ROW).r(5).pad(5).gap(6));
        if (ord.isEmpty()) so.add(text(TM + "Aucune offre d'achat."));
        l.add(so);
        // boîte de réception
        UiNode items = grid(10).gap(3);
        for (MarketManager.Mail m : box) items.add(row(item(m.item).size(16).count(m.count)).bg(ROW).r(4).pad(2).h(20));
        UiNode sm = col(row(sectionTitle("Boîte de réception"), flexSpace(), allowed && !box.isEmpty() ? btn("Tout récupérer", a("collect", owner)).primary().withIcon("arrow_down") : null),
                box.isEmpty() ? text(TM + "Vide : les achats qui ne tiennent pas dans l'inventaire et les annonces expirées arrivent ici.").wrap() : items).gap(4);
        l.add(sm);
        // historique
        UiNode sh = col(sectionTitle("Historique")).gap(2);
        for (String h : mk().history(owner)) sh.add(text(h));
        if (mk().history(owner).isEmpty()) sh.add(text(TM + "Aucune opération."));
        l.add(sh);
        return card(text("<b>" + title).scale(1.25), l).pad(6);
    }

    // ------------------------------------------------------------ colonne de droite : inventaire et classement
    private UiNode rightColumn(ServerPlayer p) {
        int boxCount = mk().mail(MarketManager.player(p.getUUID())).stream().mapToInt(m -> m.count).sum();
        UiNode inv = card(row(text("<b>Inventaire"), flexSpace(), text(TM + "clic : vendre")),
                inv(a("pick"), 6).w(108).h(108),
                row(text(TG + "<b>" + money(s.bank().balance(p.getUUID()))), flexSpace(), row(icon("mail").size(8).color(TEXT2), text(T2 + boxCount)).gap(3)
                        .tip(T2 + "Objets dans votre boîte de réception").act(a("tab", "espace")))).pad(6).gap(5);
        UiNode top = list("weekly").gap(3).flex(1);
        int r = 1;
        for (Map.Entry<UUID, Double> e : mk().weeklyTop(5)) {
            String medal = r == 1 ? TY : r == 2 ? "<#C0C4CC>" : r == 3 ? "<#D08A4E>" : TM;
            top.add(row(text(medal + "<b>" + r).w(10), head(e.getKey(), s.name(e.getKey())).size(10), text(Txt.esc(s.name(e.getKey()))).flex(1),
                    text(TG + Txt.money(e.getValue()).replace(" $", "") + "$")).gap(4).pad(2));
            r++;
        }
        if (top.isEmpty()) top.add(text(TM + "Aucune vente cette semaine.").wrap());
        UiNode lead = card(row(row(icon("trophy").size(10).color("#1E1F2A")).bg(YELLOW).r(4).pad(3), text("<b>Classement de la semaine").wrap().flex(1)).gap(5), top).pad(6).gap(5).flex(1);
        return col(inv, lead).gap(6).w(120);
    }

    // ------------------------------------------------------------ dialogues
    private UiNode sellDialog(ServerPlayer p, int slot) {
        ItemStack st = p.getInventory().items.get(slot);
        int have = 0;
        for (ItemStack x : p.getInventory().items) if (ItemStack.isSameItemSameComponents(x, st)) have += x.getCount();
        double sug = mk().suggested(st);
        double tax = s.companies().marketTax(p.getUUID());
        String nat = nationOwner(p), co = companyOwner(p);
        return card(
                row(item(st).size(32), col(itemName(st).bold().wrap(), text(T2 + "Vous en avez " + have),
                        text(TM + MarketCategories.label(MarketCategories.classify(st)[0], MarketCategories.classify(st)[1]))).gap(3).flex(1)).gap(10),
                row(col(text(T2 + "Quantité"), input("sqty").value(String.valueOf(st.getCount())).numeric().force()).gap(3).flex(1),
                        col(text(T2 + "Prix unitaire ($)"), input("sprice").value(sug > 0 ? String.format(Locale.ROOT, "%.2f", sug) : "").placeholder("ex : 25").numeric().force()
                                .act(a("sell"))).gap(3).flex(1)).gap(8),
                text(TM + (sug > 0 ? "Prix conseillé : " + Txt.money(sug) + " • " : "") + "Taxe " + String.format(Locale.FRANCE, "%.1f", tax) + " % • "
                        + (int) s.config().getDouble("hdv.duree-jours", 7) + " jours en ligne").wrap(),
                nat != null ? check("fornation", "Vendre pour ma nation (trésor national)", false).force() : null,
                co != null ? check("forcompany", "Vendre pour mon entreprise (capital)", false).force() : null,
                row(btn("Annuler", "dialog:close"), flexSpace(), btn("Offre d'achat", a("orderslot")).withIcon("handshake")
                        .tip(T2 + "Acheter ce même objet aux autres joueurs", TM + "Quantité et prix = vos saisies"), btn("Mettre en vente", a("sell")).primary().withIcon("coin")).gap(6))
                .w(300).pad(12).gap(8);
    }

    private UiNode offerDialog(ItemStack st, String key) {
        double sug = mk().suggested(st);
        return card(
                row(item(st).size(32), col(text("<b>Offre d'achat"), itemName(st).color(TEXT2).wrap()).gap(3).flex(1)).gap(10),
                text(TM + "La somme totale est bloquée sur votre compte jusqu'à la livraison ou l'annulation.").wrap(),
                row(col(text(T2 + "Quantité voulue"), input("oqty").value("1").numeric().force()).gap(3).flex(1),
                        col(text(T2 + "Prix unitaire ($)"), input("oprice").value(sug > 0 ? String.format(Locale.ROOT, "%.2f", sug) : "").numeric().force().act(a("offer", key))).gap(3).flex(1)).gap(8),
                row(btn("Annuler", "dialog:close"), flexSpace(), btn("Publier l'offre", a("offer", key)).primary().withIcon("handshake")).gap(6))
                .w(280).pad(12).gap(8);
    }

    // ------------------------------------------------------------ actions
    @Override
    public void action(ServerPlayer p, UiSession ss, String act, String arg, Map<String, String> in) {
        switch (act) {
            case "tab" -> { ss.set("tab", arg); ss.set("open", null); }
            case "cat" -> {
                if (arg.equals(ss.get("cat"))) { ss.set("cat", null); ss.set("sub", null); }
                else { ss.set("cat", arg); ss.set("sub", null); }
            }
            case "sub" -> ss.set("sub", arg.equals(ss.get("sub")) ? null : arg);
            case "reset" -> { ss.set("cat", null); ss.set("sub", null); ss.set("open", null); }
            case "open" -> ss.set("open", arg.equals(ss.get("open")) ? null : arg);
            case "pick" -> {
                int slot = parseInt(arg);
                if (slot < 0 || slot >= p.getInventory().items.size() || p.getInventory().items.get(slot).isEmpty()) return;
                ss.set("slot", slot);
                ss.dialog = sellDialog(p, slot);
            }
            case "sell" -> {
                int slot = ss.getInt("slot", -1);
                Double pr = amount(in, "sprice");
                int q = intIn(in, "sqty", 1);
                if (slot < 0 || pr == null) { Msg.err(p, "Indiquez un prix valide."); return; }
                String owner = MarketManager.player(p.getUUID());
                if ("true".equals(in.get("fornation")) && nationOwner(p) != null) owner = nationOwner(p);
                else if ("true".equals(in.get("forcompany")) && companyOwner(p) != null) owner = companyOwner(p);
                if (mk().sell(p, slot, q, pr, owner)) ss.dialog = null;
            }
            case "orderslot" -> {
                int slot = ss.getInt("slot", -1);
                Double pr = amount(in, "sprice");
                int q = intIn(in, "sqty", 1);
                if (slot < 0 || slot >= p.getInventory().items.size() || pr == null) { Msg.err(p, "Indiquez un prix valide."); return; }
                if (mk().order(p, p.getInventory().items.get(slot), q, pr, MarketManager.player(p.getUUID()))) ss.dialog = null;
            }
            case "buybest" -> mk().buy(p, arg, 0, Math.max(1, intIn(in, "qty", 1)));
            case "buy" -> {
                long id = parseLong(arg);
                MarketManager.Listing l = mk().listing(id);
                if (l == null) { Msg.err(p, "Annonce introuvable."); return; }
                mk().buy(p, MarketManager.keyOf(l.item), id, Math.max(1, Math.min(l.qty, intIn(in, "qty", 1))));
            }
            case "offerdlg" -> {
                ItemStack st = templateFor(arg);
                if (st != null) ss.dialog = offerDialog(st, arg);
            }
            case "offer" -> {
                ItemStack st = templateFor(arg);
                Double pr = amount(in, "oprice");
                if (st == null || pr == null) { Msg.err(p, "Indiquez un prix valide."); return; }
                if (mk().order(p, st, Math.max(1, intIn(in, "oqty", 1)), pr, MarketManager.player(p.getUUID()))) ss.dialog = null;
            }
            case "fulfill" -> mk().fulfill(p, parseLong(arg), Math.max(1, intIn(in, "fqty", 1)));
            case "cancel" -> mk().cancel(p, parseLong(arg));
            case "cancelorder" -> mk().cancelOrder(p, parseLong(arg));
            case "collect" -> mk().collect(p, arg);
            default -> {
            }
        }
    }

    /** Modèle d'objet d'un groupe (annonces ou offres). */
    private ItemStack templateFor(String key) {
        for (MarketManager.Listing l : mk().listings()) if (MarketManager.keyOf(l.item).equals(key)) return l.item;
        for (MarketManager.Order o : mk().orders()) if (MarketManager.keyOf(o.item).equals(key)) return o.item;
        return null;
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return -1; }
    }

    private static long parseLong(String s) {
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return -1; }
    }
}

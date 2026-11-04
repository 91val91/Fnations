package fr.fnations.market;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import fr.fnations.FNations;
import fr.fnations.company.Company;
import fr.fnations.nation.Nation;
import fr.fnations.nation.Role;
import fr.fnations.server.FnServer;
import fr.fnations.server.Msg;
import fr.fnations.util.Txt;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hôtel des ventes : annonces de vente, offres d'achat, boîte de réception, historique et classement hebdomadaire.
 * Propriétaires : "player:uuid", "nation:id" ou "company:id". Données : world/fnations/hdv.json.
 */
public final class MarketManager {
    public static final class Listing {
        public final long id;
        public final String owner;
        public final UUID seller;
        public final String sellerName;
        public final ItemStack item;
        public int qty;
        public final double price;
        public final long created, expires;

        Listing(long id, String owner, UUID seller, String sellerName, ItemStack item, int qty, double price, long created, long expires) {
            this.id = id;
            this.owner = owner;
            this.seller = seller;
            this.sellerName = sellerName;
            this.item = item;
            this.qty = qty;
            this.price = price;
            this.created = created;
            this.expires = expires;
        }
    }

    public static final class Order {
        public final long id;
        public final String owner;
        public final UUID buyer;
        public final String buyerName;
        public final ItemStack item;
        public int qty;
        public final double price;
        public final long created, expires;

        Order(long id, String owner, UUID buyer, String buyerName, ItemStack item, int qty, double price, long created, long expires) {
            this.id = id;
            this.owner = owner;
            this.buyer = buyer;
            this.buyerName = buyerName;
            this.item = item;
            this.qty = qty;
            this.price = price;
            this.created = created;
            this.expires = expires;
        }
    }

    public static final class Mail {
        public final ItemStack item;
        public int count;

        Mail(ItemStack item, int count) {
            this.item = item;
            this.count = count;
        }
    }

    /** Regroupement d'annonces (ou d'offres) d'un même objet. */
    public record Group(String key, ItemStack item, int qty, double min, double avg, int count, String cat, String sub) {}

    private final FnServer s;
    private final Path file;
    private final Map<Long, Listing> listings = new LinkedHashMap<>();
    private final Map<Long, Order> orders = new LinkedHashMap<>();
    private final Map<String, List<Mail>> mail = new HashMap<>();
    private final Map<String, LinkedList<String>> history = new HashMap<>();
    private final Map<String, Deque<Double>> sales = new HashMap<>();
    private final Map<UUID, Double> weekly = new HashMap<>();
    private String week = "";
    private long nextId = 1;
    private final SimpleDateFormat fmt = new SimpleDateFormat("dd/MM HH:mm");

    public MarketManager(FnServer s) {
        this.s = s;
        this.file = s.dataDir().resolve("hdv.json");
        load();
    }

    // ------------------------------------------------------------ propriétaires
    public static String player(UUID u) { return "player:" + u; }

    public static String nation(Nation n) { return "nation:" + n.id(); }

    public String ownerName(String owner) {
        if (owner.startsWith("player:")) {
            try { return s.name(UUID.fromString(owner.substring(7))); } catch (Exception e) { return "?"; }
        }
        if (owner.startsWith("nation:")) {
            Nation n = s.nations().get(owner.substring(7));
            return n == null ? owner.substring(7) : n.name();
        }
        if (owner.startsWith("company:")) {
            Company c = s.companies().get(owner.substring(8));
            return c == null ? owner.substring(8) : c.name();
        }
        return owner;
    }

    /** Le joueur peut-il agir au nom de ce propriétaire ? */
    public boolean canAct(ServerPlayer p, String owner) {
        if (owner.equals(player(p.getUUID()))) return true;
        if (owner.startsWith("nation:")) {
            Nation n = s.nations().of(p);
            return n != null && owner.equals(nation(n)) && n.can(p.getUUID(), Role.OFFICER);
        }
        if (owner.startsWith("company:")) {
            Company c = s.companies().of(p.getUUID());
            return c != null && owner.equals(c.owner()) && c.type() == Company.Type.TRADING && c.can(p.getUUID(), Company.Rank.DIRECTEUR);
        }
        return false;
    }

    private void pay(String owner, double amount, String reason) {
        if (amount <= 0) return;
        if (owner.startsWith("player:")) s.bank().deposit(UUID.fromString(owner.substring(7)), amount, reason);
        else if (owner.startsWith("nation:")) {
            Nation n = s.nations().get(owner.substring(7));
            if (n != null) s.bank().nationDeposit(n, amount, reason);
        } else if (owner.startsWith("company:")) {
            Company c = s.companies().get(owner.substring(8));
            if (c != null) s.companies().deposit(c, amount, reason);
        }
    }

    private boolean charge(String owner, double amount, String reason) {
        if (amount <= 0) return true;
        if (owner.startsWith("player:")) return s.bank().withdraw(UUID.fromString(owner.substring(7)), amount, reason);
        if (owner.startsWith("nation:")) {
            Nation n = s.nations().get(owner.substring(7));
            return n != null && s.bank().nationWithdraw(n, amount, reason);
        }
        if (owner.startsWith("company:")) {
            Company c = s.companies().get(owner.substring(8));
            return c != null && s.companies().withdraw(c, amount, reason);
        }
        return false;
    }

    // ------------------------------------------------------------ requêtes
    public static String keyOf(ItemStack st) {
        String id = BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
        return st.getComponentsPatch().isEmpty() ? id : id + "#" + Integer.toHexString(ItemStack.hashItemAndComponents(st));
    }

    public List<Listing> listings() { return new ArrayList<>(listings.values()); }

    public List<Order> orders() { return new ArrayList<>(orders.values()); }

    public Listing listing(long id) { return listings.get(id); }

    public Order order(long id) { return orders.get(id); }

    public List<Listing> listingsOf(String owner) {
        return listings.values().stream().filter(l -> l.owner.equals(owner)).toList();
    }

    public List<Order> ordersOf(String owner) {
        return orders.values().stream().filter(o -> o.owner.equals(owner)).toList();
    }

    public List<Mail> mail(String owner) { return mail.getOrDefault(owner, List.of()); }

    public List<String> history(String owner) { return history.getOrDefault(owner, new LinkedList<>()); }

    public List<Listing> group(String key) {
        return listings.values().stream().filter(l -> keyOf(l.item).equals(key))
                .sorted(Comparator.comparingDouble((Listing l) -> l.price).thenComparingLong(l -> l.created)).toList();
    }

    public List<Order> orderGroup(String key) {
        return orders.values().stream().filter(o -> keyOf(o.item).equals(key))
                .sorted(Comparator.comparingDouble((Order o) -> -o.price).thenComparingLong(o -> o.created)).toList();
    }

    public List<Group> groups() {
        Map<String, List<Listing>> by = new LinkedHashMap<>();
        for (Listing l : listings.values()) by.computeIfAbsent(keyOf(l.item), k -> new ArrayList<>()).add(l);
        List<Group> out = new ArrayList<>();
        for (var e : by.entrySet()) {
            int q = 0;
            double sum = 0, min = Double.MAX_VALUE;
            for (Listing l : e.getValue()) {
                q += l.qty;
                sum += l.qty * l.price;
                min = Math.min(min, l.price);
            }
            ItemStack it = e.getValue().getFirst().item;
            String[] c = MarketCategories.classify(it);
            out.add(new Group(e.getKey(), it, q, min, q == 0 ? 0 : sum / q, e.getValue().size(), c[0], c[1]));
        }
        out.sort(Comparator.comparing((Group g) -> g.item().getHoverName().getString()));
        return out;
    }

    public List<Group> orderGroups() {
        Map<String, List<Order>> by = new LinkedHashMap<>();
        for (Order o : orders.values()) by.computeIfAbsent(keyOf(o.item), k -> new ArrayList<>()).add(o);
        List<Group> out = new ArrayList<>();
        for (var e : by.entrySet()) {
            int q = 0;
            double sum = 0, max = 0;
            for (Order o : e.getValue()) {
                q += o.qty;
                sum += o.qty * o.price;
                max = Math.max(max, o.price);
            }
            ItemStack it = e.getValue().getFirst().item;
            String[] c = MarketCategories.classify(it);
            out.add(new Group(e.getKey(), it, q, max, q == 0 ? 0 : sum / q, e.getValue().size(), c[0], c[1]));
        }
        out.sort(Comparator.comparing((Group g) -> g.item().getHoverName().getString()));
        return out;
    }

    /** Prix conseillé : moyenne des dernières ventes, sinon prix moyen des annonces. */
    public double suggested(ItemStack st) {
        Deque<Double> d = sales.get(keyOf(st));
        if (d != null && !d.isEmpty()) return Math.round(d.stream().mapToDouble(Double::doubleValue).average().orElse(0) * 100) / 100.0;
        for (Group g : groups()) if (g.key().equals(keyOf(st))) return Math.round(g.avg() * 100) / 100.0;
        return 0;
    }

    public List<Double> lastSales(String key) { return new ArrayList<>(sales.getOrDefault(key, new ArrayDeque<>())); }

    public List<Map.Entry<UUID, Double>> weeklyTop(int n) {
        rollWeek();
        List<Map.Entry<UUID, Double>> l = new ArrayList<>(weekly.entrySet());
        l.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        return l.size() > n ? l.subList(0, n) : l;
    }

    private void rollWeek() {
        LocalDate d = LocalDate.now();
        String w = d.get(IsoFields.WEEK_BASED_YEAR) + "-" + d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        if (!w.equals(week)) {
            week = w;
            weekly.clear();
        }
    }

    public double tax(ServerPlayer seller) { return s.companies().marketTax(seller.getUUID()) / 100.0; }

    public long duration() { return (long) (s.config().getDouble("hdv.duree-jours", 7) * 86_400_000L); }

    // ------------------------------------------------------------ inventaire
    private static int count(ServerPlayer p, ItemStack ref) {
        int n = 0;
        for (ItemStack st : p.getInventory().items) if (ItemStack.isSameItemSameComponents(st, ref)) n += st.getCount();
        return n;
    }

    private static void take(ServerPlayer p, ItemStack ref, int amount, int firstSlot) {
        var items = p.getInventory().items;
        if (firstSlot >= 0 && firstSlot < items.size() && ItemStack.isSameItemSameComponents(items.get(firstSlot), ref)) {
            int t = Math.min(amount, items.get(firstSlot).getCount());
            items.get(firstSlot).shrink(t);
            amount -= t;
        }
        for (int i = 0; i < items.size() && amount > 0; i++) {
            ItemStack st = items.get(i);
            if (!ItemStack.isSameItemSameComponents(st, ref)) continue;
            int t = Math.min(amount, st.getCount());
            st.shrink(t);
            amount -= t;
        }
        p.getInventory().setChanged();
    }

    /** Donne des objets au joueur ; le surplus part dans sa boîte de réception. */
    private void deliver(ServerPlayer p, String owner, ItemStack template, int count) {
        if (p != null && owner.equals(player(p.getUUID()))) {
            int max = template.getMaxStackSize();
            while (count > 0) {
                int n = Math.min(max, count);
                ItemStack st = template.copyWithCount(n);
                if (!p.getInventory().add(st)) {
                    int left = st.getCount() + count - n;
                    addMail(owner, template, left);
                    return;
                }
                count -= n;
            }
            return;
        }
        addMail(owner, template, count);
    }

    private void addMail(String owner, ItemStack template, int count) {
        if (count <= 0) return;
        List<Mail> box = mail.computeIfAbsent(owner, k -> new ArrayList<>());
        for (Mail m : box) if (ItemStack.isSameItemSameComponents(m.item, template)) { m.count += count; return; }
        box.add(new Mail(template.copyWithCount(1), count));
    }

    private void log(String owner, String line) {
        LinkedList<String> l = history.computeIfAbsent(owner, k -> new LinkedList<>());
        l.addFirst("<#6C6F7E>" + fmt.format(new Date()) + " " + line);
        while (l.size() > 30) l.removeLast();
    }

    private static String itemName(ItemStack st) {
        return st.getComponentsPatch().isEmpty() ? "<item:" + BuiltInRegistries.ITEM.getKey(st.getItem()) + ">" : Txt.esc(st.getHoverName().getString());
    }

    // ------------------------------------------------------------ actions
    /** Met en vente qty objets identiques à ceux de la case slot. */
    public boolean sell(ServerPlayer p, int slot, int qty, double price, String owner) {
        if (!canAct(p, owner)) { Msg.err(p, "Vous ne pouvez pas vendre au nom de " + ownerName(owner) + "."); return false; }
        if (slot < 0 || slot >= p.getInventory().items.size()) return false;
        ItemStack ref = p.getInventory().items.get(slot);
        if (ref.isEmpty()) { Msg.err(p, "Case vide."); return false; }
        if (price <= 0 || price > 1e9) { Msg.err(p, "Prix invalide."); return false; }
        int have = count(p, ref);
        qty = Math.min(qty, have);
        if (qty <= 0) { Msg.err(p, "Quantité invalide."); return false; }
        long mine = listings.values().stream().filter(l -> l.seller.equals(p.getUUID())).count();
        int max = s.companies().marketSlots(p.getUUID());
        if (mine >= max) { Msg.err(p, "Vous avez déjà " + max + " annonces en ligne."); return false; }
        ItemStack template = ref.copyWithCount(1);
        take(p, template, qty, slot);
        long now = System.currentTimeMillis();
        Listing l = new Listing(nextId++, owner, p.getUUID(), p.getScoreboardName(), template, qty, Math.round(price * 100) / 100.0, now, now + duration());
        listings.put(l.id, l);
        log(owner, "<#8C96FF>Mise en vente <white>" + qty + "× " + itemName(template) + " <gray>à " + Txt.money(l.price) + "/u");
        Msg.ok(p, "Annonce publiée : " + qty + "× " + itemName(template) + " à " + Txt.money(l.price) + " l'unité.");
        return true;
    }

    /** Achat au meilleur prix dans un groupe (ou dans une annonce précise si listingId > 0). */
    public void buy(ServerPlayer p, String key, long listingId, int qty) {
        if (qty <= 0) { Msg.err(p, "Quantité invalide."); return; }
        List<Listing> pool = listingId > 0 ? (listings.containsKey(listingId) ? List.of(listings.get(listingId)) : List.of()) : group(key);
        int bought = 0;
        double spent = 0;
        ItemStack got = null;
        for (Listing l : pool) {
            if (qty <= 0) break;
            if (l.seller.equals(p.getUUID()) && l.owner.equals(player(p.getUUID()))) continue;
            int n = Math.min(qty, l.qty);
            double bal = s.bank().balance(p.getUUID());
            if (bal < n * l.price) n = (int) Math.floor(bal / l.price);
            if (n <= 0) break;
            double cost = Math.round(n * l.price * 100) / 100.0;
            if (!s.bank().withdraw(p.getUUID(), cost, "Achat HDV : " + n + "× " + Txt.plain(itemName(l.item)))) break;
            double sellerTax = s.companies().marketTax(l.seller) / 100.0;
            double net = Math.round(cost * (1 - sellerTax) * 100) / 100.0;
            pay(l.owner, net, "Vente HDV : " + n + "× " + Txt.plain(itemName(l.item)));
            deliver(p, player(p.getUUID()), l.item, n);
            l.qty -= n;
            qty -= n;
            bought += n;
            spent += cost;
            got = l.item;
            recordSale(l.item, l.price, n, l.seller, cost, l.owner);
            log(l.owner, "<#43D17A>Vendu <white>" + n + "× " + itemName(l.item) + " <gray>à " + Txt.esc(p.getScoreboardName()) + " <#43D17A>+" + Txt.money(net));
            ServerPlayer sp = s.player(l.seller);
            if (sp != null) Msg.ok(sp, "HDV : " + Txt.esc(p.getScoreboardName()) + " a acheté " + n + "× " + itemName(l.item) + " (+" + Txt.money(net) + ").");
            if (l.qty <= 0) listings.remove(l.id);
        }
        if (bought == 0) { Msg.err(p, "Achat impossible (fonds insuffisants ou plus de stock)."); return; }
        log(player(p.getUUID()), "<#F5A623>Acheté <white>" + bought + "× " + itemName(got) + " <#F04747>-" + Txt.money(spent));
        Msg.ok(p, "Acheté : " + bought + "× " + itemName(got) + " pour " + Txt.money(spent) + ".");
        Msg.sound(p, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
    }

    private void recordSale(ItemStack item, double price, int n, UUID seller, double cost, String owner) {
        Deque<Double> d = sales.computeIfAbsent(keyOf(item), k -> new ArrayDeque<>());
        d.addFirst(price);
        while (d.size() > 10) d.removeLast();
        rollWeek();
        weekly.merge(seller, cost, Double::sum);
        Company c = s.companies().of(seller);
        if (c != null && c.type() == Company.Type.TRADING) s.companies().addXp(c, cost / 10.0);
    }

    public void cancel(ServerPlayer p, long id) {
        Listing l = listings.get(id);
        if (l == null) return;
        if (!canAct(p, l.owner) && !l.seller.equals(p.getUUID())) { Msg.err(p, "Ce n'est pas votre annonce."); return; }
        listings.remove(id);
        deliver(p, l.owner, l.item, l.qty);
        log(l.owner, "<#6C6F7E>Annonce retirée : " + l.qty + "× " + itemName(l.item));
        Msg.ok(p, "Annonce retirée, objets rendus" + (l.owner.equals(player(p.getUUID())) ? "." : " dans la boîte de réception."));
    }

    /** Offre d'achat : la somme est bloquée jusqu'à la livraison ou l'annulation. */
    public boolean order(ServerPlayer p, ItemStack template, int qty, double price, String owner) {
        if (!canAct(p, owner)) { Msg.err(p, "Vous ne pouvez pas acheter au nom de " + ownerName(owner) + "."); return false; }
        if (template.isEmpty() || qty <= 0 || qty > 100000 || price <= 0 || price > 1e9) { Msg.err(p, "Offre invalide."); return false; }
        double total = Math.round(qty * price * 100) / 100.0;
        if (!charge(owner, total, "Offre d'achat HDV : " + qty + "× " + Txt.plain(itemName(template)))) {
            Msg.err(p, "Fonds insuffisants pour bloquer " + Txt.money(total) + ".");
            return false;
        }
        long now = System.currentTimeMillis();
        Order o = new Order(nextId++, owner, p.getUUID(), p.getScoreboardName(), template.copyWithCount(1), qty, Math.round(price * 100) / 100.0, now, now + duration());
        orders.put(o.id, o);
        log(owner, "<#8C96FF>Offre d'achat <white>" + qty + "× " + itemName(template) + " <gray>à " + Txt.money(o.price) + "/u");
        Msg.ok(p, "Offre publiée : " + qty + "× " + itemName(template) + " à " + Txt.money(o.price) + " (" + Txt.money(total) + " bloqués).");
        return true;
    }

    /** Vendre ses objets à une offre d'achat. */
    public void fulfill(ServerPlayer p, long id, int qty) {
        Order o = orders.get(id);
        if (o == null) { Msg.err(p, "Offre introuvable."); return; }
        if (o.buyer.equals(p.getUUID()) && o.owner.equals(player(p.getUUID()))) { Msg.err(p, "C'est votre propre offre."); return; }
        int n = Math.min(Math.min(qty <= 0 ? o.qty : qty, o.qty), count(p, o.item));
        if (n <= 0) { Msg.err(p, "Vous n'avez pas cet objet."); return; }
        take(p, o.item, n, -1);
        double gross = Math.round(n * o.price * 100) / 100.0;
        double net = Math.round(gross * (1 - tax(p)) * 100) / 100.0;
        s.bank().deposit(p.getUUID(), net, "Vente HDV (offre) : " + n + "× " + Txt.plain(itemName(o.item)));
        deliver(null, o.owner, o.item, n);
        o.qty -= n;
        if (o.qty <= 0) orders.remove(o.id);
        recordSale(o.item, o.price, n, p.getUUID(), gross, player(p.getUUID()));
        log(player(p.getUUID()), "<#43D17A>Vendu (offre) <white>" + n + "× " + itemName(o.item) + " <#43D17A>+" + Txt.money(net));
        log(o.owner, "<#F5A623>Reçu <white>" + n + "× " + itemName(o.item) + " <gray>de " + Txt.esc(p.getScoreboardName()));
        Msg.ok(p, "Vendu : " + n + "× " + itemName(o.item) + " pour " + Txt.money(net) + ".");
        ServerPlayer b = s.player(o.buyer);
        if (b != null) Msg.ok(b, "HDV : " + n + "× " + itemName(o.item) + " livrés dans la boîte de réception.");
    }

    public void cancelOrder(ServerPlayer p, long id) {
        Order o = orders.get(id);
        if (o == null) return;
        if (!canAct(p, o.owner) && !o.buyer.equals(p.getUUID())) { Msg.err(p, "Ce n'est pas votre offre."); return; }
        orders.remove(id);
        double refund = Math.round(o.qty * o.price * 100) / 100.0;
        pay(o.owner, refund, "Annulation d'offre HDV");
        log(o.owner, "<#6C6F7E>Offre annulée, " + Txt.money(refund) + " rendus");
        Msg.ok(p, "Offre annulée : " + Txt.money(refund) + " rendus.");
    }

    /** Récupère la boîte de réception dans l'inventaire du joueur. */
    public void collect(ServerPlayer p, String owner) {
        if (!canAct(p, owner)) { Msg.err(p, "Accès refusé."); return; }
        List<Mail> box = mail.get(owner);
        if (box == null || box.isEmpty()) { Msg.err(p, "Boîte de réception vide."); return; }
        int moved = 0;
        for (var it = box.iterator(); it.hasNext(); ) {
            Mail m = it.next();
            int max = m.item.getMaxStackSize();
            while (m.count > 0) {
                int n = Math.min(max, m.count);
                ItemStack st = m.item.copyWithCount(n);
                boolean ok = p.getInventory().add(st);
                int given = n - st.getCount();
                m.count -= given;
                moved += given;
                if (!ok || given < n) break;
            }
            if (m.count <= 0) it.remove();
            else break;
        }
        if (box.isEmpty()) mail.remove(owner);
        if (moved > 0) Msg.ok(p, moved + " objet(s) récupéré(s)" + (mail.containsKey(owner) ? " (inventaire plein, le reste vous attend)." : "."));
        else Msg.err(p, "Inventaire plein.");
    }

    /** Une entreprise dissoute : ses annonces, offres et objets reviennent au joueur. */
    public void onOwnerRemoved(String owner, ServerPlayer heir) {
        String to = player(heir.getUUID());
        for (Listing l : new ArrayList<>(listings.values())) if (l.owner.equals(owner)) { listings.remove(l.id); addMail(to, l.item, l.qty); }
        for (Order o : new ArrayList<>(orders.values())) if (o.owner.equals(owner)) { orders.remove(o.id); s.bank().deposit(heir.getUUID(), o.qty * o.price, "Offre HDV annulée"); }
        List<Mail> box = mail.remove(owner);
        if (box != null) for (Mail m : box) addMail(to, m.item, m.count);
    }

    /** Expiration des annonces et des offres (appelé chaque minute). */
    public void tick() {
        long now = System.currentTimeMillis();
        for (Listing l : new ArrayList<>(listings.values())) {
            if (l.expires > now) continue;
            listings.remove(l.id);
            addMail(l.owner, l.item, l.qty);
            log(l.owner, "<#6C6F7E>Annonce expirée : " + l.qty + "× " + itemName(l.item) + " (boîte de réception)");
        }
        for (Order o : new ArrayList<>(orders.values())) {
            if (o.expires > now) continue;
            orders.remove(o.id);
            pay(o.owner, o.qty * o.price, "Offre HDV expirée");
            log(o.owner, "<#6C6F7E>Offre expirée : " + Txt.money(o.qty * o.price) + " rendus");
        }
    }

    // ------------------------------------------------------------ persistance
    private JsonElement enc(ItemStack st) {
        return ItemStack.CODEC.encodeStart(s.server().registryAccess().createSerializationContext(JsonOps.INSTANCE), st.copyWithCount(1))
                .result().orElse(null);
    }

    private ItemStack dec(JsonElement e) {
        if (e == null) return ItemStack.EMPTY;
        return ItemStack.CODEC.parse(s.server().registryAccess().createSerializationContext(JsonOps.INSTANCE), e).result().orElse(ItemStack.EMPTY);
    }

    private void load() {
        if (!Files.exists(file)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            nextId = root.has("next") ? root.get("next").getAsLong() : 1;
            week = root.has("week") ? root.get("week").getAsString() : "";
            for (JsonElement e : arr(root, "listings")) {
                JsonObject o = e.getAsJsonObject();
                ItemStack it = dec(o.get("item"));
                if (it.isEmpty()) continue;
                Listing l = new Listing(o.get("id").getAsLong(), o.get("owner").getAsString(), UUID.fromString(o.get("seller").getAsString()),
                        o.get("sellerName").getAsString(), it, o.get("qty").getAsInt(), o.get("price").getAsDouble(), o.get("created").getAsLong(), o.get("expires").getAsLong());
                listings.put(l.id, l);
            }
            for (JsonElement e : arr(root, "orders")) {
                JsonObject o = e.getAsJsonObject();
                ItemStack it = dec(o.get("item"));
                if (it.isEmpty()) continue;
                Order ord = new Order(o.get("id").getAsLong(), o.get("owner").getAsString(), UUID.fromString(o.get("buyer").getAsString()),
                        o.get("buyerName").getAsString(), it, o.get("qty").getAsInt(), o.get("price").getAsDouble(), o.get("created").getAsLong(), o.get("expires").getAsLong());
                orders.put(ord.id, ord);
            }
            if (root.has("mail")) for (var e : root.getAsJsonObject("mail").entrySet()) {
                List<Mail> box = new ArrayList<>();
                for (JsonElement m : e.getValue().getAsJsonArray()) {
                    ItemStack it = dec(m.getAsJsonObject().get("item"));
                    if (!it.isEmpty()) box.add(new Mail(it, m.getAsJsonObject().get("count").getAsInt()));
                }
                if (!box.isEmpty()) mail.put(e.getKey(), box);
            }
            if (root.has("history")) for (var e : root.getAsJsonObject("history").entrySet()) {
                LinkedList<String> l = new LinkedList<>();
                for (JsonElement x : e.getValue().getAsJsonArray()) l.add(x.getAsString());
                history.put(e.getKey(), l);
            }
            if (root.has("sales")) for (var e : root.getAsJsonObject("sales").entrySet()) {
                Deque<Double> d = new ArrayDeque<>();
                for (JsonElement x : e.getValue().getAsJsonArray()) d.add(x.getAsDouble());
                sales.put(e.getKey(), d);
            }
            if (root.has("weekly")) for (var e : root.getAsJsonObject("weekly").entrySet()) weekly.put(UUID.fromString(e.getKey()), e.getValue().getAsDouble());
        } catch (Exception e) {
            FNations.LOG.error("HDV : impossible de lire {}", file, e);
        }
    }

    private static JsonArray arr(JsonObject o, String k) { return o.has(k) ? o.getAsJsonArray(k) : new JsonArray(); }

    public void save() {
        JsonObject root = new JsonObject();
        root.addProperty("next", nextId);
        root.addProperty("week", week);
        JsonArray la = new JsonArray();
        for (Listing l : listings.values()) {
            JsonElement it = enc(l.item);
            if (it == null) continue;
            JsonObject o = new JsonObject();
            o.addProperty("id", l.id);
            o.addProperty("owner", l.owner);
            o.addProperty("seller", l.seller.toString());
            o.addProperty("sellerName", l.sellerName);
            o.add("item", it);
            o.addProperty("qty", l.qty);
            o.addProperty("price", l.price);
            o.addProperty("created", l.created);
            o.addProperty("expires", l.expires);
            la.add(o);
        }
        root.add("listings", la);
        JsonArray oa = new JsonArray();
        for (Order ord : orders.values()) {
            JsonElement it = enc(ord.item);
            if (it == null) continue;
            JsonObject o = new JsonObject();
            o.addProperty("id", ord.id);
            o.addProperty("owner", ord.owner);
            o.addProperty("buyer", ord.buyer.toString());
            o.addProperty("buyerName", ord.buyerName);
            o.add("item", it);
            o.addProperty("qty", ord.qty);
            o.addProperty("price", ord.price);
            o.addProperty("created", ord.created);
            o.addProperty("expires", ord.expires);
            oa.add(o);
        }
        root.add("orders", oa);
        JsonObject mo = new JsonObject();
        mail.forEach((k, box) -> {
            JsonArray a = new JsonArray();
            for (Mail m : box) {
                JsonElement it = enc(m.item);
                if (it == null) continue;
                JsonObject o = new JsonObject();
                o.add("item", it);
                o.addProperty("count", m.count);
                a.add(o);
            }
            mo.add(k, a);
        });
        root.add("mail", mo);
        JsonObject ho = new JsonObject();
        history.forEach((k, l) -> {
            JsonArray a = new JsonArray();
            l.forEach(a::add);
            ho.add(k, a);
        });
        root.add("history", ho);
        JsonObject so = new JsonObject();
        sales.forEach((k, d) -> {
            JsonArray a = new JsonArray();
            d.forEach(a::add);
            so.add(k, a);
        });
        root.add("sales", so);
        JsonObject wo = new JsonObject();
        weekly.forEach((k, v) -> wo.addProperty(k.toString(), v));
        root.add("weekly", wo);
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling("hdv.json.tmp");
            Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
            Files.writeString(tmp, gson.toJson(root), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            FNations.LOG.error("HDV : impossible d'écrire {}", file, e);
        }
    }
}

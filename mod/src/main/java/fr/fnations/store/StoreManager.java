package fr.fnations.store;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.fnations.FNations;
import fr.fnations.rank.Rank;
import fr.fnations.server.FnServer;
import fr.fnations.server.Msg;
import fr.fnations.util.Store;
import fr.fnations.util.Txt;
import fr.fnations.util.Yaml;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Boutique en ligne : livraison des achats (grades, LootBoxs, dollars, spawners).
 * La boutique web (Tebex ou tout site capable de lancer une commande serveur) exécute
 * « fnstore <type> <joueur> ... » ; si le joueur est hors ligne, l'achat lui est livré à sa connexion.
 * Avec une clé secrète Tebex, le serveur interroge lui-même l'API « plugin » de Tebex.
 */
public final class StoreManager {
    private final FnServer s;
    private final Store store;
    /** Livraisons en attente : clé = uuid ou nom en minuscules. */
    private final Map<String, List<String>> pending = new HashMap<>();
    private final Map<UUID, LinkedList<String>> history = new HashMap<>();
    private final SimpleDateFormat fmt = new SimpleDateFormat("dd/MM HH:mm");
    private HttpClient http;
    private long nextPoll;
    private volatile boolean polling;

    public StoreManager(FnServer s) {
        this.s = s;
        this.store = new Store(s.dataDir(), "boutique.yml");
        load();
    }

    public String url() { return s.config().getString("boutique.url", "https://fnations.tebex.io"); }

    private String secret() { return s.config().getString("boutique.tebex-cle-secrete", ""); }

    // ------------------------------------------------------------ livraison
    /** Commande de livraison : type + arguments (ex. "grade ultra 30", "lootbox legendaire 2", "dollars 50000", "spawner blaze 1 1"). */
    public String deliver(String player, String spec) {
        String[] a = spec.trim().split("\\s+");
        if (a.length == 0 || a[0].isEmpty()) return "Livraison vide.";
        String err = validate(a);
        if (err != null) return err;
        UUID u = s.uuidByName(player);
        ServerPlayer p = u == null ? s.playerByName(player) : s.player(u);
        if (p == null) {
            String key = u != null ? u.toString() : player.toLowerCase();
            pending.computeIfAbsent(key, k -> new ArrayList<>()).add(String.join(" ", a));
            FNations.LOG.info("Boutique : livraison en attente pour {} : {}", player, String.join(" ", a));
            return null;
        }
        apply(p, a);
        return null;
    }

    private String validate(String[] a) {
        switch (a[0].toLowerCase()) {
            case "grade" -> {
                if (a.length < 2 || Rank.byId(a[1]) == null || Rank.byId(a[1]) == Rank.NONE) return "Grade inconnu (premium, super_premium, ultra).";
            }
            case "lootbox" -> {
                if (a.length < 2 || !s.loot().exists(a[1])) return "LootBox inconnue (classic, legendaire, ultra).";
            }
            case "dollars" -> {
                if (a.length < 2 || Txt.parseAmount(a[1]) == null) return "Montant invalide.";
            }
            case "spawner" -> {
                if (a.length < 2 || !s.spawners().exists(a[1])) return "Type de spawner inconnu.";
            }
            default -> {
                return "Type de livraison inconnu : " + a[0];
            }
        }
        return null;
    }

    private static int intArg(String[] a, int i, int def) {
        try { return a.length > i ? Integer.parseInt(a[i]) : def; } catch (NumberFormatException e) { return def; }
    }

    private void apply(ServerPlayer p, String[] a) {
        String label;
        switch (a[0].toLowerCase()) {
            case "grade" -> {
                Rank r = Rank.byId(a[1]);
                int days = intArg(a, 2, 0);
                s.ranks().grant(p.getUUID(), p.getScoreboardName(), r, days);
                label = "Grade " + r.display + (days > 0 ? " (" + days + " jours)" : " (permanent)");
            }
            case "lootbox" -> {
                int n = Math.max(1, intArg(a, 2, 1));
                s.loot().give(p, a[1], n);
                label = n + "× LootBox " + s.loot().display(a[1]);
            }
            case "dollars" -> {
                double v = Txt.parseAmount(a[1]);
                s.bank().deposit(p.getUUID(), v, "Achat boutique");
                label = Txt.money(v);
            }
            case "spawner" -> {
                int level = Math.max(1, intArg(a, 2, 1)), n = Math.max(1, intArg(a, 3, 1));
                for (int i = 0; i < n; i++) fr.fnations.util.Inv.give(p, s.spawners().item(a[1], level));
                label = n + "× Spawner " + s.spawners().display(a[1]) + " niveau " + level;
            }
            default -> label = String.join(" ", a);
        }
        LinkedList<String> h = history.computeIfAbsent(p.getUUID(), k -> new LinkedList<>());
        h.addFirst("<#6C6F7E>" + fmt.format(new Date()) + " <white>" + label);
        while (h.size() > 30) h.removeLast();
        Msg.send(p, "<#8C96FF><b>Boutique :</b> <white>" + label + " <green>livré. Merci pour votre achat !");
        Msg.sound(p, SoundEvents.PLAYER_LEVELUP, 1f, 1.4f);
    }

    public void onJoin(ServerPlayer p) {
        List<String> list = new ArrayList<>();
        List<String> a = pending.remove(p.getUUID().toString());
        if (a != null) list.addAll(a);
        List<String> b = pending.remove(p.getScoreboardName().toLowerCase());
        if (b != null) list.addAll(b);
        for (String spec : list) apply(p, spec.split("\\s+"));
    }

    public List<String> history(UUID u) { return history.getOrDefault(u, new LinkedList<>()); }

    public int pendingCount(ServerPlayer p) {
        return pending.getOrDefault(p.getUUID().toString(), List.of()).size() + pending.getOrDefault(p.getScoreboardName().toLowerCase(), List.of()).size();
    }

    // ------------------------------------------------------------ Tebex (API « plugin »)
    /** Appelé chaque seconde : interroge Tebex si une clé secrète est configurée. */
    public void tick() {
        if (secret().isBlank() || polling || System.currentTimeMillis() < nextPoll) return;
        polling = true;
        nextPoll = System.currentTimeMillis() + Math.max(30, s.config().getInt("boutique.tebex-intervalle-secondes", 60)) * 1000L;
        if (http == null) http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        // joueurs connectés (relevés ici, sur le fil du serveur) : seules leurs commandes « en ligne » sont exécutées
        java.util.Set<String> online = new java.util.HashSet<>();
        for (ServerPlayer p : s.server().getPlayerList().getPlayers()) {
            online.add(p.getScoreboardName().toLowerCase(java.util.Locale.ROOT));
            online.add(p.getUUID().toString().replace("-", ""));
        }
        Thread.ofVirtual().name("fnations-tebex").start(() -> poll(online));
    }

    /** Commandes Tebex exécutées dont la confirmation n'a pas encore été acceptée (évite une double livraison). */
    private final java.util.Set<Long> executed = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private JsonObject get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://plugin.tebex.io" + path)).header("X-Tebex-Secret", secret())
                .header("User-Agent", "FNations").timeout(Duration.ofSeconds(15)).GET().build();
        HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() / 100 != 2) throw new IllegalStateException("Tebex " + path + " : HTTP " + r.statusCode());
        return JsonParser.parseString(r.body()).getAsJsonObject();
    }

    private void poll(java.util.Set<String> online) {
        try {
            JsonObject queue = get("/queue");
            JsonObject meta = queue.getAsJsonObject("meta");
            if (meta != null && meta.has("next_check"))
                nextPoll = System.currentTimeMillis() + Math.max(30, meta.get("next_check").getAsInt()) * 1000L;
            List<Long> done = new ArrayList<>();
            List<String[]> toRun = new ArrayList<>();
            if (meta != null && meta.has("execute_offline") && meta.get("execute_offline").getAsBoolean()) {
                JsonObject off = get("/queue/offline-commands");
                for (JsonElement e : arr(off, "commands")) {
                    JsonObject c = e.getAsJsonObject();
                    JsonObject pl = c.getAsJsonObject("player");
                    long id = c.get("id").getAsLong();
                    if (executed.add(id)) toRun.add(new String[]{c.get("command").getAsString(), pl.get("name").getAsString(), str(pl, "uuid")});
                    done.add(id);
                }
            }
            for (JsonElement e : arr(queue, "players")) {
                JsonObject pl = e.getAsJsonObject();
                String name = str(pl, "name"), uuid = str(pl, "uuid").replace("-", "").toLowerCase(java.util.Locale.ROOT);
                // commande « en ligne » : elle attend que le joueur soit connecté (Tebex la garde en file)
                if (!online.contains(name.toLowerCase(java.util.Locale.ROOT)) && !online.contains(uuid)) continue;
                JsonObject on = get("/queue/online-commands/" + pl.get("id").getAsString());
                for (JsonElement ce : arr(on, "commands")) {
                    JsonObject c = ce.getAsJsonObject();
                    long id = c.get("id").getAsLong();
                    if (executed.add(id)) toRun.add(new String[]{c.get("command").getAsString(), name, str(pl, "uuid")});
                    done.add(id);
                }
            }
            if (!toRun.isEmpty()) s.server().execute(() -> toRun.forEach(c -> run(c[0], c[1], c[2])));
            if (!done.isEmpty()) {
                StringBuilder q = new StringBuilder();
                for (Long id : done) q.append(q.isEmpty() ? "?" : "&").append("ids[]=").append(id);
                HttpRequest del = HttpRequest.newBuilder(URI.create("https://plugin.tebex.io/queue" + q)).header("X-Tebex-Secret", secret())
                        .header("User-Agent", "FNations").timeout(Duration.ofSeconds(15)).DELETE().build();
                HttpResponse<Void> dr = http.send(del, HttpResponse.BodyHandlers.discarding());
                if (dr.statusCode() / 100 == 2) {
                    executed.removeAll(done);
                    FNations.LOG.info("Boutique Tebex : {} commande(s) exécutée(s)", done.size());
                } else {
                    // les commandes déjà exécutées ne le seront pas une seconde fois ; la confirmation sera renvoyée
                    FNations.LOG.warn("Boutique Tebex : confirmation refusée (HTTP {}), nouvel essai au prochain passage", dr.statusCode());
                }
            }
        } catch (Exception ex) {
            FNations.LOG.warn("Boutique Tebex : {}", ex.getMessage());
        } finally {
            polling = false;
        }
    }

    private static JsonArray arr(JsonObject o, String k) { return o != null && o.has(k) && o.get(k).isJsonArray() ? o.getAsJsonArray(k) : new JsonArray(); }

    private static String str(JsonObject o, String k) { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : ""; }

    /** Exécute une commande de la boutique comme la console ({username}, {name}, {uuid} remplacés). */
    private void run(String cmd, String name, String uuid) {
        String c = cmd.replace("{username}", name).replace("{name}", name).replace("{uuid}", uuid).replace("{id}", uuid);
        if (c.startsWith("/")) c = c.substring(1);
        s.server().getCommands().performPrefixedCommand(s.server().createCommandSourceStack(), c);
    }

    // ------------------------------------------------------------ persistance
    private void load() {
        Yaml y = store.get();
        Yaml p = y.getSection("en-attente");
        if (p != null) for (String k : p.getKeys()) pending.put(k, new ArrayList<>(p.getStringList(k)));
        Yaml h = y.getSection("historique");
        if (h != null) for (String k : h.getKeys()) {
            try { history.put(UUID.fromString(k), new LinkedList<>(h.getStringList(k))); } catch (IllegalArgumentException ignored) {}
        }
    }

    public void save() {
        Yaml y = store.fresh();
        pending.forEach((k, v) -> y.set("en-attente." + k, new ArrayList<>(v)));
        history.forEach((k, v) -> y.set("historique." + k, new ArrayList<>(v)));
        store.save();
    }
}

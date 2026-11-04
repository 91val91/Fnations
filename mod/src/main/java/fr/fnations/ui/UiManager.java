package fr.fnations.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.fnations.FNations;
import fr.fnations.net.UiPayload;
import fr.fnations.server.FnServer;
import fr.fnations.util.Txt;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.Deflater;

/** Routage des pages de la tablette : ouverture, actions des joueurs, envoi des vues. */
public final class UiManager {
    /** Barre de navigation : id de page, icône, libellé. */
    private static final List<String[]> NAV = List.of(
            new String[]{"accueil", "home", "Accueil"},
            new String[]{"pays", "flag", "Mon pays"},
            new String[]{"banque", "bank", "Banque"},
            new String[]{"entreprise", "briefcase", "Entreprise"},
            new String[]{"metiers", "pickaxe", "Métiers"},
            new String[]{"hdv", "shop", "Hôtel des ventes"},
            new String[]{"recherche", "flask", "Recherche & Développement"},
            new String[]{"guerres", "swords", "Guerres"},
            new String[]{"chambres", "bed", "Chambres"},
            new String[]{"classements", "trophy", "Classements"});

    private final FnServer s;
    private final Map<String, UiPage> pages = new LinkedHashMap<>();
    private final Map<UUID, UiSession> sessions = new HashMap<>();

    public UiManager(FnServer s) { this.s = s; }

    public void register(UiPage page) { pages.put(page.id(), page); }

    public UiPage page(String id) { return pages.get(id); }

    public boolean isOpen(UUID u) { return sessions.containsKey(u); }

    public UiSession session(ServerPlayer p) { return sessions.get(p.getUUID()); }

    // ------------------------------------------------------------ ouverture / rafraîchissement
    public void open(ServerPlayer p, String pageId) { open(p, pageId, Map.of()); }

    public void open(ServerPlayer p, String pageId, Map<String, String> state) {
        UiPage page = pages.get(pageId);
        if (page == null) return;
        UiSession ss = sessions.computeIfAbsent(p.getUUID(), k -> new UiSession());
        if (!pageId.equals(ss.page)) ss.dialog = null;
        ss.page = pageId;
        state.forEach(ss::set);
        try {
            page.enter(p, ss);
        } catch (Exception e) {
            FNations.LOG.error("Page {} : erreur à l'ouverture", pageId, e);
        }
        send(p, ss);
    }

    public void refresh(ServerPlayer p) {
        UiSession ss = sessions.get(p.getUUID());
        if (ss != null) send(p, ss);
    }

    /** Rafraîchit la page de tous les joueurs qui la regardent. */
    public void refreshAll(String pageId) {
        for (var e : new HashMap<>(sessions).entrySet()) {
            if (!pageId.equals(e.getValue().page)) continue;
            ServerPlayer p = s.player(e.getKey());
            if (p != null) send(p, e.getValue());
        }
    }

    public void toast(ServerPlayer p, String mini) {
        UiSession ss = sessions.get(p.getUUID());
        if (ss != null) ss.toast(mini);
    }

    /** Ferme l'écran côté client. */
    public void close(ServerPlayer p) {
        sessions.remove(p.getUUID());
        JsonObject root = new JsonObject();
        root.addProperty("close", true);
        ServerPlayNetworking.send(p, new UiPayload(compress(root)));
    }

    public void forget(UUID u) { sessions.remove(u); }

    // ------------------------------------------------------------ actions
    public void handle(ServerPlayer p, String action, String data) {
        if (action == null) return;
        if (action.equals("close")) { sessions.remove(p.getUUID()); return; }
        if (action.startsWith("nav:")) { open(p, action.substring(4)); return; }
        UiSession ss = sessions.get(p.getUUID());
        if (ss == null) return;
        long now = System.currentTimeMillis();
        if (now - ss.windowStart > 1000) { ss.windowStart = now; ss.actions = 0; }
        if (++ss.actions > 25) return; // anti-spam
        if (action.equals("refresh")) { send(p, ss); return; }
        if (action.equals("dialog:close")) { ss.dialog = null; send(p, ss); return; }
        int dot = action.indexOf('.');
        if (dot <= 0) return;
        String pageId = action.substring(0, dot);
        UiPage page = pages.get(pageId);
        if (page == null || !pageId.equals(ss.page)) return;
        String rest = action.substring(dot + 1);
        int colon = rest.indexOf(':');
        String act = colon < 0 ? rest : rest.substring(0, colon);
        String arg = colon < 0 ? "" : rest.substring(colon + 1);
        ss.dirty = true;
        try {
            page.action(p, ss, act, arg, inputs(data));
        } catch (Exception e) {
            FNations.LOG.error("Page {} : erreur sur l'action {}", pageId, action, e);
            ss.toast(UiTheme.TR + "Une erreur est survenue.");
        }
        if (ss.dirty && sessions.get(p.getUUID()) == ss) send(p, ss);
    }

    private static Map<String, String> inputs(String data) {
        Map<String, String> out = new HashMap<>();
        if (data == null || data.isEmpty()) return out;
        try {
            JsonObject o = JsonParser.parseString(data).getAsJsonObject();
            if (o.has("in")) for (var e : o.getAsJsonObject("in").entrySet()) out.put(e.getKey(), clip(e.getValue()));
        } catch (Exception ignored) {
        }
        return out;
    }

    private static String clip(JsonElement e) {
        String v = e.isJsonPrimitive() ? e.getAsString() : e.toString();
        return v.length() > 200 ? v.substring(0, 200) : v;
    }

    // ------------------------------------------------------------ envoi
    private void send(ServerPlayer p, UiSession ss) {
        UiPage page = pages.get(ss.page);
        if (page == null) return;
        ss.dirty = false;
        JsonObject root = new JsonObject();
        root.addProperty("page", page.id());
        root.addProperty("sel", page.nav());
        JsonArray nav = new JsonArray();
        for (String[] n : NAV) {
            JsonObject o = new JsonObject();
            o.addProperty("id", n[0]);
            o.addProperty("i", n[1]);
            o.addProperty("tip", n[2]);
            nav.add(o);
        }
        root.add("nav", nav);
        UiNode body;
        try {
            body = page.build(p, ss);
        } catch (Exception e) {
            FNations.LOG.error("Page {} : erreur de construction", page.id(), e);
            body = UiNode.card(UiNode.text(UiTheme.TR + "Impossible d'afficher cette page."));
        }
        root.add("body", body.json());
        if (ss.dialog != null) root.add("dialog", ss.dialog.json());
        if (!ss.toasts.isEmpty()) {
            JsonArray t = new JsonArray();
            ss.toasts.forEach(t::add);
            root.add("toasts", t);
            ss.toasts.clear();
        }
        int r = page.refresh(p, ss);
        if (r > 0) root.addProperty("refresh", r);
        root.addProperty("money", Txt.money(s.bank().balance(p.getUUID())));
        ServerPlayNetworking.send(p, new UiPayload(compress(root)));
    }

    private static byte[] compress(JsonObject root) {
        byte[] raw = root.toString().getBytes(StandardCharsets.UTF_8);
        Deflater d = new Deflater(Deflater.BEST_SPEED);
        d.setInput(raw);
        d.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length / 3 + 64);
        byte[] buf = new byte[8192];
        while (!d.finished()) out.write(buf, 0, d.deflate(buf));
        d.end();
        return out.toByteArray();
    }
}

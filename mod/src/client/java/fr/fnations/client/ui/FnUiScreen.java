package fr.fnations.client.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.fnations.net.UiActionPayload;
import fr.fnations.util.Txt;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.Inflater;

/**
 * Tablette FNations : écran unique qui affiche les pages envoyées par le serveur.
 * Tout est dessiné sur un canevas virtuel de 560 × 315 mis à l'échelle de la fenêtre.
 */
public final class FnUiScreen extends Screen implements UiView.Hooks {
    public static final int W = 560, H = 315;

    private record Toast(Component text, long until) {}

    private String pageId = "";
    private UiView.Node root, dialog;
    private final Map<String, EditBox> boxes = new LinkedHashMap<>();
    private final Map<String, UiView.Node> inputNodes = new HashMap<>();
    private final Map<String, Boolean> checks = new HashMap<>();
    private final Map<String, Double> scrolls = new HashMap<>();
    private final Set<String> seen = new HashSet<>();
    private final List<Toast> toasts = new ArrayList<>();
    private int refreshSec;
    private long nextRefresh;
    private boolean closedByServer;
    private double f = 1, offX, offY;
    private int vmx, vmy;
    private UiView.Node hover, tipNode;
    private EditBox focused;
    private String focusedId;
    // carte du monde : une vue par carte (conservée entre deux mises à jour), glisser-déplacer
    private final Map<String, WorldMapView.View> mapViews = new HashMap<>();
    private UiView.Node dragMap;
    private double dragX, dragY;
    private boolean dragMoved;
    // roulettes des LootBoxs : début de l'animation, dernière case passée, fin jouée
    private final Map<String, Long> spinStart = new HashMap<>();
    private final Map<String, Integer> spinTick = new HashMap<>();
    private final Set<String> spinDone = new HashSet<>();
    /** Écran mis de côté pendant la confirmation d'un lien (boutique en ligne). */
    private static FnUiScreen parked;
    private boolean linking;

    private FnUiScreen() { super(Component.literal("FNations")); }

    // =================================================================== réception
    public static void receive(byte[] data) {
        JsonObject page;
        try {
            page = JsonParser.parseString(inflate(data)).getAsJsonObject();
        } catch (Exception e) {
            fr.fnations.FNations.LOG.error("Page FNations illisible", e);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean confirming = parked != null && mc.screen instanceof ConfirmLinkScreen;
        if (page.has("close")) {
            if (mc.screen instanceof FnUiScreen s) { s.closedByServer = true; mc.setScreen(null); }
            else if (confirming) parked.closedByServer = true;
            return;
        }
        FnUiScreen s = mc.screen instanceof FnUiScreen cur ? cur : confirming ? parked : new FnUiScreen();
        s.update(page);
        if (mc.screen != s && s != parked) mc.setScreen(s);
    }

    private static String inflate(byte[] data) throws Exception {
        Inflater inf = new Inflater();
        inf.setInput(data);
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length * 4);
        byte[] buf = new byte[8192];
        while (!inf.finished()) {
            int n = inf.inflate(buf);
            if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break;
            out.write(buf, 0, n);
        }
        inf.end();
        return out.toString(StandardCharsets.UTF_8);
    }

    private void update(JsonObject page) {
        String id = page.has("page") ? page.get("page").getAsString() : "";
        if (!id.equals(pageId)) {
            boxes.clear();
            inputNodes.clear();
            checks.clear();
            scrolls.clear();
            focused = null;
            focusedId = null;
        } else if (root != null) {
            List<UiView.Node> lists = new ArrayList<>();
            UiView.collect(root, "list", lists);
            if (dialog != null) UiView.collect(dialog, "list", lists);
            for (UiView.Node l : lists) if (l.id != null) scrolls.put(l.id, l.scroll);
        }
        pageId = id;
        var regs = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.registryAccess();
        String sel = page.has("sel") ? page.get("sel").getAsString() : "";
        // barre de navigation (construite côté client à partir de la liste du serveur)
        JsonObject nav = node("col");
        nav.addProperty("w", 30);
        nav.addProperty("bg", "#0F1016");
        nav.addProperty("pady", 7);
        nav.addProperty("gap", 2);
        nav.addProperty("align", "center");
        JsonArray navKids = new JsonArray();
        // hauteur des boutons : tout doit tenir dans la barre (entrées + fermer)
        int navCount = (page.has("nav") ? page.getAsJsonArray("nav").size() : 0) + 1;
        int bh = Math.max(14, Math.min(20, (H - 2 * 7 - navCount * 2) / navCount));
        if (page.has("nav")) for (JsonElement e : page.getAsJsonArray("nav")) {
            JsonObject n = e.getAsJsonObject();
            JsonObject b = node("btn");
            b.addProperty("i", n.get("i").getAsString());
            b.addProperty("a", "nav:" + n.get("id").getAsString());
            b.addProperty("style", "nav");
            b.addProperty("w", 22);
            b.addProperty("h", bh);
            if (n.get("id").getAsString().equals(sel)) b.addProperty("sel", true);
            JsonArray tip = new JsonArray();
            tip.add(n.get("tip").getAsString());
            b.add("tip", tip);
            navKids.add(b);
        }
        JsonObject spacer = node("space");
        spacer.addProperty("flex", 1);
        navKids.add(spacer);
        JsonObject close = node("btn");
        close.addProperty("i", "close");
        close.addProperty("a", "client:close");
        close.addProperty("style", "nav");
        close.addProperty("w", 22);
        close.addProperty("h", bh);
        JsonArray ctip = new JsonArray();
        ctip.add("Fermer");
        close.add("tip", ctip);
        navKids.add(close);
        nav.add("c", navKids);

        JsonObject body = page.has("body") ? page.getAsJsonObject("body") : node("col");
        if (!body.has("flex")) body.addProperty("flex", 1);
        JsonObject wrap = node("col");
        wrap.addProperty("flex", 1);
        wrap.addProperty("pad", 7);
        JsonArray wk = new JsonArray();
        wk.add(body);
        wrap.add("c", wk);
        JsonObject r = node("row");
        r.addProperty("gap", 0);
        JsonArray rk = new JsonArray();
        rk.add(nav);
        rk.add(wrap);
        r.add("c", rk);
        root = new UiView.Node(r, regs);
        dialog = page.has("dialog") ? new UiView.Node(page.getAsJsonObject("dialog"), regs) : null;
        // défilements mémorisés
        List<UiView.Node> lists = new ArrayList<>();
        UiView.collect(root, "list", lists);
        if (dialog != null) UiView.collect(dialog, "list", lists);
        for (UiView.Node l : lists) if (l.id != null && scrolls.containsKey(l.id)) l.scroll = scrolls.get(l.id);
        // champs : valeurs initiales
        List<UiView.Node> inputs = new ArrayList<>();
        UiView.collect(root, "input", inputs);
        if (dialog != null) UiView.collect(dialog, "input", inputs);
        Set<String> ids = new HashSet<>();
        for (UiView.Node n : inputs) {
            if (n.id == null) continue;
            ids.add(n.id);
            inputNodes.put(n.id, n);
            EditBox b = boxes.get(n.id);
            if (b == null) {
                b = new EditBox(font(), 0, 0, 80, 9, Component.empty());
                b.setBordered(false);
                b.setMaxLength(Math.max(1, n.max));
                b.setTextColor(0xFFFFFFFF);
                if (n.placeholder != null) b.setHint(Txt.mm("<#6C6F7E>" + n.placeholder));
                if (n.numeric) b.setFilter(v -> v.matches("[0-9 .,kKmM-]*"));
                b.setValue(n.value == null ? "" : n.value);
                boxes.put(n.id, b);
            } else if (n.force) {
                b.setValue(n.value == null ? "" : n.value);
            }
        }
        boxes.keySet().removeIf(k -> !ids.contains(k));
        inputNodes.keySet().removeIf(k -> !ids.contains(k));
        if (focusedId != null && !boxes.containsKey(focusedId)) { focused = null; focusedId = null; }
        List<UiView.Node> cks = new ArrayList<>();
        UiView.collect(root, "check", cks);
        if (dialog != null) UiView.collect(dialog, "check", cks);
        for (UiView.Node c : cks) if (c.id != null && (c.force || !checks.containsKey(c.id))) checks.put(c.id, c.checked);
        if (page.has("toasts")) for (JsonElement e : page.getAsJsonArray("toasts"))
            toasts.add(new Toast(Txt.mm(e.getAsString()), System.currentTimeMillis() + 4500));
        while (toasts.size() > 4) toasts.removeFirst();
        refreshSec = page.has("refresh") ? page.get("refresh").getAsInt() : 0;
        nextRefresh = System.currentTimeMillis() + refreshSec * 1000L;
    }

    private static JsonObject node(String t) {
        JsonObject o = new JsonObject();
        o.addProperty("t", t);
        return o;
    }

    private net.minecraft.client.gui.Font font() { return Minecraft.getInstance().font; }

    // =================================================================== actions
    private void send(String action) {
        if (action == null || action.equals("noop")) return;
        if (action.equals("client:close")) { onClose(); return; }
        if (action.startsWith("url:")) { openLink(action.substring(4)); return; }
        if (action.startsWith("client:focus:")) { focusMap(action.substring(13)); return; }
        JsonObject d = new JsonObject();
        JsonObject in = new JsonObject();
        boxes.forEach((k, b) -> in.addProperty(k, b.getValue()));
        checks.forEach(in::addProperty);
        d.add("in", in);
        String data = d.toString();
        if (data.length() > 8000) data = "{}";
        ClientPlayNetworking.send(new UiActionPayload(action, data));
    }

    /** Lien externe (boutique en ligne) : fenêtre de confirmation de Minecraft, puis retour à la tablette. */
    private void openLink(String url) {
        if (!url.startsWith("https://") && !url.startsWith("http://")) return;
        linking = true;
        parked = this;
        ConfirmLinkScreen.confirmLinkNow(this, url);
    }

    /** Centre les cartes du monde affichées sur un point (« x,z » ou « x,z,échelle », en blocs) et l'épingle. */
    private void focusMap(String arg) {
        String[] v = arg.split(",");
        if (v.length < 2 || root == null) return;
        double x, z, sc;
        try {
            x = Double.parseDouble(v[0]);
            z = Double.parseDouble(v[1]);
            sc = v.length > 2 ? Double.parseDouble(v[2]) : 0;
        } catch (NumberFormatException e) {
            return;
        }
        List<UiView.Node> maps = new ArrayList<>();
        UiView.collect(root, "worldmap", maps);
        for (UiView.Node m : maps) {
            WorldMapView.View view = view(m);
            WorldMapView.focus(view, x, z, sc > 0 ? sc : Math.max(2.0, view.scale));
            view.pinX = x;
            view.pinZ = z;
        }
    }

    private WorldMapView.View view(UiView.Node n) { return mapViews.computeIfAbsent(n.id == null ? "" : n.id, k -> new WorldMapView.View()); }

    private void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.6F));
    }

    // --- API utilisée par les tests automatiques
    public String pageId() { return pageId; }

    public boolean hasDialog() { return dialog != null; }

    /** Déclenche l'action d'un élément affiché (bouton, ligne...). */
    public boolean press(String action) {
        UiView.Node n = dialog != null ? UiView.find(dialog, action) : null;
        if (n == null && root != null) n = UiView.find(root, action);
        if (n == null || n.off) return false;
        send(action);
        return true;
    }

    /** Envoie une action telle quelle (ex. "hdv.pick:3"), avec les valeurs des champs. */
    public void sendAction(String action) { send(action); }

    public boolean hasAction(String action) {
        return (dialog != null && UiView.find(dialog, action) != null) || (root != null && UiView.find(root, action) != null);
    }

    public void type(String inputId, String value) {
        EditBox b = boxes.get(inputId);
        if (b != null) b.setValue(value);
    }

    public void setCheck(String id, boolean v) { checks.put(id, v); }

    // =================================================================== crochets de mise en page
    @Override
    public void input(UiView.Node n) {
        EditBox b = boxes.get(n.id);
        if (b == null) return;
        seen.add(n.id);
        b.setX(n.x + 5);
        b.setY(n.y + (n.h - 8) / 2);
        b.setWidth(Math.max(4, n.w - 10));
    }

    @Override
    public void check(UiView.Node n) {}

    @Override
    public String filterText(String inputId) {
        EditBox b = boxes.get(inputId);
        return b == null ? "" : b.getValue();
    }

    // =================================================================== transformation canevas → écran
    private void computeTransform() {
        var win = Minecraft.getInstance().getWindow();
        double real = Math.min(win.getWidth() / (double) W, win.getHeight() / (double) H);
        if (real >= 2) real = Math.floor(real);
        real = Math.max(0.5, real);
        f = real / win.getGuiScale();
        offX = (width - W * f) / 2;
        offY = (height - H * f) / 2;
    }

    private double vx(double mx) { return (mx - offX) / f; }

    private double vy(double my) { return (my - offY) / f; }

    private void doLayout() {
        if (root == null) return;
        seen.clear();
        UiView.layout(root, 0, 0, W, H, this);
        if (dialog != null) {
            int dw = Math.min(W - 40, UiView.prefW(dialog));
            int dh = Math.min(H - 30, UiView.prefH(dialog, dw));
            UiView.layout(dialog, (W - dw) / 2, (H - dh) / 2, dw, dh, this);
        }
    }

    // =================================================================== rendu
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        if (root == null) return;
        computeTransform();
        vmx = (int) Math.floor(vx(mx));
        vmy = (int) Math.floor(vy(my));
        doLayout();
        UiView.Node top = dialog != null ? dialog : root;
        hover = UiView.hitAction(top, vmx, vmy);
        tipNode = UiView.hitTip(top, vmx, vmy);
        g.pose().pushPose();
        g.pose().translate(offX, offY, 0);
        g.pose().scale((float) f, (float) f, 1);
        UiDraw.rr(g, 0, 0, W, H, 8, UiDraw.BG);
        UiDraw.rr(g, 0, 0, 30, H, 8, UiDraw.NAV);
        g.fill(22, 0, 30, H, UiDraw.NAV);
        g.enableScissor(0, 0, W, H);
        draw(g, root, pt);
        g.disableScissor();
        if (dialog != null) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            UiDraw.rr(g, 0, 0, W, H, 8, 0xA0000000);
            UiDraw.rr(g, dialog.x - 1, dialog.y - 1, dialog.w + 2, dialog.h + 2, 8, UiDraw.LINE);
            draw(g, dialog, pt);
            g.pose().popPose();
        }
        drawToasts(g);
        g.pose().popPose();
        drawTooltip(g, mx, my);
    }

    private void draw(GuiGraphics g, UiView.Node n, float pt) {
        if (n.hidden || n.w <= 0 || n.h <= 0) return;
        boolean hovered = hover == n;
        switch (n.t) {
            case "btn" -> drawButton(g, n, hovered);
            case "text", "iname" -> drawText(g, n);
            case "icon" -> UiDraw.icon(g, n.icon, n.x, n.y, Math.min(n.size, Math.min(n.w, n.h)), n.color);
            case "item" -> drawItem(g, n.stack, n.x, n.y, n.size, n.num);
            case "head" -> PlayerFaceRenderer.draw(g, skin(n.uuid), n.x, n.y, Math.min(n.w, n.h));
            case "bar" -> drawBar(g, n);
            case "input" -> drawInput(g, n, pt);
            case "check" -> drawCheck(g, n, hovered);
            case "chip" -> {
                UiDraw.rr(g, n.x, n.y, n.w, n.h, 6, n.bg == 0 ? UiDraw.ROW : n.bg);
                int tw = font().width(n.comp);
                UiDraw.line(g, font(), n.comp, n.x + Math.max(4, (n.w - tw) / 2), n.y + 2, n.w - 6, n.color);
            }
            case "sep" -> g.fill(n.x, n.y, n.x + n.w, n.y + 1, UiDraw.LINE);
            case "map" -> drawMap(g, n);
            case "inv" -> drawInv(g, n);
            case "player" -> {
                var p = Minecraft.getInstance().player;
                if (p != null) InventoryScreen.renderEntityInInventoryFollowsMouse(g, n.x, n.y, n.x + n.w, n.y + n.h,
                        Math.max(10, (int) (n.h / 2.3)), 0.0625F, vmx, vmy, p);
            }
            case "terrain" -> drawTerrain(g, n);
            case "list" -> drawList(g, n, pt);
            case "roulette" -> drawRoulette(g, n);
            case "worldmap" -> WorldMapView.draw(g, n, view(n), n.nations, n.nationIdx, vmx, vmy, f * Minecraft.getInstance().getWindow().getGuiScale());
            default -> {
                drawBox(g, n, hovered);
                for (UiView.Node c : n.kids) draw(g, c, pt);
            }
        }
    }

    private void drawBox(GuiGraphics g, UiView.Node n, boolean hovered) {
        boolean interactive = n.a != null && !n.off;
        if (n.bd != 0) {
            UiDraw.rr(g, n.x, n.y, n.w, n.h, n.r, n.bd);
            int inner = n.bg != 0 ? n.bg : UiDraw.BG;
            UiDraw.rr(g, n.x + 1, n.y + 1, n.w - 2, n.h - 2, Math.max(0, n.r - 1), hovered && interactive ? UiDraw.lighten(inner, 0.06f) : inner);
        } else if (n.bg != 0) {
            UiDraw.rr(g, n.x, n.y, n.w, n.h, n.r, hovered && interactive ? UiDraw.lighten(n.bg, 0.07f) : n.bg);
        } else if (hovered && interactive || (n.hov && n.contains(vmx, vmy))) {
            UiDraw.rr(g, n.x, n.y, n.w, n.h, Math.max(3, n.r), UiDraw.ROW_HOVER);
        }
    }

    private void drawButton(GuiGraphics g, UiView.Node n, boolean hovered) {
        int bg, fg = UiDraw.TEXT;
        boolean off = n.off;
        switch (n.style) {
            case "primary" -> bg = off ? 0xFF2A2D45 : hovered ? UiDraw.ACCENT_HOVER : UiDraw.ACCENT;
            case "danger" -> bg = off ? 0xFF3A2427 : hovered ? 0xFFE45A5C : 0xFFD83C3E;
            case "success" -> bg = off ? 0xFF203528 : hovered ? 0xFF3DBB6B : 0xFF2FA65A;
            case "ghost" -> { bg = hovered && !off ? UiDraw.ROW_HOVER : 0; fg = hovered ? UiDraw.TEXT : UiDraw.TEXT2; }
            case "nav" -> { bg = n.sel ? UiDraw.ACCENT : hovered ? UiDraw.ROW : 0; fg = n.sel || hovered ? UiDraw.TEXT : UiDraw.TEXT2; }
            case "tab" -> { bg = n.sel ? UiDraw.ACCENT_SOFT_BG : hovered ? UiDraw.ROW : 0; fg = n.sel ? UiDraw.TEXT : hovered ? UiDraw.TEXT : UiDraw.TEXT2; }
            default -> bg = off ? 0xFF22232E : n.sel ? UiDraw.ACCENT : hovered ? 0xFF3A3D50 : 0xFF2F3140;
        }
        if (n.bg != 0 && !n.sel) bg = hovered && !off ? UiDraw.lighten(n.bg, 0.1f) : n.bg;
        if (off) fg = UiDraw.MUTED;
        UiDraw.rr(g, n.x, n.y, n.w, n.h, n.r, bg);
        if (n.style.equals("tab") && n.sel) g.fill(n.x + 3, n.y + n.h - 2, n.x + n.w - 3, n.y + n.h, UiDraw.ACCENT);
        int tw = font().width(n.comp);
        boolean hasText = tw > 0;
        int is = hasText ? 10 : Math.min(16, Math.min(n.w, n.h) - 6);
        int cw = (n.icon != null ? is + (hasText ? 4 : 0) : 0) + tw;
        int cx = n.x + Math.max(3, (n.w - cw) / 2);
        if (n.icon != null) {
            UiDraw.icon(g, n.icon, cx, n.y + (n.h - is) / 2, is, n.color != UiDraw.TEXT ? n.color : fg);
            cx += is + (hasText ? 4 : 0);
        }
        if (hasText) UiDraw.line(g, font(), n.comp, cx, n.y + (n.h - 8) / 2, n.x + n.w - cx - 2, fg);
    }

    private void drawText(GuiGraphics g, UiView.Node n) {
        int color = n.off ? UiDraw.MUTED : n.color;
        float s = n.scale;
        if (n.wrap) {
            List<FormattedCharSequence> lines = UiView.lines(n, n.w);
            int lh = UiView.lineH(n), y = n.y;
            for (FormattedCharSequence l : lines) {
                int lw = (int) (font().width(l) * s);
                int x = n.align.equals("center") ? n.x + (n.w - lw) / 2 : n.align.equals("right") ? n.x + n.w - lw : n.x;
                g.pose().pushPose();
                g.pose().translate(x, y, 0);
                g.pose().scale(s, s, 1);
                g.drawString(font(), l, 0, 0, color, false);
                g.pose().popPose();
                y += lh;
            }
            return;
        }
        int tw = (int) Math.ceil(font().width(n.comp) * s);
        int x = tw >= n.w ? n.x : n.align.equals("center") ? n.x + (n.w - tw) / 2 : n.align.equals("right") ? n.x + n.w - tw : n.x;
        g.pose().pushPose();
        g.pose().translate(x, n.y, 0);
        g.pose().scale(s, s, 1);
        UiDraw.line(g, font(), n.comp, 0, 0, (int) Math.floor(n.w / s) + 1, color);
        g.pose().popPose();
    }

    private void drawItem(GuiGraphics g, ItemStack st, int x, int y, int size, int count) {
        if (st.isEmpty()) return;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        float s = size / 16f;
        g.pose().scale(s, s, 1);
        g.renderItem(st, 0, 0);
        g.pose().popPose();
        if (count > 1) {
            String c = count >= 10000 ? (count / 1000) + "k" : String.valueOf(count);
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            g.drawString(font(), c, x + size + 1 - font().width(c), y + size - 7, 0xFFFFFFFF, true);
            g.pose().popPose();
        }
    }

    private static PlayerSkin skin(UUID u) {
        if (u == null) return DefaultPlayerSkin.getDefaultSkin();
        var con = Minecraft.getInstance().getConnection();
        PlayerInfo info = con == null ? null : con.getPlayerInfo(u);
        return info != null ? info.getSkin() : DefaultPlayerSkin.get(u);
    }

    private void drawBar(GuiGraphics g, UiView.Node n) {
        double v = 0;
        try { v = n.value == null ? 0 : Double.parseDouble(n.value); } catch (NumberFormatException ignored) {}
        int r = n.h / 2;
        UiDraw.rr(g, n.x, n.y, n.w, n.h, r, n.bg == 0 ? 0xFF14151D : n.bg);
        int fw = (int) Math.round(n.w * Math.max(0, Math.min(1, v)));
        if (fw > 0) UiDraw.rr(g, n.x, n.y, Math.max(fw, Math.min(n.w, n.h)), n.h, r, n.color == UiDraw.TEXT ? UiDraw.ACCENT : n.color);
        if (n.comp != null && !n.comp.getString().isEmpty() && n.h >= 9) {
            int tw = font().width(n.comp);
            g.drawString(font(), n.comp, n.x + (n.w - tw) / 2, n.y + (n.h - 8) / 2, 0xFFFFFFFF, true);
        }
    }

    private void drawInput(GuiGraphics g, UiView.Node n, float pt) {
        EditBox b = boxes.get(n.id);
        boolean foc = b != null && b == focused;
        UiDraw.rr(g, n.x, n.y, n.w, n.h, n.r, foc ? UiDraw.ACCENT : UiDraw.LINE);
        UiDraw.rr(g, n.x + 1, n.y + 1, n.w - 2, n.h - 2, Math.max(0, n.r - 1), UiDraw.FIELD);
        if (b != null) {
            g.enableScissor(n.x + 2, n.y + 1, n.x + n.w - 2, n.y + n.h - 1);
            b.render(g, vmx, vmy, pt);
            g.disableScissor();
        }
    }

    private void drawCheck(GuiGraphics g, UiView.Node n, boolean hovered) {
        boolean on = checks.getOrDefault(n.id, false);
        int by = n.y + (n.h - 10) / 2;
        UiDraw.rr(g, n.x, by, 10, 10, 2, on ? UiDraw.ACCENT : hovered ? UiDraw.ACCENT_HOVER : UiDraw.LINE);
        if (!on) UiDraw.rr(g, n.x + 1, by + 1, 8, 8, 2, UiDraw.FIELD);
        else UiDraw.icon(g, "check", n.x + 1, by + 1, 8, 0xFFFFFFFF);
        UiDraw.line(g, font(), n.comp, n.x + 14, n.y + (n.h - 8) / 2, n.w - 14, n.off ? UiDraw.MUTED : UiDraw.TEXT);
    }

    private void drawList(GuiGraphics g, UiView.Node n, float pt) {
        drawBox(g, n, false);
        g.enableScissor(n.x, n.y, n.x + n.w, n.y + n.h);
        for (UiView.Node c : n.kids) {
            if (c.hidden || c.y + c.h < n.y || c.y > n.y + n.h) continue;
            draw(g, c, pt);
        }
        g.disableScissor();
        if (n.contentH > n.h) {
            int tx = n.x + n.w - UiView.BAR;
            UiDraw.rr(g, tx, n.y + 1, UiView.BAR - 1, n.h - 2, 2, 0x20FFFFFF);
            int th = Math.max(14, n.h * n.h / Math.max(1, n.contentH));
            int ty = n.y + 1 + (int) ((n.h - 2 - th) * (n.scroll / Math.max(1, n.contentH - n.h)));
            UiDraw.rr(g, tx, ty, UiView.BAR - 1, th, 2, 0xFF4A4D60);
        }
    }

    private int[] mapGeometry(UiView.Node n) {
        int rows = n.rows.length, cols = rows == 0 ? 0 : n.rows[0].length();
        if (rows == 0 || cols == 0) return null;
        int cell = Math.max(2, Math.min(n.w / cols, n.h / rows));
        int ox = n.x + (n.w - cell * cols) / 2, oy = n.y + (n.h - cell * rows) / 2;
        return new int[]{cols, rows, cell, ox, oy};
    }

    private void drawMap(GuiGraphics g, UiView.Node n) {
        int[] m = mapGeometry(n);
        if (m == null) return;
        int cols = m[0], rows = m[1], cell = m[2], ox = m[3], oy = m[4];
        UiDraw.rr(g, ox - 2, oy - 2, cols * cell + 4, rows * cell + 4, 4, UiDraw.FIELD);
        if (n.terrainUnder) {
            TerrainCache.Entry e = TerrainCache.area((n.cx - cols / 2) * 16, (n.cz - rows / 2) * 16, cols * 16, rows * 16);
            if (e != null) g.blit(RenderType::guiTextured, e.id(), ox, oy, 0, 0, cols * cell, rows * cell, e.w(), e.h(), e.w(), e.h());
        }
        for (int r = 0; r < rows; r++) {
            String line = n.rows[r];
            for (int c = 0; c < cols && c < line.length(); c++) {
                Integer col = n.pal.get(line.charAt(c));
                if (col != null && (col >>> 24) != 0) g.fill(ox + c * cell, oy + r * cell, ox + (c + 1) * cell, oy + (r + 1) * cell, col);
            }
        }
        for (int c = 0; c <= cols; c++) g.fill(ox + c * cell, oy, ox + c * cell + 1, oy + rows * cell, 0x30000000);
        for (int r = 0; r <= rows; r++) g.fill(ox, oy + r * cell, ox + cols * cell, oy + r * cell + 1, 0x30000000);
        // position du joueur (centre)
        int pcx = ox + (cols / 2) * cell, pcy = oy + (rows / 2) * cell;
        g.fill(pcx, pcy, pcx + cell, pcy + 1, 0xFFFFFFFF);
        g.fill(pcx, pcy + cell - 1, pcx + cell, pcy + cell, 0xFFFFFFFF);
        g.fill(pcx, pcy, pcx + 1, pcy + cell, 0xFFFFFFFF);
        g.fill(pcx + cell - 1, pcy, pcx + cell, pcy + cell, 0xFFFFFFFF);
        if (hover == n || tipNode == n) {
            int hc = (vmx - ox) / cell, hr = (vmy - oy) / cell;
            if (hc >= 0 && hr >= 0 && hc < cols && hr < rows && vmx >= ox && vmy >= oy)
                g.fill(ox + hc * cell, oy + hr * cell, ox + (hc + 1) * cell, oy + (hr + 1) * cell, 0x50FFFFFF);
        }
    }

    private int invSlotAt(UiView.Node n, double mx, double my) {
        int cols = Math.max(1, n.cols), size = Math.min(18, n.w / cols);
        int c = (int) ((mx - n.x) / size), r = (int) ((my - n.y) / size);
        if (mx < n.x || my < n.y || c < 0 || c >= cols) return -1;
        int i = r * cols + c;
        return i >= 0 && i < 36 ? i : -1;
    }

    private void drawInv(GuiGraphics g, UiView.Node n) {
        var p = Minecraft.getInstance().player;
        if (p == null) return;
        int cols = Math.max(1, n.cols), size = Math.min(18, n.w / cols);
        int hov = n.contains(vmx, vmy) ? invSlotAt(n, vmx, vmy) : -1;
        for (int i = 0; i < 36; i++) {
            int x = n.x + (i % cols) * size, y = n.y + (i / cols) * size;
            if (y + size > n.y + n.h + 1) break;
            ItemStack st = p.getInventory().items.get(i);
            UiDraw.rr(g, x + 1, y + 1, size - 2, size - 2, 3, i == hov ? UiDraw.ROW_HOVER : UiDraw.ROW);
            if (!st.isEmpty()) {
                g.enableScissor(x + 1, y + 1, x + size - 1, y + size - 1);
                drawItem(g, st, x + 1 + (size - 18) / 2, y + 1 + (size - 18) / 2, 16, st.getCount());
                g.disableScissor();
            }
        }
    }

    private void drawTerrain(GuiGraphics g, UiView.Node n) {
        var p = Minecraft.getInstance().player;
        int side = Math.min(n.w, n.h), x = n.x + (n.w - side) / 2, y = n.y + (n.h - side) / 2;
        UiDraw.rr(g, x - 2, y - 2, side + 4, side + 4, 5, UiDraw.FIELD);
        if (p == null) return;
        TerrainCache.Entry e = TerrainCache.around(p.getBlockX(), p.getBlockZ(), n.radius);
        if (e != null) g.blit(RenderType::guiTextured, e.id(), x, y, 0, 0, side, side, e.w(), e.h(), e.w(), e.h());
        int c = side / 2;
        UiDraw.rr(g, x + c - 3, y + c - 3, 6, 6, 3, 0xFFFFFFFF);
        UiDraw.rr(g, x + c - 2, y + c - 2, 4, 4, 2, UiDraw.ACCENT);
    }

    /**
     * Roulette d'ouverture de LootBox : la bande défile puis ralentit (courbe quartique) jusqu'au gain,
     * avec un « tic » à chaque case qui passe sous le repère et un halo sur la case gagnante.
     */
    private void drawRoulette(GuiGraphics g, UiView.Node n) {
        String id = n.id == null ? "" : n.id;
        long now = System.currentTimeMillis();
        long start = spinStart.computeIfAbsent(id, k -> now - n.el);
        double t = n.dur <= 0 ? 1 : Math.min(1, (now - start) / (double) n.dur);
        double ease = 1 - Math.pow(1 - t, 4);
        int ch = n.h - 12, cw = Math.min(ch, 64), pitch = cw + 4;
        // point d'arrêt dans la case gagnante, différent à chaque ouverture
        double jitter = (Math.floorMod(id.hashCode(), 1000) / 1000.0 - 0.5) * 0.7;
        double target = n.win * pitch + cw / 2.0 + jitter * cw - n.w / 2.0;
        double off = target * ease;
        UiDraw.rr(g, n.x, n.y, n.w, n.h, 6, UiDraw.FIELD);
        g.enableScissor(n.x + 1, n.y + 1, n.x + n.w - 1, n.y + n.h - 1);
        int first = Math.max(0, (int) Math.floor(off / pitch) - 1);
        boolean done = t >= 1;
        float pulse = (float) (0.5 + 0.5 * Math.sin(now / 160.0));
        for (int i = 0; i < n.kids.size(); i++) {
            UiView.Node k = n.kids.get(i);
            int x = (int) Math.round(n.x + i * pitch - off), y = n.y + 6;
            if (i < first || x > n.x + n.w) { k.w = 0; continue; }
            k.x = Math.max(n.x, x);
            k.y = y;
            k.w = Math.max(0, Math.min(x + cw, n.x + n.w) - k.x);
            k.h = ch;
            int rc = k.color == UiDraw.TEXT ? UiDraw.TEXT2 : k.color;
            boolean winner = done && i == n.win;
            UiDraw.rr(g, x, y, cw, ch, 5, winner ? UiDraw.alpha(rc, 0.55f + 0.45f * pulse) : UiDraw.LINE);
            UiDraw.rr(g, x + 1, y + 1, cw - 2, ch - 2, 4, winner ? UiDraw.lighten(UiDraw.ROW, 0.08f) : UiDraw.ROW);
            g.fillGradient(x + 1, y + ch / 3, x + cw - 1, y + ch - 1, rc & 0x00FFFFFF, (rc & 0x00FFFFFF) | (winner ? 0x90000000 : 0x55000000));
            g.fill(x + 3, y + ch - 3, x + cw - 3, y + ch - 1, rc);
            int is = Math.max(16, Math.min(32, cw - 18));
            drawItem(g, k.stack, x + (cw - is) / 2, y + (ch - is) / 2 - 2, is, k.num);
        }
        g.disableScissor();
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        // fondu sur les bords (au-dessus des objets et de leur nombre)
        int fade = Math.min(40, n.w / 6), base = UiDraw.FIELD & 0x00FFFFFF;
        for (int i = 0; i < fade; i++) {
            int a = (int) (0xE0 * Math.pow(1 - i / (double) fade, 1.6));
            g.fill(n.x + 1 + i, n.y + 1, n.x + 2 + i, n.y + n.h - 1, a << 24 | base);
            g.fill(n.x + n.w - 2 - i, n.y + 1, n.x + n.w - 1 - i, n.y + n.h - 1, a << 24 | base);
        }
        // repère central
        int mx = n.x + n.w / 2, gold = 0xFFF5C542;
        g.fill(mx - 1, n.y + 3, mx + 1, n.y + n.h - 3, done ? UiDraw.alpha(gold, 0.6f + 0.4f * pulse) : gold);
        for (int i = 0; i < 5; i++) {
            g.fill(mx - 5 + i, n.y + 1 + i, mx + 5 - i, n.y + 2 + i, gold);
            g.fill(mx - 5 + i, n.y + n.h - 2 - i, mx + 5 - i, n.y + n.h - 1 - i, gold);
        }
        g.pose().popPose();
        // sons : une case qui passe sous le repère, puis le gain
        int under = (int) Math.floor((off + n.w / 2.0) / pitch);
        Integer last = spinTick.put(id, under);
        var sm = Minecraft.getInstance().getSoundManager();
        if (!done && last != null && last != under)
            sm.play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HAT.value(), 1.6F + (float) ease * 0.4F, 0.35F));
        if (done && spinDone.add(id) && now - start < n.dur + 1500)
            sm.play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.0F, 0.6F));
    }

    private void drawToasts(GuiGraphics g) {
        long now = System.currentTimeMillis();
        toasts.removeIf(t -> t.until() < now);
        int y = H - 8 - toasts.size() * 19 + 3;
        g.pose().pushPose();
        g.pose().translate(0, 0, 350);
        for (Toast t : toasts) {
            int tw = Math.min(260, font().width(t.text()) + 18);
            int x = W - tw - 8;
            float a = Math.min(1f, (t.until() - now) / 400f);
            UiDraw.rr(g, x, y, tw, 16, 5, UiDraw.alpha(0xF0262836, a));
            g.fill(x, y + 3, x + 2, y + 13, UiDraw.alpha(UiDraw.ACCENT, a));
            UiDraw.line(g, font(), t.text(), x + 9, y + 4, tw - 12, UiDraw.alpha(0xFFFFFFFF, Math.max(0.1f, a)));
            y += 19;
        }
        g.pose().popPose();
    }

    private void drawTooltip(GuiGraphics g, int mx, int my) {
        UiView.Node n = tipNode;
        if (n == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (n.t.equals("inv")) {
            int i = invSlotAt(n, vmx, vmy);
            if (i >= 0 && mc.player != null) {
                ItemStack st = mc.player.getInventory().items.get(i);
                if (!st.isEmpty()) g.renderTooltip(font(), st, mx, my);
            }
            return;
        }
        if (n.t.equals("map")) {
            int[] m = mapGeometry(n);
            if (m == null) return;
            int c = (vmx - m[3]) / m[2], r = (vmy - m[4]) / m[2];
            if (vmx < m[3] || vmy < m[4] || c < 0 || r < 0 || c >= m[0] || r >= m[1]) return;
            char ch = n.rows[r].charAt(c);
            List<Component> l = new ArrayList<>();
            l.add(Txt.mm(n.names.getOrDefault(ch, "<gray>Terres sauvages")));
            l.add(Txt.mm("<dark_gray>Chunk " + (n.cx + c - m[0] / 2) + ", " + (n.cz + r - m[1] / 2)));
            g.renderComponentTooltip(font(), l, mx, my);
            return;
        }
        if (n.t.equals("item") && !n.stack.isEmpty() && n.tip == null) {
            g.renderTooltip(font(), n.stack, mx, my);
            return;
        }
        if (n.tip != null) g.renderComponentTooltip(font(), n.tip, mx, my);
    }

    // =================================================================== entrées
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (root == null) return false;
        double x = vx(mx), y = vy(my);
        UiView.Node top = dialog != null ? dialog : root;
        UiView.Node t = UiView.hitAction(top, x, y);
        if (t != null && t.t.equals("input")) {
            EditBox b = boxes.get(t.id);
            setFocus(b, t.id);
            if (b != null) b.mouseClicked(x, y, button);
            return true;
        }
        setFocus(null, null);
        if (t != null && t.t.equals("worldmap")) {
            if (button == 0) {
                dragMap = t;
                dragX = x;
                dragY = y;
                dragMoved = false;
            } else if (button == 1 && Minecraft.getInstance().player != null) {
                var p = Minecraft.getInstance().player;
                WorldMapView.View v = view(t);
                WorldMapView.focus(v, p.getX(), p.getZ(), Math.max(2.0, v.scale));
                v.pinX = Double.NaN;
            }
            return true;
        }
        if (t == null || button != 0) return true;
        switch (t.t) {
            case "check" -> {
                click();
                checks.put(t.id, !checks.getOrDefault(t.id, false));
                if (t.a != null) send(t.a);
            }
            case "map" -> {
                int[] m = mapGeometry(t);
                if (m != null && t.a != null) {
                    int c = (int) ((x - m[3]) / m[2]), r = (int) ((y - m[4]) / m[2]);
                    if (c >= 0 && r >= 0 && c < m[0] && r < m[1]) {
                        click();
                        send(t.a + ":" + (t.cx + c - m[0] / 2) + "," + (t.cz + r - m[1] / 2));
                    }
                }
            }
            case "inv" -> {
                int i = invSlotAt(t, x, y);
                var p = Minecraft.getInstance().player;
                if (i >= 0 && p != null && !p.getInventory().items.get(i).isEmpty() && t.a != null) {
                    click();
                    send(t.a + ":" + i);
                }
            }
            default -> {
                if (t.a != null && !t.off) {
                    click();
                    if (t.a.equals("dialog:close")) dialog = null;
                    send(t.a);
                }
            }
        }
        return true;
    }

    private void setFocus(EditBox b, String id) {
        if (focused != null && focused != b) focused.setFocused(false);
        focused = b;
        focusedId = id;
        if (b != null) b.setFocused(true);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragMap != null && button == 0) {
            WorldMapView.pan(dragMap, view(dragMap), dx / f, dy / f);
            if (Math.abs(vx(mx) - dragX) + Math.abs(vy(my) - dragY) > 3) dragMoved = true;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragMap != null && button == 0) {
            if (!dragMoved) {
                WorldMapView.View v = view(dragMap);
                double[] b = WorldMapView.blockAt(dragMap, v, vx(mx), vy(my));
                v.pinX = b[0];
                v.pinZ = b[1];
                click();
            }
            dragMap = null;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (root == null) return false;
        UiView.Node wm = UiView.hitAction(dialog != null ? dialog : root, vx(mx), vy(my));
        if (wm != null && wm.t.equals("worldmap")) {
            if (sy != 0) WorldMapView.zoom(wm, view(wm), vx(mx), vy(my), sy > 0 ? 1.25 : 0.8);
            return true;
        }
        UiView.Node l = UiView.hitList(dialog != null ? dialog : root, vx(mx), vy(my));
        if (l != null) {
            l.scroll = Math.max(0, Math.min(l.contentH - l.h, l.scroll - sy * 18));
            if (l.id != null) scrolls.put(l.id, l.scroll);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (focused != null) { setFocus(null, null); return true; }
            if (dialog != null) { dialog = null; send("dialog:close"); return true; }
            onClose();
            return true;
        }
        if (focused != null) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                UiView.Node n = inputNodes.get(focusedId);
                if (n != null && n.a != null) { click(); send(n.a); }
                return true;
            }
            if (key == GLFW.GLFW_KEY_TAB) {
                List<String> ids = new ArrayList<>(boxes.keySet());
                int i = ids.indexOf(focusedId);
                String next = ids.isEmpty() ? null : ids.get((i + 1) % ids.size());
                setFocus(next == null ? null : boxes.get(next), next);
                return true;
            }
            focused.keyPressed(key, scan, mods);
            return true;
        }
        if (fr.fnations.client.FNationsClient.TABLET != null && fr.fnations.client.FNationsClient.TABLET.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (focused != null) return focused.charTyped(c, mods);
        return false;
    }

    @Override
    protected void init() {
        linking = false;
        if (parked == this) parked = null;
    }

    @Override
    public void tick() {
        if (closedByServer) {
            Minecraft.getInstance().setScreen(null);
            return;
        }
        if (refreshSec > 0 && System.currentTimeMillis() >= nextRefresh) {
            nextRefresh = System.currentTimeMillis() + refreshSec * 1000L;
            send("refresh");
        }
    }

    @Override
    public void removed() {
        if (linking) return;
        if (!closedByServer && Minecraft.getInstance().getConnection() != null) ClientPlayNetworking.send(new UiActionPayload("close", "{}"));
    }

    @Override
    public boolean isPauseScreen() { return false; }
}

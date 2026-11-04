package fr.fnations.client.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import fr.fnations.FNations;
import fr.fnations.earth.EarthData;
import fr.fnations.util.Txt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Carte du monde (Terre au 1/1000) : texture du relief générée une fois, zoom, déplacement, territoires. */
final class WorldMapView {
    /** Trois niveaux de détail : grille complète (4320 × 2160), moitié, quart. */
    private static final int LEVELS = 3;
    private static final ResourceLocation[] TEX = new ResourceLocation[LEVELS];
    private static volatile NativeImage[] pending;
    private static boolean ready, started;

    static {
        for (int i = 0; i < LEVELS; i++) TEX[i] = FNations.id("dynamic/worldmap_" + i);
    }

    /** Nation affichée : couleur, chunks revendiqués, capitale. */
    record Nation(String name, String tag, int color, int[] chunks, int[] home) {}

    /** Vue d'une carte : centre (pixels de la grille 4320 × 2160) et échelle (pixels écran par pixel de grille). */
    static final class View {
        double cx, cy, scale;
        boolean init;
        double pinX = Double.NaN, pinZ;
    }

    private WorldMapView() {}

    // ------------------------------------------------------------ texture
    static boolean ensureTexture() {
        if (ready) return true;
        NativeImage[] imgs = pending;
        if (imgs != null) {
            for (int i = 0; i < LEVELS; i++) Minecraft.getInstance().getTextureManager().register(TEX[i], new DynamicTexture(imgs[i]));
            pending = null;
            ready = true;
            return true;
        }
        if (!started) {
            started = true;
            Thread.ofPlatform().daemon().name("fnations-worldmap").start(() -> {
                try {
                    NativeImage[] out = new NativeImage[LEVELS];
                    out[0] = build();
                    for (int i = 1; i < LEVELS; i++) out[i] = half(out[i - 1]);
                    pending = out;
                } catch (Throwable t) {
                    FNations.LOG.error("Carte du monde : génération impossible", t);
                }
            });
        }
        return false;
    }

    /** Réduction de moitié (moyenne de 2 × 2 pixels). */
    private static NativeImage half(NativeImage src) {
        int w = src.getWidth() / 2, h = src.getHeight() / 2;
        NativeImage out = new NativeImage(w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int r = 0, g = 0, b = 0;
                for (int k = 0; k < 4; k++) {
                    int c = src.getPixel(2 * x + (k & 1), 2 * y + (k >> 1));
                    r += (c >> 16) & 255;
                    g += (c >> 8) & 255;
                    b += c & 255;
                }
                out.setPixel(x, y, 0xFF000000 | (r / 4) << 16 | (g / 4) << 8 | (b / 4));
            }
        }
        return out;
    }

    private static int mix(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    private static int scale(int c, double f) {
        int r = (int) Math.min(255, ((c >> 16) & 255) * f), g = (int) Math.min(255, ((c >> 8) & 255) * f), b = (int) Math.min(255, (c & 255) * f);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static NativeImage build() {
        EarthData d = EarthData.get();
        int[] land = {0xFF2C5E9E, 0xFFEEF3FA, 0xFFB9C0A8, 0xFF3F7F3A, 0xFF6FA04B, 0xFFA9C46A, 0xFFCDBB83, 0xFFE9D9A6, 0xFF9E9A92, 0xFF5DA9E9};
        NativeImage img = new NativeImage(EarthData.W, EarthData.H, false);
        for (int y = 0; y < EarthData.H; y++) {
            for (int x = 0; x < EarthData.W; x++) {
                double m = d.meters(x, y);
                int c;
                int cl = d.cover(x, y);
                if (m < 0 || cl == EarthData.WATER) {
                    double depth = -m;
                    c = depth < 200 ? mix(0xFF6FB6EE, 0xFF4C8FD6, depth / 200) : mix(0xFF4C8FD6, 0xFF1B3A6B, (depth - 200) / 5500);
                } else {
                    c = land[Math.min(land.length - 1, cl)];
                    if (m > 1200 && cl != EarthData.ICE) c = mix(c, 0xFF8C7B6B, (m - 1200) / 2500);
                    if (m > 3600) c = mix(c, 0xFFF4F6FA, (m - 3600) / 1800);
                    double nw = d.meters(x - 1, y - 1);
                    double shade = 1 + Math.max(-0.25, Math.min(0.2, (nw - m) / 1400.0));
                    c = scale(c, shade);
                    // frontières entre pays
                    int ci = d.countryIndex(x, y);
                    if (ci > 0 && (border(d, ci, x + 1, y) || border(d, ci, x, y + 1))) c = mix(c, 0xFF2A2622, 0.55);
                }
                img.setPixel(x, y, c);
            }
        }
        return img;
    }

    private static boolean border(EarthData d, int ci, int x, int y) {
        if (y >= EarthData.H) return false;
        int o = d.countryIndex(x, y);
        return o > 0 && o != ci && d.meters(x, y) >= 0;
    }

    // ------------------------------------------------------------ données des nations
    static List<Nation> parse(JsonArray arr) {
        List<Nation> out = new ArrayList<>();
        if (arr == null) return out;
        for (JsonElement e : arr) {
            JsonObject o = e.getAsJsonObject();
            String k = o.has("k") ? o.get("k").getAsString() : "";
            String[] parts = k.isEmpty() ? new String[0] : k.split(";");
            int[] chunks = new int[parts.length * 2];
            int n = 0;
            for (String p : parts) {
                String[] v = p.split(",");
                if (v.length != 2) continue;
                try {
                    chunks[n++] = Integer.parseInt(v[0]);
                    chunks[n++] = Integer.parseInt(v[1]);
                } catch (NumberFormatException ignored) {
                }
            }
            int[] home = null;
            if (o.has("home")) home = new int[]{o.getAsJsonArray("home").get(0).getAsInt(), o.getAsJsonArray("home").get(1).getAsInt()};
            out.add(new Nation(o.get("n").getAsString(), o.has("t") ? o.get("t").getAsString() : "", UiDraw.color(o.get("c").getAsString(), 0xFF5865F2),
                    java.util.Arrays.copyOf(chunks, n), home));
        }
        return out;
    }

    static Map<Long, Nation> index(List<Nation> ns) {
        Map<Long, Nation> m = new HashMap<>();
        for (Nation n : ns) for (int i = 0; i + 1 < n.chunks().length; i += 2) m.put(((long) n.chunks()[i] << 32) ^ (n.chunks()[i + 1] & 0xFFFFFFFFL), n);
        return m;
    }

    // ------------------------------------------------------------ géométrie
    /** Coordonnées de grille « bord » : le pixel i couvre [i, i + 1). */
    static double gx(double blockX) { return (blockX + EarthData.WORLD_W / 2) / EarthData.BLOCKS_PER_PX; }

    static double gy(double blockZ) { return (blockZ + EarthData.WORLD_H / 2) / EarthData.BLOCKS_PER_PX; }

    static double blockX(double gx) { return gx * EarthData.BLOCKS_PER_PX - EarthData.WORLD_W / 2; }

    static double blockZ(double gy) { return gy * EarthData.BLOCKS_PER_PX - EarthData.WORLD_H / 2; }

    static void clamp(View v, int w, int h) {
        double min = Math.max(w / (double) EarthData.W, h / (double) EarthData.H);
        v.scale = Math.max(min, Math.min(12, v.scale));
        double hw = w / (2 * v.scale), hh = h / (2 * v.scale);
        v.cx = Math.max(hw, Math.min(EarthData.W - hw, v.cx));
        v.cy = Math.max(hh, Math.min(EarthData.H - hh, v.cy));
    }

    static void focus(View v, double bx, double bz, double scale) {
        v.cx = gx(bx);
        v.cy = gy(bz);
        v.scale = scale;
        v.init = true;
    }

    /** Pixels réels par pixel virtuel lors du dernier rendu (choix du niveau de détail). */
    private static double lastReal = 2;

    /** Géométrie d'affichage : niveau de détail, coin haut-gauche (grille) et pixels écran par pixel de grille. */
    private record Geo(int level, double u0, double v0, double sx, double sy, int uw, int vh) {
        double x(UiView.Node n, double gx) { return n.x + (gx - u0) * sx; }

        double y(UiView.Node n, double gy) { return n.y + (gy - v0) * sy; }
    }

    private static Geo geo(UiView.Node n, View v) {
        double texPerReal = 1 / Math.max(1e-6, v.scale * lastReal);
        int level = texPerReal >= 4 ? 2 : texPerReal >= 2 ? 1 : 0, k = 1 << level;
        int uw = Math.max(1, (int) Math.round(n.w / v.scale / k)), vh = Math.max(1, (int) Math.round(n.h / v.scale / k));
        uw = Math.min(uw, EarthData.W >> level);
        vh = Math.min(vh, EarthData.H >> level);
        double rw = uw * (double) k, rh = vh * (double) k;
        double u0 = Math.max(0, Math.min(EarthData.W - rw, v.cx - rw / 2)), v0 = Math.max(0, Math.min(EarthData.H - rh, v.cy - rh / 2));
        return new Geo(level, u0, v0, n.w / rw, n.h / rh, uw, vh);
    }

    // ------------------------------------------------------------ rendu
    static void draw(GuiGraphics g, UiView.Node n, View v, List<Nation> nations, Map<Long, Nation> idx, int mx, int my, double realScale) {
        lastReal = realScale;
        Font font = Minecraft.getInstance().font;
        UiDraw.rr(g, n.x, n.y, n.w, n.h, 6, 0xFF1B3A6B);
        var pl = Minecraft.getInstance().player;
        if (!v.init) {
            v.scale = Math.max(n.w / (double) EarthData.W, n.h / (double) EarthData.H) * 2.2;
            if (pl != null) { v.cx = gx(pl.getX()); v.cy = gy(pl.getZ()); }
            else { v.cx = EarthData.W / 2.0; v.cy = EarthData.H / 2.0; }
            v.init = true;
        }
        clamp(v, n.w, n.h);
        if (!ensureTexture()) {
            g.drawCenteredString(font, Txt.mm("<#A3A6B4>Préparation de la carte du monde…"), n.x + n.w / 2, n.y + n.h / 2 - 4, 0xFFFFFFFF);
            return;
        }
        Geo q = geo(n, v);
        int k = 1 << q.level();
        g.enableScissor(n.x, n.y, n.x + n.w, n.y + n.h);
        g.blit(RenderType::guiTextured, TEX[q.level()], n.x, n.y, (float) (q.u0() / k), (float) (q.v0() / k), n.w, n.h, q.uw(), q.vh(),
                EarthData.W >> q.level(), EarthData.H >> q.level());
        // méridiens et parallèles tous les 30°
        for (int lon = -150; lon <= 150; lon += 30) {
            int x = (int) q.x(n, gx(EarthData.blockX(lon)));
            if (x > n.x && x < n.x + n.w) g.fill(x, n.y, x + 1, n.y + n.h, lon == 0 ? 0x28FFFFFF : 0x14FFFFFF);
        }
        for (int lat = -60; lat <= 60; lat += 30) {
            int y = (int) q.y(n, gy(EarthData.blockZ(lat)));
            if (y > n.y && y < n.y + n.h) g.fill(n.x, y, n.x + n.w, y + 1, lat == 0 ? 0x30FFFFFF : 0x14FFFFFF);
        }
        // territoires (un carré par chunk revendiqué)
        for (Nation na : nations) {
            int col = (na.color() & 0x00FFFFFF) | 0xB0000000;
            for (int i = 0; i + 1 < na.chunks().length; i += 2) {
                double bx = na.chunks()[i] * 16, bz = na.chunks()[i + 1] * 16;
                int x = (int) Math.floor(q.x(n, gx(bx))), y = (int) Math.floor(q.y(n, gy(bz)));
                int x2 = Math.max(x + 1, (int) Math.floor(q.x(n, gx(bx + 16)))), y2 = Math.max(y + 1, (int) Math.floor(q.y(n, gy(bz + 16))));
                if (x2 < n.x || y2 < n.y || x > n.x + n.w || y > n.y + n.h) continue;
                g.fill(x, y, x2, y2, col);
            }
        }
        // capitales
        for (Nation na : nations) {
            if (na.home() == null) continue;
            int x = (int) q.x(n, gx(na.home()[0])), y = (int) q.y(n, gy(na.home()[1]));
            if (x < n.x || y < n.y || x > n.x + n.w || y > n.y + n.h) continue;
            UiDraw.rr(g, x - 3, y - 3, 6, 6, 3, 0xFF000000);
            UiDraw.rr(g, x - 2, y - 2, 4, 4, 2, na.color() | 0xFF000000);
            String label = v.scale > 3 ? na.name() + " [" + na.tag() + "]" : na.tag();
            if (v.scale > 0.6) g.drawString(font, Component.literal(label), x + 5, y - 4, 0xFFFFFFFF, true);
        }
        // point épinglé
        if (!Double.isNaN(v.pinX)) {
            int x = (int) q.x(n, gx(v.pinX)), y = (int) q.y(n, gy(v.pinZ));
            g.fill(x, y - 9, x + 1, y, 0xFFFFFFFF);
            UiDraw.rr(g, x - 3, y - 13, 7, 7, 3, 0xFF000000);
            UiDraw.rr(g, x - 2, y - 12, 5, 5, 2, 0xFFF04747);
        }
        // joueur
        if (pl != null) {
            int x = (int) q.x(n, gx(pl.getX())), y = (int) q.y(n, gy(pl.getZ()));
            UiDraw.rr(g, x - 4, y - 4, 8, 8, 4, 0xFFFFFFFF);
            UiDraw.rr(g, x - 3, y - 3, 6, 6, 3, UiDraw.ACCENT);
        }
        // échelle
        double kmPerPx = EarthData.BLOCKS_PER_PX / q.sx();
        double[] steps = {10, 25, 50, 100, 250, 500, 1000, 2500, 5000};
        double km = steps[0];
        for (double st : steps) if (st / kmPerPx <= n.w / 4.0) km = st;
        int len = (int) (km / kmPerPx);
        int bx0 = n.x + 8, by0 = n.y + n.h - 12;
        UiDraw.rr(g, bx0 - 4, by0 - 13, Math.max(len, font.width((int) km + " km")) + 8, 19, 4, 0x90111219);
        g.fill(bx0, by0, bx0 + len, by0 + 2, 0xFFFFFFFF);
        g.fill(bx0, by0 - 2, bx0 + 1, by0 + 2, 0xFFFFFFFF);
        g.fill(bx0 + len - 1, by0 - 2, bx0 + len, by0 + 2, 0xFFFFFFFF);
        g.drawString(font, Component.literal((int) km + " km"), bx0, by0 - 10, 0xFFFFFFFF, true);
        // infos sous la souris (ou point épinglé)
        double ix = Double.NaN, iz = 0;
        if (n.contains(mx, my)) { ix = blockX(q.u0() + (mx - n.x) / q.sx()); iz = blockZ(q.v0() + (my - n.y) / q.sy()); }
        else if (!Double.isNaN(v.pinX)) { ix = v.pinX; iz = v.pinZ; }
        if (!Double.isNaN(ix)) {
            String[] place = EarthData.get().place(ix, iz);
            Nation owner = idx.get(((long) Math.floorDiv((int) Math.floor(ix), 16) << 32) ^ (Math.floorDiv((int) Math.floor(iz), 16) & 0xFFFFFFFFL));
            List<Component> lines = new ArrayList<>();
            lines.add(Txt.mm("<b>" + (place[0].isEmpty() ? "Océan" : place[0]) + "</b>" + (place[1].isEmpty() ? "" : " <#A3A6B4>(" + place[1] + ")")));
            lines.add(Txt.mm("<#A3A6B4>" + EarthData.latLon(ix, iz) + " • X " + (int) ix + " Z " + (int) iz));
            if (owner != null) lines.add(Txt.mm("<#43D17A>Territoire de " + owner.name()));
            int bw = 0;
            for (Component c : lines) bw = Math.max(bw, font.width(c));
            int bxI = n.x + n.w - bw - 14, byI = n.y + n.h - 10 - lines.size() * 10 - 4;
            UiDraw.rr(g, bxI, byI, bw + 10, lines.size() * 10 + 6, 4, 0xD0111219);
            for (int i = 0; i < lines.size(); i++) g.drawString(font, lines.get(i), bxI + 5, byI + 4 + i * 10, 0xFFFFFFFF, false);
        }
        g.disableScissor();
    }

    /** Coordonnées de bloc sous un point de la carte. */
    static double[] blockAt(UiView.Node n, View v, double mx, double my) {
        Geo q = geo(n, v);
        return new double[]{blockX(q.u0() + (mx - n.x) / q.sx()), blockZ(q.v0() + (my - n.y) / q.sy())};
    }

    /** Zoom en gardant le point sous la souris immobile. */
    static void zoom(UiView.Node n, View v, double mx, double my, double factor) {
        double[] before = blockAt(n, v, mx, my);
        v.scale *= factor;
        clamp(v, n.w, n.h);
        double[] after = blockAt(n, v, mx, my);
        v.cx += gx(before[0]) - gx(after[0]);
        v.cy += gy(before[1]) - gy(after[1]);
        clamp(v, n.w, n.h);
    }

    static void pan(UiView.Node n, View v, double dx, double dy) {
        v.cx -= dx / v.scale;
        v.cy -= dy / v.scale;
        clamp(v, n.w, n.h);
    }
}

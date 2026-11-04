package fr.fnations.client.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import fr.fnations.util.Txt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Arbre d'éléments reçu du serveur et moteur de mise en page (lignes, colonnes, listes, grilles). */
final class UiView {
    static final int BAR = 5;

    private UiView() {}

    static Font font() { return Minecraft.getInstance().font; }

    // =================================================================== nœud
    static final class Node {
        final String t;
        final List<Node> kids = new ArrayList<>();
        int x, y, w, h;
        boolean hidden;
        final int fw, fh, gap, padX, padY, r, size, maxLines, cols, num, max;
        final double flex;
        final int bg, bd, color;
        final float scale;
        final String a, id, align, valign, style, icon, filter, keys, label, placeholder, value;
        final boolean off, sel, hov, wrap, fill, glint, numeric, force, checked, noTip;
        final Component comp;
        final List<Component> tip;
        ItemStack stack = ItemStack.EMPTY;
        // liste
        double scroll;
        int contentH;
        // carte
        String[] rows;
        final Map<Character, Integer> pal = new HashMap<>();
        final Map<Character, String> names = new HashMap<>();
        int cx, cz, radius;
        boolean terrainUnder;
        // tête
        UUID uuid;
        String name;
        // roulette (LootBox) : index gagnant, durée et temps déjà écoulé (ms)
        int win;
        long dur, el;
        // carte du monde : nations et index chunk → nation
        List<WorldMapView.Nation> nations;
        Map<Long, WorldMapView.Nation> nationIdx;

        Node(JsonObject j, HolderLookup.Provider regs) {
            t = str(j, "t", "col");
            fw = num(j, "w", 0);
            fh = num(j, "h", 0);
            gap = num(j, "gap", t.equals("row") ? 6 : t.equals("col") || t.equals("list") || t.equals("grid") ? 4 : 0);
            int pad = num(j, "pad", 0);
            padX = num(j, "padx", pad);
            padY = num(j, "pady", pad);
            r = num(j, "r", t.equals("btn") || t.equals("input") ? 4 : 0);
            size = num(j, "size", t.equals("icon") ? 12 : t.equals("head") ? 16 : 16);
            maxLines = num(j, "lines", 0);
            cols = num(j, "cols", 9);
            num = num(j, "n", 1);
            max = num(j, "max", 64);
            flex = j.has("flex") ? j.get("flex").getAsDouble() : 0;
            bg = UiDraw.color(str(j, "bg", null), 0);
            bd = UiDraw.color(str(j, "bd", null), 0);
            color = UiDraw.color(str(j, "color", null), UiDraw.TEXT);
            scale = j.has("scale") ? j.get("scale").getAsFloat() : 1f;
            a = str(j, "a", null);
            id = str(j, "id", null);
            align = str(j, "align", "left");
            valign = str(j, "valign", "center");
            style = str(j, "style", "secondary");
            icon = str(j, "i", null);
            filter = str(j, "filter", null);
            keys = str(j, "k", null);
            label = str(j, "label", null);
            placeholder = str(j, "ph", null);
            value = str(j, "v", null);
            off = bool(j, "off");
            sel = bool(j, "sel");
            hov = bool(j, "hov");
            wrap = bool(j, "wrap");
            fill = bool(j, "fill");
            glint = bool(j, "glint");
            numeric = bool(j, "num");
            force = bool(j, "force");
            checked = bool(j, "v");
            noTip = bool(j, "notip");
            if (t.equals("item") || t.equals("iname")) stack = decodeItem(j, regs);
            if (t.equals("iname")) {
                Component hn = stack.isEmpty() ? Component.literal("?") : stack.getHoverName();
                comp = bool(j, "b") ? hn.copy().withStyle(net.minecraft.ChatFormatting.BOLD) : hn;
            } else {
                String s = t.equals("text") ? str(j, "s", "") : label;
                comp = s == null ? Component.empty() : Txt.mm(s);
            }
            List<Component> tl = null;
            if (j.has("tip") && j.get("tip").isJsonArray()) {
                tl = new ArrayList<>();
                for (JsonElement e : j.getAsJsonArray("tip")) tl.add(Txt.mm(e.getAsString()));
            }
            tip = tl;
            if (t.equals("head")) {
                try { uuid = UUID.fromString(str(j, "uuid", "")); } catch (IllegalArgumentException e) { uuid = null; }
                name = str(j, "name", "?");
            }
            if (t.equals("map")) {
                JsonArray ra = j.getAsJsonArray("rows");
                rows = new String[ra.size()];
                for (int i = 0; i < rows.length; i++) rows[i] = ra.get(i).getAsString();
                if (j.has("pal")) for (var e : j.getAsJsonObject("pal").entrySet())
                    pal.put(e.getKey().charAt(0), UiDraw.color(e.getValue().getAsString(), 0));
                if (j.has("names")) for (var e : j.getAsJsonObject("names").entrySet())
                    names.put(e.getKey().charAt(0), e.getValue().getAsString());
                cx = num(j, "cx", 0);
                cz = num(j, "cz", 0);
                terrainUnder = bool(j, "terrain");
            }
            if (t.equals("terrain")) radius = num(j, "radius", 48);
            if (t.equals("roulette")) {
                win = num(j, "win", 0);
                dur = j.has("dur") ? j.get("dur").getAsLong() : 5000;
                el = j.has("el") ? j.get("el").getAsLong() : 0;
            }
            if (t.equals("worldmap")) {
                nations = WorldMapView.parse(j.has("nations") && j.get("nations").isJsonArray() ? j.getAsJsonArray("nations") : null);
                nationIdx = WorldMapView.index(nations);
            }
            if (j.has("c")) for (JsonElement e : j.getAsJsonArray("c")) if (e.isJsonObject()) kids.add(new Node(e.getAsJsonObject(), regs));
        }

        boolean clickable() {
            if (off) return false;
            return a != null || t.equals("input") || t.equals("check");
        }

        boolean tipped() { return tip != null || (t.equals("item") && !noTip && !stack.isEmpty()); }

        boolean contains(double mx, double my) { return mx >= x && my >= y && mx < x + w && my < y + h; }
    }

    private static String str(JsonObject j, String k, String def) { return j.has(k) && !j.get(k).isJsonNull() ? j.get(k).getAsString() : def; }

    private static int num(JsonObject j, String k, int def) { return j.has(k) ? (int) Math.round(j.get(k).getAsDouble()) : def; }

    private static boolean bool(JsonObject j, String k) { return j.has(k) && j.get(k).isJsonPrimitive() && j.get(k).getAsJsonPrimitive().isBoolean() && j.get(k).getAsBoolean(); }

    private static ItemStack decodeItem(JsonObject j, HolderLookup.Provider regs) {
        ItemStack st = ItemStack.EMPTY;
        if (j.has("st") && regs != null) {
            st = ItemStack.CODEC.parse(regs.createSerializationContext(JsonOps.INSTANCE), j.get("st")).result().orElse(ItemStack.EMPTY);
        }
        if (st.isEmpty() && j.has("id")) {
            ResourceLocation rl = ResourceLocation.tryParse(j.get("id").getAsString());
            var item = rl == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(rl);
            st = item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
        }
        if (!st.isEmpty() && bool(j, "glint")) st.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return st;
    }

    // =================================================================== tailles naturelles
    static int lineH(Node n) { return Math.round(10 * n.scale); }

    static List<FormattedCharSequence> lines(Node n, int w) {
        int avail = Math.max(10, (int) (w / n.scale));
        List<FormattedCharSequence> l = font().split(n.comp, avail);
        if (n.maxLines > 0 && l.size() > n.maxLines) l = new ArrayList<>(l.subList(0, n.maxLines));
        return l;
    }

    static int btnContentW(Node n) {
        int tw = font().width(n.comp);
        int iw = n.icon == null ? 0 : (tw > 0 ? 14 : 12);
        return tw + iw;
    }

    static int prefW(Node n) {
        if (n.fw > 0) return n.fw;
        return switch (n.t) {
            case "text", "iname" -> (int) Math.ceil(font().width(n.comp) * n.scale);
            case "btn" -> btnContentW(n) + (n.style.equals("tab") ? 12 : 16);
            case "icon", "item", "head" -> n.size;
            case "input" -> 90;
            case "check" -> 14 + font().width(n.comp);
            case "chip" -> font().width(n.comp) + 10;
            case "bar" -> 60;
            case "row" -> {
                int s = 0, k = 0;
                for (Node c : n.kids) if (!c.hidden) { s += prefW(c); k++; }
                yield s + Math.max(0, k - 1) * n.gap + 2 * n.padX;
            }
            case "col", "list" -> {
                int m = 0;
                for (Node c : n.kids) m = Math.max(m, prefW(c));
                yield m + 2 * n.padX + (n.t.equals("list") ? BAR + 1 : 0);
            }
            case "grid" -> {
                int m = 0;
                for (Node c : n.kids) m = Math.max(m, prefW(c));
                int cols = Math.max(1, n.cols);
                yield cols * m + (cols - 1) * n.gap + 2 * n.padX;
            }
            case "map" -> n.rows.length == 0 ? 0 : n.rows[0].length() * 8;
            case "inv" -> Math.max(1, n.cols) * 18;
            case "player" -> 50;
            case "terrain" -> 96;
            case "roulette", "worldmap" -> 200;
            default -> 0;
        };
    }

    static int prefH(Node n, int w) {
        if (n.fh > 0) return n.fh;
        return switch (n.t) {
            case "text", "iname" -> {
                int count = n.wrap ? Math.max(1, lines(n, w).size()) : 1;
                yield (count - 1) * lineH(n) + Math.round(9 * n.scale);
            }
            case "btn" -> 16;
            case "icon", "item", "head" -> n.size;
            case "input" -> 16;
            case "check", "chip" -> 12;
            case "bar" -> 6;
            case "sep" -> 1;
            case "row" -> {
                int[] ws = rowWidths(n, w);
                int m = 0;
                for (int i = 0; i < n.kids.size(); i++) if (!n.kids.get(i).hidden) m = Math.max(m, prefH(n.kids.get(i), ws[i]));
                yield m + 2 * n.padY;
            }
            case "col", "list" -> {
                int inner = w - 2 * n.padX - (n.t.equals("list") ? BAR + 1 : 0), s = 0, k = 0;
                for (Node c : n.kids) {
                    if (c.hidden) continue;
                    s += prefH(c, kidWidth(c, inner));
                    k++;
                }
                yield s + Math.max(0, k - 1) * n.gap + 2 * n.padY;
            }
            case "grid" -> {
                int cols = Math.max(1, n.cols), cw = (w - 2 * n.padX - (cols - 1) * n.gap) / cols, s = 0, rowsN = 0;
                for (int i = 0; i < n.kids.size(); i += cols) {
                    int m = 0;
                    for (int k = i; k < Math.min(n.kids.size(), i + cols); k++) m = Math.max(m, prefH(n.kids.get(k), cw));
                    s += m;
                    rowsN++;
                }
                yield s + Math.max(0, rowsN - 1) * n.gap + 2 * n.padY;
            }
            case "map" -> n.rows.length * 8;
            case "inv" -> (int) Math.ceil(36.0 / Math.max(1, n.cols)) * 18;
            case "player" -> 80;
            case "terrain" -> w;
            case "roulette" -> 70;
            case "worldmap" -> 140;
            default -> 0;
        };
    }

    /** Les conteneurs, textes et champs s'étirent en largeur dans une colonne ; les boutons gardent leur taille. */
    static boolean stretchW(Node c) {
        return c.fill || switch (c.t) {
            case "col", "row", "list", "grid", "text", "iname", "bar", "sep", "input", "map", "terrain", "roulette", "worldmap" -> true;
            default -> false;
        };
    }

    static int kidWidth(Node c, int inner) {
        if (c.fw > 0) return Math.min(c.fw, inner);
        if (stretchW(c)) return inner;
        return Math.min(prefW(c), inner);
    }

    static int[] rowWidths(Node n, int w) {
        int k = n.kids.size();
        int[] ws = new int[k];
        int visible = 0;
        for (Node c : n.kids) if (!c.hidden) visible++;
        int inner = w - 2 * n.padX - Math.max(0, visible - 1) * n.gap;
        double flex = 0;
        int fixed = 0;
        for (int i = 0; i < k; i++) {
            Node c = n.kids.get(i);
            if (c.hidden) continue;
            if (c.flex > 0 && c.fw <= 0) flex += c.flex;
            else { ws[i] = prefW(c); fixed += ws[i]; }
        }
        int rem = inner - fixed;
        if (flex > 0) {
            int left = Math.max(0, rem);
            for (int i = 0; i < k; i++) {
                Node c = n.kids.get(i);
                if (c.hidden || !(c.flex > 0 && c.fw <= 0)) continue;
                ws[i] = (int) Math.floor(Math.max(0, rem) * c.flex / flex);
                left -= ws[i];
            }
            for (int i = k - 1; i >= 0 && left > 0; i--) {
                Node c = n.kids.get(i);
                if (!c.hidden && c.flex > 0 && c.fw <= 0) { ws[i] += left; break; }
            }
        } else if (rem < 0) {
            // trop large : on rétrécit les textes et conteneurs sans largeur fixe
            int shrinkable = 0;
            for (int i = 0; i < k; i++) if (!n.kids.get(i).hidden && n.kids.get(i).fw <= 0 && stretchW(n.kids.get(i))) shrinkable += ws[i];
            if (shrinkable > 0) {
                double ratio = Math.max(0, (shrinkable + rem) / (double) shrinkable);
                for (int i = 0; i < k; i++) {
                    Node c = n.kids.get(i);
                    if (!c.hidden && c.fw <= 0 && stretchW(c)) ws[i] = (int) Math.floor(ws[i] * ratio);
                }
            }
        }
        return ws;
    }

    // =================================================================== placement
    interface Hooks {
        void input(Node n);

        void check(Node n);

        String filterText(String inputId);
    }

    static void layout(Node n, int x, int y, int w, int h, Hooks hooks) {
        n.x = x;
        n.y = y;
        n.w = Math.max(0, w);
        n.h = Math.max(0, h);
        switch (n.t) {
            case "row" -> layoutRow(n, hooks);
            case "col" -> layoutCol(n, hooks);
            case "list" -> layoutList(n, hooks);
            case "grid" -> layoutGrid(n, hooks);
            case "input" -> hooks.input(n);
            case "check" -> hooks.check(n);
            default -> {
            }
        }
    }

    private static boolean stretchV(Node c) {
        return switch (c.t) {
            case "col", "list", "grid", "worldmap" -> true;
            default -> false;
        };
    }

    private static void layoutRow(Node n, Hooks hooks) {
        int[] ws = rowWidths(n, n.w);
        int innerH = n.h - 2 * n.padY;
        int cx = n.x + n.padX;
        for (int i = 0; i < n.kids.size(); i++) {
            Node c = n.kids.get(i);
            if (c.hidden) continue;
            int ch;
            if (c.fh > 0) ch = Math.min(c.fh, innerH);
            else if (stretchV(c) || (c.t.equals("row") && c.bg != 0)) ch = innerH;
            else ch = Math.min(innerH, prefH(c, ws[i]));
            int cy = n.y + n.padY + switch (n.valign) {
                case "top" -> 0;
                case "bottom" -> innerH - ch;
                default -> (innerH - ch) / 2;
            };
            layout(c, cx, cy, ws[i], ch, hooks);
            cx += ws[i] + n.gap;
        }
    }

    private static void layoutCol(Node n, Hooks hooks) {
        int innerW = n.w - 2 * n.padX, innerH = n.h - 2 * n.padY;
        int k = n.kids.size(), visible = 0;
        int[] hs = new int[k];
        double flex = 0;
        int fixed = 0;
        for (int i = 0; i < k; i++) {
            Node c = n.kids.get(i);
            if (c.hidden) continue;
            visible++;
            if (c.flex > 0 && c.fh <= 0) flex += c.flex;
            else { hs[i] = prefH(c, kidWidth(c, innerW)); fixed += hs[i]; }
        }
        int rem = innerH - fixed - Math.max(0, visible - 1) * n.gap;
        if (flex > 0) for (int i = 0; i < k; i++) {
            Node c = n.kids.get(i);
            if (!c.hidden && c.flex > 0 && c.fh <= 0) hs[i] = (int) Math.max(0, Math.floor(rem * c.flex / flex));
        }
        int cy = n.y + n.padY;
        for (int i = 0; i < k; i++) {
            Node c = n.kids.get(i);
            if (c.hidden) continue;
            int cw = kidWidth(c, innerW);
            int cxo = switch (n.align) {
                case "center" -> (innerW - cw) / 2;
                case "right" -> innerW - cw;
                default -> 0;
            };
            layout(c, n.x + n.padX + cxo, cy, cw, hs[i], hooks);
            cy += hs[i] + n.gap;
        }
    }

    static String plain(Node n) {
        StringBuilder sb = new StringBuilder();
        collectText(n, sb);
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private static void collectText(Node n, StringBuilder sb) {
        if (n.keys != null) sb.append(n.keys).append(' ');
        if (n.t.equals("text") || n.t.equals("iname") || n.t.equals("btn") || n.t.equals("chip")) sb.append(n.comp.getString()).append(' ');
        if (n.t.equals("item") && !n.stack.isEmpty()) sb.append(n.stack.getHoverName().getString()).append(' ');
        for (Node c : n.kids) collectText(c, sb);
    }

    private static void layoutList(Node n, Hooks hooks) {
        String q = n.filter == null ? "" : hooks.filterText(n.filter).trim().toLowerCase(Locale.ROOT);
        for (Node c : n.kids) c.hidden = !q.isEmpty() && !plain(c).contains(q);
        int innerW = n.w - 2 * n.padX - BAR - 1;
        int total = 0, visible = 0;
        for (Node c : n.kids) {
            if (c.hidden) continue;
            total += prefH(c, innerW);
            visible++;
        }
        total += Math.max(0, visible - 1) * n.gap + 2 * n.padY;
        n.contentH = total;
        n.scroll = Math.max(0, Math.min(n.scroll, Math.max(0, total - n.h)));
        int cy = n.y + n.padY - (int) Math.round(n.scroll);
        for (Node c : n.kids) {
            if (c.hidden) continue;
            int ch = prefH(c, innerW);
            layout(c, n.x + n.padX, cy, innerW, ch, hooks);
            cy += ch + n.gap;
        }
    }

    private static void layoutGrid(Node n, Hooks hooks) {
        int cols = Math.max(1, n.cols);
        int cw = (n.w - 2 * n.padX - (cols - 1) * n.gap) / cols;
        int cy = n.y + n.padY;
        for (int i = 0; i < n.kids.size(); i += cols) {
            int m = 0;
            for (int k = i; k < Math.min(n.kids.size(), i + cols); k++) m = Math.max(m, prefH(n.kids.get(k), cw));
            for (int k = i; k < Math.min(n.kids.size(), i + cols); k++) {
                layout(n.kids.get(k), n.x + n.padX + (k - i) * (cw + n.gap), cy, cw, m, hooks);
            }
            cy += m + n.gap;
        }
    }

    // =================================================================== recherche sous la souris
    /** Élément cliquable le plus profond sous la souris. */
    static Node hitAction(Node n, double mx, double my) {
        if (n.hidden || !n.contains(mx, my)) return null;
        for (int i = n.kids.size() - 1; i >= 0; i--) {
            Node r = hitAction(n.kids.get(i), mx, my);
            if (r != null) return r;
        }
        return n.clickable() || n.t.equals("map") || n.t.equals("inv") || n.t.equals("worldmap") ? n : null;
    }

    /** Élément à info-bulle le plus profond sous la souris. */
    static Node hitTip(Node n, double mx, double my) {
        if (n.hidden || !n.contains(mx, my)) return null;
        for (int i = n.kids.size() - 1; i >= 0; i--) {
            Node r = hitTip(n.kids.get(i), mx, my);
            if (r != null) return r;
        }
        return n.tipped() || n.t.equals("map") || n.t.equals("inv") ? n : null;
    }

    /** Liste défilante la plus profonde sous la souris. */
    static Node hitList(Node n, double mx, double my) {
        if (n.hidden || !n.contains(mx, my)) return null;
        for (int i = n.kids.size() - 1; i >= 0; i--) {
            Node r = hitList(n.kids.get(i), mx, my);
            if (r != null) return r;
        }
        return n.t.equals("list") && n.contentH > n.h ? n : null;
    }

    /** Premier élément portant cette action (pour les tests automatiques). */
    static Node find(Node n, String action) {
        if (action.equals(n.a)) return n;
        for (Node c : n.kids) {
            Node r = find(c, action);
            if (r != null) return r;
        }
        return null;
    }

    static void collect(Node n, String type, List<Node> out) {
        if (n.t.equals(type)) out.add(n);
        for (Node c : n.kids) collect(c, type, out);
    }
}

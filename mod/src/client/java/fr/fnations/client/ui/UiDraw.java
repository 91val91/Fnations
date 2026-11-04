package fr.fnations.client.ui;

import fr.fnations.FNations;
import fr.fnations.ui.UiIcons;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/** Primitives de dessin de l'interface : couleurs, rectangles arrondis, icônes, texte tronqué. */
public final class UiDraw {
    public static final ResourceLocation ICONS = FNations.id("textures/gui/ui_icons.png");

    public static final int BG = 0xFF16171F, NAV = 0xFF0F1016, PANEL = 0xFF1E1F2A, ROW = 0xFF262836, ROW_HOVER = 0xFF30323F,
            FIELD = 0xFF111219, LINE = 0xFF2E3040, ACCENT = 0xFF5865F2, ACCENT_HOVER = 0xFF6D79F5, TEXT = 0xFFFFFFFF,
            TEXT2 = 0xFFA3A6B4, MUTED = 0xFF6C6F7E, GREEN = 0xFF43D17A, RED = 0xFFF04747, ORANGE = 0xFFF5A623,
            ACCENT_SOFT_BG = 0xFF2B2F5C;

    private UiDraw() {}

    /** "#RRGGBB" ou "#AARRGGBB" → ARGB. */
    public static int color(String s, int def) {
        if (s == null || s.isEmpty()) return def;
        try {
            String h = s.startsWith("#") ? s.substring(1) : s;
            long v = Long.parseLong(h, 16);
            return h.length() <= 6 ? (int) (0xFF000000L | v) : (int) v;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static int lighten(int c, float f) {
        int a = c >>> 24, r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        r = Math.min(255, (int) (r + (255 - r) * f));
        g = Math.min(255, (int) (g + (255 - g) * f));
        b = Math.min(255, (int) (b + (255 - b) * f));
        return a << 24 | r << 16 | g << 8 | b;
    }

    public static int alpha(int c, float a) { return ((int) (Math.max(0, Math.min(1, a)) * (c >>> 24)) << 24) | (c & 0xFFFFFF); }

    /** Rectangle aux coins arrondis (rayon en pixels virtuels). */
    public static void rr(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) { g.fill(x, y, x + w, y + h, color); return; }
        g.fill(x, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            double d = r - i - 0.5;
            int inset = r - (int) Math.round(Math.sqrt(r * r - d * d));
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            g.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color);
        }
    }

    /** Contour arrondi d'un pixel. */
    public static void outline(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        rr(g, x, y, w, h, r, color);
    }

    public static void icon(GuiGraphics g, String name, int x, int y, int size, int color) {
        int i = UiIcons.index(name);
        if (i < 0) return;
        g.blit(RenderType::guiTextured, ICONS, x, y, (i % 16) * 16, (i / 16) * 16, size, size, 16, 16, 256, 256, color);
    }

    /** Texte sur une ligne, tronqué avec « … » s'il dépasse maxW. */
    public static void line(GuiGraphics g, Font font, Component c, int x, int y, int maxW, int color) {
        if (maxW <= 0) return;
        if (font.width(c) <= maxW) {
            g.drawString(font, c, x, y, color, false);
            return;
        }
        int ell = font.width("…");
        FormattedText cut = font.substrByWidth(c, Math.max(0, maxW - ell));
        FormattedCharSequence seq = Language.getInstance().getVisualOrder(FormattedText.composite(cut, Component.literal("…")));
        g.drawString(font, seq, x, y, color, false);
    }
}

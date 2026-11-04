package fr.fnations.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Mini-parseur façon MiniMessage : {@code <gold>Texte <b>gras</b></gold>}, couleurs nommées, {@code <#ff8800>},
 * {@code <b> <i> <u> <st> <obf> <reset>}, échappement {@code \<}. Permet de réutiliser tous les textes du plugin.
 */
public final class Txt {
    public static final String PREFIX = "<dark_gray>[<gold>FNations<dark_gray>]</dark_gray> <gray>";
    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.FRANCE));

    private Txt() {}

    /** Analyse un texte balisé en composant. */
    public static MutableComponent mm(String in) {
        MutableComponent root = Component.empty();
        Deque<Style> stack = new ArrayDeque<>();
        Deque<String> names = new ArrayDeque<>();
        Style style = Style.EMPTY;
        StringBuilder buf = new StringBuilder();
        int i = 0;
        while (i < in.length()) {
            char c = in.charAt(i);
            if (c == '\\' && i + 1 < in.length() && in.charAt(i + 1) == '<') {
                buf.append('<');
                i += 2;
                continue;
            }
            if (c == '<') {
                int end = in.indexOf('>', i);
                if (end > i) {
                    String rawTag = in.substring(i + 1, end).trim();
                    if (rawTag.startsWith("item:") || rawTag.startsWith("lang:")) {
                        flush(root, buf, style);
                        String ref = rawTag.substring(5);
                        String key = rawTag.startsWith("item:") ? itemKey(ref) : ref;
                        root.append(Component.translatable(key).setStyle(style));
                        i = end + 1;
                        continue;
                    }
                    String tag = rawTag.toLowerCase(Locale.ROOT);
                    boolean closing = tag.startsWith("/");
                    String name = closing ? tag.substring(1) : tag;
                    Style next = closing ? null : apply(style, name);
                    if (closing || next != null) {
                        flush(root, buf, style);
                        if (closing) {
                            // dépile jusqu'à la balise correspondante (ou une seule si nom vide)
                            while (!names.isEmpty()) {
                                String n = names.pop();
                                style = stack.pop();
                                if (name.isEmpty() || n.equals(name) || alias(n).equals(alias(name))) break;
                            }
                        } else if (name.equals("reset")) {
                            stack.clear();
                            names.clear();
                            style = Style.EMPTY;
                        } else {
                            stack.push(style);
                            names.push(name);
                            style = next;
                        }
                        i = end + 1;
                        continue;
                    }
                }
            }
            buf.append(c);
            i++;
        }
        flush(root, buf, style);
        return root;
    }

    /** Clé de traduction d'un item ("fnations:steel_plate" ou "minecraft:iron_ingot"). */
    private static String itemKey(String ref) {
        net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(ref);
        if (id == null) return ref;
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(id)
                .map(net.minecraft.world.item.Item::getDescriptionId).orElse(ref);
    }

    private static String alias(String n) {
        return switch (n) {
            case "bold" -> "b";
            case "italic", "em" -> "i";
            case "underlined" -> "u";
            case "strikethrough" -> "st";
            case "obfuscated" -> "obf";
            default -> n;
        };
    }

    private static Style apply(Style s, String tag) {
        switch (alias(tag)) {
            case "b": return s.withBold(true);
            case "i": return s.withItalic(true);
            case "u": return s.withUnderlined(true);
            case "st": return s.withStrikethrough(true);
            case "obf": return s.withObfuscated(true);
            case "reset": return Style.EMPTY;
            default:
        }
        if (tag.startsWith("#") && tag.length() == 7) {
            try {
                return s.withColor(TextColor.fromRgb(Integer.parseInt(tag.substring(1), 16)));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        ChatFormatting f = ChatFormatting.getByName(tag);
        if (f != null && f.isColor()) return s.withColor(f);
        return null;
    }

    private static void flush(MutableComponent root, StringBuilder buf, Style style) {
        if (buf.isEmpty()) return;
        root.append(Component.literal(buf.toString()).setStyle(style));
        buf.setLength(0);
    }

    /** Composant sans italique (noms et lore d'items). */
    public static MutableComponent c(String mini) {
        return Component.empty().withStyle(s -> s.withItalic(false)).append(mm(mini));
    }

    public static List<Component> lines(List<String> mini) {
        List<Component> out = new ArrayList<>();
        for (String s : mini) out.add(c(s));
        return out;
    }

    public static MutableComponent prefixed(String mini) { return mm(PREFIX + mini); }

    public static String plain(String mini) { return mm(mini).getString(); }

    /** Échappe un texte saisi par un joueur. */
    public static String esc(String s) { return s == null ? "" : s.replace("<", "\\<"); }

    public static String money(double v) { return MONEY.format(v) + " $"; }

    public static String duration(long seconds) {
        if (seconds < 0) seconds = 0;
        long d = seconds / 86400, h = (seconds % 86400) / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("j ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (d == 0 && h == 0) sb.append(s).append("s");
        return sb.toString().trim();
    }

    public static String progress(double ratio, int len, String full, String empty) {
        ratio = Math.max(0, Math.min(1, ratio));
        int f = (int) Math.round(ratio * len);
        return "<" + full + ">" + "|".repeat(f) + "<" + empty + ">" + "|".repeat(len - f);
    }

    public static Double parseAmount(String s) {
        try {
            double v = Double.parseDouble(s.replace(",", ".").replace(" ", ""));
            if (v <= 0 || Double.isNaN(v) || Double.isInfinite(v)) return null;
            return Math.floor(v * 100) / 100.0;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

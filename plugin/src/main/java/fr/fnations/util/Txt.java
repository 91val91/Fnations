package fr.fnations.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Utilitaires de texte (MiniMessage) et de formatage. */
public final class Txt {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    public static final String PREFIX = "<dark_gray>[<gold>FNations<dark_gray>]</dark_gray> <gray>";
    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.FRANCE));

    private Txt() {}

    /** Composant sans italique (pour noms/lore d'items). */
    public static Component c(String mini) {
        return MM.deserialize(mini).decoration(TextDecoration.ITALIC, false);
    }

    public static List<Component> lines(List<String> mini) {
        List<Component> out = new ArrayList<>();
        for (String s : mini) out.add(c(s));
        return out;
    }

    public static void send(CommandSender to, String mini) {
        to.sendMessage(MM.deserialize(PREFIX + mini));
    }

    public static void ok(CommandSender to, String mini) { send(to, "<green>" + mini); }

    public static void err(CommandSender to, String mini) { send(to, "<red>" + mini); }

    public static void bar(Player p, String mini) { p.sendActionBar(MM.deserialize(mini)); }

    public static Component raw(String mini) { return MM.deserialize(mini); }

    public static String plain(Component c) {
        return c == null ? "" : PlainTextComponentSerializer.plainText().serialize(c);
    }

    public static String money(double v) { return MONEY.format(v) + " $"; }

    /** Échappe un texte saisi par un joueur pour MiniMessage. */
    public static String esc(String s) { return MM.escapeTags(s); }

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

    /** Barre de progression textuelle. */
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

package fr.fnations.rank;

import net.minecraft.ChatFormatting;

import java.util.List;

/** Grades achetés sur la boutique en ligne. */
public enum Rank {
    NONE("Joueur", "#A3A6B4", ChatFormatting.GRAY, 0, 0, 0, 0, 0),
    PREMIUM("Premium", "#F5A623", ChatFormatting.GOLD, 1, 0.05, 5, 0.5, 500),
    SUPER_PREMIUM("Super Premium", "#3BA7F0", ChatFormatting.AQUA, 2, 0.10, 10, 1.0, 1500),
    ULTRA("Ultra", "#B06BFF", ChatFormatting.LIGHT_PURPLE, 3, 0.15, 20, 2.0, 4000);

    public final String display, color;
    public final ChatFormatting chat;
    public final int level;
    /** Bonus sur les revenus de métier, emplacements HDV en plus, points de taxe HDV en moins, récompense quotidienne. */
    public final double jobBonus, taxCut, daily;
    public final int hdvSlots;

    Rank(String display, String color, ChatFormatting chat, int level, double jobBonus, int hdvSlots, double taxCut, double daily) {
        this.display = display;
        this.color = color;
        this.chat = chat;
        this.level = level;
        this.jobBonus = jobBonus;
        this.hdvSlots = hdvSlots;
        this.taxCut = taxCut;
        this.daily = daily;
    }

    public String id() { return name().toLowerCase(); }

    public static Rank byId(String s) {
        if (s == null) return null;
        String k = s.trim().toLowerCase().replace(" ", "_").replace("-", "_");
        if (k.equals("superpremium")) k = "super_premium";
        for (Rank r : values()) if (r.name().equalsIgnoreCase(k) || r.display.equalsIgnoreCase(s.trim())) return r;
        return null;
    }

    public String tag() { return "<" + color + "><b>[" + display + "]</b>"; }

    /** Avantages affichés dans la boutique. */
    public List<String> perks() {
        return switch (this) {
            case NONE -> List.of();
            case PREMIUM -> List.of("Préfixe [Premium] doré", "+5 % revenus de métier", "+5 ventes à l'HDV", "Taxe HDV -0,5 point",
                    "500 $ par jour", "1 LootBox Classic/semaine");
            case SUPER_PREMIUM -> List.of("Préfixe [Super Premium]", "+10 % revenus de métier", "+10 ventes à l'HDV", "Taxe HDV -1 point",
                    "1 500 $ par jour", "1 LootBox Classic/semaine", "1 LootBox Légendaire/mois");
            case ULTRA -> List.of("Préfixe [Ultra] violet", "+15 % revenus de métier", "+20 ventes à l'HDV", "Taxe HDV -2 points",
                    "4 000 $ par jour", "1 LootBox Légendaire/sem.", "1 LootBox Ultra/mois");
        };
    }
}

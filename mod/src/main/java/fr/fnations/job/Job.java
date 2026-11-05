package fr.fnations.job;

import java.util.List;

/** Les métiers (compétences) : chaque joueur progresse dans tous à la fois. */
public enum Job {
    BUILDER("builder", "Builder", "hammer", "minecraft:bricks",
            "La compétence Builder récompense les blocs posés pour bâtir votre pays. Ses paliers augmentent votre portée de construction et vous font parfois économiser des blocs.",
            List.of("Poser des blocs (une fois par emplacement)", "Les escaliers, dalles, vitres et lanternes rapportent plus",
                    "Faire noter vos constructions par le jury (/notation) : jusqu'à 2 000 XP selon la note")),
    AGRICULTEUR("agriculteur", "Agriculteur", "wheat", "minecraft:wheat",
            "Récoltez les cultures arrivées à maturité pour nourrir votre nation. Les paliers débloquent la double récolte et la replantation automatique.",
            List.of("Récolter blé, carottes, pommes de terre, betteraves mûrs", "Melons, citrouilles, verrues du Nether, cacao", "Canne à sucre, cactus et bambou (pousses naturelles)")),
    CHASSEUR("chasseur", "Chasseur", "sword", "minecraft:bow",
            "Éliminez monstres et animaux. Plus la créature est dangereuse, plus la prime est élevée. Les paliers augmentent vos primes et vous renforcent après chaque victoire.",
            List.of("Tuer des monstres (zombie, squelette, creeper...)", "Primes élevées : enderman, blaze, wither squelette, warden", "Animaux d'élevage : petite récompense")),
    BUCHERON("bucheron", "Bûcheron", "axe", "minecraft:iron_axe",
            "Abattez les arbres et replantez les forêts. Les paliers débloquent la double coupe, l'abattage d'arbres entiers et la replantation automatique.",
            List.of("Couper des bûches naturelles", "Planter des pousses d'arbre")),
    INGENIEUR("ingenieur", "Ingénieur", "cog", "fnations:circuit",
            "Fabriquez le matériel du mod à la Station de Craft et installez des machines. Les paliers vous font parfois récupérer des ingrédients.",
            List.of("Fabriquer à la Station de Craft (plus l'objet est complexe, plus il rapporte)", "Installer des machines",
                    "Note R&D de vos constructions notées par le jury : jusqu'à 800 XP")),
    MINEUR("mineur", "Mineur", "pickaxe", "minecraft:iron_pickaxe",
            "Extrayez la pierre et les minerais naturels du sous-sol. Les paliers débloquent le double minerai et l'effet Célérité.",
            List.of("Miner la pierre, l'ardoise des profondeurs, la netherrack", "Minerais : charbon, fer, cuivre, or, redstone, lapis, diamant, émeraude", "Débris antiques et quartz du Nether"));

    public final String id, display, icon, item, description;
    public final List<String> howTo;

    Job(String id, String display, String icon, String item, String description, List<String> howTo) {
        this.id = id;
        this.display = display;
        this.icon = icon;
        this.item = item;
        this.description = description;
        this.howTo = howTo;
    }

    public static Job byId(String id) {
        for (Job j : values()) if (j.id.equalsIgnoreCase(id) || j.name().equalsIgnoreCase(id)) return j;
        return null;
    }

    /** Bonus débloqués au palier donné (1 à 6), hors récompense en argent. */
    public List<String> perks(int palier) {
        return switch (this) {
            case BUILDER -> switch (palier) {
                case 1 -> List.of("+0,5 bloc de portée");
                case 2 -> List.of("+1 bloc de portée");
                case 3 -> List.of("+1,5 bloc de portée");
                case 4 -> List.of("+2 blocs de portée", "5 % de chance de ne pas consommer le bloc posé");
                case 5 -> List.of("+2,5 blocs de portée", "8 % de chance de ne pas consommer le bloc");
                default -> List.of("+3 blocs de portée", "12 % de chance de ne pas consommer le bloc");
            };
            case AGRICULTEUR -> switch (palier) {
                case 1 -> List.of("5 % de double récolte");
                case 2 -> List.of("10 % de double récolte", "Replantation automatique des cultures");
                case 3 -> List.of("15 % de double récolte");
                case 4 -> List.of("20 % de double récolte");
                case 5 -> List.of("25 % de double récolte");
                default -> List.of("30 % de double récolte");
            };
            case CHASSEUR -> switch (palier) {
                case 1 -> List.of("Primes +10 %");
                case 2 -> List.of("Primes +20 %", "Orbes d'expérience bonus");
                case 3 -> List.of("Primes +30 %", "Force I pendant 5 s après un kill");
                case 4 -> List.of("Primes +40 %");
                case 5 -> List.of("Primes +50 %", "Régénération I pendant 5 s après un kill");
                default -> List.of("Primes +60 %");
            };
            case BUCHERON -> switch (palier) {
                case 1 -> List.of("5 % de double coupe");
                case 2 -> List.of("10 % de double coupe");
                case 3 -> List.of("15 % de double coupe", "Abattage de l'arbre entier (accroupi, hache en main)");
                case 4 -> List.of("20 % de double coupe");
                case 5 -> List.of("25 % de double coupe", "Replantation automatique de la pousse");
                default -> List.of("30 % de double coupe");
            };
            case INGENIEUR -> List.of((4 * palier) + " % de chance de récupérer un ingrédient à la Station");
            case MINEUR -> switch (palier) {
                case 1 -> List.of("5 % de double minerai");
                case 2 -> List.of("10 % de double minerai");
                case 3 -> List.of("15 % de double minerai", "Célérité I en minant");
                case 4 -> List.of("20 % de double minerai");
                case 5 -> List.of("25 % de double minerai", "Célérité II en minant");
                default -> List.of("30 % de double minerai");
            };
        };
    }
}

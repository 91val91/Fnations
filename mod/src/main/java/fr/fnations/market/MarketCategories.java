package fr.fnations.market;

import fr.fnations.registry.Category;
import fr.fnations.registry.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Set;

/** Classement automatique des objets de l'hôtel des ventes en catégories et sous-catégories. */
public final class MarketCategories {
    public record Sub(String id, String name) {}

    public record Cat(String id, String name, String icon, List<Sub> subs) {}

    public static final List<Cat> CATS = List.of(
            new Cat("technologies", "Technologies", "cog", List.of(new Sub("armes", "Armes à feu"), new Sub("munitions", "Munitions"),
                    new Sub("vehicules", "Véhicules"), new Sub("machines", "Machines"), new Sub("missiles", "Missiles & spatial"), new Sub("composants", "Composants"))),
            new Cat("equipements", "Équipements", "sword", List.of(new Sub("epees", "Épées & boucliers"), new Sub("distance", "Armes à distance"),
                    new Sub("armures", "Armures"), new Sub("outils", "Outils"), new Sub("enchantements", "Livres enchantés"))),
            new Cat("ressources", "Ressources", "box", List.of(new Sub("minerais", "Minerais & lingots"), new Sub("bois", "Bois"),
                    new Sub("agriculture", "Agriculture"), new Sub("butin", "Butin de créatures"), new Sub("autres", "Autres ressources"))),
            new Cat("nourriture", "Nourriture", "heart", List.of()),
            new Cat("blocs", "Blocs", "map", List.of(new Sub("construction", "Construction"), new Sub("nature", "Nature"), new Sub("redstone", "Redstone"))),
            new Cat("decorations", "Décorations", "star", List.of(new Sub("fleurs", "Fleurs & plantes"), new Sub("couleurs", "Laine, verre & couleurs"),
                    new Sub("mobilier", "Mobilier & lumières"))),
            new Cat("alchimie", "Alchimie", "flask", List.of()),
            new Cat("divers", "Divers", "tag", List.of()));

    private static final Set<Item> DISTANCE = Set.of(Items.BOW, Items.CROSSBOW, Items.TRIDENT, Items.ARROW, Items.SPECTRAL_ARROW, Items.TIPPED_ARROW);
    private static final Set<Item> TOOLS = Set.of(Items.SHEARS, Items.FISHING_ROD, Items.FLINT_AND_STEEL, Items.BRUSH, Items.SPYGLASS, Items.COMPASS,
            Items.CLOCK, Items.LEAD, Items.BUCKET, Items.WATER_BUCKET, Items.LAVA_BUCKET);
    private static final Set<Item> MINERALS = Set.of(Items.COAL, Items.CHARCOAL, Items.REDSTONE, Items.LAPIS_LAZULI, Items.QUARTZ, Items.NETHERITE_SCRAP,
            Items.AMETHYST_SHARD, Items.DIAMOND, Items.EMERALD, Items.FLINT, Items.GLOWSTONE_DUST, Items.CLAY_BALL, Items.BRICK);
    private static final Set<Item> DROPS = Set.of(Items.STRING, Items.BONE, Items.GUNPOWDER, Items.ENDER_PEARL, Items.SLIME_BALL, Items.LEATHER,
            Items.FEATHER, Items.BLAZE_ROD, Items.PHANTOM_MEMBRANE, Items.RABBIT_HIDE, Items.INK_SAC, Items.GLOW_INK_SAC, Items.PRISMARINE_SHARD,
            Items.PRISMARINE_CRYSTALS, Items.SHULKER_SHELL, Items.HONEYCOMB, Items.TURTLE_SCUTE, Items.ARMADILLO_SCUTE, Items.BREEZE_ROD,
            Items.NETHER_STAR, Items.ECHO_SHARD, Items.BONE_MEAL, Items.EGG, Items.RABBIT_FOOT);
    private static final Set<Item> FARM = Set.of(Items.WHEAT, Items.SUGAR_CANE, Items.BAMBOO, Items.CACTUS, Items.COCOA_BEANS, Items.PUMPKIN, Items.MELON,
            Items.WHEAT_SEEDS, Items.BEETROOT_SEEDS, Items.MELON_SEEDS, Items.PUMPKIN_SEEDS, Items.TORCHFLOWER_SEEDS, Items.PITCHER_POD, Items.HAY_BLOCK);
    private static final Set<Item> ALCHEMY = Set.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION, Items.BLAZE_POWDER, Items.NETHER_WART,
            Items.GHAST_TEAR, Items.FERMENTED_SPIDER_EYE, Items.MAGMA_CREAM, Items.GLISTERING_MELON_SLICE, Items.DRAGON_BREATH, Items.EXPERIENCE_BOTTLE,
            Items.BREWING_STAND, Items.GLASS_BOTTLE, Items.SUGAR);
    private static final Set<Item> REDSTONE = Set.of(Items.REPEATER, Items.COMPARATOR, Items.PISTON, Items.STICKY_PISTON, Items.OBSERVER, Items.HOPPER,
            Items.DROPPER, Items.DISPENSER, Items.LEVER, Items.TNT, Items.DAYLIGHT_DETECTOR, Items.TARGET, Items.NOTE_BLOCK, Items.REDSTONE_LAMP,
            Items.REDSTONE_TORCH, Items.REDSTONE_BLOCK, Items.TRIPWIRE_HOOK, Items.CRAFTER, Items.SCULK_SENSOR);
    private static final Set<Item> FURNITURE = Set.of(Items.LANTERN, Items.SOUL_LANTERN, Items.TORCH, Items.SOUL_TORCH, Items.CAMPFIRE, Items.SOUL_CAMPFIRE,
            Items.FLOWER_POT, Items.PAINTING, Items.ITEM_FRAME, Items.GLOW_ITEM_FRAME, Items.ARMOR_STAND, Items.BOOKSHELF, Items.CHISELED_BOOKSHELF,
            Items.LECTERN, Items.DECORATED_POT, Items.END_ROD, Items.SEA_LANTERN, Items.SHROOMLIGHT, Items.CHEST, Items.BARREL);

    private MarketCategories() {}

    private static TagKey<Item> c(String path) { return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", path)); }

    private static final TagKey<Item> ORES = c("ores"), INGOTS = c("ingots"), GEMS = c("gems"), RAW = c("raw_materials"), NUGGETS = c("nuggets"),
            CROPS = c("crops"), SEEDS = c("seeds"), DYES = c("dyes"), GLASS = c("glass_blocks"), GLASS_PANES = c("glass_panes"),
            STORAGE = c("storage_blocks");

    /** [catégorie, sous-catégorie] (sous-catégorie vide possible). */
    public static String[] classify(ItemStack st) {
        Item it = st.getItem();
        String fn = ModItems.idOf(st);
        if (fn != null) {
            Category cat = ModItems.CATEGORY.getOrDefault(fn, Category.COMPOSANT);
            return switch (cat) {
                case ARME -> new String[]{"technologies", "armes"};
                case MUNITION -> new String[]{"technologies", "munitions"};
                case VEHICULE -> new String[]{"technologies", "vehicules"};
                case MACHINE -> new String[]{"technologies", "machines"};
                case MISSILE, FUSEE -> new String[]{"technologies", "missiles"};
                case ECONOMIE -> new String[]{"divers", ""};
                default -> new String[]{"technologies", "composants"};
            };
        }
        if (it == Items.ENCHANTED_BOOK) return new String[]{"equipements", "enchantements"};
        if (st.is(ItemTags.SWORDS) || it == Items.SHIELD || it == Items.MACE) return new String[]{"equipements", "epees"};
        if (DISTANCE.contains(it)) return new String[]{"equipements", "distance"};
        if (st.is(ItemTags.HEAD_ARMOR) || st.is(ItemTags.CHEST_ARMOR) || st.is(ItemTags.LEG_ARMOR) || st.is(ItemTags.FOOT_ARMOR) || it == Items.ELYTRA)
            return new String[]{"equipements", "armures"};
        if (st.is(ItemTags.PICKAXES) || st.is(ItemTags.AXES) || st.is(ItemTags.SHOVELS) || st.is(ItemTags.HOES) || TOOLS.contains(it))
            return new String[]{"equipements", "outils"};
        if (ALCHEMY.contains(it)) return new String[]{"alchimie", ""};
        if (st.has(DataComponents.FOOD)) return new String[]{"nourriture", ""};
        if (st.is(ORES) || st.is(INGOTS) || st.is(GEMS) || st.is(RAW) || st.is(NUGGETS) || st.is(STORAGE) || MINERALS.contains(it))
            return new String[]{"ressources", "minerais"};
        if (st.is(ItemTags.LOGS) || st.is(ItemTags.PLANKS) || it == Items.STICK) return new String[]{"ressources", "bois"};
        if (st.is(CROPS) || st.is(SEEDS) || FARM.contains(it)) return new String[]{"ressources", "agriculture"};
        if (DROPS.contains(it)) return new String[]{"ressources", "butin"};
        if (REDSTONE.contains(it) || st.is(ItemTags.RAILS) || st.is(ItemTags.BUTTONS)) return new String[]{"blocs", "redstone"};
        if (st.is(ItemTags.SMALL_FLOWERS) || st.is(c("flowers")) || st.is(ItemTags.SAPLINGS) || st.is(ItemTags.LEAVES)) return new String[]{"decorations", "fleurs"};
        if (st.is(ItemTags.WOOL) || st.is(ItemTags.WOOL_CARPETS) || st.is(GLASS) || st.is(GLASS_PANES) || st.is(DYES) || st.is(ItemTags.TERRACOTTA)
                || st.is(ItemTags.BANNERS) || st.is(ItemTags.CANDLES)) return new String[]{"decorations", "couleurs"};
        if (FURNITURE.contains(it) || st.is(ItemTags.BEDS) || st.is(ItemTags.SIGNS) || st.is(ItemTags.DECORATED_POT_SHERDS))
            return new String[]{"decorations", "mobilier"};
        if (it instanceof BlockItem) {
            String path = BuiltInRegistries.ITEM.getKey(it).getPath();
            if (st.is(ItemTags.DIRT) || st.is(ItemTags.SAND) || path.contains("gravel") || path.contains("grass") || path.contains("moss")
                    || path.contains("mushroom") || path.contains("snow") || path.contains("ice"))
                return new String[]{"blocs", "nature"};
            return new String[]{"blocs", "construction"};
        }
        return new String[]{"divers", ""};
    }

    public static Cat cat(String id) {
        for (Cat c : CATS) if (c.id().equals(id)) return c;
        return null;
    }

    public static String label(String cat, String sub) {
        Cat c = cat(cat);
        if (c == null) return "Divers";
        for (Sub s : c.subs()) if (s.id().equals(sub)) return s.name();
        return c.name();
    }
}

package fr.fnations.registry;

import fr.fnations.FNations;
import fr.fnations.bank.BanknoteItem;
import fr.fnations.machine.MachineType;
import fr.fnations.rocket.RocketItem;
import fr.fnations.vehicle.VehicleItem;
import fr.fnations.vehicle.VehicleKind;
import fr.fnations.weapon.GunItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Tous les items FNations (les noms viennent des fichiers de langue fr_fr / en_us). */
public final class ModItems {
    public static final Map<String, Item> ALL = new LinkedHashMap<>();
    public static final Map<String, Category> CATEGORY = new LinkedHashMap<>();

    private ModItems() {}

    public static void init() {
        // ---------------- Composants ----------------
        simple("steel_ingot", Category.COMPOSANT, 64);
        simple("steel_plate", Category.COMPOSANT, 64);
        simple("compressed_iron", Category.COMPOSANT, 64);
        simple("carbon_plate", Category.COMPOSANT, 64);
        simple("iron_dust", Category.COMPOSANT, 64);
        simple("gold_dust", Category.COMPOSANT, 64);
        simple("copper_wire", Category.COMPOSANT, 64);
        simple("circuit", Category.COMPOSANT, 64);
        simple("advanced_circuit", Category.COMPOSANT, 64);
        simple("battery", Category.COMPOSANT, 64);
        simple("solar_cell", Category.COMPOSANT, 64);
        simple("motor", Category.COMPOSANT, 64);
        simple("jet_engine", Category.COMPOSANT, 16);
        simple("rotor", Category.COMPOSANT, 16);
        simple("tire", Category.COMPOSANT, 64);
        simple("track", Category.COMPOSANT, 16);
        simple("cockpit", Category.COMPOSANT, 16);
        simple("gun_barrel", Category.COMPOSANT, 64);
        simple("gun_parts", Category.COMPOSANT, 64);
        simple("explosive_charge", Category.COMPOSANT, 64);
        simple("warhead", Category.COMPOSANT, 16);
        simple("guidance_system", Category.COMPOSANT, 16);
        reg("nuclear_core", Category.COMPOSANT, p -> new FnItem(p.stacksTo(4).rarity(Rarity.EPIC), Category.COMPOSANT, List.of("<red>⚠ Radioactif")));
        simple("heat_shield", Category.COMPOSANT, 16);
        simple("rocket_engine", Category.COMPOSANT, 8);
        reg("fuel_canister", Category.COMPOSANT, p -> new FnItem(p.stacksTo(16), Category.COMPOSANT, List.of("<gray>Clic droit sur un véhicule pour le ravitailler.")));
        reg("rocket_fuel", Category.COMPOSANT, p -> new FnItem(p.stacksTo(16), Category.COMPOSANT, List.of("<gray>Clic droit sur une fusée pour la remplir.")));

        // ---------------- Munitions ----------------
        simple("ammo_9mm", Category.MUNITION, 64);
        simple("ammo_762", Category.MUNITION, 64);
        simple("ammo_sniper", Category.MUNITION, 64);
        simple("ammo_shotgun", Category.MUNITION, 64);
        simple("rpg_rocket", Category.MUNITION, 8);
        simple("tank_shell", Category.MUNITION, 16);

        // ---------------- Armes à feu ----------------
        gun("pistol", "ammo_9mm", 12, false, 1.2f, 6);
        gun("smg", "ammo_9mm", 30, true, 0.6f, 2);
        gun("assault_rifle", "ammo_762", 30, true, 1.0f, 3);
        gun("shotgun", "ammo_shotgun", 6, false, 4.0f, 18);
        gun("sniper", "ammo_sniper", 5, false, 5.0f, 30);
        gun("rpg", "rpg_rocket", 1, false, 3.0f, 40);

        // ---------------- Véhicules ----------------
        for (VehicleKind k : VehicleKind.values()) {
            reg(k.id, Category.VEHICULE, p -> new VehicleItem(p.stacksTo(1).rarity(Rarity.RARE), k));
        }

        // ---------------- Spatial ----------------
        reg("rocket", Category.FUSEE, p -> new RocketItem(p.stacksTo(1).rarity(Rarity.EPIC)));

        // ---------------- Machines (blocs) ----------------
        for (MachineType t : MachineType.values()) {
            ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, FNations.id(t.id));
            Item item = new FnBlockItem(ModBlocks.get(t), new Item.Properties().setId(key).useBlockDescriptionPrefix(), t.lore);
            ALL.put(t.id, Registry.register(BuiltInRegistries.ITEM, key, item));
            CATEGORY.put(t.id, Category.MACHINE);
        }

        // ---------------- Missiles ----------------
        reg("missile_tactical", Category.MISSILE, p -> new FnItem(p.stacksTo(4), Category.MISSILE, List.of("<gray>Portée : 400 blocs")));
        reg("missile_cruise", Category.MISSILE, p -> new FnItem(p.stacksTo(4), Category.MISSILE, List.of("<gray>Portée : 2 000 blocs")));
        reg("missile_ballistic", Category.MISSILE, p -> new FnItem(p.stacksTo(2).rarity(Rarity.RARE), Category.MISSILE, List.of("<gray>Portée : 6 000 blocs")));
        reg("missile_nuclear", Category.MISSILE, p -> new FnItem(p.stacksTo(1).rarity(Rarity.EPIC), Category.MISSILE, List.of("<gray>Portée : 12 000 blocs", "<red>⚠ Zone irradiée après impact")));

        // ---------------- Économie ----------------
        reg("banknote", Category.ECONOMIE, p -> new BanknoteItem(p.stacksTo(64)));
    }

    private static void simple(String id, Category c, int stack) {
        reg(id, c, p -> new FnItem(p.stacksTo(stack), c, List.of()));
    }

    private static void gun(String id, String ammo, int magazine, boolean auto, float recoil, int delay) {
        reg(id, Category.ARME, p -> new GunItem(p.stacksTo(1), id, ammo, magazine, auto, recoil, delay));
    }

    private static void reg(String id, Category c, Function<Item.Properties, Item> factory) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, FNations.id(id));
        Item item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(new Item.Properties().setId(key)));
        ALL.put(id, item);
        CATEGORY.put(id, c);
    }

    public static Item get(String id) { return ALL.get(id); }

    public static ItemStack stack(String id) { return stack(id, 1); }

    public static ItemStack stack(String id, int count) {
        Item i = ALL.get(id);
        if (i == null) throw new IllegalArgumentException("Item FNations inconnu : " + id);
        return new ItemStack(i, count);
    }

    /** Identifiant FNations d'un stack (ex: "steel_plate"), ou null. */
    public static String idOf(ItemStack s) {
        if (s == null || s.isEmpty()) return null;
        var key = BuiltInRegistries.ITEM.getKey(s.getItem());
        return key.getNamespace().equals(FNations.MOD_ID) ? key.getPath() : null;
    }

    public static boolean is(ItemStack s, String id) { return id.equals(idOf(s)); }

    /** Item avec lore + catégorie. */
    public static class FnItem extends Item {
        private final Category category;
        private final List<String> lore;

        public FnItem(Properties p, Category category, List<String> lore) {
            super(p);
            this.category = category;
            this.lore = lore;
        }

        public Category category() { return category; }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext ctx, List<net.minecraft.network.chat.Component> out, net.minecraft.world.item.TooltipFlag flag) {
            for (String l : lore) out.add(fr.fnations.util.Txt.c(l));
            out.add(fr.fnations.util.Txt.c("<dark_gray>FNations • " + category.display));
        }
    }

    /** Bloc-machine avec lore. */
    public static class FnBlockItem extends BlockItem {
        private final List<String> lore;

        public FnBlockItem(net.minecraft.world.level.block.Block block, Properties p, List<String> lore) {
            super(block, p);
            this.lore = lore;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext ctx, List<net.minecraft.network.chat.Component> out, net.minecraft.world.item.TooltipFlag flag) {
            for (String l : lore) out.add(fr.fnations.util.Txt.c(l));
            out.add(fr.fnations.util.Txt.c("<dark_gray>FNations • Machines"));
        }
    }
}

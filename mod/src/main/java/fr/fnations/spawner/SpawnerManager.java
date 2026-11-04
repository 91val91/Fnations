package fr.fnations.spawner;

import fr.fnations.registry.ModComponents;
import fr.fnations.registry.ModItems;
import fr.fnations.server.FnServer;
import fr.fnations.server.Msg;
import fr.fnations.util.Inv;
import fr.fnations.util.Txt;
import fr.fnations.util.Yaml;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.util.LinkedHashMap;
import java.util.Map;

/** Spawners FNations : types de mobs (spawners.yml), niveaux, amélioration et récupération. */
public final class SpawnerManager {
    public record Type(String id, String name, EntityType<?> entity, String rarity) {}

    public record Level(int delaySeconds, int count, int maxNearby, double upgradeCost) {}

    private final FnServer s;
    private final Map<String, Type> types = new LinkedHashMap<>();
    private final Level[] levels = new Level[6];
    private int range = 16;

    public SpawnerManager(FnServer s) {
        this.s = s;
        reload();
    }

    public void reload() {
        types.clear();
        Yaml y = s.yaml("spawners.yml");
        range = y.getInt("distance-activation", 16);
        Yaml t = y.getSection("types");
        if (t != null) for (String id : t.getKeys()) {
            Yaml e = t.getSection(id);
            String ent = e.getString("entite", "minecraft:" + id);
            ResourceLocation rl = ResourceLocation.tryParse(ent);
            EntityType<?> type = rl == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(rl).orElse(null);
            if (type == null) continue;
            types.put(id, new Type(id, e.getString("nom", id), type, e.getString("rarete", "commun")));
        }
        for (int l = 1; l <= 5; l++) {
            Yaml e = y.getSection("niveaux." + l);
            levels[l] = e == null ? new Level(Math.max(6, 36 - 6 * l), 1 + l / 2, 4 + 2 * l, 5000 * l * l)
                    : new Level(e.getInt("delai-secondes", 30), e.getInt("mobs", 1), e.getInt("max-autour", 6), e.getDouble("cout-amelioration", 0));
        }
    }

    public Map<String, Type> types() { return types; }

    public boolean exists(String id) { return id != null && types.containsKey(id.toLowerCase()); }

    public String display(String id) {
        Type t = types.get(id == null ? "" : id.toLowerCase());
        return t == null ? id : t.name();
    }

    public EntityType<?> entity(String id) {
        Type t = types.get(id);
        return t == null ? null : t.entity();
    }

    public Level info(int level) { return levels[Math.max(1, Math.min(5, level))]; }

    public int activationRange() { return range; }

    /** Objet spawner (type + niveau conservés quand on le pose ou le casse). */
    public ItemStack item(String id, int level) {
        ItemStack st = ModItems.stack("spawner");
        EntityType<?> e = entity(id.toLowerCase());
        st.set(ModComponents.SPAWNER, new ModComponents.SpawnerData(id.toLowerCase(), e == null ? "" : BuiltInRegistries.ENTITY_TYPE.getKey(e).toString(),
                Math.max(1, Math.min(5, level))));
        return st;
    }

    // ------------------------------------------------------------ actions
    public boolean isOwner(ServerPlayer p, SpawnerBlockEntity be) {
        return p.getUUID().equals(be.owner()) || be.owner() == null || p.hasPermissions(2);
    }

    public void upgrade(ServerPlayer p, SpawnerBlockEntity be) {
        if (!isOwner(p, be)) { Msg.err(p, "Ce spawner appartient à " + be.ownerName() + "."); return; }
        if (be.level() >= 5) { Msg.err(p, "Niveau maximal atteint."); return; }
        double cost = info(be.level() + 1).upgradeCost();
        if (!s.bank().withdraw(p.getUUID(), cost, "Amélioration de spawner")) { Msg.err(p, "Il faut " + Txt.money(cost) + " en banque."); return; }
        be.setLevel(be.level() + 1);
        Msg.ok(p, "Spawner " + display(be.mob()) + " amélioré au niveau " + be.level() + " !");
        Msg.sound(p, SoundEvents.ANVIL_USE, 0.7f, 1.3f);
    }

    public void pickup(ServerPlayer p, SpawnerBlockEntity be) {
        if (!isOwner(p, be)) { Msg.err(p, "Ce spawner appartient à " + be.ownerName() + "."); return; }
        BlockPos pos = be.getBlockPos();
        ItemStack it = item(be.mob(), be.level());
        p.serverLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        Inv.give(p, it);
        Msg.ok(p, "Spawner récupéré.");
    }
}

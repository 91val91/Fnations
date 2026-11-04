package fr.fnations.nation;

import fr.fnations.room.Room;
import fr.fnations.server.FnServer;
import fr.fnations.server.Msg;
import fr.fnations.util.Explosions;
import fr.fnations.util.Locs;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Protection des territoires et des chambres, PvP entre alliés, titres d'entrée de territoire. */
public final class Territory {
    private final FnServer plugin;
    private final Map<UUID, Long> lastWarn = new HashMap<>();
    private final Map<UUID, String> lastChunk = new HashMap<>();

    public Territory(FnServer plugin) { this.plugin = plugin; }

    public static boolean bypass(ServerPlayer p) { return p.hasPermissions(2) && p.isCreative(); }

    /** Le joueur peut-il modifier ce bloc ? (nations + chambres) */
    public boolean canBuild(ServerPlayer p, ServerLevel level, BlockPos pos) {
        if (bypass(p)) return true;
        Room r = plugin.rooms().at(level, pos);
        if (r != null) return r.canUse(p.getUUID());
        if (!plugin.config().getBoolean("nations.protection-territoire", true)) return true;
        Nation owner = plugin.nations().at(level, pos);
        if (owner == null) return true;
        Nation mine = plugin.nations().of(p);
        if (mine == owner) return true;
        if (mine != null && plugin.config().getBoolean("nations.allies-peuvent-construire", false) && plugin.nations().allied(mine, owner)) return true;
        return mine != null && plugin.config().getBoolean("guerres.destruction-territoire-ennemi", true) && plugin.wars().activeBetween(mine, owner);
    }

    public void deny(ServerPlayer p, ServerLevel level, BlockPos pos) {
        long now = System.currentTimeMillis();
        if (now - lastWarn.getOrDefault(p.getUUID(), 0L) < 1500) return;
        lastWarn.put(p.getUUID(), now);
        Room r = plugin.rooms().at(level, pos);
        if (r != null) { Msg.err(p, "Cette chambre ne vous appartient pas."); return; }
        Nation o = plugin.nations().at(level, pos);
        Msg.err(p, "Ce territoire appartient à " + (o == null ? "?" : o.colored()) + "<red>.");
    }

    /** PvP : refuse les dégâts entre membres d'une même nation et entre alliés. */
    public boolean allowDamage(LivingEntity victim, DamageSource src) {
        if (!(victim instanceof ServerPlayer v)) return true;
        if (!(src.getEntity() instanceof ServerPlayer a) || a == v) return true;
        Nation an = plugin.nations().of(a), vn = plugin.nations().of(v);
        if (an == null || vn == null) return true;
        if (an == vn) return plugin.config().getBoolean("nations.pvp-meme-nation", false);
        if (plugin.nations().allied(an, vn)) return plugin.config().getBoolean("nations.pvp-allies", false);
        return true;
    }

    /** Filtre les blocs détruits par une explosion (appelé par le mixin d'explosion). */
    public void filterExplosion(ServerLevel level, List<BlockPos> blocks) {
        boolean protect = plugin.config().getBoolean("nations.protection-explosions", true);
        Nation attacker = Explosions.active() ? Explosions.context() : null;
        boolean military = Explosions.active();
        blocks.removeIf(b -> {
            if (plugin.rooms().at(level, b) != null) return true;
            Nation owner = plugin.nations().at(level, b);
            if (owner == null) return false;
            if (military && attacker != null) return !(attacker == owner || plugin.wars().activeBetween(attacker, owner));
            return protect;
        });
    }

    /** Titres à l'entrée d'un territoire (toutes les 10 ticks). */
    public void tick(long ticks) {
        if (ticks % 10 != 0) return;
        for (ServerPlayer p : plugin.server().getPlayerList().getPlayers()) {
            String key = Locs.chunkKey(p.level(), p.blockPosition());
            String prev = lastChunk.put(p.getUUID(), key);
            if (prev == null || prev.equals(key)) continue;
            Nation from = plugin.nations().atChunk(prev), to = plugin.nations().atChunk(key);
            if (from == to) continue;
            if (to == null) {
                Msg.title(p, "", "<gray>Terres sauvages", 4, 20, 8);
            } else {
                Nation mine = plugin.nations().of(p);
                String rel = mine == to ? "<green>Bienvenue chez vous" : plugin.wars().between(mine, to) != null ? "<red>⚔ Territoire ennemi"
                        : plugin.nations().allied(mine, to) ? "<aqua>Territoire allié" : "<gray>Territoire étranger";
                Msg.title(p, to.colored(), rel, 4, 20, 8);
            }
        }
    }

    public void forget(UUID u) {
        lastChunk.remove(u);
        lastWarn.remove(u);
    }
}

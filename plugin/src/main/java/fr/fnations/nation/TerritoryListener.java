package fr.fnations.nation;

import fr.fnations.FNations;
import fr.fnations.util.Explosions;
import fr.fnations.util.Txt;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Protection des territoires, PvP entre alliés, et messages d'entrée de territoire. */
public final class TerritoryListener implements Listener {
    private final FNations plugin;
    private final Map<UUID, Long> lastWarn = new HashMap<>();

    public TerritoryListener(FNations plugin) { this.plugin = plugin; }

    /** Le joueur peut-il modifier ce bloc ? */
    public boolean canBuild(Player p, Location l) {
        if (p.hasPermission("fnations.admin.bypass")) return true;
        if (!plugin.getConfig().getBoolean("nations.protection-territoire", true)) return true;
        Nation owner = plugin.nations().at(l);
        if (owner == null) return true;
        Nation mine = plugin.nations().of(p);
        if (mine == owner) return true;
        if (mine != null && plugin.getConfig().getBoolean("nations.allies-peuvent-construire", false) && plugin.nations().allied(mine, owner)) return true;
        return mine != null && plugin.getConfig().getBoolean("guerres.destruction-territoire-ennemi", true) && plugin.wars().activeBetween(mine, owner);
    }

    private void deny(Player p, Location l) {
        long now = System.currentTimeMillis();
        if (now - lastWarn.getOrDefault(p.getUniqueId(), 0L) < 1500) return;
        lastWarn.put(p.getUniqueId(), now);
        Nation o = plugin.nations().at(l);
        Txt.err(p, "Ce territoire appartient à " + (o == null ? "?" : o.colored()) + "<red>.");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!canBuild(e.getPlayer(), e.getBlock().getLocation())) { e.setCancelled(true); deny(e.getPlayer(), e.getBlock().getLocation()); }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!canBuild(e.getPlayer(), e.getBlock().getLocation())) { e.setCancelled(true); deny(e.getPlayer(), e.getBlock().getLocation()); }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.PHYSICAL) return;
        Block b = e.getClickedBlock();
        if (!b.getType().isInteractable() && e.getAction() != Action.PHYSICAL) return;
        if (plugin.machines().isMachine(b)) return; // géré par le système de machines
        if (!canBuild(e.getPlayer(), b.getLocation())) { e.setCancelled(true); deny(e.getPlayer(), b.getLocation()); }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) {
        if (!canBuild(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucket(PlayerBucketFillEvent e) {
        if (!canBuild(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = attackerOf(e.getDamager());
        if (attacker == null || attacker == victim) return;
        Nation a = plugin.nations().of(attacker), v = plugin.nations().of(victim);
        if (a == null || v == null) return;
        if (a == v && !plugin.getConfig().getBoolean("nations.pvp-meme-nation", false)) e.setCancelled(true);
        else if (a != v && plugin.nations().allied(a, v) && !plugin.getConfig().getBoolean("nations.pvp-allies", false)) e.setCancelled(true);
    }

    public static Player attackerOf(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        return null;
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) { filter(e.blockList()); }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(BlockExplodeEvent e) { filter(e.blockList()); }

    private void filter(List<Block> blocks) {
        boolean protect = plugin.getConfig().getBoolean("nations.protection-explosions", true);
        Nation attacker = Explosions.active() ? Explosions.context() : null;
        blocks.removeIf(b -> {
            if (plugin.rooms().at(b.getLocation()) != null) return true;
            Nation owner = plugin.nations().at(b.getLocation());
            if (owner == null) return false;
            if (attacker != null) return !(attacker == owner || plugin.wars().activeBetween(attacker, owner));
            return protect;
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location f = e.getFrom(), t = e.getTo();
        if ((f.getBlockX() >> 4) == (t.getBlockX() >> 4) && (f.getBlockZ() >> 4) == (t.getBlockZ() >> 4) && f.getWorld() == t.getWorld()) return;
        Nation from = plugin.nations().at(f), to = plugin.nations().at(t);
        if (from == to) return;
        Player p = e.getPlayer();
        if (to == null) {
            p.showTitle(Title.title(Txt.raw(""), Txt.raw("<gray>Terres sauvages"), Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(1), Duration.ofMillis(400))));
        } else {
            Nation mine = plugin.nations().of(p);
            String rel = mine == to ? "<green>Bienvenue chez vous" : plugin.wars().between(mine, to) != null ? "<red>⚔ Territoire ennemi" : plugin.nations().allied(mine, to) ? "<aqua>Territoire allié" : "<gray>Territoire étranger";
            p.showTitle(Title.title(Txt.raw(to.colored()), Txt.raw(rel), Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(1), Duration.ofMillis(400))));
        }
    }
}

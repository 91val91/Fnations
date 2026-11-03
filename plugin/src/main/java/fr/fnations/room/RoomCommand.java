package fr.fnations.room;

import fr.fnations.FNations;
import fr.fnations.util.Txt;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** /chambre + protection des chambres + guichets de réception. */
public final class RoomCommand implements TabExecutor, Listener {
    private final FNations plugin;

    public RoomCommand(FNations plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, String @NotNull [] a) {
        if (!(s instanceof Player p)) return true;
        RoomManager rm = plugin.rooms();
        if (a.length == 0) { plugin.roomGui().open(p, null); return true; }
        String sub = a[0].toLowerCase();
        if (!List.of("liste", "tp").contains(sub) && !p.hasPermission("fnations.admin")) { plugin.roomGui().open(p, null); return true; }
        switch (sub) {
            case "creer" -> {
                if (a.length < 4) { Txt.err(p, "Usage : /chambre creer <id> <prixAchat|0> <prixLocationJour|0> [groupe]"); return true; }
                try {
                    rm.create(p, a[1], Double.parseDouble(a[2]), Double.parseDouble(a[3]), a.length > 4 ? String.join(" ", List.of(a).subList(4, a.length)) : null);
                } catch (NumberFormatException e) { Txt.err(p, "Prix invalide."); }
            }
            case "supprimer" -> withRoom(p, a, r -> { rm.delete(r); Txt.ok(p, "Chambre supprimée."); });
            case "reset" -> withRoom(p, a, r -> Txt.send(p, rm.reset(r) ? "<green>Chambre restaurée." : "<red>Échec."));
            case "snapshot" -> withRoom(p, a, r -> {
                try { rm.snapshots().save(r); Txt.ok(p, "Nouvel état de référence sauvegardé."); } catch (Exception e) { Txt.err(p, e.getMessage()); }
            });
            case "setspawn" -> withRoom(p, a, r -> {
                if (!r.contains(p.getLocation())) { Txt.err(p, "Placez-vous dans la chambre."); return; }
                r.setSpawn(p.getLocation());
                Txt.ok(p, "Point d'arrivée défini.");
            });
            case "prix" -> withRoom(p, a, r -> {
                if (a.length < 4) { Txt.err(p, "Usage : /chambre prix <id> <achat> <locationJour>"); return; }
                r.setPrices(Double.parseDouble(a[2]), Double.parseDouble(a[3]));
                Txt.ok(p, "Prix mis à jour.");
            });
            case "guichet" -> {
                Block b = p.getTargetBlockExact(6);
                if (b == null) { Txt.err(p, "Regardez un bloc (PNJ, pupitre, panneau...)."); return true; }
                if (a.length > 1 && a[1].equalsIgnoreCase("retirer")) { Txt.send(p, rm.removeDesk(b) ? "<green>Guichet retiré." : "<red>Ce bloc n'est pas un guichet."); return true; }
                String group = a.length > 1 ? String.join(" ", List.of(a).subList(1, a.length)) : "";
                rm.setDesk(b, group);
                Txt.ok(p, "Guichet de réception créé" + (group.isEmpty() ? "" : " pour le groupe " + group) + ". Clic droit dessus pour ouvrir.");
            }
            case "tp" -> withRoom(p, a, r -> {
                if (!r.canUse(p.getUniqueId()) && !p.hasPermission("fnations.admin")) { Txt.err(p, "Ce n'est pas votre chambre."); return; }
                if (r.spawn() != null) p.teleportAsync(r.spawn());
            });
            case "liste" -> rm.all().forEach(r -> Txt.send(p, "<white>" + r.id() + " <gray>[" + r.group() + "] " + r.mode() + " " + r.size()));
            default -> Txt.send(p, "/chambre [creer|supprimer|reset|snapshot|setspawn|prix|guichet|tp|liste]");
        }
        return true;
    }

    private void withRoom(Player p, String[] a, java.util.function.Consumer<Room> c) {
        Room r = a.length > 1 ? plugin.rooms().get(a[1]) : plugin.rooms().at(p.getLocation());
        if (r == null) { Txt.err(p, "Chambre introuvable."); return; }
        try { c.accept(r); } catch (NumberFormatException e) { Txt.err(p, "Nombre invalide."); }
    }

    // ------------------------------------------------------------ protection
    private boolean denied(Player p, org.bukkit.Location l) {
        Room r = plugin.rooms().at(l);
        if (r == null || p.hasPermission("fnations.admin.bypass")) return false;
        return !r.canUse(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (denied(e.getPlayer(), e.getBlock().getLocation())) { e.setCancelled(true); Txt.err(e.getPlayer(), "Cette chambre ne vous appartient pas."); }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (denied(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) {
        if (denied(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null || e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            String group = plugin.rooms().deskGroup(e.getClickedBlock());
            if (group != null) {
                e.setCancelled(true);
                plugin.roomGui().open(e.getPlayer(), group);
                return;
            }
        }
        if ((e.getAction() == Action.RIGHT_CLICK_BLOCK || e.getAction() == Action.PHYSICAL)
                && denied(e.getPlayer(), e.getClickedBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, String @NotNull [] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) for (String x : List.of("creer", "supprimer", "reset", "snapshot", "setspawn", "prix", "guichet", "tp", "liste")) if (x.startsWith(a[0])) out.add(x);
        if (a.length == 2 && !a[0].equalsIgnoreCase("creer") && !a[0].equalsIgnoreCase("guichet")) plugin.rooms().all().forEach(r -> out.add(r.id()));
        return out;
    }
}

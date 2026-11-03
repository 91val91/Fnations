package fr.fnations.bank;

import fr.fnations.FNations;
import fr.fnations.util.Inv;
import fr.fnations.util.Txt;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** /banque [solde|payer|retirer|deposer] et /payer — + dépôt des billets au clic droit. */
public final class BankCommand implements TabExecutor, Listener {
    private final FNations plugin;

    public BankCommand(FNations plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] a) {
        if (!(sender instanceof Player p)) return true;
        if (cmd.getName().equalsIgnoreCase("payer")) { pay(p, a, 0); return true; }
        if (a.length == 0) { plugin.bankGui().open(p); return true; }
        switch (a[0].toLowerCase()) {
            case "solde" -> Txt.send(p, "Solde : <gold>" + Txt.money(plugin.bank().balance(p.getUniqueId())));
            case "payer" -> pay(p, a, 1);
            case "deposer" -> {
                double v = plugin.bank().depositAllNotes(p);
                if (v > 0) Txt.ok(p, Txt.money(v) + " déposés."); else Txt.err(p, "Aucun billet.");
            }
            case "retirer" -> {
                Double v = a.length > 1 ? Txt.parseAmount(a[1]) : null;
                if (v == null) { Txt.err(p, "Usage : /banque retirer <montant>"); return true; }
                if (!plugin.bank().withdraw(p.getUniqueId(), v, "Retrait en billets")) { Txt.err(p, "Fonds insuffisants."); return true; }
                Inv.give(p, plugin.bank().banknote(v));
                Txt.ok(p, "Retrait de " + Txt.money(v) + ".");
            }
            case "nation" -> plugin.bankGui().openNation(p);
            case "donner" -> {
                if (!p.hasPermission("fnations.admin")) return true;
                if (a.length < 3) { Txt.err(p, "Usage : /banque donner <joueur> <montant>"); return true; }
                @SuppressWarnings("deprecation") OfflinePlayer t = Bukkit.getOfflinePlayer(a[1]);
                Double v = Txt.parseAmount(a[2]);
                if (v == null) return true;
                plugin.bank().deposit(t.getUniqueId(), v, "Don administrateur");
                Txt.ok(p, Txt.money(v) + " donnés à " + t.getName());
            }
            default -> Txt.send(p, "/banque [solde|payer <joueur> <montant>|retirer <montant>|deposer|nation]");
        }
        return true;
    }

    private void pay(Player p, String[] a, int off) {
        if (a.length < off + 2) { Txt.err(p, "Usage : /payer <joueur> <montant>"); return; }
        Player t = Bukkit.getPlayerExact(a[off]);
        Double v = Txt.parseAmount(a[off + 1]);
        if (t == null || v == null || t == p) { Txt.err(p, "Joueur ou montant invalide."); return; }
        if (!plugin.bank().transfer(p.getUniqueId(), t.getUniqueId(), v)) { Txt.err(p, "Fonds insuffisants."); return; }
        Txt.ok(p, "Vous avez envoyé " + Txt.money(v) + " à " + t.getName());
        Txt.ok(t, "Vous avez reçu " + Txt.money(v) + " de " + p.getName());
    }

    @EventHandler
    public void onUseNote(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        double v = plugin.bank().noteValue(e.getItem());
        if (v <= 0) return;
        e.setCancelled(true);
        e.getPlayer().getInventory().setItemInMainHand(null);
        plugin.bank().deposit(e.getPlayer().getUniqueId(), v, "Dépôt de billets");
        Txt.ok(e.getPlayer(), Txt.money(v) + " déposés sur votre compte.");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, String @NotNull [] a) {
        List<String> out = new ArrayList<>();
        if (c.getName().equalsIgnoreCase("banque") && a.length == 1) {
            for (String x : List.of("solde", "payer", "retirer", "deposer", "nation")) if (x.startsWith(a[0])) out.add(x);
        } else if ((c.getName().equalsIgnoreCase("payer") && a.length == 1) || (a.length == 2 && a[0].equalsIgnoreCase("payer"))) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        }
        return out;
    }
}

package fr.fnations.nation;

import fr.fnations.FNations;
import fr.fnations.util.Txt;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** /nation — sans argument ouvre le GUI. */
public final class NationCommand implements TabExecutor {
    private static final List<String> SUBS = List.of("creer", "inviter", "rejoindre", "quitter", "expulser", "promouvoir",
            "retrograder", "transferer", "claim", "unclaim", "deposer", "retirer", "allie", "capitale", "setcapitale",
            "impot", "tag", "info", "liste", "carte", "dissoudre", "aide");
    private final FNations plugin;
    private final NationActions act;

    public NationCommand(FNations plugin, NationActions act) {
        this.plugin = plugin;
        this.act = act;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] a) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Commande joueur uniquement."); return true; }
        if (a.length == 0) { plugin.nationGui().open(p); return true; }
        String arg = a.length > 1 ? a[1] : null;
        switch (a[0].toLowerCase()) {
            case "creer", "create" -> { if (arg == null) usage(p, "creer <nom>"); else act.create(p, arg); }
            case "inviter", "invite" -> { if (arg == null) usage(p, "inviter <joueur>"); else act.invite(p, arg); }
            case "rejoindre", "join" -> { if (arg == null) usage(p, "rejoindre <nation>"); else act.join(p, arg); }
            case "quitter", "leave" -> act.leave(p);
            case "expulser", "kick" -> withTarget(p, arg, u -> act.kick(p, u));
            case "promouvoir" -> withTarget(p, arg, u -> act.promote(p, u));
            case "retrograder" -> withTarget(p, arg, u -> act.demote(p, u));
            case "transferer" -> withTarget(p, arg, u -> act.transfer(p, u));
            case "claim" -> act.claim(p);
            case "unclaim" -> act.unclaim(p);
            case "deposer" -> money(p, arg, v -> act.deposit(p, v));
            case "retirer" -> money(p, arg, v -> act.withdraw(p, v));
            case "allie", "ally" -> { if (arg == null) usage(p, "allie <nation>"); else act.ally(p, arg); }
            case "capitale", "home" -> act.home(p);
            case "setcapitale", "sethome" -> act.setHome(p);
            case "impot", "tax" -> money(p, arg, v -> act.setTax(p, v));
            case "tag" -> { if (arg == null) usage(p, "tag <TAG>"); else act.setTag(p, arg); }
            case "carte", "map" -> plugin.nationGui().territory(p);
            case "liste", "list" -> {
                Txt.send(p, "<gold>Nations (" + plugin.nations().all().size() + ") :");
                plugin.nations().all().forEach(n -> p.sendMessage(Txt.raw(" <dark_gray>• " + n.colored() + " <gray>[" + n.tag() + "] "
                        + n.members().size() + " citoyens, " + n.claims().size() + " chunks")));
            }
            case "info" -> {
                Nation n = arg == null ? plugin.nations().of(p) : plugin.nations().get(arg);
                if (n == null) { Txt.err(p, "Nation introuvable."); return true; }
                Txt.send(p, n.colored());
                plugin.nationGui().describe(n).forEach(l -> p.sendMessage(Txt.raw("  " + l)));
            }
            case "dissoudre" -> {
                if (a.length < 2 || !a[1].equalsIgnoreCase("confirmer")) usage(p, "dissoudre confirmer");
                else act.disband(p);
            }
            default -> help(p);
        }
        return true;
    }

    private void withTarget(Player p, String name, java.util.function.Consumer<UUID> c) {
        if (name == null) { usage(p, "<commande> <joueur>"); return; }
        @SuppressWarnings("deprecation") OfflinePlayer op = Bukkit.getOfflinePlayer(name);
        c.accept(op.getUniqueId());
    }

    private void money(Player p, String s, java.util.function.Consumer<Double> c) {
        Double v = s == null ? null : Txt.parseAmount(s);
        if (v == null) Txt.err(p, "Montant invalide."); else c.accept(v);
    }

    private void usage(Player p, String u) { Txt.err(p, "Usage : /nation " + u); }

    private void help(Player p) {
        Txt.send(p, "<gold>Commandes /nation :");
        for (String s : SUBS) p.sendMessage(Txt.raw(" <dark_gray>• <yellow>/nation " + s));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, String @NotNull [] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) { for (String x : SUBS) if (x.startsWith(a[0].toLowerCase())) out.add(x); }
        else if (a.length == 2) {
            switch (a[0].toLowerCase()) {
                case "rejoindre", "allie", "info" -> plugin.nations().all().forEach(n -> out.add(n.name()));
                case "inviter", "expulser", "promouvoir", "retrograder", "transferer" -> Bukkit.getOnlinePlayers().forEach(pl -> out.add(pl.getName()));
                default -> {}
            }
            out.removeIf(x -> !x.toLowerCase().startsWith(a[1].toLowerCase()));
        }
        return out;
    }
}

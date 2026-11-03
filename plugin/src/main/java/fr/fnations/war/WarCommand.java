package fr.fnations.war;

import fr.fnations.FNations;
import fr.fnations.util.Txt;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** /guerre [declarer <nation>|capturer|liste] */
public final class WarCommand implements TabExecutor {
    private final FNations plugin;

    public WarCommand(FNations plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, String @NotNull [] a) {
        if (!(s instanceof Player p)) return true;
        if (a.length == 0) { plugin.warGui().open(p); return true; }
        switch (a[0].toLowerCase()) {
            case "declarer" -> {
                if (a.length < 2) { Txt.err(p, "Usage : /guerre declarer <nation>"); return true; }
                var t = plugin.nations().get(a[1]);
                if (t == null) Txt.err(p, "Nation introuvable."); else plugin.warGui().confirmDeclare(p, t);
            }
            case "capturer" -> plugin.wars().startCapture(p);
            case "liste" -> plugin.wars().all().forEach(w -> Txt.send(p, w.attacker() + " " + w.attackerScore() + " ⚔ " + w.defenderScore() + " " + w.defender()));
            default -> plugin.warGui().open(p);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, String @NotNull [] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) for (String x : List.of("declarer", "capturer", "liste")) if (x.startsWith(a[0])) out.add(x);
        if (a.length == 2 && a[0].equalsIgnoreCase("declarer")) plugin.nations().all().forEach(n -> out.add(n.name()));
        return out;
    }
}

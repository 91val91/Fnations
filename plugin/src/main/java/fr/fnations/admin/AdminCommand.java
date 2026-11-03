package fr.fnations.admin;

import fr.fnations.FNations;
import fr.fnations.gui.Icons;
import fr.fnations.gui.PagedGui;
import fr.fnations.item.ItemDef;
import fr.fnations.item.ItemRegistry;
import fr.fnations.nation.Nation;
import fr.fnations.research.Technology;
import fr.fnations.util.Inv;
import fr.fnations.util.Txt;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** /fnations : administration (give, items, reload, save, recherche). Sans permission : ouvre le menu principal. */
public final class AdminCommand implements TabExecutor {
    private final FNations plugin;

    public AdminCommand(FNations plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, String @NotNull [] a) {
        if (c.getName().equalsIgnoreCase("menu") || a.length == 0 || !s.hasPermission("fnations.admin")) {
            if (s instanceof Player p) plugin.mainMenu().open(p);
            return true;
        }
        switch (a[0].toLowerCase()) {
            case "give" -> {
                if (a.length < 3) { Txt.err(s, "Usage : /fnations give <joueur> <item> [quantité]"); return true; }
                Player t = Bukkit.getPlayerExact(a[1]);
                if (t == null || ItemRegistry.def(a[2]) == null) { Txt.err(s, "Joueur ou item inconnu."); return true; }
                int n = a.length > 3 ? Integer.parseInt(a[3]) : 1;
                Inv.give(t, ItemRegistry.create(a[2], n));
                Txt.ok(s, n + "× " + a[2] + " donné(s) à " + t.getName());
            }
            case "items" -> {
                if (!(s instanceof Player p)) return true;
                List<PagedGui.Entry> list = new ArrayList<>();
                for (ItemDef d : ItemRegistry.all()) {
                    list.add(new PagedGui.Entry(ItemRegistry.create(d.id()), pl -> Inv.give(pl, ItemRegistry.create(d.id(), Math.min(d.stack(), 16)))));
                }
                new PagedGui("<dark_red>Items FNations (admin)", list, null).open(p);
            }
            case "reload" -> {
                plugin.reloadAll();
                Txt.ok(s, "Configuration rechargée.");
            }
            case "save" -> {
                plugin.saveAll();
                Txt.ok(s, "Données sauvegardées.");
            }
            case "recherche" -> {
                if (a.length < 3) { Txt.err(s, "Usage : /fnations recherche <nation> <techno|tout>"); return true; }
                Nation n = plugin.nations().get(a[1]);
                if (n == null) { Txt.err(s, "Nation inconnue."); return true; }
                if (a[2].equalsIgnoreCase("tout")) {
                    for (Technology t : plugin.research().all()) if (!n.researched().contains(t.id())) plugin.research().complete(n, t);
                } else {
                    Technology t = plugin.research().get(a[2]);
                    if (t == null) { Txt.err(s, "Technologie inconnue."); return true; }
                    plugin.research().complete(n, t);
                }
                Txt.ok(s, "Recherche accordée.");
            }
            default -> Txt.send(s, "/fnations [give|items|reload|save|recherche]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, String @NotNull [] a) {
        List<String> out = new ArrayList<>();
        if (!s.hasPermission("fnations.admin") || c.getName().equalsIgnoreCase("menu")) return out;
        if (a.length == 1) for (String x : List.of("give", "items", "reload", "save", "recherche")) if (x.startsWith(a[0])) out.add(x);
        if (a.length == 2 && a[0].equalsIgnoreCase("give")) Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        if (a.length == 2 && a[0].equalsIgnoreCase("recherche")) plugin.nations().all().forEach(n -> out.add(n.name()));
        if (a.length == 3 && a[0].equalsIgnoreCase("give")) ItemRegistry.all().forEach(d -> { if (d.id().startsWith(a[2])) out.add(d.id()); });
        if (a.length == 3 && a[0].equalsIgnoreCase("recherche")) { out.add("tout"); plugin.research().all().forEach(t -> out.add(t.id())); }
        return out;
    }
}

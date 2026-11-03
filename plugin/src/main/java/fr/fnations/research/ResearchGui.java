package fr.fnations.research;

import fr.fnations.FNations;
import fr.fnations.gui.Gui;
import fr.fnations.gui.Icons;
import fr.fnations.item.ItemDef;
import fr.fnations.item.ItemRegistry;
import fr.fnations.nation.Nation;
import fr.fnations.util.Txt;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** GUI de Recherche & Développement. */
public final class ResearchGui {
    private static final Map<String, Material> CAT_ICON = Map.of("economie", Material.GOLD_INGOT, "industrie", Material.PISTON,
            "militaire", Material.CROSSBOW, "vehicules", Material.MINECART, "missiles", Material.FIREWORK_ROCKET, "spatial", Material.END_CRYSTAL);
    private final FNations plugin;

    public ResearchGui(FNations plugin) { this.plugin = plugin; }

    private ResearchManager rm() { return plugin.research(); }

    public void open(Player p) {
        Nation n = plugin.nations().of(p);
        if (n == null) { Txt.err(p, "Il faut appartenir à une nation."); return; }
        Gui g = new Gui(5, "<dark_purple>Recherche & Développement");
        g.border(Material.PURPLE_STAINED_GLASS_PANE);
        g.set(4, current(n));
        int[] slots = {20, 21, 22, 23, 24, 31};
        int i = 0;
        for (Map.Entry<String, String> c : ResearchManager.CATEGORIES.entrySet()) {
            List<Technology> list = rm().byCategory(c.getKey());
            long done = list.stream().filter(t -> n.researched().contains(t.id())).count();
            g.button(slots[i++], Icons.of(CAT_ICON.getOrDefault(c.getKey(), Material.BOOK), c.getValue(),
                    "<gray>Technologies : <white>" + done + "/" + list.size(), "", "<yellow>Clic : ouvrir"), pl -> category(pl, c.getKey()));
        }
        if (n.currentResearch() != null) {
            g.button(40, Icons.of(Material.BARRIER, "<red>Annuler la recherche en cours",
                    "<gray>Remboursement : " + (int) (plugin.getConfig().getDouble("recherche.remboursement-annulation", 0.5) * 100) + "%"),
                    pl -> { rm().cancel(pl); open(pl); });
        }
        g.button(36, Icons.back(), pl -> plugin.nationGui().open(pl));
        g.fill(Material.GRAY_STAINED_GLASS_PANE);
        g.open(p);
    }

    private org.bukkit.inventory.ItemStack current(Nation n) {
        Technology t = rm().get(n.currentResearch());
        if (t == null) return Icons.of(Material.CLOCK, "<gray>Aucune recherche en cours", "<gray>Trésor : <green>" + Txt.money(n.balance()),
                "<gray>Technologies : <aqua>" + n.researched().size() + "/" + rm().all().size());
        long left = (n.researchEnd() - System.currentTimeMillis()) / 1000;
        double ratio = 1 - (double) left / Math.max(1, t.seconds() * plugin.getConfig().getDouble("recherche.multiplicateur-duree", 1.0));
        return Icons.glow(Icons.of(Material.CLOCK, "<yellow>En cours : " + t.name(),
                Txt.progress(ratio, 30, "green", "dark_gray") + " <white>" + (int) (ratio * 100) + "%", "<gray>Temps restant : <white>" + Txt.duration(left)));
    }

    public void category(Player p, String cat) {
        Nation n = plugin.nations().of(p);
        if (n == null) return;
        Gui g = new Gui(6, "<dark_purple>R&D » " + ResearchManager.CATEGORIES.get(cat));
        g.border(Material.PURPLE_STAINED_GLASS_PANE);
        g.set(4, current(n));
        int[] slots = Gui.innerSlots(6);
        int i = 0;
        for (Technology t : rm().byCategory(cat)) {
            if (i >= slots.length) break;
            ResearchManager.Status s = rm().status(n, t);
            List<String> lore = new ArrayList<>(t.description());
            lore.add("");
            lore.add("<gray>Coût : <gold>" + Txt.money(t.cost()) + " <dark_gray>(trésor national)");
            lore.add("<gray>Durée : <white>" + Txt.duration((long) (t.seconds() * plugin.getConfig().getDouble("recherche.multiplicateur-duree", 1.0))));
            if (!t.requires().isEmpty()) {
                lore.add("<gray>Prérequis :");
                for (String r : t.requires()) {
                    Technology rt = rm().get(r);
                    lore.add((n.researched().contains(r) ? " <green>✔ " : " <red>✘ ") + (rt == null ? r : Txt.plain(Txt.raw(rt.name()))));
                }
            }
            if (!t.unlocks().isEmpty()) {
                lore.add("<gray>Débloque :");
                for (String u : t.unlocks()) {
                    ItemDef d = ItemRegistry.def(u);
                    lore.add(" <dark_gray>• " + (d == null ? u : d.name()));
                }
            }
            if (t.bonusClaims() > 0) lore.add("<gray>Bonus : <green>+" + t.bonusClaims() + " chunks de territoire");
            lore.add("");
            Material icon = t.icon();
            switch (s) {
                case DONE -> lore.add("<green><b>✔ RECHERCHÉE");
                case IN_PROGRESS -> lore.add("<yellow><b>⌛ EN COURS");
                case AVAILABLE -> lore.add(n.currentResearch() == null ? "<aqua>Clic : lancer la recherche" : "<gray>Une autre recherche est en cours");
                case LOCKED -> { lore.add("<red><b>✘ VERROUILLÉE"); icon = Material.GRAY_DYE; }
            }
            var item = Icons.of(icon, (s == ResearchManager.Status.LOCKED ? "<dark_gray>" : "") + t.name(), lore);
            if (s == ResearchManager.Status.DONE || s == ResearchManager.Status.IN_PROGRESS) Icons.glow(item);
            g.button(slots[i++], item, pl -> {
                if (rm().status(n, t) == ResearchManager.Status.AVAILABLE) { rm().start(pl, t); category(pl, cat); }
            });
        }
        g.button(49, Icons.back(), this::open);
        g.fill(Material.GRAY_STAINED_GLASS_PANE);
        g.open(p);
    }
}

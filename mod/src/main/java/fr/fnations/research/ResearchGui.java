package fr.fnations.research;

import fr.fnations.gui.ConfirmPanel;
import fr.fnations.gui.Icons;
import fr.fnations.gui.PagedPanel;
import fr.fnations.gui.Panel;
import fr.fnations.gui.Prompt;
import fr.fnations.server.FnServer;
import fr.fnations.server.Msg;
import fr.fnations.util.Txt;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import fr.fnations.nation.Nation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** GUI de Recherche & Développement. */
public final class ResearchGui {
    private static final Map<String, net.minecraft.world.item.Item> CAT_ICON = Map.of("economie", Items.GOLD_INGOT, "industrie", Items.PISTON,
            "militaire", Items.CROSSBOW, "vehicules", Items.MINECART, "missiles", Items.FIREWORK_ROCKET, "spatial", Items.END_CRYSTAL);
    private final FnServer plugin;

    public ResearchGui(FnServer plugin) { this.plugin = plugin; }

    private ResearchManager rm() { return plugin.research(); }

    public void open(ServerPlayer p) {
        Nation n = plugin.nations().of(p);
        if (n == null) { Msg.err(p, "Il faut appartenir à une nation."); return; }
        Panel g = new Panel(5, "<dark_purple>Recherche & Développement");
        g.border(Items.PURPLE_STAINED_GLASS_PANE);
        g.set(4, current(n));
        int[] slots = {20, 21, 22, 23, 24, 31};
        int i = 0;
        for (Map.Entry<String, String> c : ResearchManager.CATEGORIES.entrySet()) {
            List<Technology> list = rm().byCategory(c.getKey());
            long done = list.stream().filter(t -> n.researched().contains(t.id())).count();
            g.button(slots[i++], Icons.of(CAT_ICON.getOrDefault(c.getKey(), Items.BOOK), c.getValue(),
                    "<gray>Technologies : <white>" + done + "/" + list.size(), "", "<yellow>Clic : ouvrir"), pl -> category(pl, c.getKey()));
        }
        if (n.currentResearch() != null) {
            g.button(40, Icons.of(Items.BARRIER, "<red>Annuler la recherche en cours",
                    "<gray>Remboursement : " + (int) (plugin.config().getDouble("recherche.remboursement-annulation", 0.5) * 100) + "%"),
                    pl -> { rm().cancel(pl); open(pl); });
        }
        g.button(36, Icons.back(), pl -> plugin.nationGui().open(pl));
        g.fill(Items.GRAY_STAINED_GLASS_PANE);
        g.open(p);
    }

    private net.minecraft.world.item.ItemStack current(Nation n) {
        Technology t = rm().get(n.currentResearch());
        if (t == null) return Icons.of(Items.CLOCK, "<gray>Aucune recherche en cours", "<gray>Trésor : <green>" + Txt.money(n.balance()),
                "<gray>Technologies : <aqua>" + n.researched().size() + "/" + rm().all().size());
        long left = (n.researchEnd() - System.currentTimeMillis()) / 1000;
        double ratio = 1 - (double) left / Math.max(1, t.seconds() * plugin.config().getDouble("recherche.multiplicateur-duree", 1.0));
        return Icons.glow(Icons.of(Items.CLOCK, "<yellow>En cours : " + t.name(),
                Txt.progress(ratio, 30, "green", "dark_gray") + " <white>" + (int) (ratio * 100) + "%", "<gray>Temps restant : <white>" + Txt.duration(left)));
    }

    public void category(ServerPlayer p, String cat) {
        Nation n = plugin.nations().of(p);
        if (n == null) return;
        Panel g = new Panel(6, "<dark_purple>R&D » " + ResearchManager.CATEGORIES.get(cat));
        g.border(Items.PURPLE_STAINED_GLASS_PANE);
        g.set(4, current(n));
        int[] slots = Panel.innerSlots(6);
        int i = 0;
        for (Technology t : rm().byCategory(cat)) {
            if (i >= slots.length) break;
            ResearchManager.Status s = rm().status(n, t);
            List<String> lore = new ArrayList<>(t.description());
            lore.add("");
            lore.add("<gray>Coût : <gold>" + Txt.money(t.cost()) + " <dark_gray>(trésor national)");
            lore.add("<gray>Durée : <white>" + Txt.duration((long) (t.seconds() * plugin.config().getDouble("recherche.multiplicateur-duree", 1.0))));
            if (!t.requires().isEmpty()) {
                lore.add("<gray>Prérequis :");
                for (String r : t.requires()) {
                    Technology rt = rm().get(r);
                    lore.add((n.researched().contains(r) ? " <green>✔ " : " <red>✘ ") + (rt == null ? r : Txt.plain(rt.name())));
                }
            }
            if (!t.unlocks().isEmpty()) {
                lore.add("<gray>Débloque :");
                for (String u : t.unlocks()) {
                    lore.add(" <dark_gray>• <white><item:fnations:" + u + ">");
                }
            }
            if (t.bonusClaims() > 0) lore.add("<gray>Bonus : <green>+" + t.bonusClaims() + " chunks de territoire");
            lore.add("");
            net.minecraft.world.item.Item icon = t.icon();
            switch (s) {
                case DONE -> lore.add("<green><b>✔ RECHERCHÉE");
                case IN_PROGRESS -> lore.add("<yellow><b>⌛ EN COURS");
                case AVAILABLE -> lore.add(n.currentResearch() == null ? "<aqua>Clic : lancer la recherche" : "<gray>Une autre recherche est en cours");
                case LOCKED -> { lore.add("<red><b>✘ VERROUILLÉE"); icon = Items.GRAY_DYE; }
            }
            var item = Icons.of(icon, (s == ResearchManager.Status.LOCKED ? "<dark_gray>" : "") + t.name(), lore);
            if (s == ResearchManager.Status.DONE || s == ResearchManager.Status.IN_PROGRESS) Icons.glow(item);
            g.button(slots[i++], item, pl -> {
                if (rm().status(n, t) == ResearchManager.Status.AVAILABLE) { rm().start(pl, t); category(pl, cat); }
            });
        }
        g.button(49, Icons.back(), this::open);
        g.fill(Items.GRAY_STAINED_GLASS_PANE);
        g.open(p);
    }
}

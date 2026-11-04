package fr.fnations.ui.pages;

import fr.fnations.loot.LootManager;
import fr.fnations.registry.ModItems;
import fr.fnations.server.FnServer;
import fr.fnations.ui.UiNode;
import fr.fnations.ui.UiPage;
import fr.fnations.ui.UiSession;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static fr.fnations.ui.UiNode.*;
import static fr.fnations.ui.UiTheme.*;

/** Ouverture d'une LootBox : roulette, gain, contenu possible. */
public final class LootboxPage extends UiPage {
    public LootboxPage(FnServer s) { super(s); }

    @Override
    public String id() { return "lootbox"; }

    @Override
    public String nav() { return "boutique"; }

    @Override
    public UiNode build(ServerPlayer p, UiSession ss) {
        LootManager lm = s.loot();
        LootManager.Box box = lm.box(ss.get("box", "classic"));
        if (box == null) return card(empty("LootBox inconnue."));
        int left = lm.count(p, box.id());
        LootManager.Opening o = lm.opening(p.getUUID());
        boolean mine = o != null && o.box.equals(box.id());
        UiNode head = row(row(item(ModItems.stack("lootbox_" + box.id())).size(32)).bg(ROW).r(8).pad(5),
                col(text("<b><" + box.color() + ">LootBox " + box.name()).scale(1.6), text(T2 + "Il vous en reste " + left)).gap(4).flex(1),
                btn("Boutique", "nav:boutique").withIcon("cart"), btn("", "client:close").withIcon("close").ghost().w(20)).gap(10);

        UiNode stage;
        if (mine) {
            List<UiNode> cells = new ArrayList<>();
            for (LootManager.Reward r : o.strip) cells.add(item(lm.icon(r)).color(LootManager.rarityColor(r.rarity())).tip("<" + LootManager.rarityColor(r.rarity()) + ">" + lm.label(r)));
            UiNode wheel = roulette("r" + o.id, cells, LootManager.WIN_INDEX, LootManager.SPIN_MS, System.currentTimeMillis() - o.start).h(78);
            UiNode result;
            if (o.delivered) {
                String col = LootManager.rarityColor(o.win.rarity());
                result = row(row(item(lm.icon(o.win)).size(32)).bg(ROW).border(col).r(8).pad(5),
                        col(text("<" + col + "><b>" + LootManager.rarityName(o.win.rarity()).toUpperCase()), text("<b>Vous gagnez " + lm.label(o.win)).scale(1.25).wrap()).gap(3).flex(1),
                        left > 0 ? btn("Ouvrir une autre", a("open", box.id())).primary().withIcon("refresh") : btn("Acheter sur la boutique", "url:" + s.store().url()).primary().withIcon("cart"))
                        .bg(PANEL).r(8).pad(8).gap(10);
            } else {
                result = row(icon("clock").size(12).color(TEXT2), text(T2 + "Ouverture en cours…")).gap(6).pad(8);
            }
            stage = col(wheel, result).gap(8);
        } else {
            stage = col(icon("box").size(32).color(box.color()), text(T2 + "Ouvrez votre LootBox pour lancer la roulette.").center(),
                    left > 0 ? btn("Ouvrir la LootBox", a("open", box.id())).primary().withIcon("box").w(150)
                            : btn("Acheter sur la boutique", "url:" + s.store().url()).primary().withIcon("cart").w(170))
                    .str("align", "center").gap(8).pad(12);
        }
        UiNode content = grid(4).gap(5);
        for (LootManager.Reward r : box.rewards()) {
            String col = LootManager.rarityColor(r.rarity());
            content.add(row(item(lm.icon(r)).size(16), col(text(lm.label(r)).lines(1), text("<" + col + ">" + LootManager.rarityName(r.rarity()) + TM + " • "
                    + String.format(java.util.Locale.FRANCE, "%.1f", lm.chance(box, r)) + " %")).gap(2).flex(1)).bg(ROW).r(5).pad(5).gap(5));
        }
        return col(head, card(stage), card(sectionTitle("Contenu possible"), list("content").add(content).flex(1)).flex(1)).gap(8);
    }

    @Override
    public void action(ServerPlayer p, UiSession ss, String act, String arg, Map<String, String> in) {
        if (act.equals("open")) s.loot().open(p, arg);
    }
}

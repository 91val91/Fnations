package fr.fnations.ui.pages;

import fr.fnations.server.FnServer;
import fr.fnations.spawner.SpawnerBlockEntity;
import fr.fnations.spawner.SpawnerManager;
import fr.fnations.ui.UiNode;
import fr.fnations.ui.UiPage;
import fr.fnations.ui.UiSession;
import fr.fnations.util.Locs;
import fr.fnations.util.Txt;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

import static fr.fnations.ui.UiNode.*;
import static fr.fnations.ui.UiTheme.*;

/** Spawner : mob, niveau, cadence, amélioration et récupération. */
public final class SpawnerPage extends UiPage {
    public SpawnerPage(FnServer s) { super(s); }

    @Override
    public String id() { return "spawner"; }

    @Override
    public String nav() { return ""; }

    @Override
    public int refresh(ServerPlayer p, UiSession ss) { return 2; }

    public static Map<String, String> at(ServerPlayer p, BlockPos pos) {
        return Map.of("pos", pos.getX() + "," + pos.getY() + "," + pos.getZ() + "," + Locs.dim(p.level()));
    }

    private SpawnerBlockEntity spawner(ServerPlayer p, UiSession ss) {
        String[] v = ss.get("pos", "").split(",");
        if (v.length != 4 || !v[3].equals(Locs.dim(p.level()))) return null;
        try {
            BlockPos pos = new BlockPos(Integer.parseInt(v[0]), Integer.parseInt(v[1]), Integer.parseInt(v[2]));
            if (!p.blockPosition().closerThan(pos, 8)) return null;
            return p.level().getBlockEntity(pos) instanceof SpawnerBlockEntity be ? be : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public UiNode build(ServerPlayer p, UiSession ss) {
        SpawnerBlockEntity be = spawner(p, ss);
        if (be == null) return card(empty("Spawner introuvable ou trop loin."), btn("Fermer", "client:close"), flexSpace());
        SpawnerManager sm = s.spawners();
        SpawnerManager.Level cur = sm.info(be.level());
        boolean owner = sm.isOwner(p, be);
        UiNode levels = row().gap(4);
        for (int l = 1; l <= 5; l++) levels.add(row(text((l <= be.level() ? "<b>" : TM) + l).center().flex(1)).h(18).r(4).bg(l <= be.level() ? ACCENT : ROW).flex(1));
        UiNode stats = row(stat("clock", "Délai", "~" + cur.delaySeconds() + " s").flex(1), stat("users", "Mobs par cycle", String.valueOf(cur.count())).flex(1),
                stat("shield", "Maximum autour", String.valueOf(cur.maxNearby())).flex(1), stat("chart", "Apparitions", String.valueOf(be.spawned())).flex(1)).gap(6);
        UiNode upgrade;
        if (be.level() >= 5) upgrade = row(icon("star").size(12).color(YELLOW), text(TY + "<b>Niveau maximal atteint")).gap(6);
        else {
            SpawnerManager.Level next = sm.info(be.level() + 1);
            upgrade = row(col(text("<b>Niveau " + (be.level() + 1)), text(T2 + "Délai ~" + next.delaySeconds() + " s • " + next.count() + " mob(s) • max " + next.maxNearby())).gap(2).flex(1),
                    btn("Améliorer : " + money(next.upgradeCost()), a("upgrade")).primary().withIcon("arrow_up").disabled(!owner)).gap(8);
        }
        return col(
                row(row(item(sm.item(be.mob(), be.level())).size(32)).bg(ROW).r(8).pad(6),
                        col(text("<b>Spawner " + Txt.esc(sm.display(be.mob()))).scale(1.6), text(T2 + "Propriétaire : " + (be.ownerName().isEmpty() ? "—" : Txt.esc(be.ownerName())))).gap(4).flex(1),
                        btn("", "client:close").withIcon("close").ghost().w(20)).gap(10),
                card(sectionTitle("Niveau " + be.level() + " / 5"), levels, stats),
                card(sectionTitle("Amélioration"), upgrade,
                        text(TM + "Les mobs apparaissent tant qu'un joueur est à moins de " + sm.activationRange() + " blocs.").wrap()),
                card(row(col(text("<b>Récupérer le spawner"), text(T2 + "Il garde son type et son niveau.")).gap(2).flex(1),
                        btn("Récupérer", a("pickup")).disabled(!owner).withIcon("box")).gap(8)),
                flexSpace()).gap(8);
    }

    @Override
    public void action(ServerPlayer p, UiSession ss, String act, String arg, Map<String, String> in) {
        SpawnerBlockEntity be = spawner(p, ss);
        if (be == null) return;
        switch (act) {
            case "upgrade" -> s.spawners().upgrade(p, be);
            case "pickup" -> {
                s.spawners().pickup(p, be);
                s.ui().close(p);
            }
            default -> {
            }
        }
    }
}

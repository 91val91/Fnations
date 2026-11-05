package fr.fnations.ui.pages;

import fr.fnations.craft.StationRecipe;
import fr.fnations.machine.MachineBlock;
import fr.fnations.machine.MachineType;
import fr.fnations.registry.Category;
import fr.fnations.registry.ModItems;
import fr.fnations.research.Technology;
import fr.fnations.server.FnServer;
import fr.fnations.server.Msg;
import fr.fnations.ui.UiNode;
import fr.fnations.ui.UiPage;
import fr.fnations.ui.UiSession;
import fr.fnations.util.Inv;
import fr.fnations.util.Locs;
import fr.fnations.util.Txt;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static fr.fnations.ui.UiNode.*;
import static fr.fnations.ui.UiTheme.*;

/** Station de Craft : catégories, recettes, ingrédients et fabrication. */
public final class StationPage extends UiPage {
    private static final Map<Category, String[]> CATS = new LinkedHashMap<>();
    static {
        CATS.put(Category.COMPOSANT, new String[]{"cog", "Composants"});
        CATS.put(Category.ARME, new String[]{"sword", "Armes à feu"});
        CATS.put(Category.MUNITION, new String[]{"box", "Munitions"});
        CATS.put(Category.VEHICULE, new String[]{"cart", "Véhicules"});
        CATS.put(Category.MACHINE, new String[]{"factory", "Machines"});
        CATS.put(Category.MISSILE, new String[]{"arrow_up", "Missiles"});
        CATS.put(Category.FUSEE, new String[]{"globe", "Spatial"});
    }

    public StationPage(FnServer s) { super(s); }

    @Override
    public String id() { return "station"; }

    @Override
    public String nav() { return ""; }

    /** Ouverture depuis un bloc de Station de Craft. */
    public static Map<String, String> at(ServerPlayer p, BlockPos pos) {
        return Map.of("pos", pos.getX() + "," + pos.getY() + "," + pos.getZ() + "," + Locs.dim(p.level()));
    }

    private boolean near(ServerPlayer p, UiSession ss) {
        if (p.hasPermissions(2)) return true;
        String[] v = ss.get("pos", "").split(",");
        if (v.length != 4 || !v[3].equals(Locs.dim(p.level()))) return false;
        try {
            BlockPos pos = new BlockPos(Integer.parseInt(v[0]), Integer.parseInt(v[1]), Integer.parseInt(v[2]));
            return p.blockPosition().closerThan(pos, 7) && p.level().getBlockState(pos).getBlock() instanceof MachineBlock mb && mb.type == MachineType.CRAFTING_STATION;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public UiNode build(ServerPlayer p, UiSession ss) {
        Category cat = Category.byName(ss.get("cat", "COMPOSANT"));
        List<StationRecipe> all = s.station().all();
        UiNode left = card(text(T2 + "<b>CATÉGORIES")).w(122);
        for (var e : CATS.entrySet()) {
            long n = all.stream().filter(r -> r.category().equals(e.getKey().name())).count();
            boolean on = e.getKey() == cat;
            left.add(row(icon(e.getValue()[0]).size(12).color(on ? TEXT : TEXT2), text((on ? "<b>" : T2) + e.getValue()[1]).flex(1), text((on ? "" : TM) + n))
                    .pad(6, 5).r(5).bg(on ? ACCENT : null).act(a("cat", e.getKey().name())).gap(6));
        }
        boolean near = near(p, ss);
        left.add(flexSpace(), near ? null : text(TY + "Consultation : rendez-vous à une Station de Craft pour fabriquer.").wrap());

        String sel = ss.get("recipe", "");
        UiNode grid = grid(6).gap(5);
        for (StationRecipe r : all) {
            if (!r.category().equals(cat.name())) continue;
            boolean job = s.jobs().canCraft(p, "fnations:" + r.result());
            boolean ok = job && Inv.has(p, r.ingredients()) && researched(p, r);
            boolean on = r.id().equals(sel);
            var q = s.jobs().requirement("fnations:" + r.result());
            UiNode cell = col(item(ModItems.stack(r.result(), r.amount())).size(24).flag("notip")).str("align", "center").pad(5).h(34).r(6)
                    .bg(on ? ACCENT : ok ? "#1F3A2C" : !job ? "#3A2427" : ROW).act(a("recipe", r.id()));
            cell.tip("<b><item:fnations:" + r.result() + ">", q == null || q.palier() <= 0 ? TM + "Aucun palier requis"
                    : (job ? TG + "✔ " : TR + "🔒 ") + fr.fnations.job.JobManager.label(q));
            grid.add(cell);
        }
        UiNode center = card(text("<b>Station de Craft").scale(1.25),
                list("recipes").add(grid).flex(1),
                row(chip(TG + "Fabricable").bg("#1F3A2C").tip(T2 + "Vous avez tout le nécessaire"),
                        chip(TR + "Palier de métier requis").bg("#3A2427").tip(T2 + "Votre palier de métier est trop bas (voir la page Métiers)")).gap(5)).flex(1);
        StationRecipe r = s.station().get(sel);
        UiNode right;
        if (r == null || !r.category().equals(cat.name())) {
            right = card(icon("factory").size(28).color(TEXT2), text("<b>Choisissez une recette").center(), text(T2 + "Les fabrications rapportent de l'XP d'Ingénieur.").wrap().center(),
                    flexSpace()).str("align", "center").w(176);
        } else {
            UiNode ings = col().gap(3);
            boolean ok = true;
            for (var e : r.ingredients().entrySet()) {
                int have = Inv.count(p, e.getKey());
                boolean enough = have >= e.getValue();
                ok &= enough;
                ings.add(row(item(Inv.stack(e.getKey(), 1)).size(16), text(Inv.display(e.getKey())).flex(1),
                        text((enough ? TG : TR) + Math.min(have, 999) + "/" + e.getValue())).bg(ROW).r(4).pad(3).gap(6));
            }
            Technology t = s.research().requiredFor(r.result());
            boolean res = researched(p, r);
            UiNode tech = t == null ? null : row(icon(res ? "check" : "lock").size(10).color(res ? GREEN : RED), text((res ? T2 : TR) + "Recherche : " + Txt.plain(t.name())).wrap().flex(1)).gap(5);
            var q = s.jobs().requirement("fnations:" + r.result());
            boolean job = s.jobs().canCraft(p, "fnations:" + r.result());
            UiNode jobRow = q == null || q.palier() <= 0 ? null : row(icon(job ? "check" : "lock").size(10).color(job ? GREEN : RED),
                    text((job ? T2 : TR) + "Métier : " + fr.fnations.job.JobManager.label(q)
                            + (job ? "" : " (vous : " + s.jobs().palier(p.getUUID(), q.job()) + ")")).wrap().flex(1)).gap(5).act("nav:metiers");
            boolean can = ok && res && job && near;
            right = card(row(item(ModItems.stack(r.result(), r.amount())).size(32), col(text("<b><item:fnations:" + r.result() + ">").wrap(), text(T2 + "Quantité : " + r.amount())).gap(3).flex(1)).gap(8),
                    sectionTitle("Ingrédients"), list("ings").add(ings).flex(1), tech, jobRow,
                    row(btn("Fabriquer", a("craft", r.id() + ";1")).primary().disabled(!can).flex(1), btn("×5", a("craft", r.id() + ";5")).disabled(!can).w(34)).gap(5)).w(176);
        }
        return row(left, center, right).gap(8);
    }

    private boolean researched(ServerPlayer p, StationRecipe r) {
        Technology t = s.research().requiredFor(r.result());
        return t == null || s.research().playerCanUse(p, t.id());
    }

    @Override
    public void action(ServerPlayer p, UiSession ss, String act, String arg, Map<String, String> in) {
        switch (act) {
            case "cat" -> { ss.set("cat", arg); ss.set("recipe", null); }
            case "recipe" -> ss.set("recipe", arg);
            case "craft" -> {
                String[] v = arg.split(";");
                StationRecipe r = s.station().get(v[0]);
                if (r == null) return;
                if (!near(p, ss)) { Msg.err(p, "Rendez-vous à une Station de Craft pour fabriquer."); return; }
                int times = v.length > 1 && v[1].equals("5") ? 5 : 1;
                for (int k = 0; k < times; k++) if (!s.station().craft(p, r, k == 0)) break;
            }
            default -> {
            }
        }
    }
}

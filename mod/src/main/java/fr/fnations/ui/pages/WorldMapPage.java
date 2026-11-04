package fr.fnations.ui.pages;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.fnations.earth.EarthData;
import fr.fnations.earth.EarthWorld;
import fr.fnations.nation.Nation;
import fr.fnations.server.FnServer;
import fr.fnations.ui.UiNode;
import fr.fnations.ui.UiPage;
import fr.fnations.ui.UiSession;
import fr.fnations.util.Txt;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

import static fr.fnations.ui.UiNode.*;
import static fr.fnations.ui.UiTheme.*;

/** Carte du monde au 1/1000 : la Terre, les territoires des nations et votre position. */
public final class WorldMapPage extends UiPage {
    public WorldMapPage(FnServer s) { super(s); }

    @Override
    public String id() { return "carte"; }

    @Override
    public UiNode build(ServerPlayer p, UiSession ss) {
        JsonArray arr = new JsonArray();
        UiNode list = list("mapnations").gap(3).filter("mq").flex(1);
        for (Nation n : s.nations().all()) {
            JsonObject o = new JsonObject();
            o.addProperty("n", n.name());
            o.addProperty("t", n.tag());
            o.addProperty("c", NationPage.hex(n.color()));
            StringBuilder sb = new StringBuilder();
            for (String k : n.claims()) {
                String[] v = k.split(";");
                if (v.length == 3 && v[0].equals("minecraft:overworld")) sb.append(v[1]).append(',').append(v[2]).append(';');
            }
            o.addProperty("k", sb.toString());
            if (n.home() != null && n.home().dim().equals("minecraft:overworld")) {
                JsonArray h = new JsonArray();
                h.add((int) n.home().x());
                h.add((int) n.home().z());
                o.add("home", h);
            }
            arr.add(o);
            String focus = n.home() == null ? null : "client:focus:" + (int) n.home().x() + "," + (int) n.home().z();
            String[] place = n.home() == null ? new String[]{"", ""} : EarthData.get().place(n.home().x(), n.home().z());
            list.add(row(space(4, 22).bg(NationPage.hex(n.color())).r(2), col(text("<b>" + Txt.esc(n.name()) + " " + TM + "[" + n.tag() + "]"),
                    text(T2 + (place[0].isEmpty() ? n.claims().size() + " chunks" : place[0] + " • " + n.claims().size() + " chunks"))).gap(2).flex(1))
                    .pad(4).gap(6).r(4).act(focus).hover());
        }
        if (list.isEmpty()) list.add(empty("Aucune nation pour l'instant."));
        boolean earth = EarthWorld.isEarth(s.server().overworld());
        String[] here = EarthData.get().place(p.getX(), p.getZ());
        UiNode side = card(
                text("<b>Carte du monde").scale(1.3),
                text(T2 + "Échelle 1/1000 : 1 bloc = 1 km").wrap(),
                row(icon("user").size(10).color(TEXT), col(text("<b>Votre position"), text(T2 + EarthData.latLon(p.getX(), p.getZ())),
                        text(here[0].isEmpty() ? TB + "En mer" : TG + here[0] + TM + " (" + here[1] + ")")).gap(2).flex(1)).gap(6).bg(ROW).r(5).pad(6)
                        .act("client:focus:" + p.getBlockX() + "," + p.getBlockZ()).hover(),
                earth ? null : text(TY + "Ce monde n'utilise pas le générateur Terre (level-type=fnations:earth).").wrap(),
                sectionTitle("Nations"), input("mq").placeholder("Rechercher une nation"), list,
                text(TM + "Molette : zoom • Glisser : déplacer • Clic : informations").wrap()).w(170);
        return row(card(worldmap("wm", arr).flex(1)).pad(4).flex(1), side).gap(8);
    }

    @Override
    public void action(ServerPlayer p, UiSession ss, String act, String arg, Map<String, String> in) {}
}

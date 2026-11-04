package fr.fnations.ui.pages;

import fr.fnations.room.Room;
import fr.fnations.room.RoomManager;
import fr.fnations.server.FnServer;
import fr.fnations.server.Msg;
import fr.fnations.ui.UiNode;
import fr.fnations.ui.UiPage;
import fr.fnations.ui.UiSession;
import fr.fnations.util.Txt;
import net.minecraft.server.level.ServerPlayer;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static fr.fnations.ui.UiNode.*;
import static fr.fnations.ui.UiTheme.*;

/** Réception de l'hôtel : liste des chambres, location, achat, colocataires. */
public final class RoomPage extends UiPage {
    private final SimpleDateFormat fmt = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    public RoomPage(FnServer s) { super(s); }

    @Override
    public String id() { return "chambres"; }

    private RoomManager rm() { return s.rooms(); }

    private static String statusColor(Room r, UUID viewer) {
        if (viewer.equals(r.owner()) || r.members().contains(viewer)) return BLUE;
        return switch (r.mode()) {
            case FREE -> GREEN;
            case RENTED -> YELLOW;
            case OWNED -> RED;
        };
    }

    private static String statusLabel(Room r, UUID viewer) {
        if (viewer.equals(r.owner())) return TB + "À vous";
        if (r.members().contains(viewer)) return TB + "Colocation";
        return switch (r.mode()) {
            case FREE -> TG + "Libre";
            case RENTED -> TY + "Louée";
            case OWNED -> TR + "Vendue";
        };
    }

    @Override
    public UiNode build(ServerPlayer p, UiSession ss) {
        String group = ss.get("group", "");
        Set<String> groups = new LinkedHashSet<>();
        for (Room r : rm().all()) if (r.group() != null && !r.group().isEmpty()) groups.add(r.group());
        UiNode chips = row(btn("Toutes", a("group", "")).style("tab").selected(group.isEmpty()), btn("Mes chambres", a("group", "@mine")).style("tab").selected(group.equals("@mine"))).gap(2);
        for (String g : groups) chips.add(btn(Txt.esc(g), a("group", g)).style("tab").selected(g.equals(group)));
        String sel = ss.get("room", "");
        UiNode list = list("rooms").gap(3).flex(1);
        for (Room r : rm().all()) {
            if (group.equals("@mine") ? !r.canUse(p.getUUID()) : !group.isEmpty() && !group.equalsIgnoreCase(r.group())) continue;
            boolean on = r.id().equals(sel);
            list.add(row(icon("dot").size(8).color(statusColor(r, p.getUUID())), col(text("<b>Chambre " + Txt.esc(r.id())), text(TM + Txt.esc(r.group()) + " • " + r.size())).gap(2).flex(1),
                    text(r.rentPrice() > 0 ? T2 + money(r.rentPrice()) + "/j" : r.buyPrice() > 0 ? T2 + money(r.buyPrice()) : TM + "—"))
                    .bg(on ? ACCENT_SOFT : ROW).r(5).pad(6, 5).gap(6).act(a("room", r.id())));
        }
        if (list.isEmpty()) list.add(empty("Aucune chambre ici."));
        UiNode left = card(text("<b>Réception").scale(1.25), chips, list, text(TM + "Solde : " + money(s.bank().balance(p.getUUID())))).w(200);
        Room r = rm().get(sel);
        UiNode right = r == null ? card(icon("bed").size(28).color(TEXT2), text("<b>Choisissez une chambre").center(),
                text(T2 + "Louez-la à la journée ou achetez-la. Le contenu est restauré par WorldEdit à la libération.").wrap().center(), flexSpace()).str("align", "center")
                : detail(p, r);
        return col(header("bed", "Chambres", "Location et achat de chambres de l'hôtel"), row(left, right.flex(1)).gap(8).flex(1)).gap(8);
    }

    private UiNode detail(ServerPlayer p, Room r) {
        UUID u = p.getUUID();
        boolean owner = u.equals(r.owner()), admin = p.hasPermissions(2);
        UiNode info = row(stat("map", "Taille", r.size() + " (" + r.volume() + " blocs)").flex(1), stat("tag", "Groupe", Txt.esc(r.group())).flex(1)).gap(6);
        String occupant = switch (r.mode()) {
            case FREE -> TG + "Libre — disponible dès maintenant";
            case RENTED -> TY + "Louée par " + Txt.esc(s.name(r.owner())) + " jusqu'au " + fmt.format(new Date(r.expiry()))
                    + TM + " (" + Txt.duration((r.expiry() - System.currentTimeMillis()) / 1000) + ")";
            case OWNED -> TR + "Propriété de " + Txt.esc(s.name(r.owner()));
        };
        UiNode actions = row().gap(6);
        if (r.mode() == Room.Mode.FREE) {
            if (r.buyPrice() > 0) actions.add(btn("Acheter " + money(r.buyPrice()), a("buy", r.id())).primary().withIcon("coin"));
            if (r.rentPrice() > 0) {
                actions.add(btn("Louer 1 jour", a("rent", r.id() + ";1")).withIcon("clock"));
                actions.add(btn("7 jours", a("rent", r.id() + ";7")));
            }
        }
        if (owner && r.mode() == Room.Mode.RENTED) {
            actions.add(btn("Prolonger 1 jour", a("rent", r.id() + ";1")).withIcon("clock"));
            actions.add(btn("+7 jours", a("rent", r.id() + ";7")));
        }
        UiNode actions2 = row().gap(6);
        if ((r.mode() == Room.Mode.FREE || owner && r.mode() == Room.Mode.RENTED) && r.rentPrice() > 0)
            actions2.add(row(input("days").placeholder("Jours").numeric().w(44), btn("Louer X jours", a("rentx", r.id()))).gap(4));
        if (r.canUse(u) || admin) actions2.add(btn("Se téléporter", a("tp", r.id())).withIcon("teleport"));
        UiNode mates = null;
        if (owner) {
            UiNode ml = row().gap(4);
            for (UUID m : r.members()) ml.add(row(head(m, s.name(m)).size(12), text(Txt.esc(s.name(m))), btn("", a("unmate", r.id() + ";" + m)).withIcon("close").ghost().w(14).h(14))
                    .bg(ROW).r(4).pad(3).gap(4));
            if (r.members().isEmpty()) ml.add(text(TM + "Aucun colocataire."));
            mates = card(sectionTitle("Colocataires"), ml, row(input("mate").placeholder("Pseudo (connecté)").flex(1).act(a("mate", r.id())),
                    btn("Ajouter", a("mate", r.id())).withIcon("plus")).gap(6)).bg(ROW);
        }
        UiNode danger = owner ? row(text(T2 + (r.mode() == Room.Mode.OWNED ? "Revendre la chambre (" + (int) s.config().getDouble("chambres.revente-pourcent", 50) + " % remboursés)"
                : "Résilier la location") + " — le contenu sera effacé.").wrap().flex(1), btn(r.mode() == Room.Mode.OWNED ? "Revendre" : "Résilier", a("release", r.id())).danger()).gap(8) : null;
        UiNode adminRow = admin ? row(text(TM + "Administration").flex(1), btn("Réinitialiser", a("reset", r.id())), r.mode() != Room.Mode.FREE ? btn("Expulser", a("evict", r.id())).danger() : null).gap(6) : null;
        UiNode info2 = row(stat("coin", "Achat", r.buyPrice() > 0 ? money(r.buyPrice()) : "—").flex(1), stat("clock", "Location", r.rentPrice() > 0 ? money(r.rentPrice()) + "/jour" : "—").flex(1)).gap(6);
        return card(row(text("<b>Chambre " + Txt.esc(r.id())).scale(1.6), chip(statusLabel(r, u)).bg(ROW)).gap(8), text(occupant).wrap(), info, info2,
                actions.isEmpty() ? null : actions, actions2.isEmpty() ? null : actions2, mates, flexSpace(), danger, adminRow);
    }

    @Override
    public void action(ServerPlayer p, UiSession ss, String act, String arg, Map<String, String> in) {
        String[] parts = arg.split(";");
        Room r = rm().get(parts[0]);
        switch (act) {
            case "group" -> { ss.set("group", arg); ss.set("room", null); }
            case "room" -> ss.set("room", arg);
            case "buy" -> { if (r != null) ss.dialog = confirm("Acheter la chambre " + r.id() + " ?", money(r.buyPrice()) + ", propriété permanente.", "Acheter", a("buy_ok", r.id()), false); }
            case "buy_ok" -> { ss.dialog = null; if (r != null) rm().buy(p, r); }
            case "rent" -> {
                if (r == null || parts.length < 2) return;
                try { rm().rent(p, r, Math.max(1, Integer.parseInt(parts[1]))); } catch (NumberFormatException ignored) {}
            }
            case "rentx" -> {
                int d = intIn(in, "days", 0);
                if (r == null || d <= 0) { Msg.err(p, "Nombre de jours invalide."); return; }
                rm().rent(p, r, d);
            }
            case "tp" -> {
                if (r == null || (!r.canUse(p.getUUID()) && !p.hasPermissions(2)) || r.spawn() == null) { Msg.err(p, "Téléportation impossible."); return; }
                s.ui().close(p);
                rm().teleport(p, r);
            }
            case "mate" -> {
                if (r == null || !p.getUUID().equals(r.owner())) return;
                ServerPlayer t = s.playerByName(in.getOrDefault("mate", "").trim());
                if (t == null) { Msg.err(p, "Joueur introuvable (doit être connecté)."); return; }
                r.members().add(t.getUUID());
                Msg.ok(p, t.getScoreboardName() + " peut maintenant utiliser la chambre.");
                Msg.send(t, "<green>" + p.getScoreboardName() + " vous a ajouté à sa chambre " + r.id() + ".");
            }
            case "unmate" -> {
                if (r == null || !p.getUUID().equals(r.owner()) || parts.length < 2) return;
                try { r.members().remove(UUID.fromString(parts[1])); } catch (IllegalArgumentException ignored) {}
            }
            case "release" -> { if (r != null) ss.dialog = confirm("Libérer la chambre " + r.id() + " ?", "Son contenu sera effacé.", "Libérer", a("release_ok", r.id()), true); }
            case "release_ok" -> { ss.dialog = null; if (r != null) rm().release(p, r); }
            case "reset" -> { if (r != null && p.hasPermissions(2)) Msg.send(p, rm().reset(r) ? "<green>Chambre réinitialisée." : "<red>Échec de la restauration."); }
            case "evict" -> { if (r != null && p.hasPermissions(2)) ss.dialog = confirm("Expulser l'occupant ?", null, "Expulser", a("release_ok", r.id()), true); }
            default -> {
            }
        }
    }
}

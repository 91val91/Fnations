package fr.fnations.admin;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.fnations.gui.Icons;
import fr.fnations.gui.PagedPanel;
import fr.fnations.nation.Nation;
import fr.fnations.nation.NationActions;
import fr.fnations.registry.ModItems;
import fr.fnations.research.Technology;
import fr.fnations.room.Room;
import fr.fnations.server.FnServer;
import fr.fnations.server.Msg;
import fr.fnations.util.Inv;
import fr.fnations.util.Locs;
import fr.fnations.util.Txt;
import fr.fnations.war.War;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** Toutes les commandes FNations (Brigadier). Sans argument, chaque commande ouvre son interface. */
public final class Commands {
    private static final List<String> NATION_SUBS = List.of("creer", "inviter", "rejoindre", "quitter", "expulser", "promouvoir",
            "retrograder", "transferer", "claim", "unclaim", "deposer", "retirer", "allie", "capitale", "setcapitale",
            "impot", "tag", "info", "liste", "carte", "dissoudre", "aide");

    private Commands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        cmd(d, List.of("nation", "n", "pays"), 0, Commands::nation, Commands::nationSuggest);
        cmd(d, List.of("banque", "bank", "argent"), 0, Commands::bank, (p, a) -> a.length == 1 ? List.of("solde", "payer", "retirer", "deposer", "nation", "donner") : players(p));
        cmd(d, List.of("payer", "pay"), 0, (p, a) -> pay(p, a, 0), (p, a) -> a.length == 1 ? players(p) : List.of());
        cmd(d, List.of("guerre", "war"), 0, Commands::war, (p, a) -> a.length == 1 ? List.of("declarer", "capturer", "liste") : nations());
        cmd(d, List.of("chambre", "chambres", "hotel"), 0, Commands::room, Commands::roomSuggest);
        cmd(d, List.of("recherche", "rd", "research"), 0, (p, a) -> ui(p, "recherche"), (p, a) -> List.of());
        cmd(d, List.of("menu", "tablette"), 0, (p, a) -> ui(p, "accueil"), (p, a) -> List.of());
        cmd(d, List.of("station"), 2, (p, a) -> ui(p, "station"), (p, a) -> List.of());
        cmd(d, List.of("metier", "metiers", "jobs", "competences"), 0, Commands::jobs, Commands::jobsSuggest);
        cmd(d, List.of("entreprise", "ent", "company"), 0, (p, a) -> ui(p, "entreprise"), (p, a) -> List.of());
        cmd(d, List.of("hdv", "ah", "market"), 0, Commands::market, (p, a) -> a.length == 1 ? List.of("vendre") : List.of());
        cmd(d, List.of("classement", "classements", "top"), 0, (p, a) -> ui(p, "classements"), (p, a) -> List.of());
        cmd(d, List.of("fnations", "fn"), 0, Commands::admin, Commands::adminSuggest);
    }

    // ------------------------------------------------------------ infrastructure
    private static FnServer s() { return FnServer.get(); }

    private static void ui(ServerPlayer p, String page) { s().ui().open(p, page); }

    private static void ui(ServerPlayer p, String page, java.util.Map<String, String> state) { s().ui().open(p, page, state); }

    private static void cmd(CommandDispatcher<CommandSourceStack> d, List<String> names, int perm,
                            BiConsumer<ServerPlayer, String[]> exec, BiFunction<ServerPlayer, String[], List<String>> suggest) {
        SuggestionProvider<CommandSourceStack> sp = (ctx, b) -> {
            ServerPlayer p = ctx.getSource().getPlayer();
            String rem = b.getRemaining();
            String[] parts = rem.split(" ", -1);
            List<String> opts = p == null || s() == null ? List.of() : suggest.apply(p, parts);
            String last = parts[parts.length - 1].toLowerCase();
            var nb = b.createOffset(b.getStart() + rem.lastIndexOf(' ') + 1);
            for (String o : opts) if (o.toLowerCase().startsWith(last)) nb.suggest(o);
            return nb.buildFuture();
        };
        for (String n : names) {
            LiteralArgumentBuilder<CommandSourceStack> root = literal(n).requires(src -> src.hasPermission(perm))
                    .executes(ctx -> run(ctx, exec, new String[0]))
                    .then(argument("args", StringArgumentType.greedyString()).suggests(sp)
                            .executes(ctx -> run(ctx, exec, StringArgumentType.getString(ctx, "args").trim().split("\\s+"))));
            d.register(root);
        }
    }

    private static int run(CommandContext<CommandSourceStack> ctx, BiConsumer<ServerPlayer, String[]> exec, String[] args) {
        ServerPlayer p = ctx.getSource().getPlayer();
        if (p == null) {
            ctx.getSource().sendFailure(Txt.mm("<red>Commande réservée aux joueurs."));
            return 0;
        }
        if (s() == null) return 0;
        exec.accept(p, args);
        return 1;
    }

    private static List<String> players(ServerPlayer p) {
        List<String> out = new ArrayList<>();
        for (ServerPlayer o : p.server.getPlayerList().getPlayers()) out.add(o.getScoreboardName());
        return out;
    }

    private static List<String> nations() {
        List<String> out = new ArrayList<>();
        s().nations().all().forEach(n -> out.add(n.name()));
        return out;
    }

    private static String arg(String[] a, int i) { return a.length > i ? a[i] : null; }

    // ------------------------------------------------------------ /nation
    private static void nation(ServerPlayer p, String[] a) {
        FnServer s = s();
        NationActions act = s.nationActions();
        if (a.length == 0) { ui(p, "pays"); return; }
        String arg = arg(a, 1);
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
            case "carte", "map" -> ui(p, "pays", java.util.Map.of("tab", "territoire"));
            case "liste", "list" -> {
                Msg.send(p, "<gold>Nations (" + s.nations().all().size() + ") :");
                s.nations().all().forEach(n -> Msg.raw(p, " <dark_gray>• " + n.colored() + " <gray>[" + n.tag() + "] "
                        + n.members().size() + " citoyens, " + n.claims().size() + " chunks"));
            }
            case "info" -> {
                Nation n = arg == null ? s.nations().of(p) : s.nations().get(arg);
                if (n == null) { Msg.err(p, "Nation introuvable."); return; }
                Msg.send(p, n.colored());
                s.nations().describe(n).forEach(l -> Msg.raw(p, "  " + l));
            }
            case "dissoudre" -> {
                if (arg == null || !arg.equalsIgnoreCase("confirmer")) usage(p, "dissoudre confirmer");
                else act.disband(p);
            }
            default -> {
                Msg.send(p, "<gold>Commandes /nation :");
                for (String x : NATION_SUBS) Msg.raw(p, " <dark_gray>• <yellow>/nation " + x);
            }
        }
    }

    private static List<String> nationSuggest(ServerPlayer p, String[] a) {
        if (a.length <= 1) return NATION_SUBS;
        return switch (a[0].toLowerCase()) {
            case "rejoindre", "allie", "info" -> nations();
            case "inviter", "expulser", "promouvoir", "retrograder", "transferer" -> players(p);
            case "dissoudre" -> List.of("confirmer");
            default -> List.of();
        };
    }

    private static void withTarget(ServerPlayer p, String name, java.util.function.Consumer<UUID> c) {
        if (name == null) { usage(p, "<commande> <joueur>"); return; }
        UUID u = s().uuidByName(name);
        if (u == null) { Msg.err(p, "Joueur inconnu."); return; }
        c.accept(u);
    }

    private static void money(ServerPlayer p, String v, java.util.function.Consumer<Double> c) {
        Double d = v == null ? null : Txt.parseAmount(v);
        if (d == null) Msg.err(p, "Montant invalide."); else c.accept(d);
    }

    private static void usage(ServerPlayer p, String u) { Msg.err(p, "Usage : /nation " + u); }

    // ------------------------------------------------------------ /banque & /payer
    private static void bank(ServerPlayer p, String[] a) {
        FnServer s = s();
        if (a.length == 0) { ui(p, "banque"); return; }
        switch (a[0].toLowerCase()) {
            case "solde" -> Msg.send(p, "Solde : <gold>" + Txt.money(s.bank().balance(p.getUUID())));
            case "payer" -> pay(p, a, 1);
            case "deposer" -> {
                double v = s.bank().depositAllNotes(p);
                if (v > 0) Msg.ok(p, Txt.money(v) + " déposés."); else Msg.err(p, "Aucun billet.");
            }
            case "retirer" -> {
                Double v = a.length > 1 ? Txt.parseAmount(a[1]) : null;
                if (v == null) { Msg.err(p, "Usage : /banque retirer <montant>"); return; }
                if (!s.bank().withdraw(p.getUUID(), v, "Retrait en billets")) { Msg.err(p, "Fonds insuffisants."); return; }
                Inv.give(p, s.bank().banknote(v));
                Msg.ok(p, "Retrait de " + Txt.money(v) + ".");
            }
            case "nation" -> ui(p, "banque");
            case "donner" -> {
                if (!p.hasPermissions(2)) { Msg.err(p, "Réservé aux administrateurs."); return; }
                if (a.length < 3) { Msg.err(p, "Usage : /banque donner <joueur> <montant>"); return; }
                UUID u = s.uuidByName(a[1]);
                Double v = Txt.parseAmount(a[2]);
                if (u == null || v == null) { Msg.err(p, "Joueur ou montant invalide."); return; }
                s.bank().deposit(u, v, "Don administrateur");
                Msg.ok(p, Txt.money(v) + " donnés à " + Txt.esc(a[1]));
            }
            default -> Msg.send(p, "/banque [solde|payer <joueur> <montant>|retirer <montant>|deposer|nation]");
        }
    }

    private static void pay(ServerPlayer p, String[] a, int off) {
        if (a.length < off + 2) { Msg.err(p, "Usage : /payer <joueur> <montant>"); return; }
        ServerPlayer t = s().playerByName(a[off]);
        Double v = Txt.parseAmount(a[off + 1]);
        if (t == null || v == null || t == p) { Msg.err(p, "Joueur ou montant invalide."); return; }
        if (!s().bank().transfer(p.getUUID(), t.getUUID(), v)) { Msg.err(p, "Fonds insuffisants."); return; }
        Msg.ok(p, "Vous avez envoyé " + Txt.money(v) + " à " + t.getScoreboardName());
        Msg.ok(t, "Vous avez reçu " + Txt.money(v) + " de " + p.getScoreboardName());
    }

    // ------------------------------------------------------------ /guerre
    private static void war(ServerPlayer p, String[] a) {
        FnServer s = s();
        if (a.length == 0) { ui(p, "guerres"); return; }
        switch (a[0].toLowerCase()) {
            case "declarer" -> {
                if (a.length < 2) { Msg.err(p, "Usage : /guerre declarer <nation>"); return; }
                Nation t = s.nations().get(a[1]);
                if (t == null) Msg.err(p, "Nation introuvable.");
                else { ui(p, "guerres"); s.ui().handle(p, "guerres.declare:" + t.id(), "{}"); }
            }
            case "capturer" -> s.wars().startCapture(p);
            case "liste" -> {
                if (s.wars().all().isEmpty()) Msg.send(p, "<green>☮ Le monde est en paix.");
                for (War w : s.wars().all()) Msg.send(p, w.attacker() + " " + w.attackerScore() + " ⚔ " + w.defenderScore() + " " + w.defender());
            }
            default -> ui(p, "guerres");
        }
    }

    // ------------------------------------------------------------ /chambre
    private static void room(ServerPlayer p, String[] a) {
        FnServer s = s();
        if (a.length == 0) { ui(p, "chambres"); return; }
        String sub = a[0].toLowerCase();
        if (!List.of("liste", "tp").contains(sub) && !p.hasPermissions(2)) { ui(p, "chambres"); return; }
        var rm = s.rooms();
        switch (sub) {
            case "pos1", "pos2" -> {
                rm.snapshots().pick(p, sub.equals("pos1") ? 0 : 1, p.blockPosition());
                Msg.ok(p, "Position " + sub.charAt(3) + " : " + p.blockPosition().toShortString());
            }
            case "creer" -> {
                if (a.length < 4) { Msg.err(p, "Usage : /chambre creer <id> <prixAchat|0> <prixLocationJour|0> [groupe]"); return; }
                try {
                    rm.create(p, a[1], Double.parseDouble(a[2]), Double.parseDouble(a[3]), a.length > 4 ? String.join(" ", Arrays.copyOfRange(a, 4, a.length)) : null);
                } catch (NumberFormatException e) { Msg.err(p, "Prix invalide."); }
            }
            case "supprimer" -> withRoom(p, a, r -> { rm.delete(r); Msg.ok(p, "Chambre supprimée."); });
            case "reset" -> withRoom(p, a, r -> Msg.send(p, rm.reset(r) ? "<green>Chambre restaurée." : "<red>Échec."));
            case "snapshot" -> withRoom(p, a, r -> rm.snapshot(p, r));
            case "setspawn" -> withRoom(p, a, r -> {
                if (!r.contains(p.level(), p.blockPosition())) { Msg.err(p, "Placez-vous dans la chambre."); return; }
                r.setSpawn(Locs.Spot.of(p.level(), p.position(), p.getYRot(), p.getXRot()));
                Msg.ok(p, "Point d'arrivée défini.");
            });
            case "prix" -> withRoom(p, a, r -> {
                if (a.length < 4) { Msg.err(p, "Usage : /chambre prix <id> <achat> <locationJour>"); return; }
                r.setPrices(Double.parseDouble(a[2]), Double.parseDouble(a[3]));
                Msg.ok(p, "Prix mis à jour.");
            });
            case "guichet" -> {
                Vec3 eye = p.getEyePosition();
                BlockHitResult hit = p.level().clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(6)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
                if (hit.getType() != HitResult.Type.BLOCK) { Msg.err(p, "Regardez un bloc (pupitre, panneau...)."); return; }
                BlockPos b = hit.getBlockPos();
                if (a.length > 1 && a[1].equalsIgnoreCase("retirer")) { Msg.send(p, rm.removeDesk(p.level(), b) ? "<green>Guichet retiré." : "<red>Ce bloc n'est pas un guichet."); return; }
                String group = a.length > 1 ? String.join(" ", Arrays.copyOfRange(a, 1, a.length)) : "";
                rm.setDesk(p.level(), b, group);
                Msg.ok(p, "Guichet de réception créé" + (group.isEmpty() ? "" : " pour le groupe " + Txt.esc(group)) + ". Clic droit dessus pour ouvrir.");
            }
            case "tp" -> withRoom(p, a, r -> {
                if (!r.canUse(p.getUUID()) && !p.hasPermissions(2)) { Msg.err(p, "Ce n'est pas votre chambre."); return; }
                rm.teleport(p, r);
            });
            case "liste" -> rm.all().forEach(r -> Msg.send(p, "<white>" + r.id() + " <gray>[" + Txt.esc(r.group()) + "] " + r.mode() + " " + r.size()));
            default -> Msg.send(p, "/chambre [pos1|pos2|creer|supprimer|reset|snapshot|setspawn|prix|guichet|tp|liste]");
        }
    }

    private static void withRoom(ServerPlayer p, String[] a, java.util.function.Consumer<Room> c) {
        Room r = a.length > 1 ? s().rooms().get(a[1]) : s().rooms().at(p.level(), p.blockPosition());
        if (r == null) { Msg.err(p, "Chambre introuvable."); return; }
        try { c.accept(r); } catch (NumberFormatException e) { Msg.err(p, "Nombre invalide."); }
    }

    private static List<String> roomSuggest(ServerPlayer p, String[] a) {
        if (a.length <= 1) return p.hasPermissions(2)
                ? List.of("pos1", "pos2", "creer", "supprimer", "reset", "snapshot", "setspawn", "prix", "guichet", "tp", "liste")
                : List.of("tp", "liste");
        if (a.length == 2 && !List.of("creer", "guichet", "pos1", "pos2").contains(a[0].toLowerCase())) {
            List<String> out = new ArrayList<>();
            s().rooms().all().forEach(r -> out.add(r.id()));
            return out;
        }
        return List.of();
    }

    // ------------------------------------------------------------ /metier
    private static void jobs(ServerPlayer p, String[] a) {
        FnServer s = s();
        if (a.length >= 4 && a[0].equalsIgnoreCase("set") && p.hasPermissions(2)) {
            ServerPlayer t = s.playerByName(a[1]);
            fr.fnations.job.Job j = fr.fnations.job.Job.byId(a[2]);
            Double v = Txt.parseAmount(a[3]);
            if (t == null || j == null || v == null) { Msg.err(p, "Usage : /metier set <joueur> <metier> <xp>"); return; }
            s.jobs().setXp(t, j, v);
            Msg.ok(p, j.display + " de " + t.getScoreboardName() + " : " + Math.round(v) + " XP.");
            return;
        }
        fr.fnations.job.Job j = a.length > 0 ? fr.fnations.job.Job.byId(a[0]) : null;
        ui(p, "metiers", j == null ? java.util.Map.of() : java.util.Map.of("job", j.id));
    }

    private static List<String> jobsSuggest(ServerPlayer p, String[] a) {
        List<String> ids = new ArrayList<>();
        for (fr.fnations.job.Job j : fr.fnations.job.Job.values()) ids.add(j.id);
        if (a.length <= 1) {
            if (p.hasPermissions(2)) ids.add("set");
            return ids;
        }
        if (a[0].equalsIgnoreCase("set")) return a.length == 2 ? players(p) : a.length == 3 ? ids : List.of();
        return List.of();
    }

    // ------------------------------------------------------------ /hdv
    private static void market(ServerPlayer p, String[] a) {
        FnServer s = s();
        if (a.length >= 2 && a[0].equalsIgnoreCase("vendre")) {
            Double price = Txt.parseAmount(a[1]);
            int slot = p.getInventory().selected;
            var held = p.getInventory().items.get(slot);
            if (price == null || held.isEmpty()) { Msg.err(p, "Usage : /hdv vendre <prix unitaire> [quantité] (objet en main)"); return; }
            int qty = held.getCount();
            try { if (a.length > 2) qty = Math.max(1, Integer.parseInt(a[2])); } catch (NumberFormatException ignored) {}
            s.market().sell(p, slot, qty, price, fr.fnations.market.MarketManager.player(p.getUUID()));
            return;
        }
        ui(p, "hdv");
    }

    // ------------------------------------------------------------ /fnations (admin)
    private static void admin(ServerPlayer p, String[] a) {
        FnServer s = s();
        if (a.length == 0 || !p.hasPermissions(2)) { ui(p, "accueil"); return; }
        switch (a[0].toLowerCase()) {
            case "give" -> {
                if (a.length < 3) { Msg.err(p, "Usage : /fnations give <joueur> <item> [quantité]"); return; }
                ServerPlayer t = s.playerByName(a[1]);
                Item it = ModItems.get(a[2]);
                if (t == null || it == null) { Msg.err(p, "Joueur ou item inconnu."); return; }
                int n = 1;
                try { if (a.length > 3) n = Math.max(1, Integer.parseInt(a[3])); } catch (NumberFormatException ignored) {}
                while (n > 0) {
                    int c = Math.min(n, it.getDefaultMaxStackSize());
                    Inv.give(t, ModItems.stack(a[2], c));
                    n -= c;
                }
                Msg.ok(p, a[2] + " donné(s) à " + t.getScoreboardName());
            }
            case "items" -> {
                List<PagedPanel.Entry> list = new ArrayList<>();
                for (var e : ModItems.ALL.entrySet()) {
                    String id = e.getKey();
                    list.add(new PagedPanel.Entry(ModItems.stack(id), pl -> Inv.give(pl, ModItems.stack(id, Math.min(16, e.getValue().getDefaultMaxStackSize())))));
                }
                new PagedPanel("<dark_red>Items FNations (admin)", list, null).open(p);
            }
            case "reload" -> { s.reloadAll(); Msg.ok(p, "Configuration rechargée."); }
            case "save" -> { s.saveAll(); Msg.ok(p, "Données sauvegardées."); }
            case "recherche" -> {
                if (a.length < 3) { Msg.err(p, "Usage : /fnations recherche <nation> <techno|tout>"); return; }
                Nation n = s.nations().get(a[1]);
                if (n == null) { Msg.err(p, "Nation inconnue."); return; }
                if (a[2].equalsIgnoreCase("tout")) {
                    for (Technology t : s.research().all()) if (!n.researched().contains(t.id())) s.research().complete(n, t);
                } else {
                    Technology t = s.research().get(a[2]);
                    if (t == null) { Msg.err(p, "Technologie inconnue."); return; }
                    s.research().complete(n, t);
                }
                Msg.ok(p, "Recherche accordée.");
            }
            default -> Msg.send(p, "/fnations [give|items|reload|save|recherche]");
        }
    }

    private static List<String> adminSuggest(ServerPlayer p, String[] a) {
        if (!p.hasPermissions(2)) return List.of();
        if (a.length <= 1) return List.of("give", "items", "reload", "save", "recherche");
        if (a.length == 2 && a[0].equalsIgnoreCase("give")) return players(p);
        if (a.length == 2 && a[0].equalsIgnoreCase("recherche")) return nations();
        if (a.length == 3 && a[0].equalsIgnoreCase("give")) return new ArrayList<>(ModItems.ALL.keySet());
        if (a.length == 3 && a[0].equalsIgnoreCase("recherche")) {
            List<String> out = new ArrayList<>(List.of("tout"));
            s().research().all().forEach(t -> out.add(t.id()));
            return out;
        }
        return List.of();
    }
}

package fr.fnations.ui.pages;

import com.google.gson.JsonObject;
import fr.fnations.nation.Nation;
import fr.fnations.nation.NationActions;
import fr.fnations.nation.Role;
import fr.fnations.server.FnServer;
import fr.fnations.server.Msg;
import fr.fnations.ui.UiNode;
import fr.fnations.ui.UiPage;
import fr.fnations.ui.UiSession;
import fr.fnations.util.Locs;
import fr.fnations.util.Txt;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static fr.fnations.ui.UiNode.*;
import static fr.fnations.ui.UiTheme.*;

/** Mon pays : profil, membres, territoire, diplomatie, paramètres ; création / adhésion sans nation. */
public final class NationPage extends UiPage {
    private static final List<String> COLORS = List.of("aqua", "red", "green", "blue", "yellow", "gold", "light_purple",
            "dark_aqua", "dark_green", "dark_red", "white", "gray");
    private static final List<String> TAGS = List.of("Build", "Farm", "Commerce", "Pacifique", "Militaire", "Industrie", "Recherche", "RolePlay", "Spatial");

    public NationPage(FnServer s) { super(s); }

    @Override
    public String id() { return "pays"; }

    private NationActions act() { return s.nationActions(); }

    public static String hex(String mcColor) {
        ChatFormatting f = ChatFormatting.getByName(mcColor);
        Integer c = f == null ? null : f.getColor();
        return c == null ? ACCENT : String.format("#%06X", c);
    }

    /** Couleur sombre teintée pour les bandeaux. */
    private static String dark(String mcColor) {
        ChatFormatting f = ChatFormatting.getByName(mcColor);
        Integer c = f == null ? null : f.getColor();
        if (c == null) return ACCENT_SOFT;
        int r = ((c >> 16) & 255) * 30 / 100 + 20, g = ((c >> 8) & 255) * 30 / 100 + 20, b = (c & 255) * 30 / 100 + 30;
        return String.format("#%02X%02X%02X", r, g, b);
    }

    @Override
    public UiNode build(ServerPlayer p, UiSession ss) {
        Nation n = s.nations().of(p);
        if (n == null) return noNation(p, ss);
        String tab = ss.get("tab", "profil");
        boolean leader = n.can(p.getUUID(), Role.LEADER), officer = n.can(p.getUUID(), Role.OFFICER);
        UiNode tabs = row(
                btn("Profil", a("tab", "profil")).style("tab").selected(tab.equals("profil")),
                btn("Membres", a("tab", "membres")).style("tab").selected(tab.equals("membres")),
                btn("Territoire", a("tab", "territoire")).style("tab").selected(tab.equals("territoire")),
                btn("Diplomatie", a("tab", "diplomatie")).style("tab").selected(tab.equals("diplomatie")),
                leader ? btn("Paramètres", a("tab", "parametres")).style("tab").selected(tab.equals("parametres")) : null,
                flexSpace(),
                chip(TG + money(n.balance())).bg(ROW).tip(T2 + "Trésor national")).gap(2);
        UiNode body = switch (tab) {
            case "membres" -> members(p, n, officer);
            case "territoire" -> territory(p, n, officer);
            case "diplomatie" -> diplomacy(p, n, officer);
            case "parametres" -> leader ? settings(n) : profile(p, n, officer);
            default -> profile(p, n, officer);
        };
        return col(tabs, body.flex(1)).gap(6);
    }

    // ================================================================ profil
    private UiNode profile(ServerPlayer p, Nation n, boolean officer) {
        UiNode tags = row().gap(4);
        for (String t : n.tags()) tags.add(chip(T2 + "#" + t).bg("#55000000"));
        if (n.tags().isEmpty()) tags.add(chip(TM + "#Nouveau").bg("#55000000"));
        UiNode buttons = row(btn("Se téléporter", a("home")).primary().withIcon("teleport"),
                officer ? btn("Définir la capitale ici", a("sethome")).withIcon("flag") : null,
                btn("Banque nationale", "nav:banque").withIcon("bank")).gap(6);
        UiNode banner = row(
                col(text("<b>" + Txt.esc(n.name())).scale(2), tags,
                        text(n.description().isEmpty() ? TM + "Aucune description." : T2 + Txt.esc(n.description())).wrap().lines(2),
                        flexSpace(), buttons).gap(5).flex(1),
                col(text("<b>" + n.tag()).scale(2.5).center().color(hex(n.color())), text(TM + "Fondée il y a " + days(n) + " j").center())
                        .w(92).bg("#40000000").r(6).pad(8).gap(6))
                .bg(dark(n.color())).r(8).pad(10).gap(10).h(98);

        double[] lvl = s.nations().level(n);
        UiNode level = row(icon("star").size(16).color(TEXT), col(text("Niveau <#D6D9FF>(" + Math.round(lvl[1] * 100) + " %)"),
                text("<b>" + (int) lvl[0]).scale(1.5)).gap(2)).bg(ACCENT).r(6).pad(8).gap(8);
        UiNode techs = row(icon("medal").size(16).color(TEXT2), col(text(T2 + "Technologies"),
                text("<b>" + n.researched().size() + "/" + s.research().all().size()).scale(1.5)).gap(2)).bg(ROW).r(6).pad(8).gap(8);
        fr.fnations.rating.RatingManager.Score sc = s.ratings().nationScore(n.id());
        java.util.List<String> noteTip = new java.util.ArrayList<>();
        noteTip.add("<b>Notation des constructions");
        for (var c : fr.fnations.rating.RatingManager.Criterion.values())
            noteTip.add(T2 + c.display + " : <white>" + (sc.count() == 0 ? "—" : fr.fnations.rating.RatingManager.fmt(sc.criteria()[c.ordinal()]) + "/10"));
        noteTip.add(TM + sc.count() + " construction(s) notée(s) • clic : ouvrir");
        UiNode note = row(icon("medal").size(16).color(YELLOW), col(text(T2 + "Notation"),
                text(sc.count() == 0 ? "<b>" + TM + "—" : "<b><" + NotationPage.color(sc.global()) + ">" + fr.fnations.rating.RatingManager.fmt(sc.global()) + "</b>" + TM + "/10").scale(1.5)).gap(2))
                .bg(ROW).r(6).pad(8).gap(8).act("nav:notation").tip(noteTip).hover();
        UiNode statsA = col(
                row(text(T2 + "Claims").flex(1), text("<b>" + n.claims().size() + "/" + s.nations().maxClaims(n))),
                row(text(T2 + "Puissance").flex(1), text("<b>" + s.nations().power(n))),
                row(text(T2 + "Trésor").flex(1), text("<b>" + money(n.balance())))).bg(ROW).r(6).pad(8).gap(6).flex(1);
        UiNode statsB = col(
                row(text(T2 + "Guerres").flex(1), text("<b>" + TG + n.wins() + "V " + TR + n.losses() + "D")),
                row(text(T2 + "Création").flex(1), text("<b>" + days(n) + " j")),
                row(text(T2 + "Joueurs").flex(1), text("<b>" + TA + s.nations().online(n).size() + "</b>" + T2 + "/" + n.members().size()))).bg(ROW).r(6).pad(8).gap(6).flex(1);
        UiNode members = list("pm").gap(2).flex(1);
        n.members().entrySet().stream().sorted((x, y) -> y.getValue().power - x.getValue().power).forEach(e -> {
            boolean on = s.player(e.getKey()) != null;
            members.add(row(head(e.getKey(), s.name(e.getKey())).size(12),
                    icon(e.getValue() == Role.LEADER ? "crown" : e.getValue() == Role.OFFICER ? "shield" : "user").size(8).color(e.getValue() == Role.LEADER ? YELLOW : TEXT2),
                    text((on ? "" : T2) + Txt.esc(s.name(e.getKey()))).flex(1), icon("dot").size(6).color(on ? GREEN : MUTED)).pad(3).gap(5).r(4).hover()
                    .tip("<b>" + Txt.esc(s.name(e.getKey())), e.getValue().display, on ? TG + "En ligne" : TM + "Hors ligne"));
        });
        UiNode grid = row(col(level, techs, note).gap(6).w(108), card(sectionTitle("Territoire"), miniMap(n)).w(124).pad(6),
                col(statsA, statsB).gap(6).flex(1), card(sectionTitle("Citoyens"), members).w(130).pad(6)).gap(6);
        return col(banner, text("<b>Informations").scale(1.25), grid.flex(1)).gap(8);
    }

    private static long days(Nation n) { return Math.max(0, (System.currentTimeMillis() - n.createdAt()) / 86_400_000L); }

    /** Carte du territoire de la nation (zone englobante, sans relief). */
    private UiNode miniMap(Nation n) {
        String dim = n.home() != null ? n.home().dim() : "minecraft:overworld";
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (String k : n.claims()) {
            String[] parts = k.split(";");
            if (parts.length < 3 || !parts[0].equals(dim)) continue;
            int x = Integer.parseInt(parts[1]), z = Integer.parseInt(parts[2]);
            minX = Math.min(minX, x); maxX = Math.max(maxX, x); minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
        }
        int cols = 13, rows = 11;
        int cx = minX == Integer.MAX_VALUE ? (n.home() == null ? 0 : (int) n.home().x() >> 4) : (minX + maxX) / 2;
        int cz = minZ == Integer.MAX_VALUE ? (n.home() == null ? 0 : (int) n.home().z() >> 4) : (minZ + maxZ) / 2;
        List<String> lines = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            StringBuilder sb = new StringBuilder();
            for (int c = 0; c < cols; c++) {
                String key = Locs.chunkKey(dim, cx + c - cols / 2, cz + r - rows / 2);
                Nation o = s.nations().atChunk(key);
                sb.append(o == null ? '.' : o == n ? 'n' : 'o');
            }
            lines.add(sb.toString());
        }
        JsonObject pal = new JsonObject(), names = new JsonObject();
        pal.addProperty(".", "#232534");
        pal.addProperty("n", hex(n.color()));
        pal.addProperty("o", "#555869");
        names.addProperty("n", "<" + n.color() + ">" + n.name());
        names.addProperty("o", "<gray>Autre nation");
        return map(cx, cz, lines, pal, names, null).h(92);
    }

    // ================================================================ membres
    private UiNode members(ServerPlayer p, Nation n, boolean officer) {
        UiNode l = list("members").gap(4).flex(1);
        boolean leader = n.can(p.getUUID(), Role.LEADER);
        n.members().entrySet().stream().sorted((x, y) -> y.getValue().power - x.getValue().power).forEach(e -> {
            UUID u = e.getKey();
            boolean on = s.player(u) != null, self = u.equals(p.getUUID());
            UiNode actions = row().gap(4);
            if (leader && !self) {
                if (e.getValue() == Role.MEMBER) actions.add(btn("Promouvoir", a("promote", u)).withIcon("arrow_up"));
                if (e.getValue() == Role.OFFICER) actions.add(btn("Rétrograder", a("demote", u)).withIcon("arrow_down"));
                actions.add(btn("Céder", a("transfer", u)).withIcon("crown").tip(T2 + "Transférer la direction"));
            }
            if (officer && !self && e.getValue().power < n.role(p.getUUID()).power) actions.add(btn("Expulser", a("kick", u)).danger());
            l.add(row(head(u, s.name(u)).size(16), col(text("<b>" + Txt.esc(s.name(u))), text(e.getValue().display)).gap(2).flex(1),
                    chip(on ? TG + "En ligne" : TM + "Hors ligne").bg(FIELD), actions).bg(ROW).r(5).pad(6).gap(8));
        });
        UiNode invite = officer ? row(input("invite").placeholder("Pseudo du joueur").flex(1).act(a("invite")),
                btn("Inviter", a("invite")).primary().withIcon("plus")).gap(6) : null;
        UiNode leave = n.leader().equals(p.getUUID()) ? null : btn("Quitter la nation", a("leave")).danger();
        return col(row(text("<b>Citoyens " + TM + "(" + n.members().size() + "/" + s.config().getInt("nations.membres-max", 50) + ")").scale(1.25).flex(1), leave),
                invite, l).gap(8);
    }

    // ================================================================ territoire
    private UiNode territory(ServerPlayer p, Nation mine, boolean officer) {
        int cx = p.blockPosition().getX() >> 4, cz = p.blockPosition().getZ() >> 4;
        String dim = Locs.dim(p.level());
        int cols = 21, rows = 13;
        Map<String, Character> codes = new LinkedHashMap<>();
        JsonObject pal = new JsonObject(), names = new JsonObject();
        pal.addProperty(".", "#00000000");
        pal.addProperty("m", "#9943D17A");
        pal.addProperty("h", "#99B06BFF");
        names.addProperty("m", "<green>" + mine.name() + " (vous)");
        names.addProperty("h", "<light_purple>Hôtel (chambres)");
        char next = 'A';
        List<String> lines = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            StringBuilder sb = new StringBuilder();
            for (int c = 0; c < cols; c++) {
                int x = cx + c - cols / 2, z = cz + r - rows / 2;
                Nation o = s.nations().atChunk(Locs.chunkKey(dim, x, z));
                char ch;
                if (s.rooms().intersectsChunk(dim, x, z)) ch = 'h';
                else if (o == null) ch = '.';
                else if (o == mine) ch = 'm';
                else {
                    Character known = codes.get(o.id());
                    if (known == null && next <= 'Z') {
                        known = next++;
                        codes.put(o.id(), known);
                        boolean ally = s.nations().allied(mine, o), war = s.wars().between(mine, o) != null;
                        pal.addProperty(String.valueOf(known), war ? "#B0F04747" : ally ? "#993BA7F0" : "#99F5A623");
                        names.addProperty(String.valueOf(known), (war ? "<red>" : ally ? "<aqua>" : "<gold>") + o.name() + (war ? " (ennemi)" : ally ? " (allié)" : ""));
                    }
                    ch = known == null ? 'Z' : known;
                }
                sb.append(ch);
            }
            lines.add(sb.toString());
        }
        UiNode map = map(cx, cz, lines, pal, names, officer ? a("claimat") : null).flag("terrain").flex(1);
        double cost = s.config().getDouble("nations.cout-claim", 500);
        UiNode side = card(
                text("<b>Territoire").scale(1.25),
                row(text(T2 + "Chunks").flex(1), text("<b>" + mine.claims().size() + " / " + s.nations().maxClaims(mine))),
                bar(mine.claims().size() / (double) Math.max(1, s.nations().maxClaims(mine))).h(5),
                row(text(T2 + "Coût d'un chunk").flex(1), text(TO + money(cost))),
                sep(),
                officer ? btn("Revendiquer ce chunk", a("claim")).primary().withIcon("plus").flag("fill") : text(TM + "Seuls les Ministres revendiquent.").wrap(),
                officer ? btn("Abandonner ce chunk", a("unclaim")).withIcon("minus").flag("fill") : null,
                text(TM + "Cliquez sur un chunk libre de la carte, adjacent à votre territoire, pour le revendiquer.").wrap(),
                flexSpace(),
                sectionTitle("Légende"),
                legend("#43D17A", "Votre nation"), legend("#3BA7F0", "Alliés"), legend("#F04747", "Ennemis"), legend("#F5A623", "Autres nations"),
                legend("#B06BFF", "Hôtel"), row(icon("dot").size(8).color(TEXT), text(T2 + "Votre position")).gap(5))
                .w(150);
        return row(card(map).flex(1).pad(6), side).gap(8);
    }

    private static UiNode legend(String color, String label) {
        return row(space(8, 8).bg(color).r(2), text(T2 + label)).gap(5);
    }

    // ================================================================ diplomatie
    private UiNode diplomacy(ServerPlayer p, Nation mine, boolean officer) {
        UiNode l = list("dip").gap(4).flex(1);
        for (Nation o : s.nations().all()) {
            if (o == mine) continue;
            boolean ally = mine.allies().contains(o.id()), war = s.wars().between(mine, o) != null, req = mine.allyRequests().contains(o.id());
            String status = war ? TR + "⚔ En guerre" : ally ? TB + "★ Allié" : req ? TY + "✉ Propose une alliance" : TM + "Neutre";
            UiNode actions = row().gap(4);
            if (officer && !war) actions.add(btn(ally ? "Rompre l'alliance" : req ? "Accepter l'alliance" : "Proposer une alliance", a("ally", o.id()))
                    .withIcon("handshake").style(req ? "success" : "secondary"));
            if (officer && !ally && !war) actions.add(btn("Déclarer la guerre", a("war", o.id())).danger().withIcon("swords"));
            l.add(row(space(4, 26).bg(hex(o.color())).r(2), col(text("<b>" + Txt.esc(o.name()) + " " + TM + "[" + o.tag() + "]"),
                    text(T2 + o.members().size() + " citoyens • " + o.claims().size() + " chunks • puissance " + s.nations().power(o))).gap(2).flex(1),
                    text(status), actions).bg(ROW).r(5).pad(6).gap(8));
        }
        if (l.isEmpty()) l.add(empty("Aucune autre nation pour le moment."));
        return col(text("<b>Relations internationales").scale(1.25), l).gap(8);
    }

    // ================================================================ paramètres
    private UiNode settings(Nation n) {
        UiNode colors = row().gap(4);
        for (String c : COLORS) colors.add(btn("", a("color", c)).w(16).h(16).r(8).bg(hex(c)).selected(c.equals(n.color()))
                .tip("<" + c + ">" + c));
        UiNode tags = row().gap(4);
        for (String t : TAGS) tags.add(btn("#" + t, a("tagtoggle", t)).selected(n.tags().contains(t)));
        return col(
                text("<b>Paramètres de la nation").scale(1.25),
                card(row(col(text(T2 + "Tag (2 à 5 caractères)"), row(input("tag").value(n.tag()).maxLen(5).w(80), btn("Valider", a("tag"))).gap(6)).gap(4).flex(1),
                                col(text(T2 + "Impôt horaire par citoyen"), row(input("tax").value(String.valueOf(n.taxPerHour())).numeric().w(80), btn("Valider", a("tax"))).gap(6)).gap(4).flex(1),
                                col(text(T2 + "Recrutement"), btn(n.open() ? "Ouvert à tous" : "Sur invitation", a("open")).style(n.open() ? "success" : "secondary")).gap(4).flex(1)).gap(10),
                        text(T2 + "Couleur"), colors,
                        text(T2 + "Étiquettes (4 maximum)"), tags,
                        text(T2 + "Description"), row(input("desc").value(n.description()).maxLen(120).flex(1), btn("Valider", a("desc"))).gap(6)),
                card(row(col(text("<b>" + TR + "Dissoudre la nation"), text(T2 + "Le trésor vous est reversé. Tous les territoires sont perdus.")).gap(2).flex(1),
                        btn("Dissoudre", a("disband")).danger()).gap(8)),
                flexSpace()).gap(8);
    }

    // ================================================================ sans nation
    private UiNode noNation(ServerPlayer p, UiSession ss) {
        String sel = ss.get("sel", "");
        UiNode list = list("nations").gap(3).filter("q").flex(1);
        list.add(row(icon("plus").size(10).color(sel.isEmpty() ? TEXT : TA), text((sel.isEmpty() ? "<b>" : TA) + "Créer mon pays").flex(1))
                .pad(6, 5).r(5).bg(sel.isEmpty() ? ACCENT : null).act(a("select", "")).gap(6));
        for (Nation n : s.nations().all()) {
            boolean can = n.open() || n.invites().contains(p.getUUID());
            boolean on = n.id().equals(sel);
            list.add(row(space(3, 12).bg(hex(n.color())).r(1), text((on ? "<b>" : "") + Txt.esc(n.name())).flex(1),
                    text(n.invites().contains(p.getUUID()) ? TY + "Invité" : n.open() ? TG + "Ouvert" : TM + "Fermé"))
                    .pad(6, 5).r(5).bg(on ? ACCENT_SOFT : null).act(a("select", n.id())).gap(6).keys(n.tag()));
        }
        UiNode left = card(text("<b>Pays du monde").scale(1.25), input("q").placeholder("Rechercher...").withIcon("search"), list).w(170);
        Nation chosen = s.nations().get(sel);
        UiNode right = chosen == null ? createForm(p) : joinView(p, chosen);
        return col(header("flag", "Gestion du pays", "Fondez votre pays ou rejoignez une nation existante"), row(left, right.flex(1)).gap(8).flex(1)).gap(8);
    }

    private UiNode joinView(ServerPlayer p, Nation n) {
        boolean can = n.open() || n.invites().contains(p.getUUID());
        UiNode members = row().gap(3);
        int k = 0;
        for (UUID u : n.members().keySet()) { if (k++ >= 12) break; members.add(head(u, s.name(u)).size(14).tip(Txt.esc(s.name(u)))); }
        return card(
                row(col(text("<b>" + Txt.esc(n.name())).scale(2), text(TM + "[" + n.tag() + "] • fondée il y a " + days(n) + " jours")).gap(4).flex(1),
                        text("<b>" + n.tag()).scale(2).color(hex(n.color()))).gap(8),
                text(n.description().isEmpty() ? TM + "Aucune description." : T2 + Txt.esc(n.description())).wrap(),
                row(stat("users", "Citoyens", String.valueOf(n.members().size())).flex(1), stat("map", "Territoire", n.claims().size() + " chunks").flex(1),
                        stat("star", "Puissance", String.valueOf(s.nations().power(n))).flex(1)).gap(6),
                sectionTitle("Citoyens"), members,
                flexSpace(),
                row(text(can ? TG + "Vous pouvez rejoindre cette nation." : TM + "Cette nation recrute sur invitation.").flex(1),
                        btn("Rejoindre", a("join", n.id())).primary().disabled(!can)).gap(8));
    }

    private UiNode createForm(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        BlockPos pos = p.blockPosition();
        Holder<Biome> biome = level.getBiome(pos);
        String biomeKey = biome.unwrapKey().map(k -> "biome." + k.location().getNamespace() + "." + k.location().getPath()).orElse("biome.minecraft.plains");
        double cost = s.config().getDouble("nations.cout-creation", 5000);
        Nation owner = s.nations().at(level, pos);
        String[] place = fr.fnations.earth.EarthData.get().place(pos.getX(), pos.getZ());
        boolean earth = fr.fnations.earth.EarthWorld.isEarth(level);
        UiNode info = col(
                sectionTitle("Localisation"),
                earth ? text("<b>" + (place[0].isEmpty() ? "En mer" : Txt.esc(place[0])) + (place[1].isEmpty() ? "" : TM + " • " + place[1])) : null,
                earth ? text(T2 + fr.fnations.earth.EarthData.latLon(pos.getX(), pos.getZ())) : null,
                text("<b><lang:" + biomeKey + "> " + TM + "(" + pos.getX() + ", " + pos.getZ() + ")"),
                sectionTitle("Ressources"), text(resources(level, pos, biome)).wrap(),
                sectionTitle("Cultures"), text(T2 + cultures(biome)).wrap(),
                owner != null ? text(TR + "Ce chunk appartient déjà à " + Txt.esc(owner.name()) + ".").wrap() : null).gap(4).flex(1);
        UiNode form = col(
                text(T2 + "Nom du pays"),
                input("name").placeholder("Ex : Atlantis").maxLen(16).act(a("create")),
                check("announce", "Annoncer la création à tout le serveur", true),
                row(text(T2 + "Coût de fondation : " + TO + money(cost)).flex(1), btn("Créer le pays", a("create")).primary().withIcon("flag")).gap(8)).gap(5);
        return card(text("<b>Créer un pays").scale(1.5),
                text(T2 + "Votre pays sera fondé ici : le chunk où vous vous trouvez devient votre capitale.").wrap(),
                row(terrain(48).w(132).h(132), info).gap(10).valign("top"), flexSpace(), form);
    }

    private static String cultures(Holder<Biome> b) {
        float t = b.value().getBaseTemperature();
        boolean rain = b.value().hasPrecipitation();
        if (b.is(BiomeTags.IS_JUNGLE)) return "Cacao, melons, bambou, canne à sucre";
        if (t >= 1.5f && !rain) return "Cactus, canne à sucre, citrouilles";
        if (t >= 0.9f) return "Melons, canne à sucre, blé, carottes";
        if (t <= 0.2f) return "Betteraves, baies sucrées, pommes de terre";
        return "Blé, carottes, pommes de terre, betteraves";
    }

    /** Ressources : minerais détectés sous la zone + richesses du biome. */
    private static String resources(ServerLevel level, BlockPos pos, Holder<Biome> b) {
        Map<String, Integer> found = new LinkedHashMap<>();
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -16; dx <= 16; dx += 2) for (int dz = -16; dz <= 16; dz += 2) for (int y = level.getMinY(); y < top; y += 2) {
            m.set(pos.getX() + dx, y, pos.getZ() + dz);
            if (!level.isLoaded(m)) continue;
            BlockState st = level.getBlockState(m);
            String k = null;
            if (st.is(BlockTags.COAL_ORES)) k = "Charbon";
            else if (st.is(BlockTags.IRON_ORES)) k = "Fer";
            else if (st.is(BlockTags.COPPER_ORES)) k = "Cuivre";
            else if (st.is(BlockTags.GOLD_ORES)) k = "Or";
            else if (st.is(BlockTags.REDSTONE_ORES)) k = "Redstone";
            else if (st.is(BlockTags.LAPIS_ORES)) k = "Lapis";
            else if (st.is(BlockTags.DIAMOND_ORES)) k = "Diamant";
            else if (st.is(BlockTags.EMERALD_ORES)) k = "Émeraude";
            else if (st.is(Blocks.WATER)) k = null;
            if (k != null) found.merge(k, 1, Integer::sum);
        }
        List<String> out = new ArrayList<>();
        found.entrySet().stream().sorted((x, y) -> y.getValue() - x.getValue()).limit(5)
                .forEach(e -> out.add((e.getValue() > 12 ? "<white>" : "<#A3A6B4>") + e.getKey() + (e.getValue() > 12 ? " +" : "")));
        if (b.is(BiomeTags.IS_FOREST) || b.is(BiomeTags.IS_TAIGA) || b.is(BiomeTags.IS_JUNGLE)) out.add("<white>Bois +");
        if (b.is(BiomeTags.IS_OCEAN) || b.is(BiomeTags.IS_RIVER) || b.is(BiomeTags.IS_BEACH)) out.add("<white>Eau, poisson");
        if (b.is(BiomeTags.IS_MOUNTAIN)) out.add("<white>Pierre +");
        if (b.is(BiomeTags.IS_BADLANDS)) out.add("<white>Argile, or");
        if (out.isEmpty()) out.add("<#6C6F7E>Aucune ressource notable détectée");
        return String.join("<#6C6F7E>, ", out);
    }

    // ================================================================ actions
    @Override
    public void action(ServerPlayer p, UiSession ss, String act, String arg, Map<String, String> in) {
        Nation n = s.nations().of(p);
        switch (act) {
            case "tab" -> ss.set("tab", arg);
            case "select" -> ss.set("sel", arg);
            case "create" -> {
                String name = in.getOrDefault("name", "").trim();
                if (name.isEmpty()) { Msg.err(p, "Choisissez un nom pour votre pays."); return; }
                act().create(p, name, !"false".equals(in.get("announce")));
                if (s.nations().of(p) != null) ss.set("tab", "profil");
            }
            case "join" -> act().join(p, arg);
            case "home" -> {
                act().home(p);
                s.ui().close(p);
            }
            case "sethome" -> act().setHome(p);
            case "invite" -> {
                String t = in.getOrDefault("invite", "").trim();
                if (t.isEmpty()) Msg.err(p, "Saisissez un pseudo."); else act().invite(p, t);
            }
            case "promote" -> withUuid(arg, u -> act().promote(p, u));
            case "demote" -> withUuid(arg, u -> act().demote(p, u));
            case "kick" -> withUuid(arg, u -> ss.dialog = confirm("Expulser " + s.name(u) + " ?", "Il perdra l'accès au territoire de la nation.",
                    "Expulser", a("kick_ok", u), true));
            case "kick_ok" -> { withUuid(arg, u -> act().kick(p, u)); ss.dialog = null; }
            case "transfer" -> withUuid(arg, u -> ss.dialog = confirm("Céder la nation à " + s.name(u) + " ?", "Vous deviendrez Ministre.",
                    "Céder", a("transfer_ok", u), true));
            case "transfer_ok" -> { withUuid(arg, u -> act().transfer(p, u)); ss.dialog = null; }
            case "leave" -> ss.dialog = confirm("Quitter " + (n == null ? "la nation" : n.name()) + " ?", null, "Quitter", a("leave_ok"), true);
            case "leave_ok" -> { act().leave(p); ss.dialog = null; }
            case "claim" -> act().claim(p);
            case "unclaim" -> act().unclaim(p);
            case "claimat" -> {
                String[] xz = arg.split(",");
                if (xz.length != 2 || n == null) return;
                try {
                    int x = Integer.parseInt(xz[0]), z = Integer.parseInt(xz[1]);
                    if (n.claims().contains(Locs.chunkKey(Locs.dim(p.level()), x, z))) {
                        ss.dialog = confirm("Abandonner le chunk " + x + ", " + z + " ?", null, "Abandonner", a("unclaimat", arg), true);
                    } else act().claimAt(p, x, z);
                } catch (NumberFormatException ignored) {
                }
            }
            case "unclaimat" -> {
                String[] xz = arg.split(",");
                ss.dialog = null;
                try { act().unclaimAt(p, Integer.parseInt(xz[0]), Integer.parseInt(xz[1])); } catch (Exception ignored) {}
            }
            case "ally" -> act().ally(p, arg);
            case "war" -> {
                Nation o = s.nations().get(arg);
                if (o != null) ss.dialog = confirm("Déclarer la guerre à " + o.name() + " ?",
                        "Coût : " + money(s.config().getDouble("guerres.cout-declaration", 10000)) + ", prélevé sur le trésor national.", "Déclarer", a("war_ok", arg), true);
            }
            case "war_ok" -> {
                ss.dialog = null;
                Nation o = s.nations().get(arg);
                if (o != null) s.wars().declare(p, o);
            }
            case "color" -> { if (n != null && n.can(p.getUUID(), Role.LEADER) && COLORS.contains(arg)) n.setColor(arg); }
            case "tagtoggle" -> {
                if (n == null || !n.can(p.getUUID(), Role.LEADER) || !TAGS.contains(arg)) return;
                if (n.tags().contains(arg)) n.tags().remove(arg);
                else if (n.tags().size() < 4) n.tags().add(arg);
                else Msg.err(p, "4 étiquettes maximum.");
            }
            case "tag" -> act().setTag(p, in.getOrDefault("tag", ""));
            case "tax" -> {
                Double v = amount(in, "tax");
                act().setTax(p, v == null ? 0 : v);
            }
            case "open" -> { if (n != null && n.can(p.getUUID(), Role.LEADER)) n.setOpen(!n.open()); }
            case "desc" -> {
                if (n == null || !n.can(p.getUUID(), Role.LEADER)) return;
                String d = in.getOrDefault("desc", "").trim();
                n.setDescription(d.length() > 120 ? d.substring(0, 120) : d);
                Msg.ok(p, "Description mise à jour.");
            }
            case "disband" -> ss.dialog = confirm("Dissoudre " + (n == null ? "la nation" : n.name()) + " ?", "Irréversible : territoires, alliances et recherches seront perdus.",
                    "Dissoudre", a("disband_ok"), true);
            case "disband_ok" -> { ss.dialog = null; act().disband(p); ss.set("tab", "profil"); }
            default -> {
            }
        }
    }

    private static void withUuid(String s, java.util.function.Consumer<UUID> c) {
        try { c.accept(UUID.fromString(s)); } catch (IllegalArgumentException ignored) {}
    }
}

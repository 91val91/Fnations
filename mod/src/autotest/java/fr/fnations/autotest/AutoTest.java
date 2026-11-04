package fr.fnations.autotest;

import fr.fnations.client.screen.PromptScreen;
import fr.fnations.vehicle.VehicleEntity;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Scénario de test automatique : se connecte au serveur local, joue les systèmes et prend des captures. */
public final class AutoTest implements ClientModInitializer {
    private record Step(int delay, Runnable action) {}

    private final List<Step> steps = new ArrayList<>();
    private final List<String> report = new ArrayList<>();
    private int index, wait, joinedTicks;
    private boolean connecting;
    private Vec3 driveStart;

    @Override
    public void onInitializeClient() {
        if (System.getProperty("fnations.autotest") == null) return;
        String scenario = System.getenv("FN_SCENARIO");
        if ("showcase".equals(scenario)) showcase();
        else if ("terre".equals(scenario)) earth();
        else if ("boutique".equals(scenario)) boutique();
        else script();
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private static Minecraft mc() { return Minecraft.getInstance(); }

    private void cmd(String c) { mc().player.connection.sendCommand(c); }

    private void shot(String name) {
        Screenshot.grab(mc().gameDirectory, "fn_" + name + ".png", mc().getMainRenderTarget(), m -> {});
        log("capture " + name);
    }

    private void log(String s) {
        report.add(s);
        System.out.println("[AUTOTEST] " + s);
    }

    private void step(int wait, Runnable r) { steps.add(new Step(wait, r)); }

    private void click(int slot) {
        var p = mc().player;
        mc().gameMode.handleInventoryMouseClick(p.containerMenu.containerId, slot, 0, ClickType.PICKUP, p);
    }

    private void use(BlockPos pos) {
        mc().gameMode.useItemOn(mc().player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    private Entity nearest(Class<?> cls) {
        Entity best = null;
        for (Entity e : mc().level.entitiesForRendering()) {
            if (!cls.isInstance(e)) continue;
            if (best == null || e.distanceToSqr(mc().player) < best.distanceToSqr(mc().player)) best = e;
        }
        return best;
    }

    /** Scénario « terre » : survol de lieux réels du monde au 1/1000. */
    private void earth() {
        step(40, () -> {
            cmd("gamemode spectator");
            cmd("time set 6000");
            cmd("gamerule doDaylightCycle false");
            cmd("weather clear");
            mc().options.renderDistance().set(12);
            mc().options.hideGui = true;
        });
        Object[][] places = {
                {"paris", 48.86, 2.35, 96, 0f, 34f},
                {"alpes", 45.7, 6.2, 196, -90f, 24f},
                {"himalaya", 27.3, 86.9, 238, 180f, 20f},
                {"sahara", 25.0, 10.0, 112, 0f, 30f},
                {"amazonie", -3.0, -60.0, 100, 0f, 32f},
                {"groenland", 70.5, -42.0, 205, 180f, 24f},
                {"fuji", 34.9, 138.7, 135, 180f, 22f},
                {"gibraltar", 35.95, -6.3, 150, -90f, 32f},
                {"australie", -25.0, 133.0, 108, 0f, 32f},
        };
        for (Object[] pl : places) {
            String name = (String) pl[0];
            double lat = (Double) pl[1], lon = (Double) pl[2];
            int y = (Integer) pl[3];
            float yaw = (Float) pl[4], pitch = (Float) pl[5];
            int x = (int) Math.round(fr.fnations.earth.EarthData.blockX(lon)), z = (int) Math.round(fr.fnations.earth.EarthData.blockZ(lat));
            step(10, () -> {
                cmd("tp @s " + x + " " + y + " " + z + " " + yaw + " " + pitch);
                String[] place = fr.fnations.earth.EarthData.get().place(x, z);
                log(name + " : " + fr.fnations.earth.EarthData.latLon(x, z) + " -> " + place[0] + " (" + place[1] + "), bloc " + x + " " + z);
            });
            step(520, () -> shot("terre_" + name));
        }
        step(20, () -> {
            try {
                Files.write(mc().gameDirectory.toPath().resolve("autotest-result.txt"), report);
            } catch (Exception e) {
                e.printStackTrace();
            }
            log("FIN");
            mc().stop();
        });
    }

    /**
     * Scénario « boutique » : livraisons de la boutique en ligne (grade, LootBoxs, dollars, spawner),
     * pages Boutique, ouverture de LootBoxs (roulette), spawner (pose, amélioration, mobs, récupération)
     * et carte du monde avec une nation fondée à Paris.
     */
    private void boutique() {
        int bx = 262, bz = -5439, y = 120;
        BlockPos spawnerAt = new BlockPos(bx + 3, y, bz + 3);
        step(40, () -> {
            cmd("gamemode creative");
            cmd("time set 6000");
            cmd("gamerule doDaylightCycle false");
            cmd("weather clear");
            cmd("gamerule doMobSpawning false");
            cmd("forceload add " + (bx - 24) + " " + (bz - 24) + " " + (bx + 24) + " " + (bz + 24));
            cmd("fill " + (bx - 12) + " " + (y - 1) + " " + (bz - 12) + " " + (bx + 12) + " " + (y - 1) + " " + (bz + 12) + " minecraft:smooth_stone");
            cmd("fill " + (bx - 12) + " " + y + " " + (bz - 12) + " " + (bx + 12) + " " + (y + 8) + " " + (bz + 12) + " minecraft:air");
            cmd("tp @s " + (bx + 0.5) + " " + y + " " + (bz + 0.5) + " 0 15");
            cmd("clear @s");
        });
        // ---------- achats livrés comme par Tebex (commande console fnstore)
        step(40, () -> {
            cmd("fnstore grade FnTester ultra 30");
            cmd("fnstore lootbox FnTester legendaire 2");
            cmd("fnstore lootbox FnTester ultra 1");
            cmd("fnstore lootbox FnTester classic 2");
            cmd("fnstore dollars FnTester 250000");
            cmd("fnstore spawner FnTester blaze 1");
            cmd("fnstore dollars JoueurHorsLigne 1000");
        });
        step(40, () -> {
            var team = mc().player.getTeam();
            log("grade : " + (team == null ? "aucune équipe" : team.getName() + " préfixe « " + team.getPlayerPrefix().getString() + " »"));
            log("lootboxs : classic " + mc().player.getInventory().countItem(fr.fnations.registry.ModItems.get("lootbox_classic"))
                    + ", légendaire " + mc().player.getInventory().countItem(fr.fnations.registry.ModItems.get("lootbox_legendaire"))
                    + ", ultra " + mc().player.getInventory().countItem(fr.fnations.registry.ModItems.get("lootbox_ultra")));
            int sp = slotOf(fr.fnations.registry.ModItems.get("spawner"));
            log("spawner : slot " + sp + (sp >= 0 ? " « " + mc().player.getInventory().items.get(sp).getHoverName().getString() + " »" : ""));
            mc().player.connection.sendChat("Bonjour, je viens d'acheter le grade Ultra !");
        });
        step(20, () -> mc().options.keyPlayerList.setDown(true));
        step(10, () -> shot("grade_chat_tab"));
        step(5, () -> { mc().options.keyPlayerList.setDown(false); mc().setScreen(new InventoryScreen(mc().player)); });
        step(30, () -> shot("inventaire_boutique"));
        step(5, () -> mc().setScreen(null));
        // ---------- pages de la boutique
        step(10, () -> cmd("boutique"));
        step(40, () -> { page("boutique"); shot("ui_boutique_grades"); });
        step(5, () -> press("boutique.tab:lootboxs"));
        step(25, () -> shot("ui_boutique_lootboxs"));
        step(5, () -> press("boutique.tab:dollars"));
        step(25, () -> shot("ui_boutique_dollars"));
        step(5, () -> press("boutique.tab:spawners"));
        step(25, () -> shot("ui_boutique_spawners"));
        step(5, () -> press("boutique.tab:avantages"));
        step(25, () -> shot("ui_boutique_avantages"));
        step(5, () -> press("boutique.claim:DAILY"));
        step(25, () -> shot("ui_boutique_recompense"));
        // lien vers la boutique en ligne : fenêtre de confirmation puis retour à la tablette
        step(5, () -> press("url:https://fnations.tebex.io"));
        step(20, () -> { log("écran du lien : " + (mc().screen == null ? "aucun" : mc().screen.getClass().getSimpleName())); shot("lien_boutique"); });
        step(5, () -> { if (mc().screen != null) mc().screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0, 0); });
        step(15, () -> log("retour à la tablette : " + (ui() != null ? ui().pageId() : "non")));
        // ---------- LootBoxs : roulette
        step(5, () -> press("boutique.tab:lootboxs"));
        step(15, () -> press("boutique.open:legendaire"));
        step(12, () -> { page("lootbox"); shot("lootbox_depart"); });
        step(45, () -> shot("lootbox_roulette"));
        step(90, () -> shot("lootbox_ralenti"));
        step(40, () -> shot("lootbox_resultat"));
        step(5, () -> press("lootbox.open:ultra"));
        step(150, () -> shot("lootbox_ultra_resultat"));
        // ouverture par clic droit avec l'objet en main
        step(5, () -> {
            mc().setScreen(null);
            int sl = slotOf(fr.fnations.registry.ModItems.get("lootbox_classic"));
            log("LootBox classic en main : slot " + sl);
            if (sl >= 0 && sl < 9) {
                mc().player.getInventory().selected = sl;
                mc().gameMode.useItem(mc().player, InteractionHand.MAIN_HAND);
            }
        });
        step(150, () -> { page("lootbox"); shot("lootbox_classic_resultat"); });
        // ---------- nation fondée à Paris (pour la carte du monde)
        step(5, () -> press("nav:pays"));
        step(30, () -> { page("pays"); shot("ui_creer_pays_terre"); });
        step(5, () -> { type("name", "Francia"); press("pays.create"); });
        step(20, () -> cmd("nation deposer 50000"));
        int[][] spiral = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}, {2, -1}, {2, 0}, {2, 1}, {2, 2}, {1, 2}, {0, 2},
                {-1, 2}, {-2, 2}, {-2, 1}, {-2, 0}, {-2, -1}, {-2, -2}, {-1, -2}, {0, -2}, {1, -2}, {2, -2}};
        step(10, () -> {
            int cx = Math.floorDiv(bx, 16), cz = Math.floorDiv(bz, 16);
            for (int[] o : spiral) ui().sendAction("pays.claimat:" + (cx + o[0]) + "," + (cz + o[1]));
            log("revendications envoyées : " + spiral.length);
        });
        step(30, () -> press("pays.tab:territoire"));
        step(30, () -> shot("ui_pays_territoire_terre"));
        // ---------- carte du monde
        step(5, () -> press("nav:carte"));
        step(120, () -> { page("carte"); shot("ui_carte_monde"); });
        step(5, () -> ui().sendAction("client:focus:" + bx + "," + bz + ",2"));
        step(20, () -> shot("ui_carte_europe"));
        step(5, () -> ui().sendAction("client:focus:" + bx + "," + bz + ",9"));
        step(20, () -> shot("ui_carte_paris"));
        step(5, () -> { type("mq", "fran"); ui().sendAction("client:focus:13358,-3340,0.8"); });
        step(20, () -> shot("ui_carte_recherche"));
        // ---------- spawner : pose, page, améliorations, mobs, récupération
        step(5, () -> {
            mc().setScreen(null);
            cmd("tp @s " + (bx + 0.5) + " " + y + " " + (bz + 0.5) + " 0 15");
        });
        step(10, () -> {
            int sl = slotOf(fr.fnations.registry.ModItems.get("spawner"));
            log("spawner à poser : slot " + sl);
            if (sl >= 0 && sl < 9) mc().player.getInventory().selected = sl;
            BlockPos under = spawnerAt.below();
            mc().gameMode.useItemOn(mc().player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(under).add(0, 0.5, 0), Direction.UP, under, false));
        });
        step(30, () -> {
            log("bloc posé : " + mc().level.getBlockState(spawnerAt).getBlock().getDescriptionId());
            cmd("clear @s fnations:spawner");
            cmd("tp @s " + (bx + 3.5) + " " + (y + 0.3) + " " + (bz + 1.2) + " 0 22");
        });
        step(40, () -> shot("spawner_bloc"));
        step(5, () -> {
            mc().player.getInventory().selected = 8;
            mc().gameMode.useItemOn(mc().player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(spawnerAt), Direction.NORTH, spawnerAt, false));
        });
        step(30, () -> { page("spawner"); shot("ui_spawner"); });
        step(5, () -> press("spawner.upgrade"));
        step(20, () -> press("spawner.upgrade"));
        step(30, () -> shot("ui_spawner_niveau3"));
        step(5, () -> { mc().setScreen(null); cmd("tp @s " + (bx + 3.5) + " " + (y + 1.5) + " " + (bz - 3.5) + " 0 18"); });
        step(420, () -> {
            int blazes = 0;
            for (Entity e : mc().level.entitiesForRendering()) if (e.getType() == net.minecraft.world.entity.EntityType.BLAZE) blazes++;
            log("blazes apparus : " + blazes);
            shot("spawner_mobs");
        });
        step(5, () -> {
            cmd("tp @s " + (bx + 3.5) + " " + y + " " + (bz + 0.5) + " 0 20");
            mc().player.getInventory().selected = 8;
        });
        step(10, () -> mc().gameMode.useItemOn(mc().player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(spawnerAt), Direction.NORTH, spawnerAt, false)));
        step(30, () -> shot("ui_spawner_mobs"));
        step(5, () -> press("spawner.pickup"));
        step(30, () -> {
            int sl = slotOf(fr.fnations.registry.ModItems.get("spawner"));
            log("spawner récupéré : " + (sl >= 0 ? mc().player.getInventory().items.get(sl).getHoverName().getString() : "non") + ", bloc restant : "
                    + mc().level.getBlockState(spawnerAt).getBlock().getDescriptionId());
            mc().setScreen(null);
            cmd("kill @e[type=minecraft:blaze]");
        });
        step(20, () -> {
            try {
                Files.write(mc().gameDirectory.toPath().resolve("autotest-result.txt"), report);
            } catch (Exception e) {
                e.printStackTrace();
            }
            log("FIN");
            mc().stop();
        });
    }

    /** Scénario court : captures rapprochées des modèles 3D animés (panneaux solaires, station de craft). */
    private void showcase() {
        step(40, () -> {
            cmd("gamemode creative");
            cmd("time set 6000");
            cmd("gamerule doDaylightCycle false");
            cmd("weather clear");
            cmd("gamerule doMobSpawning false");
            cmd("forceload add -32 -32 32 32");
            cmd("fill -15 149 -15 15 149 15 minecraft:grass_block");
            cmd("fill -15 150 -15 15 160 15 minecraft:air");
            cmd("setblock -3 150 0 fnations:solar_panel_t1[facing=north]");
            cmd("setblock 0 150 0 fnations:solar_panel_t2[facing=north]");
            cmd("setblock 3 150 0 fnations:solar_panel_t3[facing=north]");
            cmd("setblock 9 150 0 fnations:crafting_station[facing=north]");
            cmd("setblock 9 150 -6 fnations:crafting_station[facing=east]");
            for (String it : List.of("solar_panel_t1", "solar_panel_t2", "solar_panel_t3", "crafting_station", "compressor", "coal_generator", "electric_furnace", "crusher", "switch"))
                cmd("give @s fnations:" + it);
            cmd("tp @s 4.5 151 -6.5 27 18");
        });
        step(60, () -> mc().options.hideGui = true);
        int[] times = {1000, 3500, 6000, 9500, 12300, 18000};
        for (int t : times) {
            step(5, () -> cmd("time set " + t));
            step(50, () -> shot("solaire_" + t));
        }
        // gros plans (matin : panneau incliné vers l'est)
        step(5, () -> { cmd("time set 2500"); cmd("tp @s 1.9 150 -2.3 25 22"); });
        step(50, () -> shot("solaire_t2_proche"));
        step(5, () -> cmd("tp @s 6.2 150 -1.4 60 25"));
        step(50, () -> shot("solaire_t3_proche"));
        step(5, () -> cmd("tp @s -0.3 151.8 3.4 160 45"));
        step(50, () -> shot("solaire_dessus"));
        step(5, () -> { cmd("time set 18000"); cmd("tp @s 6.2 150 -1.4 60 25"); });
        step(50, () -> shot("solaire_t3_nuit"));
        // station de craft : face, trois-quarts, cycle d'objets
        step(5, () -> { cmd("time set 6000"); cmd("tp @s 9.5 150 -2.9 0 18"); });
        step(50, () -> shot("station_face"));
        step(5, () -> cmd("tp @s 11.6 150 -1.6 45 20"));
        step(40, () -> shot("station_34_a"));
        step(105, () -> shot("station_34_b"));
        step(105, () -> shot("station_34_c"));
        step(5, () -> cmd("tp @s 9.5 150 -1.3 0 22"));
        step(40, () -> shot("station_proche_a"));
        step(60, () -> shot("station_proche_b"));
        step(5, () -> cmd("tp @s 11.5 150 -5.5 90 18"));
        step(50, () -> shot("station_est"));
        step(5, () -> { cmd("time set 18000"); cmd("tp @s 11.6 150 -1.6 45 20"); });
        step(50, () -> shot("station_nuit"));
        step(5, () -> cmd("tp @s 9.5 150 -1.3 0 22"));
        step(50, () -> shot("station_nuit_proche"));
        // icônes d'inventaire
        step(5, () -> { cmd("time set 6000"); mc().options.hideGui = false; mc().setScreen(new InventoryScreen(mc().player)); });
        step(30, () -> shot("icones"));
        step(5, () -> mc().setScreen(null));
        step(40, () -> {
            try {
                Files.write(mc().gameDirectory.toPath().resolve("autotest-result.txt"), report);
            } catch (Exception e) {
                e.printStackTrace();
            }
            log("FIN");
            mc().stop();
        });
    }

    // ------------------------------------------------------------ tablette (pages serveur)
    private fr.fnations.client.ui.FnUiScreen ui() { return mc().screen instanceof fr.fnations.client.ui.FnUiScreen s ? s : null; }

    private void press(String a) {
        var s = ui();
        boolean ok = s != null && s.press(a);
        if (!ok && s != null) { s.sendAction(a); log("action " + a + " (envoyée directement)"); }
        else log("action " + a + (ok ? "" : " : aucune tablette ouverte"));
    }

    private void type(String id, String v) { var s = ui(); if (s != null) s.type(id, v); }

    private void check(String id, boolean v) { var s = ui(); if (s != null) s.setCheck(id, v); }

    private int slotOf(net.minecraft.world.item.Item item) {
        var items = mc().player.getInventory().items;
        for (int i = 0; i < items.size(); i++) if (items.get(i).is(item)) return i;
        return -1;
    }

    private void page(String expected) {
        var s = ui();
        log("page affichée : " + (s == null ? "aucune" : s.pageId()) + (s != null && s.pageId().equals(expected) ? " (ok)" : " (attendu " + expected + ")"));
    }

    private void script() {
        // ---------- préparation du terrain
        step(40, () -> {
            cmd("gamemode creative");
            cmd("time set 6000");
            cmd("gamerule doDaylightCycle false");
            cmd("weather clear");
            cmd("gamerule doMobSpawning false");
            cmd("forceload add -32 -32 32 32");
            cmd("fill -15 149 -15 15 149 15 minecraft:smooth_stone");
            cmd("fill -15 150 -15 15 160 15 minecraft:air");
            cmd("tp @s 0.5 150 -10.5 0 15");
        });
        // ---------- items + inventaire
        step(60, () -> {
            for (String it : List.of("assault_rifle", "sniper", "pistol", "rpg", "shotgun", "smg", "tank", "missile_nuclear", "banknote"))
                cmd("give @s fnations:" + it);
            cmd("give @s fnations:ammo_762 64");
            cmd("give @s fnations:ammo_sniper 32");
            for (String it : List.of("steel_plate", "circuit", "advanced_circuit", "motor", "jet_engine", "warhead", "nuclear_core", "solar_cell", "fuel_canister", "rocket_fuel"))
                cmd("give @s fnations:" + it + " 16");
            cmd("give @s fnations:compressor 4");
        });
        step(40, () -> mc().setScreen(new InventoryScreen(mc().player)));
        step(30, () -> shot("inventaire"));
        step(10, () -> mc().setScreen(null));
        // ---------- tablette : accueil, création du pays, profil, membres, territoire
        step(10, () -> { cmd("banque donner FnTester 500000"); cmd("menu"); });
        step(40, () -> { page("accueil"); shot("ui_accueil"); });
        step(5, () -> press("nav:pays"));
        step(40, () -> { page("pays"); shot("ui_creer_pays"); });
        step(5, () -> { type("name", "Atlantis"); press("pays.create"); });
        step(30, () -> shot("ui_pays_profil"));
        step(5, () -> { cmd("nation deposer 200000"); cmd("fnations recherche Atlantis tout"); });
        step(20, () -> { press("pays.tab:parametres"); });
        step(10, () -> { press("pays.tagtoggle:Build"); });
        step(10, () -> { press("pays.tagtoggle:Commerce"); type("desc", "Nation de test : commerce et construction."); });
        step(10, () -> press("pays.desc"));
        step(15, () -> press("pays.tab:profil"));
        step(30, () -> shot("ui_pays_profil2"));
        step(5, () -> press("pays.tab:membres"));
        step(30, () -> shot("ui_pays_membres"));
        step(5, () -> press("pays.tab:territoire"));
        step(40, () -> shot("ui_pays_territoire"));
        step(5, () -> {
            int cx = mc().player.getBlockX() >> 4, cz = mc().player.getBlockZ() >> 4;
            press("pays.claimat:" + (cx + 1) + "," + cz);
        });
        step(30, () -> shot("ui_pays_claim"));
        // ---------- banque
        step(5, () -> press("nav:banque"));
        step(20, () -> { type("note", "250"); press("banque.notecustom"); });
        step(30, () -> { page("banque"); shot("ui_banque"); log("billet reçu : " + (slotOf(fr.fnations.registry.ModItems.get("banknote")) >= 0)); });
        // ---------- métiers : gain réel d'XP de Builder en survie + paliers
        step(5, () -> { mc().setScreen(null); cmd("gamemode survival"); cmd("item replace entity @s hotbar.8 with minecraft:stone 16"); });
        step(20, () -> {
            mc().player.getInventory().selected = 8;
            for (int i = 0; i < 5; i++) {
                BlockPos at = new BlockPos(-3 + i, 149, -12);
                mc().gameMode.useItemOn(mc().player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false));
            }
        });
        step(20, () -> shot("metier_barre_action"));
        step(10, () -> { cmd("gamemode creative"); cmd("metier set FnTester agriculteur 2600"); cmd("metier set FnTester mineur 5200"); cmd("metier"); });
        step(40, () -> { page("metiers"); shot("ui_metiers_builder"); });
        step(5, () -> press("metiers.job:mineur"));
        step(10, () -> press("metiers.how"));
        step(30, () -> shot("ui_metiers_mineur"));
        step(5, () -> press("metiers.top"));
        step(30, () -> shot("ui_metiers_classement"));
        // ---------- entreprise
        step(5, () -> { press("dialog:close"); press("nav:entreprise"); });
        step(30, () -> shot("ui_entreprise_creation"));
        step(5, () -> press("entreprise.type:FARMER"));
        step(10, () -> { type("cname", "AgroCorp"); press("entreprise.create"); });
        step(30, () -> { page("entreprise"); shot("ui_entreprise"); });
        // ---------- hôtel des ventes : ventes (dont une au nom de la nation), achat, offre d'achat
        step(5, () -> {
            cmd("give @s minecraft:diamond_sword");
            cmd("give @s minecraft:arrow 64");
            cmd("give @s minecraft:bread 32");
            press("nav:hdv");
        });
        step(30, () -> { page("hdv"); shot("ui_hdv_vide"); });
        step(5, () -> press("hdv.pick:" + slotOf(net.minecraft.world.item.Items.DIAMOND_SWORD)));
        step(25, () -> shot("ui_hdv_dialogue_vente"));
        step(5, () -> { type("sqty", "1"); type("sprice", "100"); press("hdv.sell"); });
        step(15, () -> press("hdv.pick:" + slotOf(net.minecraft.world.item.Items.ARROW)));
        step(15, () -> { type("sqty", "64"); type("sprice", "4"); check("fornation", true); press("hdv.sell"); });
        step(15, () -> press("hdv.pick:" + slotOf(net.minecraft.world.item.Items.BREAD)));
        step(15, () -> { type("sqty", "16"); type("sprice", "2.5"); press("hdv.sell"); });
        step(15, () -> press("hdv.pick:" + slotOf(fr.fnations.registry.ModItems.get("steel_plate"))));
        step(15, () -> { type("sqty", "8"); type("sprice", "45"); press("hdv.sell"); });
        step(30, () -> shot("ui_hdv_ventes"));
        step(5, () -> press("hdv.open:minecraft:arrow"));
        step(25, () -> shot("ui_hdv_groupe"));
        step(5, () -> { type("qty", "10"); press("hdv.buybest:minecraft:arrow"); });
        step(25, () -> { shot("ui_hdv_achat"); log("flèches après achat : " + mc().player.getInventory().countItem(net.minecraft.world.item.Items.ARROW)); });
        step(5, () -> press("hdv.offerdlg:minecraft:arrow"));
        step(15, () -> { type("oqty", "20"); type("oprice", "3"); press("hdv.offer:minecraft:arrow"); });
        step(15, () -> { press("hdv.tab:offres"); });
        step(10, () -> press("hdv.open:minecraft:arrow"));
        step(25, () -> shot("ui_hdv_offres"));
        step(5, () -> press("hdv.tab:espace"));
        step(25, () -> shot("ui_hdv_espace"));
        step(5, () -> press("hdv.tab:pays"));
        step(25, () -> shot("ui_hdv_pays"));
        step(5, () -> { press("hdv.tab:ventes"); press("hdv.cat:equipements"); });
        step(25, () -> shot("ui_hdv_categorie"));
        // ---------- R&D, guerres, classements
        step(5, () -> press("nav:recherche"));
        step(30, () -> { page("recherche"); shot("ui_recherche"); });
        step(5, () -> press("recherche.cat:militaire"));
        step(10, () -> press("recherche.tech:doctrine_militaire"));
        step(25, () -> shot("ui_recherche_detail"));
        step(5, () -> { press("dialog:close"); press("nav:guerres"); });
        step(30, () -> { page("guerres"); shot("ui_guerres"); });
        step(5, () -> press("nav:classements"));
        step(25, () -> shot("ui_classements"));
        step(5, () -> press("classements.tab:metiers"));
        step(25, () -> shot("ui_classements_metiers"));
        step(5, () -> mc().setScreen(null));
        // ---------- chambre avec WorldEdit (//pos1 //pos2 puis /chambre creer)
        step(10, () -> {
            mc().player.closeContainer();
            cmd("/pos1 -14,150,10");
            cmd("/pos2 -11,153,13");
        });
        step(20, () -> cmd("chambre creer suite1 20000 500 Palace"));
        step(30, () -> { log("chambre créée (voir rooms/suite1.schem côté serveur)"); cmd("chambre"); });
        step(20, () -> press("chambres.room:suite1"));
        step(30, () -> { page("chambres"); shot("ui_chambres"); });
        step(5, () -> mc().setScreen(null));
        // ---------- machines
        step(10, () -> {
            String[] ms = {"crafting_station", "compressor", "coal_generator", "solar_panel_t1", "solar_panel_t2", "solar_panel_t3", "electric_furnace", "crusher", "switch", "missile_launcher"};
            for (int i = 0; i < ms.length; i++) cmd("setblock " + (-9 + i * 2) + " 150 -4 fnations:" + ms[i] + "[facing=north]");
            cmd("item replace block -5 150 -4 container.0 with minecraft:coal 16");
            cmd("item replace block -7 150 -4 container.0 with minecraft:iron_ingot 32");
            cmd("tp @s 0.5 151 -12.5 0 20");
        });
        step(60, () -> shot("machines"));
        step(200, () -> cmd("tp @s -6.5 150 -6.5 0 30"));
        step(20, () -> use(new BlockPos(-7, 150, -4))); // compresseur après ~12 s
        step(30, () -> shot("gui_compresseur"));
        step(5, () -> { mc().player.closeContainer(); cmd("tp @s -4.5 150 -6.5 0 30"); });
        step(15, () -> use(new BlockPos(-5, 150, -4)));
        step(30, () -> shot("gui_generateur"));
        step(5, () -> { mc().player.closeContainer(); cmd("give @s fnations:missile_tactical 2"); cmd("tp @s 9.5 150 -6.5 0 30"); });
        step(15, () -> use(new BlockPos(9, 150, -4)));
        step(30, () -> shot("gui_lanceur"));
        // ---------- tir de missile (créatif : pas besoin d'énergie)
        step(5, () -> {
            mc().player.closeContainer();
            cmd("item replace block 9 150 -4 container.0 with fnations:missile_tactical 1");
        });
        step(10, () -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                new fr.fnations.net.LaunchPayload(new BlockPos(9, 150, -4), 140, 60, true)));
        step(125, () -> shot("missile_en_vol"));
        step(5, () -> { mc().player.closeContainer(); cmd("tp @s 7.5 150 -6.5 0 30"); });
        step(15, () -> use(new BlockPos(7, 150, -4)));
        step(30, () -> shot("gui_interrupteur"));
        step(5, () -> { mc().player.closeContainer(); cmd("tp @s -8.5 150 -6.5 0 30"); });
        step(5, () -> { cmd("give @s fnations:compressed_iron 2"); cmd("give @s minecraft:coal 4"); });
        step(15, () -> use(new BlockPos(-9, 150, -4)));
        step(30, () -> { page("station"); press("station.recipe:steel_ingot"); });
        step(25, () -> shot("ui_station"));
        step(5, () -> press("station.craft:steel_ingot;1"));
        step(25, () -> { shot("ui_station_fabrique"); log("lingots d'acier : " + mc().player.getInventory().countItem(fr.fnations.registry.ModItems.get("steel_ingot"))); });
        step(5, () -> press("station.cat:ARME"));
        step(25, () -> shot("ui_station_armes"));
        step(5, () -> mc().setScreen(null));
        // ---------- véhicules et fusée
        step(10, () -> {
            cmd("summon fnations:tank -8 150 6 {Rotation:[30f,0f]}");
            cmd("summon fnations:plane 0 150 9 {Rotation:[0f,0f]}");
            cmd("summon fnations:helicopter 8 150 6 {Rotation:[-30f,0f]}");
            cmd("summon fnations:tractor -4 150 0 {Rotation:[90f,0f]}");
            cmd("summon fnations:rocket 12 150 12");
            cmd("tp @s 0.5 154 -9.5 0 25");
        });
        step(80, () -> shot("vehicules"));
        step(5, () -> cmd("tp @s 13.5 151 1.5 0 -10"));
        step(40, () -> shot("fusee"));
        // ---------- armes
        step(5, () -> {
            cmd("tp @s 0.5 150 -10.5 0 0");
            cmd("summon minecraft:zombie 0.5 150 -2.5 {NoAI:1b,Health:20f}");
            mc().player.getInventory().selected = 0;
        });
        step(40, () -> shot("arme_fusil"));
        step(5, () -> mc().options.keyUse.setDown(true));
        step(30, () -> {
            mc().options.keyUse.setDown(false);
            int ammo = fr.fnations.weapon.GunItem.ammo(mc().player.getMainHandItem());
            log("munitions après tir : " + ammo);
            shot("tir");
        });
        step(5, () -> { mc().player.getInventory().selected = 1; mc().options.keyShift.setDown(true); });
        step(30, () -> shot("lunette_sniper"));
        step(5, () -> mc().options.keyShift.setDown(false));
        // ---------- conduite du char
        step(10, () -> {
            Entity tank = nearest(VehicleEntity.class);
            log("véhicule le plus proche : " + (tank == null ? "aucun" : tank.getType().toShortString()));
            cmd("tp @s -8.5 150 3.5");
        });
        step(20, () -> {
            Entity tank = null;
            for (Entity e : mc().level.entitiesForRendering()) if (e instanceof VehicleEntity v && v.kind == fr.fnations.vehicle.VehicleKind.TANK) tank = e;
            if (tank != null) mc().gameMode.interact(mc().player, tank, InteractionHand.MAIN_HAND);
        });
        step(20, () -> {
            boolean riding = mc().player.getVehicle() instanceof VehicleEntity;
            log("monté dans le char : " + riding);
            if (riding) driveStart = mc().player.getVehicle().position();
            mc().options.keyUp.setDown(true);
        });
        step(60, () -> {
            mc().options.keyUp.setDown(false);
            if (driveStart != null && mc().player.getVehicle() != null)
                log(String.format("distance parcourue en char : %.1f blocs", mc().player.getVehicle().position().distanceTo(driveStart)));
            shot("conduite_char");
        });
        step(20, () -> mc().options.keyShift.setDown(true));
        step(10, () -> {
            mc().options.keyShift.setDown(false);
            log("descendu du char : " + (mc().player.getVehicle() == null));
        });
        // ---------- fusée vers l'orbite
        step(5, () -> cmd("tp @s 14.5 150 9.5 0 0"));
        step(20, () -> {
            Entity rocket = nearest(fr.fnations.rocket.RocketEntity.class);
            log("fusée trouvée : " + (rocket != null));
            if (rocket != null) mc().gameMode.interact(mc().player, rocket, InteractionHand.MAIN_HAND);
        });
        step(20, () -> { shot("poste_pilotage"); click(10); });
        step(80, () -> shot("decollage"));
        step(380, () -> {
            log("dimension après le vol : " + mc().level.dimension().location());
            shot("orbite");
        });
        // ---------- fin
        step(40, () -> {
            try {
                Path out = mc().gameDirectory.toPath().resolve("autotest-result.txt");
                Files.write(out, report);
            } catch (Exception e) {
                e.printStackTrace();
            }
            log("FIN");
            mc().stop();
        });
    }

    private void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            if (!connecting && mc.screen instanceof TitleScreen) {
                connecting = true;
                ServerData data = new ServerData("FNations test", "localhost:25565", ServerData.Type.OTHER);
                ConnectScreen.startConnecting(mc.screen, mc, ServerAddress.parseString("localhost:25565"), data, false, null);
            }
            return;
        }
        if (joinedTicks++ < 100) return;
        if (index >= steps.size()) return;
        if (wait > 0) { wait--; return; }
        Step s = steps.get(index++);
        try {
            s.action().run();
        } catch (Exception e) {
            log("ERREUR étape " + index + " : " + e);
            e.printStackTrace();
        }
        wait = index < steps.size() ? steps.get(index).delay() : 0;
    }

    @SuppressWarnings("unused")
    private static File unused() { return null; }
}

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
        script();
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
        // ---------- interfaces (panneaux serveur)
        step(10, () -> {
            cmd("banque donner FnTester 500000");
            cmd("nation creer Atlantis");
            cmd("nation deposer 200000");
            cmd("nation claim");
            cmd("fnations recherche Atlantis tout");
        });
        step(40, () -> cmd("menu"));
        step(30, () -> shot("menu_principal"));
        step(5, () -> click(20));
        step(30, () -> shot("gui_nation"));
        step(5, () -> click(20));
        step(30, () -> shot("gui_territoire"));
        step(5, () -> { mc().player.closeContainer(); cmd("recherche"); });
        step(30, () -> click(22));
        step(30, () -> shot("gui_recherche"));
        step(5, () -> { mc().player.closeContainer(); cmd("guerre"); });
        step(30, () -> shot("gui_guerres"));
        step(5, () -> { mc().player.closeContainer(); cmd("banque"); });
        step(30, () -> shot("gui_banque"));
        step(5, () -> click(23)); // retrait personnalisé -> fenêtre de saisie
        step(30, () -> {
            boolean prompt = mc().screen instanceof PromptScreen;
            log("fenêtre de saisie ouverte : " + prompt);
            shot("saisie");
            if (prompt) {
                for (var c : mc().screen.children()) if (c instanceof EditBox eb) eb.setValue("250");
                mc().screen.keyPressed(257, 0, 0);
            }
        });
        // ---------- chambre avec WorldEdit (//pos1 //pos2 puis /chambre creer)
        step(10, () -> {
            mc().player.closeContainer();
            cmd("/pos1 -14,150,10");
            cmd("/pos2 -11,153,13");
        });
        step(20, () -> cmd("chambre creer suite1 20000 500 Palace"));
        step(30, () -> { log("chambre créée (voir rooms/suite1.schem côté serveur)"); cmd("chambre"); });
        step(30, () -> shot("gui_chambres"));
        step(5, () -> mc().player.closeContainer());
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
        step(15, () -> use(new BlockPos(-9, 150, -4)));
        step(30, () -> shot("gui_station"));
        step(5, () -> click(21)); // armes à feu
        step(30, () -> shot("gui_station_armes"));
        step(5, () -> mc().player.closeContainer());
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

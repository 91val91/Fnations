package fr.fnations.server;

import com.mojang.authlib.GameProfile;
import fr.fnations.FNations;
import fr.fnations.admin.Advancements;
import fr.fnations.bank.BankManager;
import fr.fnations.craft.CraftingStation;
import fr.fnations.machine.MachineRecipe;
import fr.fnations.machine.MachineType;
import fr.fnations.missile.MissileManager;
import fr.fnations.nation.NationActions;
import fr.fnations.nation.NationManager;
import fr.fnations.nation.Territory;
import fr.fnations.research.ResearchManager;
import fr.fnations.room.RoomManager;
import fr.fnations.util.Inv;
import fr.fnations.util.Yaml;
import fr.fnations.war.WarManager;
import fr.fnations.weapon.WeaponManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** État serveur de FNations : configuration, managers et horloges. Une instance par serveur démarré. */
public final class FnServer {
    private static final String[] DEFAULTS = {"config.yml", "research.yml", "recipes.yml", "weapons.yml", "metiers.yml", "lootboxs.yml", "spawners.yml"};
    private static FnServer instance;

    private final MinecraftServer server;
    private final Path configDir, dataDir;
    private Yaml config;
    private final Map<MachineType, List<MachineRecipe>> machineRecipes = new EnumMap<>(MachineType.class);
    private final Advancements advancements = new Advancements();

    private BankManager bank;
    private NationManager nations;
    private Territory territory;
    private ResearchManager research;
    private WarManager wars;
    private RoomManager rooms;
    private WeaponManager weapons;
    private MissileManager missiles;
    private CraftingStation station;
    private fr.fnations.job.JobManager jobs;
    private fr.fnations.company.CompanyManager companies;
    private fr.fnations.market.MarketManager market;
    private fr.fnations.ui.UiManager ui;
    private fr.fnations.rank.RankManager ranks;
    private fr.fnations.store.StoreManager store;
    private fr.fnations.loot.LootManager loot;
    private fr.fnations.spawner.SpawnerManager spawners;
    private fr.fnations.rating.RatingManager ratings;
    private NationActions nationActions;
    private long ticks;

    private FnServer(MinecraftServer server) {
        this.server = server;
        this.configDir = FabricLoader.getInstance().getConfigDir().resolve("fnations");
        this.dataDir = server.getWorldPath(LevelResource.ROOT).resolve("fnations");
    }

    public static FnServer get() { return instance; }

    static void start(MinecraftServer server) {
        FnServer s = new FnServer(server);
        instance = s;
        s.copyDefaults();
        s.loadConfig();
        s.bank = new BankManager(s);
        s.nations = new NationManager(s);
        s.territory = new Territory(s);
        s.research = new ResearchManager(s);
        s.wars = new WarManager(s);
        s.rooms = new RoomManager(s);
        s.weapons = new WeaponManager(s);
        s.missiles = new MissileManager(s);
        s.station = new CraftingStation(s);
        s.companies = new fr.fnations.company.CompanyManager(s);
        s.jobs = new fr.fnations.job.JobManager(s);
        s.market = new fr.fnations.market.MarketManager(s);
        s.spawners = new fr.fnations.spawner.SpawnerManager(s);
        s.loot = new fr.fnations.loot.LootManager(s);
        s.ranks = new fr.fnations.rank.RankManager(s);
        s.store = new fr.fnations.store.StoreManager(s);
        s.ratings = new fr.fnations.rating.RatingManager(s);
        s.ui = new fr.fnations.ui.UiManager(s);
        fr.fnations.ui.pages.Pages.register(s, s.ui);
        NationActions actions = new NationActions(s);
        s.nationActions = actions;
        fr.fnations.earth.EarthWorld.setup(s);
        FNations.LOG.info("FNations : données dans {}, configuration dans {}", s.dataDir, s.configDir);
    }

    static void stop() {
        if (instance != null) instance.saveAll();
        instance = null;
    }

    private void copyDefaults() {
        try {
            Files.createDirectories(configDir);
            Files.createDirectories(dataDir);
            for (String f : DEFAULTS) {
                Path target = configDir.resolve(f);
                if (Files.exists(target)) continue;
                try (InputStream in = FnServer.class.getResourceAsStream("/fnations-defaults/" + f)) {
                    if (in != null) Files.copy(in, target);
                }
            }
            upgradeConfig("config.yml");
            upgradeConfig("metiers.yml");
        } catch (Exception e) {
            FNations.LOG.error("Impossible de préparer le dossier de configuration", e);
        }
    }

    /**
     * Mise à jour du mod : ajoute à la fin du fichier de l'administrateur les sections de premier niveau
     * apparues dans la nouvelle version (avec leurs commentaires). Les réglages existants ne sont pas touchés.
     */
    private void upgradeConfig(String name) {
        Path target = configDir.resolve(name);
        if (!Files.exists(target)) return;
        try (InputStream in = FnServer.class.getResourceAsStream("/fnations-defaults/" + name)) {
            if (in == null) return;
            String[] lines = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\r?\n", -1);
            Yaml cur = Yaml.load(target);
            StringBuilder extra = new StringBuilder();
            List<String> added = new ArrayList<>(), comments = new ArrayList<>();
            for (int i = 0; i < lines.length; i++) {
                String l = lines[i];
                if (l.isBlank()) { comments.clear(); continue; }
                if (l.startsWith("#")) { comments.add(l); continue; }
                int colon = l.indexOf(':');
                if (Character.isWhitespace(l.charAt(0)) || colon <= 0) { comments.clear(); continue; }
                StringBuilder block = new StringBuilder();
                comments.forEach(c -> block.append(c).append('\n'));
                comments.clear();
                block.append(l).append('\n');
                int j = i + 1;
                while (j < lines.length) {
                    if (lines[j].isBlank()) {
                        int k = j;
                        while (k < lines.length && lines[k].isBlank()) k++;
                        if (k < lines.length && Character.isWhitespace(lines[k].charAt(0))) { block.append("\n".repeat(k - j)); j = k; continue; }
                        break;
                    }
                    if (!Character.isWhitespace(lines[j].charAt(0))) break;
                    block.append(lines[j++]).append('\n');
                }
                i = j - 1;
                String key = l.substring(0, colon).trim();
                if (!cur.contains(key)) {
                    extra.append('\n').append(block);
                    added.add(key);
                }
            }
            if (!added.isEmpty()) {
                Files.writeString(target, extra.toString(), java.nio.charset.StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
                FNations.LOG.info("{} : nouvelles sections ajoutées par la mise à jour : {}", name, String.join(", ", added));
            }
        } catch (Exception e) {
            FNations.LOG.warn("Mise à jour de {} impossible : {}", name, e.toString());
        }
    }

    /** Valeurs par défaut embarquées dans le mod. */
    private static Yaml defaults(String name) {
        try (InputStream in = FnServer.class.getResourceAsStream("/fnations-defaults/" + name)) {
            return in == null ? new Yaml() : Yaml.load(in);
        } catch (Exception e) {
            return new Yaml();
        }
    }

    private Yaml loadYaml(String name) {
        Path p = configDir.resolve(name);
        if (Files.exists(p)) return Yaml.load(p);
        try (InputStream in = FnServer.class.getResourceAsStream("/fnations-defaults/" + name)) {
            return in == null ? new Yaml() : Yaml.load(in);
        } catch (Exception e) {
            return new Yaml();
        }
    }

    private void loadConfig() {
        config = loadYaml("config.yml");
        config.fillDefaults(defaults("config.yml"));
        machineRecipes.clear();
        Yaml rec = loadYaml("recipes.yml");
        Map<String, MachineType> map = Map.of("compresseur", MachineType.COMPRESSOR, "four-electrique", MachineType.ELECTRIC_FURNACE, "concasseur", MachineType.CRUSHER);
        for (var e : map.entrySet()) {
            List<MachineRecipe> list = new ArrayList<>();
            for (Map<String, Object> r : rec.getMapList("machines." + e.getKey())) {
                List<Map.Entry<String, Integer>> outs = new ArrayList<>();
                Object o = r.get("sortie");
                if (o instanceof List<?> l) for (Object x : l) outs.add(Inv.parse(String.valueOf(x)));
                else if (o != null) outs.add(Inv.parse(String.valueOf(o)));
                Map.Entry<String, Integer> in = Inv.parse(String.valueOf(r.get("entree")));
                Object d = r.get("duree");
                if (!outs.isEmpty()) list.add(new MachineRecipe(in.getKey(), in.getValue(), outs, d instanceof Number n ? n.intValue() : 5));
            }
            machineRecipes.put(e.getValue(), list);
        }
    }

    public void reloadAll() {
        loadConfig();
        research.reload();
        weapons.reload();
        missiles.reload();
        station.reload();
        jobs.reload();
        loot.reload();
        spawners.reload();
    }

    public Yaml yaml(String name) { return loadYaml(name); }

    // ------------------------------------------------------------ horloge (appelée chaque tick)
    void tick() {
        ticks++;
        weapons.tick();
        missiles.tick();
        territory.tick(ticks);
        jobs.tick(ticks);
        companies.tick(ticks);
        if (ticks % 1200 == 0) market.tick();
        loot.tick();
        if (ticks % 20 == 0) store.tick();
        if (ticks % 1200 == 0) ranks.tick();
        fr.fnations.rocket.Space.tick(this, ticks);
        if (ticks % 20 == 0) {
            research.tick();
            wars.tick();
        }
        if (ticks % 1200 == 0) rooms.tick();
        if (ticks % 72000 == 0) {
            bank.applyInterest();
            nations.collectTaxes();
        }
        long autosave = config.getLong("sauvegarde-auto-minutes", 5) * 1200L;
        if (autosave > 0 && ticks % autosave == 0) saveAll();
    }

    public void saveAll() {
        jobs.flush();
        jobs.save();
        companies.save();
        market.save();
        ranks.save();
        store.save();
        ratings.save();
        bank.save();
        nations.save();
        wars.save();
        rooms.save();
    }

    // ------------------------------------------------------------ joueurs
    public ServerPlayer player(UUID u) { return u == null ? null : server.getPlayerList().getPlayer(u); }

    public ServerPlayer playerByName(String name) { return server.getPlayerList().getPlayerByName(name); }

    public GameProfile profile(UUID u) {
        ServerPlayer p = player(u);
        if (p != null) return p.getGameProfile();
        var cache = server.getProfileCache();
        return cache == null ? new GameProfile(u, "?") : cache.get(u).orElse(new GameProfile(u, "?"));
    }

    public String name(UUID u) { return u == null ? "?" : profile(u).getName(); }

    public UUID uuidByName(String name) {
        ServerPlayer p = playerByName(name);
        if (p != null) return p.getUUID();
        var cache = server.getProfileCache();
        return cache == null ? null : cache.get(name).map(GameProfile::getId).orElse(null);
    }

    // ------------------------------------------------------------ accès
    public MinecraftServer server() { return server; }
    public Path configDir() { return configDir; }
    public Path dataDir() { return dataDir; }
    public Yaml config() { return config; }
    public List<MachineRecipe> machineRecipes(MachineType t) { return machineRecipes.getOrDefault(t, List.of()); }
    public Advancements advancements() { return advancements; }
    public BankManager bank() { return bank; }
    public NationManager nations() { return nations; }
    public Territory territory() { return territory; }
    public ResearchManager research() { return research; }
    public WarManager wars() { return wars; }
    public RoomManager rooms() { return rooms; }
    public WeaponManager weapons() { return weapons; }
    public MissileManager missiles() { return missiles; }
    public CraftingStation station() { return station; }
    public fr.fnations.job.JobManager jobs() { return jobs; }
    public fr.fnations.company.CompanyManager companies() { return companies; }
    public fr.fnations.market.MarketManager market() { return market; }
    public fr.fnations.ui.UiManager ui() { return ui; }
    public fr.fnations.rank.RankManager ranks() { return ranks; }
    public fr.fnations.store.StoreManager store() { return store; }
    public fr.fnations.loot.LootManager loot() { return loot; }
    public fr.fnations.spawner.SpawnerManager spawners() { return spawners; }

    public fr.fnations.rating.RatingManager ratings() { return ratings; }
    public NationActions nationActions() { return nationActions; }
}

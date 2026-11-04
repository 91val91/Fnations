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
    private static final String[] DEFAULTS = {"config.yml", "research.yml", "recipes.yml", "weapons.yml", "metiers.yml"};
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
        s.ui = new fr.fnations.ui.UiManager(s);
        fr.fnations.ui.pages.Pages.register(s, s.ui);
        NationActions actions = new NationActions(s);
        s.nationActions = actions;
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
        } catch (Exception e) {
            FNations.LOG.error("Impossible de préparer le dossier de configuration", e);
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
    public NationActions nationActions() { return nationActions; }
}

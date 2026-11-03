package fr.fnations;

import fr.fnations.admin.AdminCommand;
import fr.fnations.admin.Advancements;
import fr.fnations.bank.BankCommand;
import fr.fnations.bank.BankGui;
import fr.fnations.bank.BankManager;
import fr.fnations.craft.CraftingStation;
import fr.fnations.gui.GuiListener;
import fr.fnations.gui.MainMenu;
import fr.fnations.item.ItemListener;
import fr.fnations.item.ItemRegistry;
import fr.fnations.machine.MachineManager;
import fr.fnations.missile.MissileManager;
import fr.fnations.nation.NationActions;
import fr.fnations.nation.NationCommand;
import fr.fnations.nation.NationGui;
import fr.fnations.nation.NationManager;
import fr.fnations.nation.TerritoryListener;
import fr.fnations.research.ResearchGui;
import fr.fnations.research.ResearchManager;
import fr.fnations.rocket.RocketManager;
import fr.fnations.room.RoomCommand;
import fr.fnations.room.RoomGui;
import fr.fnations.room.RoomManager;
import fr.fnations.util.ChatPrompt;
import fr.fnations.util.Keys;
import fr.fnations.vehicle.VehicleManager;
import fr.fnations.war.WarCommand;
import fr.fnations.war.WarGui;
import fr.fnations.war.WarManager;
import fr.fnations.weapon.WeaponManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * FNations — serveur de nations : R&D, guerres, banque, chambres, armes à feu,
 * véhicules, machines, missiles et conquête spatiale.
 */
public final class FNations extends JavaPlugin {
    private static final String[] RESOURCES = {"items.yml", "research.yml", "recipes.yml", "weapons.yml"};

    private Advancements advancements;
    private BankManager bank;
    private BankGui bankGui;
    private NationManager nations;
    private NationGui nationGui;
    private TerritoryListener territory;
    private ResearchManager research;
    private ResearchGui researchGui;
    private WarManager wars;
    private WarGui warGui;
    private RoomManager rooms;
    private RoomGui roomGui;
    private WeaponManager weapons;
    private VehicleManager vehicles;
    private MachineManager machines;
    private MissileManager missiles;
    private RocketManager rockets;
    private CraftingStation station;
    private MainMenu mainMenu;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        for (String r : RESOURCES) if (!new File(getDataFolder(), r).exists()) saveResource(r, false);
        Keys.init(this);
        ItemRegistry.load(YamlConfiguration.loadConfiguration(new File(getDataFolder(), "items.yml")), getLogger());

        advancements = new Advancements();
        bank = new BankManager(this);
        nations = new NationManager(this);
        research = new ResearchManager(this);
        wars = new WarManager(this);
        rooms = new RoomManager(this);
        territory = new TerritoryListener(this);
        weapons = new WeaponManager(this);
        vehicles = new VehicleManager(this);
        machines = new MachineManager(this);
        missiles = new MissileManager(this);
        rockets = new RocketManager(this);
        station = new CraftingStation(this);

        NationActions actions = new NationActions(this);
        nationGui = new NationGui(this, actions);
        bankGui = new BankGui(this);
        researchGui = new ResearchGui(this);
        warGui = new WarGui(this);
        roomGui = new RoomGui(this);
        mainMenu = new MainMenu(this);

        BankCommand bankCmd = new BankCommand(this);
        RoomCommand roomCmd = new RoomCommand(this);
        register(new GuiListener(), new ChatPrompt(this), new ItemListener(), territory, wars, weapons, vehicles,
                machines, rockets, bankCmd, roomCmd);

        command("nation", new NationCommand(this, actions));
        command("banque", bankCmd);
        command("payer", bankCmd);
        command("guerre", new WarCommand(this));
        command("chambre", roomCmd);
        AdminCommand admin = new AdminCommand(this);
        command("fnations", admin);
        command("menu", admin);
        PluginCommand rd = getCommand("recherche");
        if (rd != null) rd.setExecutor((s, c, l, a) -> { if (s instanceof org.bukkit.entity.Player p) researchGui.open(p); return true; });
        PluginCommand st = getCommand("station");
        if (st != null) st.setExecutor((s, c, l, a) -> { if (s instanceof org.bukkit.entity.Player p) station.open(p); return true; });

        // Les entités (véhicules, machines, fusées) sont recréées une fois les mondes chargés
        getServer().getScheduler().runTask(this, () -> {
            vehicles.load();
            machines.load();
            rockets.load();
        });

        // Tâches périodiques
        getServer().getScheduler().runTaskTimer(this, () -> { research.tick(); wars.tick(); }, 20L, 20L);
        getServer().getScheduler().runTaskTimer(this, rooms::tick, 1200L, 1200L);
        getServer().getScheduler().runTaskTimer(this, () -> { bank.applyInterest(); nations.collectTaxes(); }, 72000L, 72000L);
        long autosave = getConfig().getLong("sauvegarde-auto-minutes", 5) * 1200L;
        getServer().getScheduler().runTaskTimer(this, this::saveAll, autosave, autosave);
        getLogger().info("FNations activé : nations, R&D, guerres, banque, chambres, armes, véhicules, machines, missiles, fusées.");
    }

    @Override
    public void onDisable() {
        if (machines == null || rockets == null) return; // activation incomplète : ne rien écraser
        saveAll();
        vehicles.shutdown();
        machines.shutdown();
        rockets.shutdown();
    }

    public void saveAll() {
        bank.save();
        nations.save();
        wars.save();
        rooms.save();
        vehicles.save();
        machines.save();
    }

    public void reloadAll() {
        reloadConfig();
        ItemRegistry.load(YamlConfiguration.loadConfiguration(new File(getDataFolder(), "items.yml")), getLogger());
        research.reload();
        weapons.reload();
        vehicles.reload();
        machines.reloadRecipes();
        missiles.reload();
        station.reload();
    }

    private void register(Listener... ls) { for (Listener l : ls) getServer().getPluginManager().registerEvents(l, this); }

    private void command(String name, TabExecutor ex) {
        PluginCommand c = getCommand(name);
        if (c == null) return;
        c.setExecutor(ex);
        c.setTabCompleter(ex);
    }

    // ------------------------------------------------------------ accès
    public Advancements advancements() { return advancements; }
    public BankManager bank() { return bank; }
    public BankGui bankGui() { return bankGui; }
    public NationManager nations() { return nations; }
    public NationGui nationGui() { return nationGui; }
    public TerritoryListener territory() { return territory; }
    public ResearchManager research() { return research; }
    public ResearchGui researchGui() { return researchGui; }
    public WarManager wars() { return wars; }
    public WarGui warGui() { return warGui; }
    public RoomManager rooms() { return rooms; }
    public RoomGui roomGui() { return roomGui; }
    public WeaponManager weapons() { return weapons; }
    public VehicleManager vehicles() { return vehicles; }
    public MachineManager machines() { return machines; }
    public MissileManager missiles() { return missiles; }
    public RocketManager rockets() { return rockets; }
    public CraftingStation station() { return station; }
    public MainMenu mainMenu() { return mainMenu; }
}

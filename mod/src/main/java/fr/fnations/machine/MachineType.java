package fr.fnations.machine;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.List;

/** Types de machines. Valeurs par seconde. */
public enum MachineType implements StringRepresentable {
    CRAFTING_STATION("crafting_station", Kind.STATION, 0, 0, 0, 0, List.of("<gray>Fabrique armes, véhicules, machines,", "<gray>missiles et fusées.")),
    COMPRESSOR("compressor", Kind.CONSUMER, 0, 20, 2000, 0.9, List.of("<gray>Consomme : 20 E/s")),
    COAL_GENERATOR("coal_generator", Kind.GENERATOR, 60, 0, 6000, 1.1, List.of("<gray>Produit : 60 E/s")),
    SOLAR_PANEL_T1("solar_panel_t1", Kind.GENERATOR, 10, 0, 2000, 0, List.of("<gray>Produit : 10 E/s (jour)")),
    SOLAR_PANEL_T2("solar_panel_t2", Kind.GENERATOR, 30, 0, 4000, 0, List.of("<gray>Produit : 30 E/s (jour)")),
    SOLAR_PANEL_T3("solar_panel_t3", Kind.GENERATOR, 80, 0, 8000, 0, List.of("<gray>Produit : 80 E/s (jour)", "<gray>25% de la production la nuit")),
    ELECTRIC_FURNACE("electric_furnace", Kind.CONSUMER, 0, 15, 2000, 0.8, List.of("<gray>Consomme : 15 E/s")),
    CRUSHER("crusher", Kind.CONSUMER, 0, 30, 3000, 1.2, List.of("<gray>Consomme : 30 E/s", "<gray>Recycle les missiles en composants.")),
    SWITCH("switch", Kind.SWITCH, 0, 0, 0, 0, List.of("<gray>Coupe les machines du réseau", "<gray>(rayon 16 blocs) en cas de surchauffe.")),
    MISSILE_LAUNCHER("missile_launcher", Kind.LAUNCHER, 0, 0, 5000, 0, List.of("<gray>Consomme de l'énergie au tir."));

    public enum Kind { STATION, GENERATOR, CONSUMER, SWITCH, LAUNCHER }

    public static final Codec<MachineType> CODEC = StringRepresentable.fromEnum(MachineType::values);

    public final String id;
    public final Kind kind;
    public final double production, consumption, capacity, heatRate;
    public final List<String> lore;

    MachineType(String id, Kind kind, double production, double consumption, double capacity, double heatRate, List<String> lore) {
        this.id = id;
        this.kind = kind;
        this.production = production;
        this.consumption = consumption;
        this.capacity = capacity;
        this.heatRate = heatRate;
        this.lore = lore;
    }

    @Override
    public String getSerializedName() { return id; }

    public boolean solar() { return this == SOLAR_PANEL_T1 || this == SOLAR_PANEL_T2 || this == SOLAR_PANEL_T3; }

    /** Machine dotée d'un menu à cases (sinon panneau ou rien). */
    public boolean hasMenu() { return kind == Kind.CONSUMER || this == COAL_GENERATOR || kind == Kind.LAUNCHER || solar(); }

    public String itemKey() { return "fnations:" + id; }
}

package fr.fnations.machine;

/** Types de blocs-machines FNations. Valeurs par seconde. */
public enum MachineType {
    CRAFTING_STATION("crafting_station", Kind.STATION, 0, 0, 0, 0),
    COMPRESSOR("compressor", Kind.CONSUMER, 0, 20, 2000, 0.9),
    COAL_GENERATOR("coal_generator", Kind.GENERATOR, 60, 0, 6000, 1.1),
    SOLAR_PANEL_T1("solar_panel_t1", Kind.GENERATOR, 10, 0, 2000, 0),
    SOLAR_PANEL_T2("solar_panel_t2", Kind.GENERATOR, 30, 0, 4000, 0),
    SOLAR_PANEL_T3("solar_panel_t3", Kind.GENERATOR, 80, 0, 8000, 0),
    ELECTRIC_FURNACE("electric_furnace", Kind.CONSUMER, 0, 15, 2000, 0.8),
    CRUSHER("crusher", Kind.CONSUMER, 0, 30, 3000, 1.2),
    SWITCH("switch", Kind.SWITCH, 0, 0, 0, 0),
    MISSILE_LAUNCHER("missile_launcher", Kind.LAUNCHER, 0, 0, 5000, 0);

    public enum Kind { STATION, GENERATOR, CONSUMER, SWITCH, LAUNCHER }

    public final String item;
    public final Kind kind;
    public final double production, consumption, capacity, heatRate;

    MachineType(String item, Kind kind, double production, double consumption, double capacity, double heatRate) {
        this.item = item;
        this.kind = kind;
        this.production = production;
        this.consumption = consumption;
        this.capacity = capacity;
        this.heatRate = heatRate;
    }

    public static MachineType byItem(String id) {
        if (id == null) return null;
        for (MachineType t : values()) if (t.item.equals(id)) return t;
        return null;
    }

    public boolean solar() { return this == SOLAR_PANEL_T1 || this == SOLAR_PANEL_T2 || this == SOLAR_PANEL_T3; }

    public boolean hasInventory() { return kind == Kind.CONSUMER || this == COAL_GENERATOR || kind == Kind.LAUNCHER; }
}

package fr.fnations.vehicle;

/** Types de véhicules : physique (simulée par le client du conducteur) et paramètres de rendu. */
public enum VehicleKind {
    //        id            mode               larg  haut  places vmax  accél virage décol montée vie   carb  conso   arme              échelle  pas
    TANK("tank", Mode.GROUND, 3.2f, 2.4f, 2, 0.28, 0.012, 2.5f, 0, 0, 600, 200, 0.02, Weapon.CANNON, 2.0f, 1.0f),
    PLANE("plane", Mode.PLANE, 3.4f, 1.8f, 1, 1.6, 0.02, 3f, 0.7, 0, 250, 300, 0.04, Weapon.MACHINE_GUN, 2.5f, 0.6f),
    HELICOPTER("helicopter", Mode.HELICOPTER, 3.0f, 2.6f, 4, 0.7, 0.02, 3.5f, 0, 0.3, 300, 250, 0.03, Weapon.MACHINE_GUN, 2.2f, 0.6f),
    TRACTOR("tractor", Mode.GROUND, 2.2f, 2.2f, 1, 0.3, 0.015, 4f, 0, 0, 200, 150, 0.01, Weapon.NONE, 1.6f, 1.0f);

    public enum Mode { GROUND, PLANE, HELICOPTER }

    public enum Weapon { NONE, CANNON, MACHINE_GUN }

    public final String id;
    public final Mode mode;
    public final float width, height;
    public final int seats;
    public final double maxSpeed, accel;
    public final float turn;
    public final double takeoffSpeed, climb;
    public final double health, fuelCapacity, fuelPerTick;
    public final Weapon weapon;
    public final float renderScale, step;

    VehicleKind(String id, Mode mode, float width, float height, int seats, double maxSpeed, double accel, float turn, double takeoffSpeed,
                double climb, double health, double fuelCapacity, double fuelPerTick, Weapon weapon, float renderScale, float step) {
        this.id = id;
        this.mode = mode;
        this.width = width;
        this.height = height;
        this.seats = seats;
        this.maxSpeed = maxSpeed;
        this.accel = accel;
        this.turn = turn;
        this.takeoffSpeed = takeoffSpeed;
        this.climb = climb;
        this.health = health;
        this.fuelCapacity = fuelCapacity;
        this.fuelPerTick = fuelPerTick;
        this.weapon = weapon;
        this.renderScale = renderScale;
        this.step = step;
    }

    public boolean flies() { return mode != Mode.GROUND; }
}

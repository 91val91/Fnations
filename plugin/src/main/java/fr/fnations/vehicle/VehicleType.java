package fr.fnations.vehicle;

import org.bukkit.configuration.ConfigurationSection;

/** Type de véhicule (config.yml > vehicules). */
public record VehicleType(String id, String name, Mode mode, double health, double maxSpeed, double accel, double turn,
                          double fuelCapacity, double fuelPerTick, int seats, float scale, double modelY, float yawOffset,
                          double seatY, float hitboxWidth, float hitboxHeight, String weapon, double takeoffSpeed, double climb) {

    public enum Mode { GROUND, PLANE, HELICOPTER }

    public static VehicleType load(String id, ConfigurationSection s) {
        return new VehicleType(id, s.getString("nom", id), Mode.valueOf(s.getString("mode", "GROUND").toUpperCase()),
                s.getDouble("vie", 200), s.getDouble("vitesse-max", 0.4), s.getDouble("acceleration", 0.02),
                s.getDouble("rotation", 4), s.getDouble("carburant-max", 100), s.getDouble("consommation", 0.01),
                s.getInt("places", 1), (float) s.getDouble("echelle", 2.0), s.getDouble("modele-y", 1.0),
                (float) s.getDouble("modele-rotation", 180), s.getDouble("siege-y", 0.5),
                (float) s.getDouble("hitbox-largeur", 2.5), (float) s.getDouble("hitbox-hauteur", 2),
                s.getString("arme", "aucune"), s.getDouble("vitesse-decollage", 0.6), s.getDouble("montee", 0.25));
    }
}

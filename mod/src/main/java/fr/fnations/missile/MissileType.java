package fr.fnations.missile;

import fr.fnations.util.Yaml;

/** Caractéristiques d'un missile (config.yml > missiles.types). */
public record MissileType(String item, double range, double minRange, float power, double speed, double arc, double energy,
                          int extraExplosions, double blastRadius, double radiationRadius, int radiationSeconds) {

    public static MissileType load(String id, Yaml s) {
        return new MissileType(id, s.getDouble("portee", 400), s.getDouble("portee-min", 30), (float) s.getDouble("puissance", 4),
                s.getDouble("vitesse", 2), s.getDouble("arc", 0.3), s.getDouble("energie", 500), s.getInt("explosions-secondaires", 0),
                s.getDouble("rayon-souffle", 0), s.getDouble("rayon-radiation", 0), s.getInt("radiation-secondes", 0));
    }
}

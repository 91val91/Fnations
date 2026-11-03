package fr.fnations.util;

import fr.fnations.nation.Nation;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

/**
 * Explosions "militaires" (missiles, obus, roquettes). Le contexte (nation attaquante) permet au listener
 * de territoire de n'autoriser les dégâts qu'aux zones sauvages et aux nations en guerre contre l'attaquant.
 */
public final class Explosions {
    private static Nation context;
    private static boolean active;

    private Explosions() {}

    public static void explode(Location loc, float power, boolean fire, boolean breakBlocks, Nation attacker, Entity source) {
        active = true;
        context = attacker;
        try {
            loc.getWorld().createExplosion(loc, power, fire, breakBlocks, source);
        } finally {
            active = false;
            context = null;
        }
    }

    public static boolean active() { return active; }

    public static Nation context() { return context; }
}

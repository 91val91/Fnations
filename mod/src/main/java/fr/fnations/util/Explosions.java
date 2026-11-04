package fr.fnations.util;

import fr.fnations.nation.Nation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Explosions militaires (missiles, obus, roquettes). Le contexte (nation attaquante) est lu par le mixin
 * d'explosion pour n'autoriser les dégâts de blocs que dans les zones sauvages et chez les ennemis en guerre.
 */
public final class Explosions {
    private static Nation context;
    private static boolean active;

    private Explosions() {}

    public static void explode(ServerLevel level, double x, double y, double z, float power, boolean fire, boolean breakBlocks, Nation attacker, Entity source) {
        active = true;
        context = attacker;
        try {
            level.explode(source, x, y, z, power, fire, breakBlocks ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
        } finally {
            active = false;
            context = null;
        }
    }

    public static boolean active() { return active; }

    public static Nation context() { return context; }
}

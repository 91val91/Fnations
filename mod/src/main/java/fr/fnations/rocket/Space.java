package fr.fnations.rocket;

import fr.fnations.server.FnServer;
import fr.fnations.util.Locs;
import fr.fnations.util.Yaml;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/** Faible gravité dans les dimensions spatiales (toutes les 2 secondes). */
public final class Space {
    private Space() {}

    public static void tick(FnServer s, long ticks) {
        if (ticks % 40 != 0) return;
        Yaml dest = s.config().getSection("fusee.destinations");
        if (dest == null) return;
        for (String id : dest.getKeys()) {
            Yaml d = dest.getSection(id);
            if (!d.getBoolean("gravite-faible", false)) continue;
            ServerLevel l = Locs.level(s.server(), d.getString("monde", ""));
            if (l == null) continue;
            for (ServerPlayer p : l.players()) {
                p.addEffect(new MobEffectInstance(MobEffects.JUMP, 60, 3, true, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, true, false, false));
            }
        }
    }
}

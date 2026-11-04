package fr.fnations.admin;

import fr.fnations.FNations;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.server.level.ServerPlayer;

/** Accorde les progrès FNations (data/fnations/advancement, critère "trigger"). */
public final class Advancements {
    public void grant(ServerPlayer p, String id) {
        AdvancementHolder adv = p.server.getAdvancements().get(FNations.id(id));
        if (adv == null) return;
        AdvancementProgress prog = p.getAdvancements().getOrStartProgress(adv);
        if (prog.isDone()) return;
        for (String c : prog.getRemainingCriteria()) p.getAdvancements().award(adv, c);
    }
}

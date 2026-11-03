package fr.fnations.admin;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;

/** Accorde les progrès du datapack FNations (critère "impossible" nommé "trigger"). */
public final class Advancements {
    public void grant(Player p, String id) {
        Advancement adv = Bukkit.getAdvancement(new NamespacedKey("fnations", id));
        if (adv == null) return; // datapack non installé
        AdvancementProgress prog = p.getAdvancementProgress(adv);
        if (prog.isDone()) return;
        for (String c : prog.getRemainingCriteria()) prog.awardCriteria(c);
    }
}

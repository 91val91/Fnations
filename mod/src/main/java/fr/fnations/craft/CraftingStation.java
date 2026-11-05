package fr.fnations.craft;

import fr.fnations.registry.Category;
import fr.fnations.registry.ModItems;
import fr.fnations.research.Technology;
import fr.fnations.server.FnServer;
import fr.fnations.server.Msg;
import fr.fnations.util.Inv;
import fr.fnations.util.Yaml;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Station de Craft : fabrication de tout le contenu du mod (armes, véhicules, machines, missiles, fusée...). */
public final class CraftingStation {
    private final FnServer plugin;
    private final Map<String, StationRecipe> recipes = new LinkedHashMap<>();

    public CraftingStation(FnServer plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        recipes.clear();
        Yaml root = plugin.yaml("recipes.yml").getSection("station");
        if (root == null) return;
        for (String id : root.getKeys()) {
            Yaml s = root.getSection(id);
            String result = s.getString("resultat", id);
            if (ModItems.get(result) == null) { fr.fnations.FNations.LOG.warn("Recette {} : item inconnu {}", id, result); continue; }
            Map<String, Integer> ing = new LinkedHashMap<>();
            for (String spec : s.getStringList("ingredients")) {
                var e = Inv.parse(spec);
                ing.merge(e.getKey(), e.getValue(), Integer::sum);
            }
            Category cat = s.contains("categorie") ? Category.byName(s.getString("categorie")) : ModItems.CATEGORY.getOrDefault(result, Category.COMPOSANT);
            recipes.put(id, new StationRecipe(id, result, s.getInt("nombre", 1), cat.name(), ing));
        }
    }

    public List<StationRecipe> all() { return new ArrayList<>(recipes.values()); }

    public StationRecipe get(String id) { return recipes.get(id); }

    /** Recette de la Station qui fabrique cet objet (id du mod, sans « fnations: »), ou null. */
    public StationRecipe forResult(String result) {
        StationRecipe r = recipes.get(result);
        if (r != null && r.result().equals(result)) return r;
        for (StationRecipe x : recipes.values()) if (x.result().equals(result)) return x;
        return null;
    }

    public boolean craft(ServerPlayer p, StationRecipe r, boolean feedback) {
        Technology t = plugin.research().requiredFor(r.result());
        if (t != null && !plugin.research().playerCanUse(p, t.id())) {
            if (feedback) Msg.err(p, "Votre nation doit rechercher : " + t.name());
            return false;
        }
        String itemId = "fnations:" + r.result();
        if (!plugin.jobs().canCraft(p, itemId)) {
            var q = plugin.jobs().requirement(itemId);
            if (feedback) Msg.err(p, fr.fnations.job.JobManager.label(q) + " requis pour fabriquer <item:" + itemId + "> (vous : palier "
                    + plugin.jobs().palier(p.getUUID(), q.job()) + ").");
            return false;
        }
        if (!Inv.takeAll(p, r.ingredients())) {
            if (feedback) Msg.err(p, "Ingrédients manquants.");
            return false;
        }
        Inv.give(p, ModItems.stack(r.result(), r.amount()));
        plugin.jobs().onStationCraft(p, r);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 1f, 1f);
        p.serverLevel().sendParticles(ParticleTypes.WAX_OFF, p.getX(), p.getY() + 1, p.getZ(), 10, 0.4, 0.4, 0.4, 0);
        if (feedback) Msg.ok(p, "Fabriqué : <item:fnations:" + r.result() + ">" + (r.amount() > 1 ? " ×" + r.amount() : ""));
        return true;
    }
}

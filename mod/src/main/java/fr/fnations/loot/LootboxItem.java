package fr.fnations.loot;

import fr.fnations.registry.Category;
import fr.fnations.registry.ModItems;
import fr.fnations.server.FnServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.List;

/** LootBox : clic droit pour l'ouvrir (roulette animée sur la tablette). */
public class LootboxItem extends ModItems.FnItem {
    public final String box;

    public LootboxItem(Properties p, String box) {
        super(p, Category.ECONOMIE, List.of("<#8C96FF>Clic droit pour ouvrir", "<gray>Gains aléatoires : argent, objets, spawners, grades..."));
        this.box = box;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            FnServer s = FnServer.get();
            if (s != null) s.loot().open(sp, box);
        }
        return InteractionResult.SUCCESS;
    }
}

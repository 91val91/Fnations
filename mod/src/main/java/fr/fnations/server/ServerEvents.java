package fr.fnations.server;

import fr.fnations.admin.Commands;
import fr.fnations.gui.Prompt;
import fr.fnations.registry.ModItems;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Branchement des événements Fabric côté serveur. */
public final class ServerEvents {
    private ServerEvents() {}

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(FnServer::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> FnServer.stop());
        ServerTickEvents.END_SERVER_TICK.register(s -> { if (FnServer.get() != null) FnServer.get().tick(); });
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> Commands.register(dispatcher));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            Prompt.forget(handler.player.getUUID());
            FnServer s = FnServer.get();
            if (s != null) {
                s.jobs().onQuit(handler.player);
                s.loot().onQuit(handler.player);
                s.ui().forget(handler.player.getUUID());
                s.territory().forget(handler.player.getUUID());
                s.wars().onQuit(handler.player);
                s.weapons().onQuit(handler.player);
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            FnServer s = FnServer.get();
            if (s != null) {
                s.jobs().onJoin(handler.player);
                s.ranks().apply(handler.player);
                s.store().onJoin(handler.player);
                s.ratings().onJoin(handler.player);
            }
        });
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register((oldP, newP, alive) -> {
            FnServer s = FnServer.get();
            if (s != null) s.jobs().applyAttributes(newP);
        });

        // ---- Métiers : blocs cassés (après la casse, seulement si autorisée)
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
            FnServer s = FnServer.get();
            if (s != null && player instanceof ServerPlayer sp && level instanceof ServerLevel sl) s.jobs().onBreak(sp, sl, pos, state);
        });

        // ---- Protection : casse de blocs
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> {
            FnServer s = FnServer.get();
            if (s == null || !(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel sl)) return true;
            if (be instanceof fr.fnations.spawner.SpawnerBlockEntity spb && !s.spawners().isOwner(sp, spb)) {
                Msg.err(sp, "Ce spawner appartient à " + spb.ownerName() + ".");
                return false;
            }
            if (s.territory().canBuild(sp, sl, pos)) return true;
            s.territory().deny(sp, sl, pos);
            return false;
        });

        // ---- Protection : interactions / pose de blocs (le guichet des chambres passe avant)
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            FnServer s = FnServer.get();
            if (s == null || level.isClientSide || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
            ServerLevel sl = (ServerLevel) level;
            String group = s.rooms().deskGroup(sl, hit.getBlockPos());
            if (group != null && hand == net.minecraft.world.InteractionHand.MAIN_HAND) {
                s.ui().open(sp, "chambres", java.util.Map.of("group", group == null ? "" : group, "room", ""));
                return InteractionResult.SUCCESS;
            }
            BlockPos target = hit.getBlockPos();
            boolean placing = !player.getItemInHand(hand).isEmpty() && player.getItemInHand(hand).getItem() instanceof net.minecraft.world.item.BlockItem;
            BlockPos check = placing && !level.getBlockState(target).canBeReplaced() ? target.relative(hit.getDirection()) : target;
            if (s.territory().canBuild(sp, sl, check) && s.territory().canBuild(sp, sl, target)) return InteractionResult.PASS;
            if (ModItems.idOf(player.getItemInHand(hand)) != null && !placing) return InteractionResult.PASS; // véhicules/fusées : vérifiés à part
            s.territory().deny(sp, sl, check);
            return InteractionResult.FAIL;
        });

        // ---- Protection : seaux
        UseItemCallback.EVENT.register((player, level, hand) -> {
            FnServer s = FnServer.get();
            if (s == null || level.isClientSide || !(player.getItemInHand(hand).getItem() instanceof BucketItem) || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
            HitResult hr = player.pick(player.blockInteractionRange(), 0, true);
            if (hr instanceof BlockHitResult bh && hr.getType() == HitResult.Type.BLOCK) {
                ServerLevel sl = (ServerLevel) level;
                if (!s.territory().canBuild(sp, sl, bh.getBlockPos()) || !s.territory().canBuild(sp, sl, bh.getBlockPos().relative(bh.getDirection()))) {
                    s.territory().deny(sp, sl, bh.getBlockPos());
                    return InteractionResult.FAIL;
                }
            }
            return InteractionResult.PASS;
        });

        // ---- PvP entre alliés / même nation
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            FnServer s = FnServer.get();
            return s == null || s.territory().allowDamage(entity, source);
        });

        // ---- Guerres : kills
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            FnServer s = FnServer.get();
            if (s != null && entity instanceof ServerPlayer victim) s.wars().onDeath(victim, source);
            if (s != null && source.getEntity() instanceof ServerPlayer killer && entity != killer) s.jobs().onKill(killer, entity);
        });
    }

    @SuppressWarnings("unused")
    private static ClipContext unused() { return null; }
}

package fr.fnations.rocket;

import fr.fnations.gui.Icons;
import fr.fnations.gui.Panel;
import fr.fnations.registry.ModEntities;
import fr.fnations.registry.ModItems;
import fr.fnations.server.FnServer;
import fr.fnations.server.Msg;
import fr.fnations.util.Inv;
import fr.fnations.util.Locs;
import fr.fnations.util.Txt;
import fr.fnations.util.Yaml;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Fusée spatiale : remplissage, poste de pilotage, compte à rebours, décollage et voyage vers une autre dimension. */
public class RocketEntity extends Entity {
    private static final EntityDataAccessor<Integer> FUEL = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.INT);
    public static final int IDLE = 0, COUNTDOWN = 1, ASCENT = 2;

    private UUID owner;
    private int timer;
    private double ascentSpeed;
    private String destination;
    private int lerpSteps;
    private double lx, ly, lz;
    private float lyr, lxr;

    public RocketEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(FUEL, 0);
        b.define(STATE, IDLE);
    }

    public static int fuelNeeded() {
        FnServer s = FnServer.get();
        return s == null ? 16 : s.config().getInt("fusee.ergol-requis", 16);
    }

    public int fuel() { return entityData.get(FUEL); }

    public int state() { return entityData.get(STATE); }

    public void setOwner(UUID u) { owner = u; }

    // ------------------------------------------------------------ passagers
    @Override
    protected boolean canAddPassenger(Entity e) { return getPassengers().isEmpty(); }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity e, EntityDimensions d, float f) { return new Vec3(0, 3.2, 0); }

    @Override
    public boolean isPickable() { return !isRemoved(); }

    @Override
    public boolean canBeCollidedWith() { return true; }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource src, float f) { return false; }

    @Override
    public ItemStack getPickResult() { return ModItems.stack("rocket"); }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        ServerPlayer p = (ServerPlayer) player;
        if (state() != IDLE) return InteractionResult.SUCCESS_SERVER;
        if (hasPassenger(p)) { controls(p); return InteractionResult.SUCCESS_SERVER; }
        ItemStack held = p.getItemInHand(hand);
        if (ModItems.is(held, "rocket_fuel")) {
            int add = Math.min(held.getCount(), fuelNeeded() - fuel());
            if (add <= 0) { Msg.err(p, "Réservoirs pleins."); return InteractionResult.SUCCESS_SERVER; }
            held.shrink(add);
            entityData.set(FUEL, fuel() + add);
            level().playSound(null, blockPosition(), SoundEvents.BUCKET_EMPTY_LAVA, SoundSource.NEUTRAL, 1f, 1f);
            Msg.ok(p, "Ergol : " + fuel() + "/" + fuelNeeded());
            return InteractionResult.SUCCESS_SERVER;
        }
        if (p.isSecondaryUseActive() && (p.getUUID().equals(owner) || p.hasPermissions(2))) {
            if (isVehicle()) return InteractionResult.SUCCESS_SERVER;
            Inv.give(p, ModItems.stack("rocket"));
            if (fuel() > 0) Inv.give(p, ModItems.stack("rocket_fuel", fuel()));
            discard();
            Msg.ok(p, "Fusée démontée.");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!isVehicle()) {
            p.startRiding(this);
            controls(p);
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    // ------------------------------------------------------------ poste de pilotage
    private void controls(ServerPlayer p) {
        FnServer s = FnServer.get();
        if (s == null) return;
        Panel g = new Panel(4, "<gold>Poste de pilotage");
        g.border(Items.ORANGE_STAINED_GLASS_PANE);
        g.set(4, Icons.of(Items.FIREWORK_ROCKET, "<gold><b>Fusée spatiale",
                "<gray>Ergol : " + Txt.progress(fuel() / (double) fuelNeeded(), 16, "gold", "dark_gray") + " <white>" + fuel() + "/" + fuelNeeded(),
                "<gray>Dimension actuelle : <white>" + Locs.dim(level())));
        Yaml dest = s.config().getSection("fusee.destinations");
        int slot = 10;
        if (dest != null) for (String id : dest.getKeys()) {
            Yaml d = dest.getSection(id);
            Item icon = Inv.item(d.getString("icone", "ENDER_PEARL"));
            boolean here = d.getString("monde", "").equals(Locs.dim(level()));
            List<String> lore = new ArrayList<>(d.getStringList("description"));
            lore.add("");
            lore.add(here ? "<gray>Vous y êtes déjà" : fuel() >= fuelNeeded() || p.isCreative() ? "<green>Clic : DÉCOLLAGE" : "<red>Ergol insuffisant");
            g.button(slot++, Icons.of(icon == Items.AIR ? Items.ENDER_PEARL : icon, d.getString("nom", id), lore), pl -> {
                if (here) return;
                pl.closeContainer();
                launch(pl, id);
            });
            if (slot == 17) slot = 19;
        }
        g.button(31, Icons.of(Items.OAK_DOOR, "<red>Descendre"), pl -> { pl.closeContainer(); pl.stopRiding(); });
        g.fill(Items.GRAY_STAINED_GLASS_PANE);
        g.open(p);
    }

    private void launch(ServerPlayer p, String destId) {
        FnServer s = FnServer.get();
        if (fuel() < fuelNeeded() && !p.isCreative()) { Msg.err(p, "Il faut " + fuelNeeded() + " Ergols de fusée."); return; }
        Yaml d = s.config().getSection("fusee.destinations." + destId);
        if (d == null || Locs.level(s.server(), d.getString("monde", "")) == null) { Msg.err(p, "Destination indisponible (dimension introuvable)."); return; }
        destination = destId;
        entityData.set(FUEL, 0);
        entityData.set(STATE, COUNTDOWN);
        timer = s.config().getInt("fusee.compte-a-rebours", 10) * 20;
        ascentSpeed = 0.05;
    }

    // ------------------------------------------------------------ tick
    @Override
    public void tick() {
        super.tick();
        if (lerpSteps > 0) {
            lerpPositionAndRotationStep(lerpSteps, lx, ly, lz, lyr, lxr);
            lerpSteps--;
        }
        if (level().isClientSide) {
            if (state() != IDLE) {
                double spread = state() == ASCENT ? 0.4 : 1.5;
                for (int i = 0; i < (state() == ASCENT ? 8 : 2); i++)
                    level().addParticle(state() == ASCENT ? ParticleTypes.FLAME : ParticleTypes.CLOUD, getX() + (random.nextDouble() - 0.5) * spread,
                            getY() + 0.2, getZ() + (random.nextDouble() - 0.5) * spread, 0, state() == ASCENT ? -0.4 : 0.05, 0);
                if (state() == ASCENT) level().addParticle(ParticleTypes.LARGE_SMOKE, getX(), getY() - 0.5, getZ(), 0, -0.1, 0);
            }
            return;
        }
        ServerLevel level = (ServerLevel) level();
        if (!onGround() && state() == IDLE) {
            setDeltaMovement(0, Math.max(-1, getDeltaMovement().y - 0.08), 0);
            move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
        }
        if (state() == COUNTDOWN) {
            if (timer % 20 == 0) {
                int sec = timer / 20;
                for (ServerPlayer pl : level.players()) if (pl.distanceToSqr(this) < 10000) Msg.title(pl, "<gold><b>" + sec, "<yellow>Décollage imminent", 0, 18, 0);
                level.playSound(null, blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.NEUTRAL, 2f, 0.6f + (10 - sec) * 0.08f);
            }
            if (--timer <= 0) {
                entityData.set(STATE, ASCENT);
                ServerPlayer pilot = getFirstPassenger() instanceof ServerPlayer sp ? sp : null;
                FnServer s = FnServer.get();
                if (pilot != null && s != null) {
                    Msg.broadcast(s.server(), "<gold>🚀 " + Txt.esc(pilot.getScoreboardName()) + " a décollé vers "
                            + s.config().getString("fusee.destinations." + destination + ".nom", destination) + "<gold> !");
                    s.advancements().grant(pilot, "rocket_launched");
                }
            }
        } else if (state() == ASCENT) {
            ascentSpeed = Math.min(2.5, ascentSpeed * 1.04 + 0.01);
            setPos(getX(), getY() + ascentSpeed, getZ());
            if (tickCount % 10 == 0) level.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.NEUTRAL, 1.5f, 0.4f);
            FnServer s = FnServer.get();
            double ceiling = s == null ? 420 : s.config().getDouble("fusee.altitude-sortie", 420);
            if (getY() >= ceiling) arrive(level);
        }
    }

    private void arrive(ServerLevel from) {
        FnServer s = FnServer.get();
        Yaml d = s == null ? null : s.config().getSection("fusee.destinations." + destination);
        ServerLevel target = d == null ? null : Locs.level(s.server(), d.getString("monde", ""));
        List<Entity> riders = new ArrayList<>(getPassengers());
        ejectPassengers();
        discard();
        if (target == null) return;
        BlockPos spawn = target.getSharedSpawnPos();
        Vec3 land = d.getBoolean("spawn-du-monde", false) ? Vec3.atBottomCenterOf(spawn) : new Vec3(d.getDouble("x"), d.getDouble("y", 100), d.getDouble("z"));
        target.getChunk(BlockPos.containing(land));
        if (d.getBoolean("plateforme", false)) platform(target, BlockPos.containing(land));
        int top = target.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(land.x), (int) Math.floor(land.z));
        if (top > target.getMinY()) land = new Vec3(land.x, Math.max(land.y, top), land.z);
        RocketEntity landed = new RocketEntity(ModEntities.ROCKET, target);
        landed.moveTo(land.x + 3, land.y, land.z, getYRot(), 0);
        landed.owner = owner;
        target.addFreshEntity(landed);
        for (Entity e : riders) {
            if (!(e instanceof ServerPlayer p)) continue;
            p.teleport(new TeleportTransition(target, land, Vec3.ZERO, p.getYRot(), p.getXRot(), TeleportTransition.DO_NOTHING));
            Msg.title(p, d.getString("nom", "Arrivée"), "<gray>Atterrissage réussi", 6, 60, 20);
            if (d.getBoolean("gravite-faible", false)) s.advancements().grant(p, "moon_landing");
        }
    }

    private static void platform(ServerLevel level, BlockPos c) {
        int y = c.getY() - 1;
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) {
            BlockPos b = new BlockPos(c.getX() + x, y, c.getZ() + z);
            if (level.getBlockState(b).isAir())
                level.setBlock(b, (Math.abs(x) == 6 || Math.abs(z) == 6 ? Blocks.LIGHT_GRAY_CONCRETE : Blocks.IRON_BLOCK).defaultBlockState(), 3);
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yr, float xr, int steps) {
        lx = x; ly = y; lz = z; lyr = yr; lxr = xr;
        lerpSteps = steps;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) { return d < 256 * 256; }

    // ------------------------------------------------------------ sauvegarde
    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("fuel", fuel());
        if (owner != null) tag.putUUID("owner", owner);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(FUEL, tag.getInt("fuel"));
        if (tag.hasUUID("owner")) owner = tag.getUUID("owner");
    }

    @Nullable
    public UUID owner() { return owner; }
}

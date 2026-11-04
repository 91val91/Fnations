package fr.fnations.vehicle;

import fr.fnations.nation.Nation;
import fr.fnations.registry.ModComponents;
import fr.fnations.registry.ModItems;
import fr.fnations.server.FnServer;
import fr.fnations.server.Msg;
import fr.fnations.util.Explosions;
import fr.fnations.util.Inv;
import fr.fnations.util.Txt;
import fr.fnations.weapon.ShellEntity;
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
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Véhicule (char, avion, hélicoptère, tracteur). Comme les bateaux vanilla, le mouvement est simulé par le client
 * du conducteur puis validé par le serveur ; carburant, dégâts et armes sont gérés par le serveur.
 */
public class VehicleEntity extends Entity {
    private static final EntityDataAccessor<Float> FUEL = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HEALTH = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> PLOW = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.BOOLEAN);

    public final VehicleKind kind;
    private UUID owner;
    // physique (client du conducteur, ou serveur sans conducteur)
    private double speed, strafe, vy;
    private Input input = Input.EMPTY;
    private float deltaYaw;
    // interpolation pour les autres clients
    private int lerpSteps;
    private double lerpX, lerpY, lerpZ;
    private float lerpYRot, lerpXRot;
    // serveur
    private Vec3 lastPos;
    private double lastSpeed, lastVy;
    private long lastShot;
    // rendu client
    public float rotor, rotorO;

    public VehicleEntity(EntityType<?> type, Level level, VehicleKind kind) {
        super(type, level);
        this.kind = kind;
        this.blocksBuilding = true;
        setNoGravity(true);
        entityData.set(HEALTH, (float) kind.health);
    }

    public void setup(UUID owner, double fuel) {
        this.owner = owner;
        entityData.set(FUEL, (float) Math.min(maxFuel(), fuel));
        entityData.set(HEALTH, (float) maxHealth());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(FUEL, 0f);
        b.define(HEALTH, 100f);
        b.define(PLOW, false);
    }

    // ------------------------------------------------------------ accès
    public float fuel() { return entityData.get(FUEL); }

    public float health() { return entityData.get(HEALTH); }

    public boolean plow() { return entityData.get(PLOW); }

    public double speed() { return speed; }

    public UUID owner() { return owner; }

    private static FnServer srv() { return FnServer.get(); }

    public double maxHealth() {
        FnServer s = srv();
        return s == null ? kind.health : s.config().getDouble("vehicules." + kind.id + ".vie", kind.health);
    }

    public double maxFuel() {
        FnServer s = srv();
        return s == null ? kind.fuelCapacity : s.config().getDouble("vehicules." + kind.id + ".carburant-max", kind.fuelCapacity);
    }

    /** Appelé chaque tick par le client du conducteur. */
    public void setInput(Input in) { this.input = in; }

    @Override
    public float maxUpStep() { return kind.step; }

    // ------------------------------------------------------------ passagers
    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof Player p ? p : null;
    }

    @Override
    protected boolean canAddPassenger(Entity e) { return getPassengers().size() < kind.seats; }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity e, EntityDimensions dims, float scale) {
        int i = Math.max(0, getPassengers().indexOf(e));
        Vec3 seat = switch (kind) {
            case TANK -> i == 0 ? new Vec3(0, 1.25, 0.3) : new Vec3(0, 1.75, -0.6);
            case PLANE -> new Vec3(0, 0.55, -0.4);
            case HELICOPTER -> new Vec3(i % 2 == 0 ? -0.45 : 0.45, 0.45, i < 2 ? 0.55 : -0.45);
            case TRACTOR -> new Vec3(0, 0.95, 0.35);
        };
        return seat.yRot(-getYRot() * Mth.DEG_TO_RAD);
    }

    @Override
    protected void positionRider(Entity e, MoveFunction f) {
        super.positionRider(e, f);
        if (kind.mode == VehicleKind.Mode.GROUND && deltaYaw != 0) {
            e.setYRot(e.getYRot() + deltaYaw);
            e.setYHeadRot(e.getYHeadRot() + deltaYaw);
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (hasPassenger(player)) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        ServerPlayer p = (ServerPlayer) player;
        ItemStack held = p.getItemInHand(hand);
        if (ModItems.is(held, "fuel_canister")) {
            if (fuel() >= maxFuel()) { Msg.err(p, "Le réservoir est plein."); return InteractionResult.SUCCESS_SERVER; }
            double add = srv() == null ? 50 : srv().config().getDouble("vehicules-general.carburant-par-jerrican", 50);
            entityData.set(FUEL, (float) Math.min(maxFuel(), fuel() + add));
            held.consume(1, p);
            level().playSound(null, blockPosition(), SoundEvents.BUCKET_EMPTY, SoundSource.NEUTRAL, 1f, 1.2f);
            Msg.ok(p, "Ravitaillé : " + (int) fuel() + "/" + (int) maxFuel());
            return InteractionResult.SUCCESS_SERVER;
        }
        if (p.isSecondaryUseActive()) {
            if (!p.getUUID().equals(owner) && !p.hasPermissions(2)) { Msg.err(p, "Ce véhicule ne vous appartient pas."); return InteractionResult.SUCCESS_SERVER; }
            if (isVehicle()) { Msg.err(p, "Le véhicule est occupé."); return InteractionResult.SUCCESS_SERVER; }
            ItemStack it = ModItems.stack(kind.id);
            it.set(ModComponents.FUEL, (double) fuel());
            Inv.give(p, it);
            discard();
            Msg.ok(p, "Véhicule rangé.");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!canUse(p)) { Msg.err(p, "Ce véhicule appartient à une autre nation."); return InteractionResult.SUCCESS_SERVER; }
        if (getPassengers().size() >= kind.seats) { Msg.err(p, "Plus de place à bord."); return InteractionResult.SUCCESS_SERVER; }
        boolean driver = !isVehicle();
        p.startRiding(this);
        if (driver) Msg.send(p, "<gray>ZQSD : conduire • Clic gauche : tirer • Accroupi : descendre"
                + (kind == VehicleKind.HELICOPTER ? " • Espace/Ctrl : monter/descendre" : "")
                + (kind == VehicleKind.TRACTOR ? " • Espace : charrue on/off" : "")
                + (kind == VehicleKind.PLANE ? " • Z : gaz, la souris dirige" : ""));
        return InteractionResult.SUCCESS_SERVER;
    }

    private boolean canUse(ServerPlayer p) {
        FnServer s = srv();
        if (s == null || owner == null || p.getUUID().equals(owner) || p.hasPermissions(2)) return true;
        Nation mine = s.nations().of(p), theirs = s.nations().of(owner);
        return theirs == null || s.nations().allied(mine, theirs) || s.wars().activeBetween(mine, theirs);
    }

    // ------------------------------------------------------------ dégâts
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource src, float amount) {
        if (isRemoved() || isInvulnerableToBase(src)) return false;
        Entity att = src.getEntity();
        if (att != null && hasPassenger(att)) return false;
        float mult = src.getDirectEntity() instanceof Player ? 0.1f : 1f;
        damageFrom(att instanceof ServerPlayer sp ? sp : null, amount * mult);
        return true;
    }

    public void damageFrom(@Nullable ServerPlayer source, float amount) {
        if (isRemoved() || level().isClientSide) return;
        float h = health() - amount;
        entityData.set(HEALTH, Math.max(0, h));
        level().playSound(null, blockPosition(), SoundEvents.IRON_GOLEM_HURT, SoundSource.NEUTRAL, 0.6f, 1.3f);
        if (getControllingPassenger() instanceof ServerPlayer d) Msg.bar(d, "<red>Blindage : " + (int) Math.max(0, h) + "/" + (int) maxHealth());
        if (h <= 0) destroy(source);
    }

    public static void damageNearby(ServerLevel level, Vec3 pos, double radius, double amount, @Nullable ServerPlayer source) {
        for (VehicleEntity v : level.getEntitiesOfClass(VehicleEntity.class, new AABB(pos, pos).inflate(radius))) {
            double d = v.position().distanceTo(pos);
            if (d <= radius) v.damageFrom(source, (float) (amount * (1 - d / (radius + 1))));
        }
    }

    private void destroy(@Nullable ServerPlayer source) {
        ServerLevel level = (ServerLevel) level();
        for (Entity e : new ArrayList<>(getPassengers())) {
            e.stopRiding();
            if (e instanceof LivingEntity le) le.hurtServer(level, level.damageSources().explosion(this, source), 8f);
        }
        Vec3 c = position();
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 1, c.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y + 1, c.z, 40, 1, 1, 1, 0.05);
        level.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.NEUTRAL, 2f, 0.7f);
        spawnAtLocation(level, ModItems.stack("steel_plate", 2));
        FnServer s = srv();
        if (s != null && source != null && owner != null) {
            Nation a = s.nations().of(source), o = s.nations().of(owner);
            if (a != null && o != null) s.wars().reward(a, o, s.config().getInt("guerres.points-vehicule", 8), "véhicule ennemi détruit");
        }
        discard();
    }

    @Override
    public boolean isPickable() { return !isRemoved(); }

    @Override
    public boolean canBeCollidedWith() { return true; }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public ItemStack getPickResult() { return ModItems.stack(kind.id); }

    // ------------------------------------------------------------ armes (serveur)
    public static void onFireRequest(ServerPlayer p) {
        if (p.getVehicle() instanceof VehicleEntity v && v.getControllingPassenger() == p) v.fire(p);
    }

    public static void onToggleRequest(ServerPlayer p) {
        if (p.getVehicle() instanceof VehicleEntity v && v.getControllingPassenger() == p && v.kind == VehicleKind.TRACTOR) {
            v.entityData.set(PLOW, !v.plow());
            Msg.bar(p, v.plow() ? "<green>Charrue abaissée" : "<gray>Charrue relevée");
        }
    }

    private void fire(ServerPlayer p) {
        FnServer s = srv();
        if (s == null || kind.weapon == VehicleKind.Weapon.NONE) return;
        long now = System.currentTimeMillis();
        ServerLevel level = (ServerLevel) level();
        Vec3 look = p.getLookAngle();
        if (kind.weapon == VehicleKind.Weapon.CANNON) {
            long cd = s.config().getLong("vehicules-general.canon-cooldown-ms", 3000);
            if (now - lastShot < cd) { Msg.bar(p, "<red>Rechargement du canon..."); return; }
            if (!p.isCreative() && !Inv.take(p, "fnations:tank_shell", 1)) { Msg.bar(p, "<red>Aucun obus de char !"); return; }
            lastShot = now;
            Vec3 start = position().add(0, kind.height * 0.8, 0).add(look.scale(kind.width * 0.9));
            level.playSound(null, start.x, start.y, start.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2f, 0.5f);
            level.sendParticles(ParticleTypes.EXPLOSION, start.x, start.y, start.z, 1, 0, 0, 0, 0);
            float power = (float) s.config().getDouble("vehicules-general.canon-explosion", 3.5);
            ShellEntity shell = new ShellEntity(level, p, ModItems.stack("tank_shell"), (float) s.config().getDouble("vehicules-general.canon-degats", 120), power, true, 0.025);
            shell.setPos(start);
            shell.shoot(look.x, look.y, look.z, 3.0f, 0.2f);
            level.addFreshEntity(shell);
            return;
        }
        if (now - lastShot < 110) return;
        String ammo = s.config().getString("vehicules.mitrailleuse.munition", "ammo_762");
        if (!p.isCreative() && !Inv.take(p, "fnations:" + ammo, 1)) { Msg.bar(p, "<red>Plus de munitions !"); return; }
        lastShot = now;
        Vec3 muzzle = p.getEyePosition().add(look.scale(kind.width * 0.7));
        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.2f, 1.5f);
        s.weapons().hitscan(p, muzzle, look, s.config().getDouble("vehicules.mitrailleuse.degats", 6), s.config().getDouble("vehicules.mitrailleuse.portee", 120), 1.3);
    }

    // ------------------------------------------------------------ physique
    @Override
    public void tick() {
        super.tick();
        tickLerp();
        if (isControlledByLocalInstance()) {
            if (level().isClientSide && getControllingPassenger() instanceof Player) drive();
            else idle();
            move(MoverType.SELF, getDeltaMovement());
            if (onGround() && vy < 0) vy = 0;
        } else {
            setDeltaMovement(Vec3.ZERO);
        }
        if (level().isClientSide) clientTick();
        else serverTick((ServerLevel) level());
    }

    private static Vec3 forward(float yaw, float pitch) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        return new Vec3(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
    }

    private boolean hasFuel() {
        return fuel() > 0 || (getControllingPassenger() instanceof Player p && p.isCreative());
    }

    /** Sans conducteur : décélération et chute. */
    private void idle() {
        deltaYaw = 0;
        speed *= 0.8;
        if (Math.abs(speed) < 0.003) speed = 0;
        vy = onGround() ? 0 : Math.max(-1.5, vy - 0.06);
        setDeltaMovement(forward(getYRot(), 0).scale(speed).add(0, vy, 0));
    }

    private void drive() {
        boolean fuel = hasFuel();
        Player d = (Player) getControllingPassenger();
        deltaYaw = 0;
        Vec3 vel;
        switch (kind.mode) {
            case GROUND -> {
                if (input.forward() && fuel) speed = Math.min(kind.maxSpeed, speed + kind.accel);
                else if (input.backward() && fuel) speed = Math.max(-kind.maxSpeed * 0.4, speed - kind.accel);
                else speed *= 0.85;
                if (Math.abs(speed) < 0.003) speed = 0;
                float turn = kind.turn * (Math.abs(speed) > 0.01 || kind == VehicleKind.TANK ? 1 : 0);
                if (input.left()) deltaYaw -= turn;
                if (input.right()) deltaYaw += turn;
                setYRot(getYRot() + deltaYaw);
                vy = onGround() ? -0.04 : Math.max(-1.5, vy - 0.08);
                if (horizontalCollision) speed *= 0.6;
                vel = forward(getYRot(), 0).scale(speed).add(0, vy, 0);
            }
            case PLANE -> {
                boolean grounded = onGround();
                if (input.forward() && fuel) speed = Math.min(kind.maxSpeed, speed + kind.accel);
                else if (input.backward()) speed = Math.max(0, speed - kind.accel * 2);
                else speed = Math.max(0, speed - (grounded ? kind.accel : kind.accel * 0.15));
                if (!fuel) speed = Math.max(0, speed - kind.accel * 0.3);
                float dy = Mth.wrapDegrees(d.getYRot() - getYRot());
                setYRot(getYRot() + Mth.clamp(dy, -kind.turn, kind.turn));
                float pitch = getXRot() + Mth.clamp(d.getXRot() - getXRot(), -3f, 3f);
                boolean lift = speed >= kind.takeoffSpeed;
                if (grounded && (!lift && pitch < 0 || pitch > 0)) pitch = 0;
                setXRot(Mth.clamp(pitch, -60, 60));
                vel = forward(getYRot(), getXRot()).scale(speed);
                if (!lift && !grounded) vel = vel.add(0, -(kind.takeoffSpeed - speed) * 1.2 - 0.1, 0);
                if (grounded && !lift) vel = new Vec3(vel.x, -0.04, vel.z);
            }
            case HELICOPTER -> {
                double target = 0, side = 0, climb = 0;
                if (fuel) {
                    if (input.forward()) target = kind.maxSpeed;
                    if (input.backward()) target = -kind.maxSpeed * 0.5;
                    if (input.left()) side = -kind.maxSpeed * 0.5;
                    if (input.right()) side = kind.maxSpeed * 0.5;
                    if (input.jump()) climb = kind.climb;
                    if (input.sprint()) climb = -kind.climb;
                    float dy = Mth.wrapDegrees(d.getYRot() - getYRot());
                    setYRot(getYRot() + Mth.clamp(dy, -kind.turn, kind.turn));
                }
                speed += (target - speed) * 0.08;
                strafe += (side - strafe) * 0.1;
                if (fuel) vy += (climb - vy) * 0.2;
                else vy = onGround() ? 0 : Math.max(-1.2, vy - 0.04);
                vel = forward(getYRot(), 0).scale(speed).add(forward(getYRot() + 90, 0).scale(strafe)).add(0, vy, 0);
            }
            default -> vel = Vec3.ZERO;
        }
        setDeltaMovement(vel);
    }

    private void clientTick() {
        rotorO = rotor;
        boolean engine = isVehicle() && fuel() > 0;
        rotor += engine ? 35f : (!onGround() ? 10f : 0f);
        if (engine && level().random.nextInt(4) == 0) {
            Vec3 back = position().add(forward(getYRot(), 0).scale(-kind.width / 2)).add(0, kind.height * 0.5, 0);
            level().addParticle(ParticleTypes.SMOKE, back.x, back.y, back.z, 0, 0.02, 0);
        }
    }

    private void serverTick(ServerLevel level) {
        Vec3 pos = position();
        if (lastPos == null) lastPos = pos;
        double moved = pos.subtract(lastPos).horizontalDistance();
        double dyMove = pos.y - lastPos.y;
        lastPos = pos;
        if (getControllingPassenger() instanceof ServerPlayer d) {
            FnServer s = srv();
            double per = s == null ? kind.fuelPerTick : s.config().getDouble("vehicules." + kind.id + ".consommation", kind.fuelPerTick);
            double factor = kind.flies() ? 0.5 + moved / kind.maxSpeed : moved / kind.maxSpeed;
            if (!d.isCreative() && factor > 0.001) entityData.set(FUEL, (float) Math.max(0, fuel() - per * factor));
            // crashs
            if (kind == VehicleKind.PLANE && lastSpeed > kind.takeoffSpeed * 0.8 && moved < lastSpeed * 0.3 && horizontalCollision) crash(d);
            if (kind.flies() && lastVy < -0.8 && onGround()) crash(d);
            if (kind == VehicleKind.TRACTOR && moved > 0.01 && tickCount % 4 == 0) farm(level, d);
            if (tickCount % 5 == 0) hud(d, moved);
        }
        lastSpeed = moved;
        lastVy = dyMove;
        if (getY() < level.getMinY() - 64) discard();
    }

    private void crash(ServerPlayer d) {
        Msg.err(d, "CRASH !");
        damageFrom(null, (float) (maxHealth() * 0.6));
    }

    private void hud(ServerPlayer d, double moved) {
        double kmh = moved * 20 * 3.6;
        String alt = kind.mode == VehicleKind.Mode.GROUND ? "" : " <dark_gray>| <gray>Alt. <white>" + (int) getY();
        String extra = kind == VehicleKind.TRACTOR ? " <dark_gray>| " + (plow() ? "<green>Charrue" : "<gray>Charrue off") : "";
        Msg.bar(d, "<gray>" + (int) kmh + " km/h" + alt + " <dark_gray>| <yellow>⛽ " + Txt.progress(fuel() / maxFuel(), 10, "yellow", "dark_gray")
                + " <dark_gray>| <red>❤ " + (int) health() + "/" + (int) maxHealth() + extra);
    }

    private void farm(ServerLevel level, ServerPlayer d) {
        FnServer s = srv();
        BlockPos center = blockPosition();
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            BlockPos b = center.offset(x, 0, z);
            if (s != null && !s.territory().canBuild(d, level, b)) continue;
            BlockState st = level.getBlockState(b);
            if (st.getBlock() instanceof CropBlock crop && crop.isMaxAge(st)) {
                for (ItemStack drop : net.minecraft.world.level.block.Block.getDrops(st, level, b, null)) net.minecraft.world.level.block.Block.popResource(level, b, drop);
                level.setBlock(b, crop.getStateForAge(0), 3);
            } else if (plow() && st.isAir()) {
                BlockState below = level.getBlockState(b.below());
                if (below.is(Blocks.GRASS_BLOCK) || below.is(Blocks.DIRT)) level.setBlock(b.below(), Blocks.FARMLAND.defaultBlockState(), 3);
            }
        }
    }

    // ------------------------------------------------------------ interpolation (comme les bateaux)
    private void tickLerp() {
        if (isControlledByLocalInstance()) {
            lerpSteps = 0;
            syncPacketPositionCodec(getX(), getY(), getZ());
        }
        if (lerpSteps > 0) {
            lerpPositionAndRotationStep(lerpSteps, lerpX, lerpY, lerpZ, lerpYRot, lerpXRot);
            lerpSteps--;
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpYRot = yRot;
        lerpXRot = xRot;
        lerpSteps = steps + 2;
    }

    @Override
    public void cancelLerp() { lerpSteps = 0; }

    @Override
    public double lerpTargetX() { return lerpSteps > 0 ? lerpX : getX(); }

    @Override
    public double lerpTargetY() { return lerpSteps > 0 ? lerpY : getY(); }

    @Override
    public double lerpTargetZ() { return lerpSteps > 0 ? lerpZ : getZ(); }

    @Override
    public float lerpTargetXRot() { return lerpSteps > 0 ? lerpXRot : getXRot(); }

    @Override
    public float lerpTargetYRot() { return lerpSteps > 0 ? lerpYRot : getYRot(); }

    // ------------------------------------------------------------ sauvegarde
    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (owner != null) tag.putUUID("owner", owner);
        tag.putFloat("fuel", fuel());
        tag.putFloat("health", health());
        tag.putBoolean("plow", plow());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("owner")) owner = tag.getUUID("owner");
        entityData.set(FUEL, tag.getFloat("fuel"));
        if (tag.contains("health")) entityData.set(HEALTH, tag.getFloat("health"));
        entityData.set(PLOW, tag.getBoolean("plow"));
    }

    @SuppressWarnings("unused")
    private static boolean isAir(Items i) { return false; }
}

package fr.fnations.missile;

import fr.fnations.registry.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Représentation visuelle d'un missile en vol (déplacé par le MissileManager). */
public class MissileEntity extends Entity {
    private static final EntityDataAccessor<ItemStack> ITEM = SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.ITEM_STACK);
    private int lerpSteps;
    private double lx, ly, lz;
    private float lyr, lxr;

    public MissileEntity(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) { b.define(ITEM, ItemStack.EMPTY); }

    public void setItem(ItemStack s) { entityData.set(ITEM, s.copyWithCount(1)); }

    public ItemStack getItem() {
        ItemStack s = entityData.get(ITEM);
        return s.isEmpty() ? ModItems.stack("missile_tactical") : s;
    }

    @Override
    public void tick() {
        super.tick();
        if (lerpSteps > 0) {
            lerpPositionAndRotationStep(lerpSteps, lx, ly, lz, lyr, lxr);
            lerpSteps--;
        }
        if (level().isClientSide) {
            level().addParticle(ParticleTypes.FLAME, getX(), getY(), getZ(), 0, 0, 0);
            level().addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY(), getZ(), 0, 0.01, 0);
        } else if (tickCount > 20 * 60 * 10) discard();
    }

    @Override
    public void lerpTo(double x, double y, double z, float yr, float xr, int steps) {
        lx = x; ly = y; lz = z; lyr = yr; lxr = xr;
        lerpSteps = steps;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) { return d < 512 * 512; }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource src, float f) { return false; }

    @Override
    public boolean shouldBeSaved() { return false; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}

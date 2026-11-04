package fr.fnations.weapon;

import fr.fnations.nation.Nation;
import fr.fnations.registry.ModEntities;
import fr.fnations.registry.ModItems;
import fr.fnations.server.FnServer;
import fr.fnations.util.Explosions;
import fr.fnations.vehicle.VehicleEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** Roquette de RPG ou obus de char : projectile explosif rendu avec son modèle 3D. */
public class ShellEntity extends ThrowableItemProjectile {
    private float damage = 16, power = 3;
    private boolean breakBlocks = true;
    private double gravity = 0.01;
    private int life = 200;

    public ShellEntity(EntityType<? extends ShellEntity> type, Level level) { super(type, level); }

    public ShellEntity(Level level, LivingEntity owner, ItemStack model, float damage, float power, boolean breakBlocks, double gravity) {
        super(ModEntities.SHELL, owner, level, model);
        this.damage = damage;
        this.power = power;
        this.breakBlocks = breakBlocks;
        this.gravity = gravity;
    }

    @Override
    protected Item getDefaultItem() { return ModItems.get("rpg_rocket"); }

    @Override
    protected double getDefaultGravity() { return gravity; }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            level().addParticle(ParticleTypes.FLAME, getX(), getY(), getZ(), 0, 0, 0);
            level().addParticle(ParticleTypes.SMOKE, getX(), getY(), getZ(), 0, 0.02, 0);
        } else if (--life <= 0) discard();
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (!(level() instanceof ServerLevel sl)) return;
        Entity owner = getOwner();
        if (hit instanceof EntityHitResult eh) {
            Entity e = eh.getEntity();
            if (e instanceof VehicleEntity v) v.damageFrom(owner instanceof ServerPlayer sp ? sp : null, damage);
            else e.hurtServer(sl, damageSources().thrown(this, owner), damage);
        }
        FnServer s = FnServer.get();
        Nation n = s != null && owner instanceof ServerPlayer sp ? s.nations().of(sp) : null;
        Explosions.explode(sl, getX(), getY(), getZ(), power, false, breakBlocks, n, this);
        VehicleEntity.damageNearby(sl, position(), power + 1.5, damage * 0.5, owner instanceof ServerPlayer sp ? sp : null);
        discard();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("damage", damage);
        tag.putFloat("power", power);
        tag.putBoolean("break", breakBlocks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = tag.getFloat("damage");
        power = tag.getFloat("power");
        breakBlocks = tag.getBoolean("break");
    }
}

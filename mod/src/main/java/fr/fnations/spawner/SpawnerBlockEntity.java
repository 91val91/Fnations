package fr.fnations.spawner;

import fr.fnations.registry.ModBlockEntities;
import fr.fnations.registry.ModComponents;
import fr.fnations.server.FnServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.UUID;

/** Spawner FNations : type de mob, niveau, propriétaire et compte à rebours. */
public class SpawnerBlockEntity extends BlockEntity {
    private String mob = "zombie";
    private String entity = "minecraft:zombie";
    private int level = 1;
    private UUID owner;
    private String ownerName = "";
    private int delay = 200;
    private long spawned;
    // rendu client : rotation du mob
    public double spin, oSpin;

    public SpawnerBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.SPAWNER, pos, state); }

    public String mob() { return mob; }
    /** Id de l'entité affichée dans la cage (connu du client). */
    public String entity() { return entity; }
    public int level() { return level; }
    public UUID owner() { return owner; }
    public String ownerName() { return ownerName; }
    public long spawned() { return spawned; }
    public int delay() { return delay; }

    public void setOwner(UUID u, String name) {
        owner = u;
        ownerName = name == null ? "" : name;
        setChanged();
    }

    public void setLevel(int l) {
        level = Math.max(1, Math.min(5, l));
        sync();
    }

    public void sync() {
        setChanged();
        if (getLevel() != null) getLevel().sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SpawnerBlockEntity be) {
        if (!(level instanceof ServerLevel sl)) return;
        FnServer s = FnServer.get();
        if (s == null) return;
        if (--be.delay > 0) return;
        SpawnerManager sm = s.spawners();
        SpawnerManager.Level info = sm.info(be.level);
        RandomSource r = sl.getRandom();
        be.delay = (int) (info.delaySeconds() * 20 * (0.8 + r.nextDouble() * 0.4));
        if (!sl.hasNearbyAlivePlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, sm.activationRange())) return;
        EntityType<?> type = sm.entity(be.mob);
        if (type == null) return;
        String eid = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
        if (!eid.equals(be.entity)) { be.entity = eid; be.sync(); }
        int nearby = sl.getEntities(type, new AABB(pos).inflate(8), e -> true).size();
        int spawnedNow = 0;
        for (int i = 0; i < info.count() && nearby + spawnedNow < info.maxNearby(); i++) {
            for (int attempt = 0; attempt < 6; attempt++) {
                double x = pos.getX() + 0.5 + (r.nextDouble() - r.nextDouble()) * 4;
                double y = pos.getY() + r.nextInt(3) - 1;
                double z = pos.getZ() + 0.5 + (r.nextDouble() - r.nextDouble()) * 4;
                BlockPos at = BlockPos.containing(x, y, z);
                if (!sl.noCollision(type.getSpawnAABB(x, y, z))) continue;
                if (!sl.getBlockState(at.below()).isSolid() && type != EntityType.BLAZE && type != EntityType.GHAST) continue;
                Entity e = type.create(sl, EntitySpawnReason.SPAWNER);
                if (e == null) break;
                e.moveTo(x, y, z, r.nextFloat() * 360f, 0f);
                if (e instanceof Mob mob) mob.finalizeSpawn(sl, sl.getCurrentDifficultyAt(at), EntitySpawnReason.SPAWNER, null);
                e.addTag("fn_spawner");
                sl.addFreshEntity(e);
                sl.sendParticles(ParticleTypes.FLAME, x, y + 0.5, z, 8, 0.3, 0.4, 0.3, 0.01);
                spawnedNow++;
                break;
            }
        }
        if (spawnedNow > 0) {
            be.spawned += spawnedNow;
            sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 12, 0.25, 0.25, 0.25, 0.02);
            be.setChanged();
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, SpawnerBlockEntity be) {
        be.oSpin = be.spin;
        be.spin = (be.spin + 1000.0F / (be.level * 40 + 180)) % 360.0;
        if (level.random.nextInt(4) == 0)
            level.addParticle(ParticleTypes.SMOKE, pos.getX() + level.random.nextDouble(), pos.getY() + level.random.nextDouble(), pos.getZ() + level.random.nextDouble(), 0, 0, 0);
        if (level.random.nextInt(6) == 0)
            level.addParticle(ParticleTypes.FLAME, pos.getX() + 0.2 + level.random.nextDouble() * 0.6, pos.getY() + 0.2, pos.getZ() + 0.2 + level.random.nextDouble() * 0.6, 0, 0.01, 0);
    }

    // ------------------------------------------------------------ données
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        tag.putString("Mob", mob);
        tag.putString("Entite", entity);
        tag.putInt("Niveau", level);
        if (owner != null) tag.putUUID("Proprietaire", owner);
        tag.putString("NomProprietaire", ownerName);
        tag.putInt("Delai", delay);
        tag.putLong("Apparitions", spawned);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        if (tag.contains("Mob")) mob = tag.getString("Mob");
        if (tag.contains("Entite")) entity = tag.getString("Entite");
        if (tag.contains("Niveau")) level = Math.max(1, Math.min(5, tag.getInt("Niveau")));
        owner = tag.hasUUID("Proprietaire") ? tag.getUUID("Proprietaire") : null;
        ownerName = tag.getString("NomProprietaire");
        if (tag.contains("Delai")) delay = tag.getInt("Delai");
        spawned = tag.getLong("Apparitions");
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput in) {
        super.applyImplicitComponents(in);
        ModComponents.SpawnerData d = in.get(ModComponents.SPAWNER);
        if (d != null) {
            mob = d.mob();
            entity = d.entityId();
            level = Math.max(1, Math.min(5, d.level()));
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder b) {
        super.collectImplicitComponents(b);
        b.set(ModComponents.SPAWNER, new ModComponents.SpawnerData(mob, entity, level));
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        tag.remove("Mob");
        tag.remove("Entite");
        tag.remove("Niveau");
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider reg) {
        CompoundTag t = new CompoundTag();
        t.putString("Mob", mob);
        t.putString("Entite", entity);
        t.putInt("Niveau", level);
        return t;
    }
}

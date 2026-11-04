package fr.fnations.registry;

import fr.fnations.FNations;
import fr.fnations.missile.MissileEntity;
import fr.fnations.rocket.RocketEntity;
import fr.fnations.vehicle.VehicleEntity;
import fr.fnations.vehicle.VehicleKind;
import fr.fnations.weapon.ShellEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import java.util.EnumMap;
import java.util.Map;

/** Entités : véhicules, obus/roquettes, missiles, fusée. */
public final class ModEntities {
    public static final Map<VehicleKind, EntityType<VehicleEntity>> VEHICLES = new EnumMap<>(VehicleKind.class);
    public static EntityType<ShellEntity> SHELL;
    public static EntityType<MissileEntity> MISSILE;
    public static EntityType<RocketEntity> ROCKET;

    private ModEntities() {}

    public static void init() {
        for (VehicleKind k : VehicleKind.values()) {
            VEHICLES.put(k, register(k.id, EntityType.Builder.<VehicleEntity>of((t, l) -> new VehicleEntity(t, l, k), MobCategory.MISC)
                    .sized(k.width, k.height).clientTrackingRange(10).updateInterval(2)));
        }
        SHELL = register("shell", EntityType.Builder.<ShellEntity>of(ShellEntity::new, MobCategory.MISC).sized(0.4f, 0.4f).clientTrackingRange(8).updateInterval(1));
        MISSILE = register("missile", EntityType.Builder.<MissileEntity>of(MissileEntity::new, MobCategory.MISC).sized(0.8f, 0.8f).clientTrackingRange(16).updateInterval(1));
        ROCKET = register("rocket", EntityType.Builder.<RocketEntity>of(RocketEntity::new, MobCategory.MISC).sized(2.2f, 8.5f).clientTrackingRange(16).updateInterval(1));
    }

    private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(String id, EntityType.Builder<T> b) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, FNations.id(id));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, b.build(key));
    }
}

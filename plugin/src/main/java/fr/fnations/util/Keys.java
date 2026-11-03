package fr.fnations.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** Clés PersistentDataContainer du plugin. */
public final class Keys {
    public static NamespacedKey ITEM, AMMO, NOTE_VALUE, VEHICLE, MACHINE, ROCKET, FUEL, UID;

    private Keys() {}

    public static void init(Plugin plugin) {
        ITEM = new NamespacedKey("fnations", "item");
        AMMO = new NamespacedKey("fnations", "ammo");
        NOTE_VALUE = new NamespacedKey("fnations", "note_value");
        VEHICLE = new NamespacedKey("fnations", "vehicle");
        MACHINE = new NamespacedKey("fnations", "machine");
        ROCKET = new NamespacedKey("fnations", "rocket");
        FUEL = new NamespacedKey("fnations", "fuel");
        UID = new NamespacedKey("fnations", "uid");
    }
}

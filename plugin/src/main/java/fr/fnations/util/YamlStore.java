package fr.fnations.util;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/** Fichier YAML de données dans le dossier du plugin. */
public final class YamlStore {
    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration yaml;

    public YamlStore(JavaPlugin plugin, String name) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), name);
        reload();
    }

    public void reload() { yaml = YamlConfiguration.loadConfiguration(file); }

    public YamlConfiguration get() { return yaml; }

    /** Repart d'un fichier vide (avant de réécrire toutes les données). */
    public YamlConfiguration fresh() {
        yaml = new YamlConfiguration();
        return yaml;
    }

    public void save() {
        try {
            file.getParentFile().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Impossible de sauvegarder " + file.getName(), e);
        }
    }
}

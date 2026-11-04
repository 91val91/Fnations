package fr.fnations.util;

import fr.fnations.FNations;

import java.nio.file.Path;

/** Fichier de données YAML dans le dossier du monde (world/fnations/). */
public final class Store {
    private final Path file;
    private Yaml yaml;

    public Store(Path folder, String name) {
        this.file = folder.resolve(name);
        this.yaml = Yaml.load(file);
    }

    public Yaml get() { return yaml; }

    public Yaml fresh() {
        yaml = new Yaml();
        return yaml;
    }

    public void save() {
        try {
            yaml.save(file);
        } catch (Exception e) {
            FNations.LOG.error("Impossible de sauvegarder {}", file, e);
        }
    }
}

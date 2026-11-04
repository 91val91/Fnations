package fr.fnations.util;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Section YAML (sous-ensemble de l'API ConfigurationSection de Bukkit) basée sur SnakeYAML. */
public final class Yaml {
    private final Map<String, Object> map;

    public Yaml() { this(new LinkedHashMap<>()); }

    public Yaml(Map<String, Object> map) { this.map = map; }

    private static org.yaml.snakeyaml.Yaml snake() {
        DumperOptions d = new DumperOptions();
        d.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        d.setIndent(2);
        d.setAllowUnicode(true);
        d.setWidth(200);
        LoaderOptions lo = new LoaderOptions();
        lo.setCodePointLimit(64 * 1024 * 1024);
        return new org.yaml.snakeyaml.Yaml(lo, d);
    }

    @SuppressWarnings("unchecked")
    public static Yaml load(Path file) {
        if (!Files.exists(file)) return new Yaml();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Object o = snake().load(r);
            return o instanceof Map ? new Yaml((Map<String, Object>) o) : new Yaml();
        } catch (Exception e) {
            throw new IllegalStateException("YAML invalide : " + file + " — " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public static Yaml load(InputStream in) {
        Object o = snake().load(in);
        return o instanceof Map ? new Yaml((Map<String, Object>) o) : new Yaml();
    }

    public void save(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            snake().dump(map, w);
        }
        Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
    }

    public Map<String, Object> raw() { return map; }

    @SuppressWarnings("unchecked")
    public Object get(String path) {
        Object cur = map;
        for (String part : path.split("\\.")) {
            if (!(cur instanceof Map)) return null;
            cur = ((Map<String, Object>) cur).get(part);
            if (cur == null) return null;
        }
        return cur;
    }

    public boolean contains(String path) { return get(path) != null; }

    public String getString(String path) { return getString(path, null); }

    public String getString(String path, String def) {
        Object o = get(path);
        return o == null ? def : String.valueOf(o);
    }

    public int getInt(String path) { return getInt(path, 0); }

    public int getInt(String path, int def) {
        Object o = get(path);
        if (o instanceof Number n) return n.intValue();
        try { return o == null ? def : Integer.parseInt(o.toString()); } catch (NumberFormatException e) { return def; }
    }

    public long getLong(String path) { return getLong(path, 0); }

    public long getLong(String path, long def) {
        Object o = get(path);
        if (o instanceof Number n) return n.longValue();
        try { return o == null ? def : Long.parseLong(o.toString()); } catch (NumberFormatException e) { return def; }
    }

    public double getDouble(String path) { return getDouble(path, 0); }

    public double getDouble(String path, double def) {
        Object o = get(path);
        if (o instanceof Number n) return n.doubleValue();
        try { return o == null ? def : Double.parseDouble(o.toString()); } catch (NumberFormatException e) { return def; }
    }

    public boolean getBoolean(String path) { return getBoolean(path, false); }

    public boolean getBoolean(String path, boolean def) {
        Object o = get(path);
        if (o instanceof Boolean b) return b;
        return o == null ? def : Boolean.parseBoolean(o.toString());
    }

    public List<String> getStringList(String path) {
        Object o = get(path);
        List<String> out = new ArrayList<>();
        if (o instanceof List<?> l) for (Object x : l) out.add(String.valueOf(x));
        return out;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getMapList(String path) {
        Object o = get(path);
        List<Map<String, Object>> out = new ArrayList<>();
        if (o instanceof List<?> l) for (Object x : l) if (x instanceof Map) out.add((Map<String, Object>) x);
        return out;
    }

    @SuppressWarnings("unchecked")
    public Yaml getSection(String path) {
        Object o = get(path);
        return o instanceof Map ? new Yaml((Map<String, Object>) o) : null;
    }

    public Set<String> getKeys() { return new LinkedHashSet<>(map.keySet()); }

    @SuppressWarnings("unchecked")
    public void set(String path, Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> cur = map;
        for (int i = 0; i < parts.length - 1; i++) {
            Object next = cur.get(parts[i]);
            if (!(next instanceof Map)) {
                next = new LinkedHashMap<String, Object>();
                cur.put(parts[i], next);
            }
            cur = (Map<String, Object>) next;
        }
        if (value == null) cur.remove(parts[parts.length - 1]);
        else cur.put(parts[parts.length - 1], value);
    }
}

package fr.fnations.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** État d'interface d'un joueur : page affichée, sélections, dialogue ouvert et notifications. */
public final class UiSession {
    public String page;
    private final Map<String, String> state = new HashMap<>();
    public UiNode dialog;
    public final List<String> toasts = new ArrayList<>();
    boolean dirty;
    long windowStart;
    int actions;

    /** Les clés d'état sont propres à chaque page (préfixe "page."). */
    private String key(String k) { return (page == null ? "" : page) + "." + k; }

    public String get(String k) { return state.get(key(k)); }

    public String get(String k, String def) { return state.getOrDefault(key(k), def); }

    public int getInt(String k, int def) {
        try { return Integer.parseInt(state.getOrDefault(key(k), String.valueOf(def))); } catch (NumberFormatException e) { return def; }
    }

    public void set(String k, Object v) {
        if (v == null) state.remove(key(k));
        else state.put(key(k), String.valueOf(v));
    }

    public void toast(String mini) {
        toasts.add(mini);
        while (toasts.size() > 6) toasts.removeFirst();
    }
}

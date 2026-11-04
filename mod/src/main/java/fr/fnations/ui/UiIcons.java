package fr.fnations.ui;

import java.util.List;

/**
 * Icônes de l'interface (textures/gui/ui_icons.png, cases de 16 × 16, 16 par ligne).
 * L'ordre est lu par tools/generate_resourcepack.py : ajoutez les nouvelles icônes à la fin.
 */
public final class UiIcons {
    private UiIcons() {}

    public static final List<String> NAMES = List.of(
            "home", "flag", "bank", "briefcase", "pickaxe", "shop", "flask", "swords", "bed", "trophy",
            "gear", "close", "search", "plus", "minus", "chevron_down", "chevron_up", "chevron_right", "chevron_left", "check",
            "lock", "star", "coin", "clock", "teleport", "users", "map", "crown", "shield", "info",
            "hammer", "wheat", "sword", "axe", "cog", "chart", "mail", "tag", "factory", "refresh",
            "trash", "edit", "heart", "globe", "cart", "box", "medal", "user", "bell", "arrow_up",
            "arrow_down", "handshake", "send", "dot");

    public static int index(String name) { return NAMES.indexOf(name); }
}

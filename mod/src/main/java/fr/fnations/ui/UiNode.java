package fr.fnations.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import fr.fnations.server.FnServer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Élément d'interface décrit par le serveur et dessiné par le client (voir FnUiScreen).
 * Types : col, row, text, btn, icon, item, head, bar, input, check, chip, list, grid, space, sep, map, inv, player, terrain.
 * Les tailles sont en pixels du canevas virtuel (560 × 315).
 */
public final class UiNode {
    private final JsonObject j = new JsonObject();
    private JsonArray kids;

    private UiNode(String type) { j.addProperty("t", type); }

    // ------------------------------------------------------------ conteneurs
    public static UiNode col(UiNode... c) { return new UiNode("col").add(c); }

    public static UiNode row(UiNode... c) { return new UiNode("row").add(c); }

    /** Liste défilante (id = mémorise le défilement côté client). */
    public static UiNode list(String id) { return new UiNode("list").str("id", id); }

    public static UiNode grid(int cols) { return new UiNode("grid").num("cols", cols); }

    /** Carte : colonne avec fond, coins arrondis et marge intérieure. */
    public static UiNode card(UiNode... c) { return col(c).bg(UiTheme.PANEL).r(6).pad(8).gap(6); }

    // ------------------------------------------------------------ feuilles
    public static UiNode text(String mini) { return new UiNode("text").str("s", mini); }

    public static UiNode btn(String label, String action) { return new UiNode("btn").str("label", label).act(action); }

    public static UiNode icon(String name) { return new UiNode("icon").str("i", name); }

    public static UiNode item(ItemStack stack) {
        UiNode n = new UiNode("item");
        if (stack == null || stack.isEmpty()) return n.str("id", "minecraft:air");
        n.num("n", stack.getCount());
        if (stack.getComponentsPatch().isEmpty()) return n.str("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        FnServer s = FnServer.get();
        if (s == null) return n.str("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        ItemStack one = stack.copyWithCount(1);
        JsonElement enc = ItemStack.CODEC.encodeStart(s.server().registryAccess().createSerializationContext(JsonOps.INSTANCE), one)
                .result().orElse(null);
        if (enc == null) return n.str("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        n.j.add("st", enc);
        return n;
    }

    public static UiNode item(String id) { return new UiNode("item").str("id", id); }

    /** Nom de l'objet, traduit par le client (nom personnalisé conservé). */
    public static UiNode itemName(ItemStack stack) {
        UiNode n = item(stack);
        n.j.addProperty("t", "iname");
        return n;
    }

    public UiNode bold() { return flag("b"); }

    public static UiNode glint(UiNode item) { return item.flag("glint"); }

    public static UiNode head(UUID uuid, String name) {
        return new UiNode("head").str("uuid", uuid == null ? "" : uuid.toString()).str("name", name == null ? "?" : name);
    }

    public static UiNode bar(double v) { return new UiNode("bar").num("v", Math.max(0, Math.min(1, v))); }

    /** Champ de saisie : sa valeur est envoyée avec les actions qui le citent (voir {@link #in(String...)}). */
    public static UiNode input(String id) { return new UiNode("input").str("id", id); }

    public static UiNode check(String id, String label, boolean value) {
        return new UiNode("check").str("id", id).str("label", label).flag("v", value);
    }

    public static UiNode chip(String label) { return new UiNode("chip").str("label", label); }

    public static UiNode space(int w, int h) { return new UiNode("space").w(w).h(h); }

    public static UiNode flexSpace() { return new UiNode("space").flex(1); }

    public static UiNode sep() { return new UiNode("sep"); }

    /** Inventaire du joueur dessiné côté client ; clic sur une case = action + ":" + index. */
    public static UiNode inv(String action, int cols) { return new UiNode("inv").act(action).num("cols", cols); }

    /** Modèle 3D du joueur local (suit la souris). */
    public static UiNode player() { return new UiNode("player"); }

    /** Carte du relief autour du joueur, générée côté client. */
    public static UiNode terrain(int radius) { return new UiNode("terrain").num("radius", radius); }

    /**
     * Carte de chunks : rows = une chaîne par ligne (un caractère par chunk), palette char → couleur,
     * names char → légende. Clic = action + ":" + cx + "," + cz.
     */
    public static UiNode map(int centerX, int centerZ, List<String> rows, JsonObject palette, JsonObject names, String action) {
        UiNode n = new UiNode("map").num("cx", centerX).num("cz", centerZ);
        JsonArray a = new JsonArray();
        rows.forEach(a::add);
        n.j.add("rows", a);
        n.j.add("pal", palette);
        n.j.add("names", names);
        if (action != null) n.act(action);
        return n;
    }

    // ------------------------------------------------------------ enfants
    public UiNode add(UiNode... c) {
        for (UiNode n : c) if (n != null) kidsArray().add(n.j);
        return this;
    }

    public UiNode add(Collection<UiNode> c) {
        for (UiNode n : c) if (n != null) kidsArray().add(n.j);
        return this;
    }

    public boolean isEmpty() { return kids == null || kids.isEmpty(); }

    private JsonArray kidsArray() {
        if (kids == null) {
            kids = new JsonArray();
            j.add("c", kids);
        }
        return kids;
    }

    // ------------------------------------------------------------ attributs
    public UiNode w(int v) { return num("w", v); }
    public UiNode h(int v) { return num("h", v); }
    public UiNode flex(double v) { return num("flex", v); }
    public UiNode gap(int v) { return num("gap", v); }
    public UiNode pad(int v) { return num("pad", v); }
    public UiNode pad(int x, int y) { return num("padx", x).num("pady", y); }
    public UiNode bg(String color) { return str("bg", color); }
    public UiNode border(String color) { return str("bd", color); }
    public UiNode r(int radius) { return num("r", radius); }
    public UiNode color(String c) { return str("color", c); }
    public UiNode scale(double s) { return num("scale", s); }
    public UiNode wrap() { return flag("wrap"); }
    public UiNode lines(int max) { return num("lines", max); }
    public UiNode center() { return str("align", "center"); }
    public UiNode right() { return str("align", "right"); }
    /** Alignement vertical des enfants d'une ligne : top, center (défaut), bottom. */
    public UiNode valign(String v) { return str("valign", v); }
    public UiNode act(String action) { return str("a", action); }
    public UiNode in(String... inputIds) {
        JsonArray a = new JsonArray();
        for (String s : inputIds) a.add(s);
        j.add("in", a);
        return this;
    }
    public UiNode tip(String... lines) { return tip(List.of(lines)); }
    public UiNode tip(List<String> lines) {
        JsonArray a = new JsonArray();
        lines.forEach(a::add);
        j.add("tip", a);
        return this;
    }
    /** Style de bouton : primary, secondary, danger, success, ghost, tab. */
    public UiNode style(String s) { return str("style", s); }
    public UiNode primary() { return style("primary"); }
    public UiNode danger() { return style("danger"); }
    public UiNode success() { return style("success"); }
    public UiNode ghost() { return style("ghost"); }
    public UiNode selected(boolean b) { return b ? flag("sel") : this; }
    public UiNode disabled(boolean b) { return b ? flag("off") : this; }
    public UiNode withIcon(String i) { return str("i", i); }
    public UiNode size(int s) { return num("size", s); }
    /** Valeur initiale d'un champ (force = remplace la saisie en cours). */
    public UiNode value(String v) { return str("v", v); }
    public UiNode force() { return flag("force"); }
    public UiNode placeholder(String p) { return str("ph", p); }
    public UiNode numeric() { return flag("num"); }
    public UiNode maxLen(int m) { return num("max", m); }
    /** Filtre côté client des lignes d'une liste selon un champ de recherche. */
    public UiNode filter(String inputId) { return str("filter", inputId); }
    /** Mots-clés de recherche d'une ligne. */
    public UiNode keys(String k) { return str("k", k); }
    /** Survol mis en évidence même sans action. */
    public UiNode hover() { return flag("hov"); }
    public UiNode count(int n) { return num("n", n); }

    public UiNode str(String k, String v) {
        if (v != null) j.addProperty(k, v);
        return this;
    }

    public UiNode num(String k, Number v) {
        j.addProperty(k, v);
        return this;
    }

    public UiNode flag(String k) { return flag(k, true); }

    public UiNode flag(String k, boolean v) {
        j.addProperty(k, v);
        return this;
    }

    public JsonObject json() { return j; }
}

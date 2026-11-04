package fr.fnations.registry;

/** Catégories d'items (affichées dans la lore et la Station de Craft). */
public enum Category {
    COMPOSANT("Composants"), MUNITION("Munitions"), ARME("Armes"), VEHICULE("Véhicules"),
    MACHINE("Machines"), MISSILE("Missiles"), FUSEE("Spatial"), ECONOMIE("Économie");

    public final String display;

    Category(String display) { this.display = display; }

    public static Category byName(String s) {
        for (Category c : values()) if (c.name().equalsIgnoreCase(s)) return c;
        return COMPOSANT;
    }
}

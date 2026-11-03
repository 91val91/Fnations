package fr.fnations.research;

import org.bukkit.Material;

import java.util.List;

/** Une technologie de l'arbre de recherche (research.yml). */
public record Technology(String id, String name, String category, Material icon, double cost, long seconds,
                         List<String> requires, List<String> unlocks, int bonusClaims, List<String> description) {}

package fr.fnations.craft;

import java.util.Map;

/** Recette de la Station de Craft. ingredients : clé -> quantité. */
public record StationRecipe(String id, String result, int amount, String category, Map<String, Integer> ingredients) {}

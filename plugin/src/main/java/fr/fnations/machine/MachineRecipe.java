package fr.fnations.machine;

import java.util.List;
import java.util.Map;

/** Recette de machine (compresseur, four électrique, concasseur). */
public record MachineRecipe(String input, int amount, List<Map.Entry<String, Integer>> outputs, int seconds) {}

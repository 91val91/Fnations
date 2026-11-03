package fr.fnations.item;

import org.bukkit.Material;

import java.util.List;

/** Définition d'un item custom (items.yml). */
public record ItemDef(String id, String name, Material material, String model, String category, List<String> lore, int stack) {}

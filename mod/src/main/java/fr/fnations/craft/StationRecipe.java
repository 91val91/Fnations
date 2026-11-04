package fr.fnations.craft;

import fr.fnations.gui.ConfirmPanel;
import fr.fnations.gui.Icons;
import fr.fnations.gui.PagedPanel;
import fr.fnations.gui.Panel;
import fr.fnations.gui.Prompt;
import fr.fnations.server.FnServer;
import fr.fnations.server.Msg;
import fr.fnations.util.Txt;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import java.util.Map;

/** Recette de la Station de Craft. ingredients : clé -> quantité. */
public record StationRecipe(String id, String result, int amount, String category, Map<String, Integer> ingredients) {}

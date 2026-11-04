package fr.fnations.research;

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


import java.util.List;

/** Une technologie de l'arbre de recherche (research.yml). */
public record Technology(String id, String name, String category, net.minecraft.world.item.Item icon, double cost, long seconds,
                         List<String> requires, List<String> unlocks, int bonusClaims, List<String> description) {}

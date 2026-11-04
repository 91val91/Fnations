package fr.fnations.nation;

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

public enum Role {
    LEADER("<gold>Chef d'État", 3), OFFICER("<yellow>Ministre", 2), MEMBER("<gray>Citoyen", 1);

    public final String display;
    public final int power;

    Role(String display, int power) {
        this.display = display;
        this.power = power;
    }

    public boolean atLeast(Role r) { return power >= r.power; }
}

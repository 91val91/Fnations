package fr.fnations.client;

import fr.fnations.util.Txt;
import fr.fnations.vehicle.VehicleEntity;
import fr.fnations.weapon.GunItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/** Affichage tête haute : munitions des armes et état du véhicule. */
public final class Hud {
    private Hud() {}

    public static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        ItemStack held = mc.player.getMainHandItem();
        if (held.getItem() instanceof GunItem gun) {
            int ammo = GunItem.ammo(held), mag = gun.magazine(held);
            int reserve = 0;
            var ammoItem = BuiltInRegistries.ITEM.getValue(fr.fnations.FNations.id(gun.ammo));
            for (ItemStack s : mc.player.getInventory().items) if (s.is(ammoItem)) reserve += s.getCount();
            String txt = (ammo == 0 ? "<red>" : "<white>") + "<b>" + ammo + "</b><gray> / " + mag + "  <dark_gray>|</dark_gray> <gray>" + reserve;
            var c = Txt.mm(txt);
            int tw = mc.font.width(c);
            g.fill(w - tw - 14, h - 34, w - 4, h - 18, 0x90000000);
            g.drawString(mc.font, c, w - tw - 9, h - 30, 0xFFFFFFFF, true);
        }
        if (mc.player.getVehicle() instanceof VehicleEntity v) {
            int x = 6, y = h - 40;
            double fuel = v.fuel() / Math.max(1, v.kind.fuelCapacity), hp = v.health() / Math.max(1, v.kind.health);
            g.fill(x - 2, y - 2, x + 104, y + 22, 0x90000000);
            g.drawString(mc.font, Txt.mm("<yellow>⛽"), x, y, 0xFFFFFFFF, true);
            g.fill(x + 12, y + 2, x + 100, y + 7, 0xFF303030);
            g.fill(x + 12, y + 2, x + 12 + (int) (88 * Math.min(1, fuel)), y + 7, 0xFFE8C31C);
            g.drawString(mc.font, Txt.mm("<red>❤"), x, y + 11, 0xFFFFFFFF, true);
            g.fill(x + 12, y + 13, x + 100, y + 18, 0xFF303030);
            g.fill(x + 12, y + 13, x + 12 + (int) (88 * Math.min(1, hp)), y + 18, 0xFFD03030);
        }
    }
}

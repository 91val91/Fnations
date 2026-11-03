package fr.fnations.util;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Saisie de texte dans le chat (utilisée par les GUIs : montants, noms, coordonnées...). */
public final class ChatPrompt implements Listener {
    private static final Map<UUID, Consumer<String>> PENDING = new ConcurrentHashMap<>();
    private static Plugin plugin;

    public ChatPrompt(Plugin p) { plugin = p; }

    public static void ask(Player player, String question, Consumer<String> answer) {
        player.closeInventory();
        PENDING.put(player.getUniqueId(), answer);
        Txt.send(player, "<yellow>" + question + " <gray>(tapez <red>annuler</red> pour annuler)");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Consumer<String> c = PENDING.remove(e.getPlayer().getUniqueId());
        if (c == null) return;
        e.setCancelled(true);
        String msg = Txt.plain(e.message()).trim();
        if (msg.equalsIgnoreCase("annuler") || msg.equalsIgnoreCase("cancel")) {
            Txt.err(e.getPlayer(), "Action annulée.");
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> c.accept(msg));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { PENDING.remove(e.getPlayer().getUniqueId()); }
}

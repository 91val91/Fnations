package fr.fnations.gui;

import fr.fnations.net.PromptOpenPayload;
import fr.fnations.util.Txt;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Saisie de texte : ouvre une fenêtre de saisie côté client (remplace la saisie dans le chat du plugin). */
public final class Prompt {
    private record Pending(int id, Consumer<String> answer) {}

    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static int nextId = 1;

    private Prompt() {}

    public static void ask(ServerPlayer player, String question, Consumer<String> answer) { ask(player, question, "", answer); }

    public static void ask(ServerPlayer player, String question, String initial, Consumer<String> answer) {
        player.closeContainer();
        int id = nextId++;
        PENDING.put(player.getUUID(), new Pending(id, answer));
        ServerPlayNetworking.send(player, new PromptOpenPayload(id, question, initial == null ? "" : initial));
    }

    /** Réponse reçue du client. */
    public static void answer(ServerPlayer player, int id, String text, boolean cancelled) {
        Pending p = PENDING.get(player.getUUID());
        if (p == null || p.id() != id) return;
        PENDING.remove(player.getUUID());
        if (cancelled) {
            player.sendSystemMessage(Txt.prefixed("<red>Action annulée."));
            return;
        }
        String t = text == null ? "" : text.trim();
        if (t.length() > 256) t = t.substring(0, 256);
        p.answer().accept(t);
    }

    public static void forget(UUID u) { PENDING.remove(u); }
}

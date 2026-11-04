package fr.fnations.bank;

import fr.fnations.nation.Nation;
import fr.fnations.registry.ModComponents;
import fr.fnations.registry.ModItems;
import fr.fnations.server.FnServer;
import fr.fnations.util.Store;
import fr.fnations.util.Txt;
import fr.fnations.util.Yaml;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Banque des joueurs (comptes) + historique. Les trésors de nation sont dans Nation#balance. */
public final class BankManager {
    private final FnServer plugin;
    private final Store store;
    private final Map<UUID, Double> accounts = new HashMap<>();
    private final Map<String, LinkedList<String>> history = new HashMap<>();
    private final SimpleDateFormat fmt = new SimpleDateFormat("dd/MM HH:mm");

    public BankManager(FnServer plugin) {
        this.plugin = plugin;
        this.store = new Store(plugin.dataDir(), "bank.yml");
        load();
    }

    private void load() {
        Yaml y = store.get();
        Yaml acc = y.getSection("accounts");
        if (acc != null) for (String k : acc.getKeys()) accounts.put(UUID.fromString(k), acc.getDouble(k));
        Yaml h = y.getSection("history");
        if (h != null) for (String k : h.getKeys()) history.put(k, new LinkedList<>(h.getStringList(k)));
    }

    public void save() {
        Yaml y = store.fresh();
        accounts.forEach((k, v) -> y.set("accounts." + k, v));
        history.forEach((k, v) -> y.set("history." + k.replace('.', '_'), new ArrayList<>(v)));
        store.save();
    }

    public List<UUID> accounts() { return new ArrayList<>(accounts.keySet()); }

    public double balance(UUID id) {
        return accounts.computeIfAbsent(id, k -> plugin.config().getDouble("economie.argent-depart", 1000));
    }

    public boolean has(UUID id, double amount) { return balance(id) >= amount; }

    public void deposit(UUID id, double amount, String reason) {
        accounts.put(id, round(balance(id) + amount));
        log(id.toString(), "<green>+" + Txt.money(amount) + " <gray>" + reason);
    }

    public boolean withdraw(UUID id, double amount, String reason) {
        if (!has(id, amount)) return false;
        accounts.put(id, round(balance(id) - amount));
        log(id.toString(), "<red>-" + Txt.money(amount) + " <gray>" + reason);
        return true;
    }

    public boolean transfer(UUID from, UUID to, double amount) {
        if (!withdraw(from, amount, "Virement vers " + plugin.name(to))) return false;
        deposit(to, amount, "Virement de " + plugin.name(from));
        return true;
    }

    // ---- Trésor de nation ----
    public boolean nationWithdraw(Nation n, double amount, String reason) {
        if (n.balance() < amount) return false;
        n.setBalance(round(n.balance() - amount));
        log("nation:" + n.id(), "<red>-" + Txt.money(amount) + " <gray>" + reason);
        return true;
    }

    public void nationDeposit(Nation n, double amount, String reason) {
        n.setBalance(round(n.balance() + amount));
        log("nation:" + n.id(), "<green>+" + Txt.money(amount) + " <gray>" + reason);
    }

    public List<String> history(String key) { return history.getOrDefault(key, new LinkedList<>()); }

    private void log(String key, String line) {
        LinkedList<String> l = history.computeIfAbsent(key, k -> new LinkedList<>());
        l.addFirst("<dark_gray>" + fmt.format(new Date()) + " " + line);
        while (l.size() > 15) l.removeLast();
    }

    private static double round(double v) { return Math.round(v * 100) / 100.0; }

    // ---- Billets physiques ----
    public ItemStack banknote(double value) {
        ItemStack it = ModItems.stack("banknote");
        it.set(ModComponents.NOTE_VALUE, value);
        it.set(DataComponents.CUSTOM_NAME, Txt.c("<green>Billet de <b>" + Txt.money(value)));
        return it;
    }

    public double noteValue(ItemStack it) {
        if (!ModItems.is(it, "banknote")) return 0;
        Double v = it.get(ModComponents.NOTE_VALUE);
        return v == null ? 0 : v * it.getCount();
    }

    /** Dépose tous les billets de l'inventaire du joueur. */
    public double depositAllNotes(ServerPlayer p) {
        double total = 0;
        var items = p.getInventory().items;
        for (int i = 0; i < items.size(); i++) {
            double v = noteValue(items.get(i));
            if (v > 0) {
                total += v;
                items.set(i, ItemStack.EMPTY);
            }
        }
        if (total > 0) deposit(p.getUUID(), total, "Dépôt de billets");
        return total;
    }

    /** Intérêts horaires (plafonnés). */
    public void applyInterest() {
        double rate = plugin.config().getDouble("economie.interets-pourcent-par-heure", 0.5) / 100.0;
        double cap = plugin.config().getDouble("economie.interets-max", 500);
        if (rate <= 0) return;
        for (Map.Entry<UUID, Double> e : new HashMap<>(accounts).entrySet()) {
            double gain = Math.min(cap, e.getValue() * rate);
            if (gain >= 0.01) deposit(e.getKey(), gain, "Intérêts");
        }
    }
}

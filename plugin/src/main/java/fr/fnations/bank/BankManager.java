package fr.fnations.bank;

import fr.fnations.FNations;
import fr.fnations.item.ItemRegistry;
import fr.fnations.nation.Nation;
import fr.fnations.util.Keys;
import fr.fnations.util.Txt;
import fr.fnations.util.YamlStore;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Banque des joueurs (comptes personnels) + historique. Les trésors de nation sont dans Nation#balance. */
public final class BankManager {
    private final FNations plugin;
    private final YamlStore store;
    private final Map<UUID, Double> accounts = new HashMap<>();
    private final Map<String, LinkedList<String>> history = new HashMap<>();
    private final SimpleDateFormat fmt = new SimpleDateFormat("dd/MM HH:mm");

    public BankManager(FNations plugin) {
        this.plugin = plugin;
        this.store = new YamlStore(plugin, "data/bank.yml");
        load();
    }

    private void load() {
        YamlConfiguration y = store.get();
        ConfigurationSection acc = y.getConfigurationSection("accounts");
        if (acc != null) for (String k : acc.getKeys(false)) accounts.put(UUID.fromString(k), acc.getDouble(k));
        ConfigurationSection h = y.getConfigurationSection("history");
        if (h != null) for (String k : h.getKeys(false)) history.put(k, new LinkedList<>(h.getStringList(k)));
    }

    public void save() {
        YamlConfiguration y = store.fresh();
        accounts.forEach((k, v) -> y.set("accounts." + k, v));
        history.forEach((k, v) -> y.set("history." + k, new ArrayList<>(v)));
        store.save();
    }

    public double balance(UUID id) {
        return accounts.computeIfAbsent(id, k -> plugin.getConfig().getDouble("economie.argent-depart", 1000));
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
        if (!withdraw(from, amount, "Virement vers " + Bukkit.getOfflinePlayer(to).getName())) return false;
        deposit(to, amount, "Virement de " + Bukkit.getOfflinePlayer(from).getName());
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
        ItemStack it = ItemRegistry.create("banknote");
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Txt.c("<green>Billet de <b>" + Txt.money(value)));
        meta.getPersistentDataContainer().set(Keys.NOTE_VALUE, PersistentDataType.DOUBLE, value);
        it.setItemMeta(meta);
        return it;
    }

    public double noteValue(ItemStack it) {
        if (!ItemRegistry.is(it, "banknote")) return 0;
        Double v = it.getItemMeta().getPersistentDataContainer().get(Keys.NOTE_VALUE, PersistentDataType.DOUBLE);
        return v == null ? 0 : v * it.getAmount();
    }

    /** Dépose tous les billets de l'inventaire du joueur. */
    public double depositAllNotes(Player p) {
        double total = 0;
        ItemStack[] c = p.getInventory().getStorageContents();
        for (int i = 0; i < c.length; i++) {
            double v = noteValue(c[i]);
            if (v > 0) {
                total += v;
                c[i] = null;
            }
        }
        p.getInventory().setStorageContents(c);
        if (total > 0) deposit(p.getUniqueId(), total, "Dépôt de billets");
        return total;
    }

    /** Intérêts horaires sur les comptes des joueurs (plafonnés). */
    public void applyInterest() {
        double rate = plugin.getConfig().getDouble("economie.interets-pourcent-par-heure", 0.5) / 100.0;
        double cap = plugin.getConfig().getDouble("economie.interets-max", 500);
        if (rate <= 0) return;
        for (Map.Entry<UUID, Double> e : new HashMap<>(accounts).entrySet()) {
            double gain = Math.min(cap, e.getValue() * rate);
            if (gain >= 0.01) deposit(e.getKey(), gain, "Intérêts");
        }
    }
}

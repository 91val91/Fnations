package fr.fnations.loot;

import fr.fnations.rank.Rank;
import fr.fnations.registry.ModItems;
import fr.fnations.server.FnServer;
import fr.fnations.server.Msg;
import fr.fnations.util.Inv;
import fr.fnations.util.Txt;
import fr.fnations.util.Yaml;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** LootBoxs (lootboxs.yml) : tirage pondéré, roulette, livraison du gain à la fin de l'animation. */
public final class LootManager {
    public static final long SPIN_MS = 6000;
    public static final int STRIP = 52, WIN_INDEX = 44;

    public record Reward(String type, String key, int count, double amount, int weight, String rarity, int days) {}

    public record Box(String id, String name, String color, List<Reward> rewards, int totalWeight) {}

    public static final class Opening {
        public final long id;
        public final String box;
        public final Reward win;
        public final List<Reward> strip;
        public final long start;
        public boolean delivered;

        Opening(long id, String box, Reward win, List<Reward> strip, long start) {
            this.id = id;
            this.box = box;
            this.win = win;
            this.strip = strip;
            this.start = start;
        }
    }

    private static final Map<String, String> RARITY = Map.of("commun", "#A3A6B4", "rare", "#3BA7F0", "epique", "#B06BFF",
            "legendaire", "#F5A623", "mythique", "#F04747");
    private static final Map<String, String> RARITY_NAME = Map.of("commun", "Commun", "rare", "Rare", "epique", "Épique",
            "legendaire", "Légendaire", "mythique", "Mythique");

    private final FnServer s;
    private final Map<String, Box> boxes = new LinkedHashMap<>();
    private final Map<UUID, Opening> openings = new HashMap<>();
    private final RandomSource random = RandomSource.create();
    private long nextId = 1;

    public LootManager(FnServer s) {
        this.s = s;
        reload();
    }

    public void reload() {
        boxes.clear();
        Yaml y = s.yaml("lootboxs.yml");
        for (String id : y.getKeys()) {
            Yaml b = y.getSection(id);
            if (b == null) continue;
            List<Reward> rewards = new ArrayList<>();
            int total = 0;
            for (Map<String, Object> r : b.getMapList("recompenses")) {
                Yaml ry = new Yaml(r);
                int w = Math.max(1, ry.getInt("poids", 1));
                String type = ry.getString("type", "argent");
                String key = switch (type) {
                    case "item" -> ry.getString("item", "minecraft:diamond");
                    case "spawner" -> ry.getString("mob", "zombie");
                    case "grade" -> ry.getString("grade", "premium");
                    case "lootbox" -> ry.getString("box", "classic");
                    default -> "";
                };
                int count = type.equals("spawner") ? ry.getInt("niveau", 1) : ry.getInt("nombre", 1);
                rewards.add(new Reward(type, key, count, ry.getDouble("montant", 0), w, ry.getString("rarete", "commun"), ry.getInt("jours", 7)));
                total += w;
            }
            if (!rewards.isEmpty()) boxes.put(id, new Box(id, b.getString("nom", id), b.getString("couleur", "#8C96FF"), rewards, total));
        }
    }

    public Map<String, Box> boxes() { return boxes; }

    public boolean exists(String id) { return id != null && boxes.containsKey(id.toLowerCase()); }

    public Box box(String id) { return id == null ? null : boxes.get(id.toLowerCase()); }

    public String display(String id) {
        Box b = box(id);
        return b == null ? id : b.name();
    }

    public Opening opening(UUID u) { return openings.get(u); }

    public static String rarityColor(String r) { return RARITY.getOrDefault(r, "#A3A6B4"); }

    public static String rarityName(String r) { return RARITY_NAME.getOrDefault(r, r); }

    public int count(ServerPlayer p, String id) { return Inv.count(p, "fnations:lootbox_" + id.toLowerCase()); }

    public void give(ServerPlayer p, String id, int n) {
        String key = "lootbox_" + id.toLowerCase();
        if (ModItems.get(key) == null) return;
        while (n > 0) {
            int c = Math.min(64, n);
            Inv.give(p, ModItems.stack(key, c));
            n -= c;
        }
    }

    // ------------------------------------------------------------ représentation
    public ItemStack icon(Reward r) {
        ItemStack st = switch (r.type()) {
            case "argent" -> s.bank().banknote(r.amount());
            case "item" -> Inv.stack(r.key(), Math.max(1, Math.min(99, r.count())));
            case "spawner" -> s.spawners().item(r.key(), r.count());
            case "grade" -> new ItemStack(Items.NAME_TAG);
            case "lootbox" -> ModItems.get("lootbox_" + r.key()) == null ? new ItemStack(Items.CHEST) : ModItems.stack("lootbox_" + r.key(), Math.max(1, r.count()));
            default -> new ItemStack(Items.PAPER);
        };
        if (r.type().equals("grade")) st.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return st;
    }

    public String label(Reward r) {
        return switch (r.type()) {
            case "argent" -> Txt.money(r.amount());
            case "item" -> (r.count() > 1 ? r.count() + "× " : "") + Inv.display(r.key());
            case "spawner" -> "Spawner " + s.spawners().display(r.key()) + " (niv. " + r.count() + ")";
            case "grade" -> {
                Rank rank = Rank.byId(r.key());
                yield "Grade " + (rank == null ? r.key() : rank.display) + " (" + r.days() + " jours)";
            }
            case "lootbox" -> (r.count() > 1 ? r.count() + "× " : "") + "LootBox " + display(r.key());
            default -> r.type();
        };
    }

    public double chance(Box b, Reward r) { return 100.0 * r.weight() / b.totalWeight(); }

    // ------------------------------------------------------------ ouverture
    private Reward roll(Box b) {
        int t = random.nextInt(b.totalWeight());
        for (Reward r : b.rewards()) {
            t -= r.weight();
            if (t < 0) return r;
        }
        return b.rewards().getLast();
    }

    /** Ouvre une LootBox de l'inventaire du joueur (consommée immédiatement, gain livré après l'animation). */
    public void open(ServerPlayer p, String id) {
        Box b = box(id);
        if (b == null) return;
        Opening cur = openings.get(p.getUUID());
        if (cur != null && !cur.delivered) { Msg.err(p, "Une ouverture est déjà en cours."); return; }
        if (!Inv.take(p, "fnations:lootbox_" + b.id(), 1)) { Msg.err(p, "Vous n'avez pas de LootBox " + b.name() + "."); return; }
        Reward win = roll(b);
        List<Reward> strip = new ArrayList<>();
        for (int i = 0; i < STRIP; i++) strip.add(i == WIN_INDEX ? win : roll(b));
        openings.put(p.getUUID(), new Opening(nextId++, b.id(), win, strip, System.currentTimeMillis()));
        Msg.sound(p, SoundEvents.CHEST_OPEN, 0.8f, 1.2f);
        s.ui().open(p, "lootbox", Map.of("box", b.id()));
    }

    private void deliver(ServerPlayer p, Opening o) {
        o.delivered = true;
        Reward r = o.win;
        switch (r.type()) {
            case "argent" -> s.bank().deposit(p.getUUID(), r.amount(), "LootBox " + display(o.box));
            case "item" -> {
                ItemStack st = Inv.stack(r.key(), 1);
                int n = r.count();
                while (n > 0) {
                    int c = Math.min(st.getMaxStackSize(), n);
                    Inv.give(p, st.copyWithCount(c));
                    n -= c;
                }
            }
            case "spawner" -> Inv.give(p, s.spawners().item(r.key(), r.count()));
            case "grade" -> {
                Rank rank = Rank.byId(r.key());
                if (rank != null) s.ranks().grant(p.getUUID(), p.getScoreboardName(), rank, r.days());
            }
            case "lootbox" -> give(p, r.key(), r.count());
            default -> {
            }
        }
        boolean big = r.rarity().equals("legendaire") || r.rarity().equals("mythique");
        Msg.send(p, "<" + rarityColor(r.rarity()) + "><b>" + rarityName(r.rarity()) + " !</b> <white>Vous gagnez " + label(r));
        Msg.sound(p, big ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.PLAYER_LEVELUP, 1f, big ? 1f : 1.5f);
        if (big) Msg.broadcast(s.server(), "<white>" + Txt.esc(p.getScoreboardName()) + " <gray>a gagné <" + rarityColor(r.rarity()) + ">" + label(r)
                + " <gray>dans une LootBox " + display(o.box) + " !");
    }

    /** Appelé chaque tick : livraison des gains dont l'animation est finie. */
    public void tick() {
        if (openings.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (var e : new ArrayList<>(openings.entrySet())) {
            Opening o = e.getValue();
            if (o.delivered || now < o.start + SPIN_MS + 250) continue;
            ServerPlayer p = s.player(e.getKey());
            if (p == null) continue;
            deliver(p, o);
            var ss = s.ui().session(p);
            if (ss != null && "lootbox".equals(ss.page)) s.ui().refresh(p);
        }
    }

    public void onQuit(ServerPlayer p) {
        Opening o = openings.get(p.getUUID());
        if (o != null && !o.delivered) deliver(p, o);
        openings.remove(p.getUUID());
    }
}

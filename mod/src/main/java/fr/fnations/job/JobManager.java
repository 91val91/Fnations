package fr.fnations.job;

import fr.fnations.FNations;
import fr.fnations.company.Company;
import fr.fnations.craft.StationRecipe;
import fr.fnations.machine.MachineBlock;
import fr.fnations.server.FnServer;
import fr.fnations.server.Msg;
import fr.fnations.util.Inv;
import fr.fnations.util.Store;
import fr.fnations.util.Txt;
import fr.fnations.util.Yaml;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Métiers : expérience et revenus par action, paliers, bonus, classement (metiers.yml + world/fnations/jobs.yml). */
public final class JobManager {
    public record Source(double xp, double money) {}

    public record Rank(UUID player, double xp) {}

    /** Palier de métier requis pour fabriquer un objet. */
    public record CraftReq(Job job, int palier) {}

    private static final ResourceLocation REACH_ID = FNations.id("builder_reach");

    private final FnServer s;
    private final Store store;
    private final Map<UUID, double[]> xp = new HashMap<>();
    private final Map<UUID, int[]> rewarded = new HashMap<>();
    private double[] thresholds = {500, 2000, 5000, 12000, 25000, 50000};
    private double[] rewards = {250, 750, 2000, 5000, 12000, 30000};
    private double xpMul = 1, moneyMul = 1;
    private boolean creative;

    private final Map<Block, Source> mine = new HashMap<>(), wood = new HashMap<>(), crops = new HashMap<>(),
            saplings = new HashMap<>(), buildBonus = new HashMap<>();
    private final Map<EntityType<?>, Source> mobs = new HashMap<>();
    private final Map<String, Source> station = new HashMap<>();
    /** id d'objet (fnations:rocket, minecraft:tnt) → métier et palier requis, dans l'ordre de metiers.yml. */
    private final Map<String, CraftReq> crafts = new LinkedHashMap<>();
    private final Map<UUID, Long> lockMsg = new HashMap<>();
    private Source build = new Source(1, 0.03), monster = new Source(5, 1), animal = new Source(1.5, 0.2), machine = new Source(5, 1);

    // revenus versés par lots (évite un historique bancaire à chaque bloc)
    private final Map<UUID, Double> pending = new HashMap<>();
    private final Map<UUID, double[]> feedback = new HashMap<>();
    private final Map<UUID, Job> feedbackJob = new HashMap<>();
    // anti-abus : blocs posés par des joueurs, emplacements déjà comptés pour le Builder
    private final Map<String, LinkedHashSet<Long>> placed = new HashMap<>();
    private final Map<UUID, LinkedHashSet<Long>> builderSeen = new HashMap<>();
    private boolean felling;

    public JobManager(FnServer s) {
        this.s = s;
        this.store = new Store(s.dataDir(), "jobs.yml");
        reload();
        load();
    }

    // ------------------------------------------------------------ configuration
    public void reload() {
        Yaml y = s.yaml("metiers.yml");
        List<String> t = y.getStringList("paliers");
        if (t.size() == 6) thresholds = t.stream().mapToDouble(Double::parseDouble).toArray();
        List<String> r = y.getStringList("recompenses");
        if (r.size() == 6) rewards = r.stream().mapToDouble(Double::parseDouble).toArray();
        xpMul = y.getDouble("multiplicateur-xp", 1);
        moneyMul = y.getDouble("multiplicateur-argent", 1);
        creative = y.getBoolean("compter-en-creatif", false);
        mine.clear(); wood.clear(); crops.clear(); saplings.clear(); buildBonus.clear(); mobs.clear(); station.clear();
        blocks(y.getSection("mineur.blocs"), mine);
        blocks(y.getSection("bucheron.blocs"), wood);
        blocks(y.getSection("bucheron.planter"), saplings);
        blocks(y.getSection("agriculteur.recoltes"), crops);
        blocks(y.getSection("builder.bonus"), buildBonus);
        build = source(y.get("builder.poser"), build);
        monster = source(y.get("chasseur.monstres"), monster);
        animal = source(y.get("chasseur.animaux"), animal);
        machine = source(y.get("ingenieur.poser-machine"), machine);
        Yaml ents = y.getSection("chasseur.entites");
        if (ents != null) for (String k : ents.getKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(k);
            if (id == null) continue;
            BuiltInRegistries.ENTITY_TYPE.getOptional(id).ifPresent(et -> mobs.put(et, source(ents.get(k), null)));
        }
        Yaml st = y.getSection("ingenieur.station");
        if (st != null) for (String k : st.getKeys()) station.put(k.toUpperCase(), source(st.get(k), null));
        crafts.clear();
        Yaml cr = y.getSection("crafts");
        if (cr != null) for (String jid : cr.getKeys()) {
            Job job = Job.byId(jid);
            Yaml js = cr.getSection(jid);
            if (job == null || js == null) { FNations.LOG.warn("metiers.yml : métier inconnu dans crafts : {}", jid); continue; }
            for (String tier : js.getKeys()) {
                int pal;
                try { pal = Math.max(0, Math.min(6, Integer.parseInt(tier.trim()))); } catch (NumberFormatException e) { continue; }
                for (String raw : js.getStringList(tier)) {
                    String id = itemId(raw);
                    ResourceLocation rl = ResourceLocation.tryParse(id);
                    if (rl == null || BuiltInRegistries.ITEM.getOptional(rl).isEmpty()) { FNations.LOG.warn("metiers.yml : objet inconnu dans crafts : {}", raw); continue; }
                    crafts.put(id, new CraftReq(job, pal));
                }
            }
        }
    }

    /** « rocket » → « fnations:rocket », « tnt » → « minecraft:tnt », les ids complets restent tels quels. */
    public static String itemId(String raw) {
        String k = raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (k.contains(":")) return k;
        return fr.fnations.registry.ModItems.get(k) != null ? "fnations:" + k : "minecraft:" + k;
    }

    // ------------------------------------------------------------ crafts par palier
    /** Condition de métier pour fabriquer cet objet (null : aucune). */
    public CraftReq requirement(String itemId) { return crafts.get(itemId); }

    /** Le joueur a-t-il le palier pour fabriquer cet objet ? (les joueurs en créatif ne sont pas limités) */
    public boolean canCraft(ServerPlayer p, String itemId) {
        CraftReq r = crafts.get(itemId);
        return r == null || r.palier() <= 0 || p.isCreative() || palier(p.getUUID(), r.job()) >= r.palier();
    }

    /** Crafts d'un métier débloqués exactement à ce palier. */
    public List<String> craftsAt(Job j, int palier) {
        List<String> out = new ArrayList<>();
        crafts.forEach((k, r) -> { if (r.job() == j && r.palier() == palier) out.add(k); });
        return out;
    }

    /** Tous les crafts d'un métier, par palier croissant. */
    public List<Map.Entry<String, CraftReq>> craftsOf(Job j) {
        List<Map.Entry<String, CraftReq>> out = new ArrayList<>();
        for (var e : crafts.entrySet()) if (e.getValue().job() == j) out.add(e);
        out.sort(java.util.Comparator.comparingInt(e -> e.getValue().palier()));
        return out;
    }

    /** Texte de la condition : « Ingénieur palier 4 ». */
    public static String label(CraftReq r) { return r.job().display + " palier " + r.palier(); }

    /** Résultat de l'établi : vide si le joueur n'a pas le palier (avec un message dans la barre d'action). */
    public ItemStack craftLock(ServerPlayer p, ItemStack result) {
        if (result.isEmpty()) return result;
        String id = BuiltInRegistries.ITEM.getKey(result.getItem()).toString();
        if (canCraft(p, id)) return result;
        CraftReq r = crafts.get(id);
        long now = System.currentTimeMillis();
        Long last = lockMsg.get(p.getUUID());
        if (last == null || now - last > 1500) {
            lockMsg.put(p.getUUID(), now);
            Msg.bar(p, "<red>🔒 <item:" + id + "> : " + label(r) + " requis <gray>(vous : palier " + palier(p.getUUID(), r.job()) + ")");
        }
        return ItemStack.EMPTY;
    }

    /**
     * XP gagnée hors action de jeu (notation du jury) : fonctionne aussi pour un joueur hors ligne,
     * dont les paliers seront annoncés et payés à la prochaine connexion.
     */
    public void award(UUID u, Job job, double amount) {
        if (amount <= 0) return;
        double[] a = xp.computeIfAbsent(u, k -> new double[Job.values().length]);
        int before = palier(a[job.ordinal()]);
        a[job.ordinal()] += amount * xpMul;
        int after = palier(a[job.ordinal()]);
        ServerPlayer p = s.player(u);
        if (p != null) for (int k = before + 1; k <= after; k++) palierUp(p, job, k);
    }

    private static Source source(Object o, Source def) {
        if (o instanceof List<?> l && !l.isEmpty()) {
            double a = l.get(0) instanceof Number n ? n.doubleValue() : 0;
            double b = l.size() > 1 && l.get(1) instanceof Number n ? n.doubleValue() : 0;
            return new Source(a, b);
        }
        if (o instanceof Map<?, ?> m) {
            Object a = m.get("xp"), b = m.get("argent");
            return new Source(a instanceof Number n ? n.doubleValue() : 0, b instanceof Number n ? n.doubleValue() : 0);
        }
        if (o instanceof Number n) return new Source(n.doubleValue(), 0);
        return def;
    }

    private static void blocks(Yaml sec, Map<Block, Source> out) {
        if (sec == null) return;
        for (String k : sec.getKeys()) {
            Source src = source(sec.get(k), null);
            if (src == null) continue;
            if (k.startsWith("#")) {
                ResourceLocation id = ResourceLocation.tryParse(k.substring(1));
                if (id == null) continue;
                for (var h : BuiltInRegistries.BLOCK.getTagOrEmpty(TagKey.create(Registries.BLOCK, id))) out.putIfAbsent(h.value(), src);
            } else {
                ResourceLocation id = ResourceLocation.tryParse(k);
                if (id != null) BuiltInRegistries.BLOCK.getOptional(id).ifPresent(b -> out.put(b, src));
            }
        }
    }

    // ------------------------------------------------------------ niveaux
    public double xp(UUID u, Job j) { return xp.computeIfAbsent(u, k -> new double[Job.values().length])[j.ordinal()]; }

    public int palier(double v) {
        int p = 0;
        for (double t : thresholds) if (v >= t) p++;
        return p;
    }

    public int palier(UUID u, Job j) { return palier(xp(u, j)); }

    public double threshold(int palier) { return palier <= 0 ? 0 : thresholds[Math.min(6, palier) - 1]; }

    /** XP à atteindre pour le prochain palier (ou le dernier palier). */
    public double next(double v) {
        for (double t : thresholds) if (v < t) return t;
        return thresholds[5];
    }

    public double reward(int palier) { return palier <= 0 ? 0 : rewards[Math.min(6, palier) - 1]; }

    public List<Rank> top(Job j, int n) {
        List<Rank> out = new ArrayList<>();
        xp.forEach((u, a) -> { if (a[j.ordinal()] > 0) out.add(new Rank(u, a[j.ordinal()])); });
        out.sort((a, b) -> Double.compare(b.xp(), a.xp()));
        return out.size() > n ? out.subList(0, n) : out;
    }

    public int rank(UUID u, Job j) {
        double mine = xp(u, j);
        int r = 1;
        for (double[] a : xp.values()) if (a[j.ordinal()] > mine) r++;
        return r;
    }

    /** Admin : définit l'XP d'un métier. */
    public void setXp(ServerPlayer p, Job j, double v) {
        double[] a = xp.computeIfAbsent(p.getUUID(), k -> new double[Job.values().length]);
        int before = palier(a[j.ordinal()]);
        a[j.ordinal()] = Math.max(0, v);
        int after = palier(a[j.ordinal()]);
        for (int k = before + 1; k <= after; k++) palierUp(p, j, k);
        applyAttributes(p);
    }

    // ------------------------------------------------------------ gains
    public void gain(ServerPlayer p, Job job, Source src) {
        if (src == null || (src.xp() <= 0 && src.money() <= 0)) return;
        if (p.isCreative() && !creative) return;
        double gx = src.xp() * xpMul, gm = src.money() * moneyMul;
        Company c = s.companies().of(p.getUUID());
        boolean match = c != null && c.type().job == job;
        if (match) {
            gx *= 1 + 0.01 * s.companies().level(c);
            gm *= 1 + 0.02 * s.companies().level(c);
        }
        if (job == Job.CHASSEUR) gm *= 1 + 0.10 * palier(p.getUUID(), Job.CHASSEUR);
        gm *= 1 + s.ranks().jobBonus(p.getUUID());
        double[] a = xp.computeIfAbsent(p.getUUID(), k -> new double[Job.values().length]);
        int before = palier(a[job.ordinal()]);
        a[job.ordinal()] += gx;
        int after = palier(a[job.ordinal()]);
        if (match) s.companies().addXp(c, gx);
        if (gm > 0) {
            double cut = c == null ? 0 : gm * c.contribution() / 100.0;
            pending.merge(p.getUUID(), gm - cut, Double::sum);
            if (cut > 0) s.companies().addContribution(c, cut);
        }
        double[] fb = feedback.computeIfAbsent(p.getUUID(), k -> new double[2]);
        if (feedbackJob.get(p.getUUID()) != job) { fb[0] = 0; fb[1] = 0; }
        feedbackJob.put(p.getUUID(), job);
        fb[0] += gx;
        fb[1] += gm;
        for (int k = before + 1; k <= after; k++) palierUp(p, job, k);
    }

    private void palierUp(ServerPlayer p, Job job, int palier) {
        int[] done = rewarded.computeIfAbsent(p.getUUID(), k -> new int[Job.values().length]);
        if (done[job.ordinal()] >= palier) return;
        done[job.ordinal()] = palier;
        double money = reward(palier);
        if (money > 0) s.bank().deposit(p.getUUID(), money, "Palier " + palier + " " + job.display);
        Msg.title(p, "<#8C96FF><b>Palier " + palier + " !", "<white>" + job.display + " <gray>• <green>+" + Txt.money(money));
        Msg.sound(p, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.1f);
        Msg.ok(p, job.display + " : palier " + palier + " atteint, " + Txt.money(money) + " versés.");
        for (String perk : job.perks(palier)) Msg.send(p, "<#8C96FF>✦ <white>" + perk);
        List<String> unlocked = craftsAt(job, palier);
        if (!unlocked.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String id : unlocked) sb.append(sb.isEmpty() ? "" : "<gray>, <white>").append("<item:").append(id).append(">");
            Msg.send(p, "<#8C96FF>✦ <white>Nouveaux crafts : " + sb);
        }
        if (palier == 6) Msg.broadcast(s.server(), "<gold>" + Txt.esc(p.getScoreboardName()) + " a atteint le palier maximal en " + job.display + " !");
        applyAttributes(p);
    }

    /** Portée du Builder (à réappliquer à la connexion et à la réapparition). */
    public void applyAttributes(ServerPlayer p) {
        var inst = p.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
        if (inst == null) return;
        inst.removeModifier(REACH_ID);
        int pal = palier(p.getUUID(), Job.BUILDER);
        if (pal > 0) inst.addTransientModifier(new AttributeModifier(REACH_ID, 0.5 * pal, AttributeModifier.Operation.ADD_VALUE));
    }

    /** Toutes les secondes : retour visuel ; toutes les minutes : versement des revenus. */
    public void tick(long ticks) {
        if (ticks % 10 == 0 && !feedback.isEmpty()) {
            for (var e : feedback.entrySet()) {
                ServerPlayer p = s.player(e.getKey());
                Job j = feedbackJob.get(e.getKey());
                double[] fb = e.getValue();
                if (p == null || j == null || fb[0] <= 0) continue;
                double v = xp(p.getUUID(), j);
                Msg.bar(p, "<#8C96FF>" + j.display + " <white>+" + fmt(fb[0]) + " XP" + (fb[1] > 0 ? " <#43D17A>+" + Txt.money(fb[1]) : "")
                        + " <#6C6F7E>(" + fmt(v) + " / " + fmt(next(v)) + ")");
                fb[0] = 0;
                fb[1] = 0;
            }
        }
        if (ticks % 1200 == 0) flush();
    }

    public void flush() {
        for (var e : pending.entrySet()) if (e.getValue() >= 0.01) s.bank().deposit(e.getKey(), e.getValue(), "Revenus des métiers");
        pending.clear();
    }

    public void flush(UUID u) {
        Double v = pending.remove(u);
        if (v != null && v >= 0.01) s.bank().deposit(u, v, "Revenus des métiers");
    }

    public double pending(UUID u) { return pending.getOrDefault(u, 0.0); }

    private static String fmt(double v) {
        return v >= 100 ? String.valueOf(Math.round(v)) : String.format(java.util.Locale.FRANCE, "%.1f", v);
    }

    // ------------------------------------------------------------ événements
    private static long key(BlockPos p) { return p.asLong(); }

    private void markPlaced(ServerLevel l, BlockPos p) {
        LinkedHashSet<Long> set = placed.computeIfAbsent(l.dimension().location().toString(), k -> new LinkedHashSet<>());
        set.add(key(p));
        if (set.size() > 200_000) {
            var it = set.iterator();
            it.next();
            it.remove();
        }
    }

    private boolean takePlaced(ServerLevel l, BlockPos p) {
        LinkedHashSet<Long> set = placed.get(l.dimension().location().toString());
        return set != null && set.remove(key(p));
    }

    public void onPlace(ServerPlayer p, ServerLevel level, BlockPos pos, BlockState state, BlockItem item) {
        markPlaced(level, pos);
        Block b = state.getBlock();
        if (b instanceof MachineBlock) gain(p, Job.INGENIEUR, machine);
        if (saplings.containsKey(b)) { gain(p, Job.BUCHERON, saplings.get(b)); return; }
        if (crops.containsKey(b)) return; // planter une graine ne rapporte pas d'XP Builder
        LinkedHashSet<Long> seen = builderSeen.computeIfAbsent(p.getUUID(), k -> new LinkedHashSet<>());
        if (seen.add(key(pos))) {
            if (seen.size() > 4096) { var it = seen.iterator(); it.next(); it.remove(); }
            gain(p, Job.BUILDER, buildBonus.getOrDefault(b, build));
            int pal = palier(p.getUUID(), Job.BUILDER);
            double chance = pal >= 6 ? 0.12 : pal == 5 ? 0.08 : pal == 4 ? 0.05 : 0;
            if (chance > 0 && !p.isCreative() && p.getRandom().nextDouble() < chance) Inv.give(p, new ItemStack(item));
        }
    }

    public void onBreak(ServerPlayer p, ServerLevel level, BlockPos pos, BlockState state) {
        boolean natural = !takePlaced(level, pos);
        if (felling) return;
        Block b = state.getBlock();
        ItemStack tool = p.getMainHandItem();
        if (natural && mine.containsKey(b)) {
            gain(p, Job.MINEUR, mine.get(b));
            int pal = palier(p.getUUID(), Job.MINEUR);
            if (pal > 0 && p.getRandom().nextDouble() < 0.05 * pal && !state.is(BlockTags.BASE_STONE_OVERWORLD) && !state.is(BlockTags.BASE_STONE_NETHER))
                Block.dropResources(state, level, pos, null, p, tool);
            if (pal >= 3) p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 120, pal >= 5 ? 1 : 0, true, false, true));
            return;
        }
        if (natural && wood.containsKey(b)) {
            gain(p, Job.BUCHERON, wood.get(b));
            int pal = palier(p.getUUID(), Job.BUCHERON);
            if (pal > 0 && p.getRandom().nextDouble() < 0.05 * pal) Block.dropResources(state, level, pos, null, p, tool);
            if (pal >= 3 && p.isShiftKeyDown() && tool.is(ItemTags.AXES)) fell(p, level, pos, state);
            if (pal >= 5) replantSapling(p, level, pos, state);
            return;
        }
        boolean planted = b instanceof CropBlock || b instanceof NetherWartBlock || b instanceof CocoaBlock || b instanceof SweetBerryBushBlock;
        if (crops.containsKey(b) && mature(state) && (natural || planted)) {
            gain(p, Job.AGRICULTEUR, crops.get(b));
            int pal = palier(p.getUUID(), Job.AGRICULTEUR);
            if (pal > 0 && p.getRandom().nextDouble() < 0.05 * pal) Block.dropResources(state, level, pos, null, p, tool);
            if (pal >= 2) replantCrop(p, level, pos, state);
        }
    }

    private static boolean mature(BlockState st) {
        Block b = st.getBlock();
        if (b instanceof CropBlock c) return c.isMaxAge(st);
        if (b instanceof NetherWartBlock) return st.getValue(NetherWartBlock.AGE) >= 3;
        if (b instanceof CocoaBlock) return st.getValue(CocoaBlock.AGE) >= 2;
        if (b instanceof SweetBerryBushBlock) return st.getValue(SweetBerryBushBlock.AGE) >= 2;
        return true;
    }

    private void replantCrop(ServerPlayer p, ServerLevel level, BlockPos pos, BlockState state) {
        Block b = state.getBlock();
        if (!(b instanceof CropBlock || b instanceof NetherWartBlock || b instanceof CocoaBlock)) return;
        if (!level.getBlockState(pos).isAir()) return;
        ItemStack seed = state.getCloneItemStack(level, pos, false);
        if (seed.isEmpty()) return;
        String key = BuiltInRegistries.ITEM.getKey(seed.getItem()).toString();
        if (!Inv.take(p, key, 1)) return;
        BlockState fresh = b instanceof CocoaBlock ? b.defaultBlockState().setValue(CocoaBlock.FACING, state.getValue(CocoaBlock.FACING)) : b.defaultBlockState();
        if (fresh.canSurvive(level, pos)) level.setBlockAndUpdate(pos, fresh);
        else Inv.give(p, seed.copyWithCount(1));
    }

    private void replantSapling(ServerPlayer p, ServerLevel level, BlockPos pos, BlockState state) {
        BlockState below = level.getBlockState(pos.below());
        if (!below.is(BlockTags.DIRT)) return;
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        String sap = id.replace("_log", "_sapling").replace("_wood", "_sapling");
        if (id.startsWith("mangrove")) sap = "mangrove_propagule";
        if (sap.equals(id)) return;
        Block sb = BuiltInRegistries.BLOCK.getValue(ResourceLocation.withDefaultNamespace(sap));
        if (sb == Blocks.AIR) return;
        if (level.getBlockState(pos).isAir() && sb.defaultBlockState().canSurvive(level, pos)) level.setBlockAndUpdate(pos, sb.defaultBlockState());
    }

    /** Abattage de l'arbre entier : bûches reliées au-dessus du bloc coupé. */
    private void fell(ServerPlayer p, ServerLevel level, BlockPos start, BlockState first) {
        felling = true;
        try {
            Deque<BlockPos> queue = new ArrayDeque<>();
            Set<BlockPos> seen = new HashSet<>();
            queue.add(start);
            seen.add(start);
            int broken = 0;
            while (!queue.isEmpty() && broken < 96) {
                BlockPos c = queue.poll();
                for (int dx = -1; dx <= 1; dx++) for (int dy = 0; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                    BlockPos n = c.offset(dx, dy, dz);
                    if (seen.contains(n) || Math.abs(n.getX() - start.getX()) > 6 || Math.abs(n.getZ() - start.getZ()) > 6) continue;
                    seen.add(n);
                    BlockState st = level.getBlockState(n);
                    if (!wood.containsKey(st.getBlock()) || takePlaced(level, n)) continue;
                    if (!s.territory().canBuild(p, level, n)) continue;
                    ItemStack tool = p.getMainHandItem();
                    if (!tool.is(ItemTags.AXES)) return;
                    level.destroyBlock(n, true, p);
                    tool.hurtAndBreak(1, p, EquipmentSlot.MAINHAND);
                    gain(p, Job.BUCHERON, wood.get(st.getBlock()));
                    broken++;
                    queue.add(n);
                }
            }
        } finally {
            felling = false;
        }
    }

    public void onKill(ServerPlayer p, LivingEntity victim) {
        if (victim instanceof Player) return;
        Source src = mobs.get(victim.getType());
        if (src == null) src = victim instanceof Enemy ? monster : victim instanceof Animal ? animal : null;
        if (src == null) return;
        if (victim.getTags().contains("fn_spawner")) src = new Source(src.xp() * 0.5, src.money() * 0.5); // mobs de spawner : moitié
        gain(p, Job.CHASSEUR, src);
        int pal = palier(p.getUUID(), Job.CHASSEUR);
        if (pal >= 2 && p.getRandom().nextDouble() < 0.25) ExperienceOrb.award(p.serverLevel(), victim.position(), 2 + pal);
        if (pal >= 3) p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 100, 0, true, false, true));
        if (pal >= 5) p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0, true, false, true));
    }

    /** Fabrication à la Station : XP Ingénieur et chance de récupérer un ingrédient. */
    public void onStationCraft(ServerPlayer p, StationRecipe r) {
        gain(p, Job.INGENIEUR, station.getOrDefault(r.category(), new Source(4, 1)));
        int pal = palier(p.getUUID(), Job.INGENIEUR);
        Company c = s.companies().of(p.getUUID());
        double chance = 0.04 * pal + (c != null && c.type() == Company.Type.INGENIEUR ? 0.01 * s.companies().level(c) : 0);
        if (chance > 0 && !r.ingredients().isEmpty() && p.getRandom().nextDouble() < chance) {
            List<String> keys = new ArrayList<>(r.ingredients().keySet());
            String k = keys.get(p.getRandom().nextInt(keys.size()));
            Inv.give(p, Inv.stack(k, 1));
            Msg.send(p, "<#8C96FF>Ingénieur : <white>vous récupérez " + Inv.display(k) + " !");
        }
    }

    public void onJoin(ServerPlayer p) {
        // paliers atteints hors ligne (XP de notation) : annonce et récompense
        int[] done = rewarded.computeIfAbsent(p.getUUID(), k -> new int[Job.values().length]);
        for (Job j : Job.values()) for (int k = done[j.ordinal()] + 1; k <= palier(p.getUUID(), j); k++) palierUp(p, j, k);
        applyAttributes(p);
    }

    public void onQuit(ServerPlayer p) {
        flush(p.getUUID());
        feedback.remove(p.getUUID());
        builderSeen.remove(p.getUUID());
    }

    // ------------------------------------------------------------ persistance
    private void load() {
        Yaml y = store.get();
        Yaml pl = y.getSection("joueurs");
        if (pl != null) for (String k : pl.getKeys()) {
            Yaml sec = pl.getSection(k);
            if (sec == null) continue;
            double[] a = new double[Job.values().length];
            int[] d = new int[Job.values().length];
            for (Job j : Job.values()) {
                a[j.ordinal()] = sec.getDouble(j.id + ".xp");
                d[j.ordinal()] = sec.getInt(j.id + ".palier-recompense");
            }
            try {
                UUID u = UUID.fromString(k);
                xp.put(u, a);
                rewarded.put(u, d);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void save() {
        Yaml y = store.fresh();
        for (var e : xp.entrySet()) {
            int[] d = rewarded.getOrDefault(e.getKey(), new int[Job.values().length]);
            Map<String, Object> m = new LinkedHashMap<>();
            for (Job j : Job.values()) {
                Map<String, Object> jm = new LinkedHashMap<>();
                jm.put("xp", Math.round(e.getValue()[j.ordinal()] * 100) / 100.0);
                jm.put("palier-recompense", d[j.ordinal()]);
                m.put(j.id, jm);
            }
            y.set("joueurs." + e.getKey(), m);
        }
        store.save();
    }

    public Map<Job, Integer> paliers(UUID u) {
        Map<Job, Integer> out = new EnumMap<>(Job.class);
        for (Job j : Job.values()) out.put(j, palier(u, j));
        return out;
    }
}

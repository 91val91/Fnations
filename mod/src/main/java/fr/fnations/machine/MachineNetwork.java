package fr.fnations.machine;

import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Machines chargées par monde (le réseau électrique fonctionne par rayon). */
public final class MachineNetwork {
    private static final Map<ServerLevel, MachineNetwork> NETS = new WeakHashMap<>();
    private final Set<MachineBlockEntity> machines = Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    public static MachineNetwork of(ServerLevel level) { return NETS.computeIfAbsent(level, l -> new MachineNetwork()); }

    void add(MachineBlockEntity be) { machines.add(be); }

    void remove(MachineBlockEntity be) { machines.remove(be); }

    public List<MachineBlockEntity> all() { return new ArrayList<>(machines); }

    public static double radius() {
        var s = fr.fnations.server.FnServer.get();
        return s == null ? 16 : s.config().getDouble("machines.rayon-reseau", 16);
    }

    public List<MachineBlockEntity> inRange(MachineBlockEntity m) {
        double r2 = radius() * radius();
        List<MachineBlockEntity> out = new ArrayList<>();
        for (MachineBlockEntity o : machines) {
            if (o.isRemoved()) continue;
            if (o.getBlockPos().distSqr(m.getBlockPos()) <= r2) out.add(o);
        }
        return out;
    }

    /** La machine est coupée par un interrupteur ouvert à portée. */
    public boolean tripped(MachineBlockEntity m) {
        if (m.type() == MachineType.SWITCH) return false;
        for (MachineBlockEntity o : inRange(m)) if (o.type() == MachineType.SWITCH && !o.enabled()) return true;
        return false;
    }
}

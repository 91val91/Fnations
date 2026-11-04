package fr.fnations.room;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Sans WorldEdit : sélection par /chambre pos1|pos2, sauvegarde en structure vanilla (.nbt). */
public class VanillaSnapshots implements RoomSnapshots {
    protected final Path folder;
    private final Map<UUID, BlockPos[]> picks = new HashMap<>();

    public VanillaSnapshots(Path folder) { this.folder = folder; }

    public void pick(ServerPlayer p, int index, BlockPos pos) {
        picks.computeIfAbsent(p.getUUID(), k -> new BlockPos[2])[index] = pos;
    }

    @Override
    public int[] selection(ServerPlayer p) {
        BlockPos[] s = picks.get(p.getUUID());
        if (s == null || s[0] == null || s[1] == null) return null;
        return new int[]{Math.min(s[0].getX(), s[1].getX()), Math.min(s[0].getY(), s[1].getY()), Math.min(s[0].getZ(), s[1].getZ()),
                Math.max(s[0].getX(), s[1].getX()), Math.max(s[0].getY(), s[1].getY()), Math.max(s[0].getZ(), s[1].getZ())};
    }

    private Path nbt(Room r) { return folder.resolve(r.id() + ".nbt"); }

    @Override
    public void save(Room r, ServerLevel level) throws Exception {
        Files.createDirectories(folder);
        StructureTemplate t = new StructureTemplate();
        t.fillFromWorld(level, r.min(), new Vec3i(r.maxX() - r.minX() + 1, r.maxY() - r.minY() + 1, r.maxZ() - r.minZ() + 1), false, null);
        NbtIo.writeCompressed(t.save(new CompoundTag()), nbt(r));
    }

    @Override
    public boolean restore(Room r, ServerLevel level) throws Exception {
        Path f = nbt(r);
        if (!Files.exists(f)) return false;
        CompoundTag tag = NbtIo.readCompressed(f, NbtAccounter.unlimitedHeap());
        StructureTemplate t = level.getStructureManager().readStructure(tag);
        return t.placeInWorld(level, r.min(), r.min(), new StructurePlaceSettings(), level.random, Block.UPDATE_CLIENTS);
    }

    @Override
    public void delete(Room r) {
        try { Files.deleteIfExists(nbt(r)); } catch (Exception ignored) {}
    }

    @Override
    public String name() { return "structure vanilla (.nbt)"; }
}

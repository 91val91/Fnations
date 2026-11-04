package fr.fnations.room;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Avec WorldEdit : sélection //pos1 //pos2 (ou /chambre pos1|pos2) et schematics Sponge (.schem). */
public final class WorldEditSnapshots extends VanillaSnapshots {
    public WorldEditSnapshots(Path folder) { super(folder); }

    @Override
    public int[] selection(ServerPlayer p) {
        try {
            var actor = FabricAdapter.adaptPlayer(p);
            Region r = WorldEdit.getInstance().getSessionManager().get(actor).getSelection(FabricAdapter.adapt(p.level()));
            BlockVector3 a = r.getMinimumPoint(), b = r.getMaximumPoint();
            return new int[]{a.x(), a.y(), a.z(), b.x(), b.y(), b.z()};
        } catch (Exception e) {
            return super.selection(p);
        }
    }

    private File schem(Room r) { return folder.resolve(r.id() + ".schem").toFile(); }

    @Override
    public void save(Room r, ServerLevel level) throws Exception {
        Files.createDirectories(folder);
        var world = FabricAdapter.adapt(level);
        CuboidRegion region = new CuboidRegion(world, BlockVector3.at(r.minX(), r.minY(), r.minZ()), BlockVector3.at(r.maxX(), r.maxY(), r.maxZ()));
        BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
        clipboard.setOrigin(region.getMinimumPoint());
        try (EditSession es = WorldEdit.getInstance().newEditSession(world)) {
            ForwardExtentCopy copy = new ForwardExtentCopy(es, region, clipboard, region.getMinimumPoint());
            copy.setCopyingEntities(false);
            Operations.complete(copy);
        }
        try (ClipboardWriter writer = BuiltInClipboardFormat.SPONGE_V3_SCHEMATIC.getWriter(new FileOutputStream(schem(r)))) {
            writer.write(clipboard);
        }
    }

    @Override
    public boolean restore(Room r, ServerLevel level) throws Exception {
        File f = schem(r);
        if (!f.exists()) return super.restore(r, level);
        ClipboardFormat fmt = ClipboardFormats.findByFile(f);
        if (fmt == null) return false;
        Clipboard cb;
        try (ClipboardReader reader = fmt.getReader(new FileInputStream(f))) {
            cb = reader.read();
        }
        try (EditSession es = WorldEdit.getInstance().newEditSession(FabricAdapter.adapt(level))) {
            Operation op = new ClipboardHolder(cb).createPaste(es).to(BlockVector3.at(r.minX(), r.minY(), r.minZ())).ignoreAirBlocks(false).build();
            Operations.complete(op);
        }
        return true;
    }

    @Override
    public void delete(Room r) {
        schem(r).delete();
        super.delete(r);
    }

    @Override
    public String name() { return "schematic WorldEdit (.schem)"; }
}

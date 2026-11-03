package fr.fnations.room;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/** Intégration WorldEdit : lecture de la sélection, sauvegarde et restauration des chambres (.schem). */
public final class RoomSnapshots {
    private final File folder;

    public RoomSnapshots(File folder) {
        this.folder = folder;
        folder.mkdirs();
    }

    /** Sélection WorldEdit du joueur : {minX,minY,minZ,maxX,maxY,maxZ} ou null. */
    public int[] selection(Player p) {
        try {
            var actor = BukkitAdapter.adapt(p);
            Region r = WorldEdit.getInstance().getSessionManager().get(actor).getSelection(BukkitAdapter.adapt(p.getWorld()));
            BlockVector3 a = r.getMinimumPoint(), b = r.getMaximumPoint();
            return new int[]{a.x(), a.y(), a.z(), b.x(), b.y(), b.z()};
        } catch (Exception e) {
            return null;
        }
    }

    private File file(Room r) { return new File(folder, r.id() + ".schem"); }

    public boolean exists(Room r) { return file(r).exists(); }

    /** Sauvegarde l'état actuel de la chambre (état "propre" restauré à la libération). */
    public void save(Room r) throws Exception {
        World w = Bukkit.getWorld(r.world());
        if (w == null) throw new IllegalStateException("Monde introuvable");
        CuboidRegion region = new CuboidRegion(BukkitAdapter.adapt(w),
                BlockVector3.at(r.minX(), r.minY(), r.minZ()), BlockVector3.at(r.maxX(), r.maxY(), r.maxZ()));
        BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
        clipboard.setOrigin(region.getMinimumPoint());
        try (EditSession es = WorldEdit.getInstance().newEditSession(region.getWorld())) {
            ForwardExtentCopy copy = new ForwardExtentCopy(es, region, clipboard, region.getMinimumPoint());
            copy.setCopyingEntities(false);
            Operations.complete(copy);
        }
        try (ClipboardWriter writer = BuiltInClipboardFormat.SPONGE_V3_SCHEMATIC.getWriter(new FileOutputStream(file(r)))) {
            writer.write(clipboard);
        }
    }

    /** Restaure la chambre dans son état sauvegardé. */
    public boolean restore(Room r) throws Exception {
        File f = file(r);
        World w = Bukkit.getWorld(r.world());
        if (!f.exists() || w == null) return false;
        ClipboardFormat fmt = ClipboardFormats.findByFile(f);
        if (fmt == null) return false;
        Clipboard cb;
        try (ClipboardReader reader = fmt.getReader(new FileInputStream(f))) {
            cb = reader.read();
        }
        try (EditSession es = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(w))) {
            Operation op = new ClipboardHolder(cb).createPaste(es)
                    .to(BlockVector3.at(r.minX(), r.minY(), r.minZ()))
                    .ignoreAirBlocks(false).build();
            Operations.complete(op);
        }
        return true;
    }

    public void delete(Room r) { file(r).delete(); }
}

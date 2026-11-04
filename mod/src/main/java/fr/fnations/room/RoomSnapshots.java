package fr.fnations.room;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Sauvegarde / restauration de l'état d'une chambre et sélection de région. */
public interface RoomSnapshots {
    /** Sélection du joueur {minX,minY,minZ,maxX,maxY,maxZ} ou null. */
    int[] selection(ServerPlayer p);

    void save(Room r, ServerLevel level) throws Exception;

    boolean restore(Room r, ServerLevel level) throws Exception;

    void delete(Room r);

    String name();
}

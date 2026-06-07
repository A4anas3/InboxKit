package com.gridapp.repository;

import com.gridapp.model.entity.Tile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TileRepository extends JpaRepository<Tile, String> {

    /**
     * Fetch all claimed tiles for Redis warm-up on startup.
     * Uses JOIN FETCH to avoid N+1 queries when accessing tile.owner.
     */
    @Query("SELECT t FROM Tile t JOIN FETCH t.owner WHERE t.owner IS NOT NULL")
    List<Tile> findAllClaimed();

    /**
     * Count tiles grouped by owner — used for leaderboard rebuild on startup.
     * Returns Object[] where [0]=ownerId (UUID), [1]=count (Long)
     */
    @Query("SELECT t.owner.id, COUNT(t) FROM Tile t WHERE t.owner IS NOT NULL GROUP BY t.owner.id")
    List<Object[]> countTilesByOwner();
}

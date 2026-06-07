package com.gridapp.service;

import com.gridapp.model.entity.ClaimHistory;
import com.gridapp.model.entity.Tile;
import com.gridapp.model.entity.User;
import com.gridapp.repository.ClaimHistoryRepository;
import com.gridapp.repository.TileRepository;
import com.gridapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Handles asynchronous PostgreSQL persistence for tile claims.
 * Extracted into its own bean so @Async and @Transactional proxies work
 * (self-invocation within the same class bypasses Spring's proxy).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TilePersistenceService {

    private final TileRepository tileRepository;
    private final UserRepository userRepository;
    private final ClaimHistoryRepository claimHistoryRepository;

    @Async
    @Transactional
    public void persistClaimAsync(String tileId, String userId, Instant claimedAt) {
        try {
            UUID userUuid = UUID.fromString(userId);

            // Upsert the tile record
            User userRef = userRepository.getReferenceById(userUuid);
            Tile tile = tileRepository.findById(tileId).orElse(new Tile());
            tile.setTileId(tileId);
            tile.setOwner(userRef);
            tile.setClaimedAt(claimedAt);
            tileRepository.save(tile);

            // Append claim history
            ClaimHistory history = ClaimHistory.builder()
                    .tileId(tileId)
                    .userId(userUuid)
                    .claimedAt(claimedAt)
                    .build();
            claimHistoryRepository.save(history);

            // Increment tiles_owned
            userRepository.incrementTilesOwned(userUuid);

            log.debug("Async PostgreSQL write complete: tileId={} userId={}", tileId, userId);
        } catch (Exception e) {
            log.error("Async PostgreSQL write FAILED for tileId={} userId={} at={}: {}",
                    tileId, userId, claimedAt, e.getMessage(), e);
        }
    }

    /**
     * Asynchronously removes a tile from PostgreSQL when a user decolors it.
     * Also decrements the user's tiles_owned counter.
     */
    @Async
    @Transactional
    public void deleteClaimAsync(String tileId) {
        try {
            tileRepository.findById(tileId).ifPresent(tile -> {
                UUID ownerUuid = tile.getOwner() != null ? tile.getOwner().getId() : null;
                tileRepository.delete(tile);
                if (ownerUuid != null) {
                    userRepository.decrementTilesOwned(ownerUuid);
                }
            });
            log.debug("Async PostgreSQL delete complete: tileId={}", tileId);
        } catch (Exception e) {
            log.error("Async PostgreSQL delete FAILED for tileId={}: {}", tileId, e.getMessage(), e);
        }
    }
}

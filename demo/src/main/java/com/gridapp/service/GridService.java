package com.gridapp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gridapp.model.dto.ClaimRequest;
import com.gridapp.model.dto.ErrorMessage;
import com.gridapp.model.dto.LeaderboardEntry;
import com.gridapp.model.dto.TileUpdate;
import com.gridapp.model.entity.User;
import com.gridapp.model.entity.Tile;
import com.gridapp.repository.TileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class GridService {

    // ── Constants ─────────────────────────────────────────────────────────────
    private static final String GRID_KEY          = "grid";
    private static final String COOLDOWN_PREFIX   = "cooldown:";
    private static final int    COOLDOWN_SECONDS  = 5;
    private static final int    GRID_ROWS         = 90;
    private static final int    GRID_COLS         = 100;
    private static final Pattern TILE_ID_PATTERN  = Pattern.compile("^\\d+_\\d+$");

    // ── Destinations ──────────────────────────────────────────────────────────
    private static final String TOPIC_GRID        = "/topic/grid";
    private static final String TOPIC_LEADERBOARD = "/topic/leaderboard";
    private static final String USER_QUEUE_ERROR  = "/queue/error";

    // ── Dependencies ──────────────────────────────────────────────────────────
    private final RedisTemplate<String, String>    redisTemplate;
    private final DefaultRedisScript<Long>         claimTileScript;
    private final DefaultRedisScript<Long>         decolorTileScript;
    private final DefaultRedisScript<Long>         cooldownScript;
    private final SimpMessagingTemplate            messagingTemplate;
    private final LeaderboardService               leaderboardService;
    private final UserService                      userService;
    private final TilePersistenceService           tilePersistenceService;
    private final TileRepository                   tileRepository;
    private final ObjectMapper                     objectMapper;

    // ═══════════════════════════════════════════════════════════════════════
    // CLAIM FLOW — The core logic, strictly ordered per spec
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Processes a tile claim request from a WebSocket client.
     * Executed on the WebSocket thread — MUST NOT do any blocking I/O (DB writes).
     *
     * @param request    the inbound claim message
     * @param sessionId  the WebSocket session ID for per-user error routing
     */
    public void processClaim(ClaimRequest request, String sessionId) {
        // Ensure Redis is warmed up if it was flushed
        if (getGridHashSize() == 0) {
            warmUpFromDatabase();
        }

        String tileId = request.getTileId();
        String userId = request.getUserId();

        // ── Step 1: Validate input ────────────────────────────────────────────
        ErrorMessage validationError = validateClaim(tileId, userId);
        if (validationError != null) {
            sendError(sessionId, validationError);
            return;
        }


        // ── Step 3: Resolve user from server (NEVER trust client data) ────────
        User user;
        try {
            user = userService.getUserById(UUID.fromString(userId));
        } catch (IllegalArgumentException e) {
            sendError(sessionId, ErrorMessage.builder()
                    .type("INVALID_INPUT")
                    .message("Invalid userId format.")
                    .build());
            return;
        }
        if (user == null) {
            sendError(sessionId, ErrorMessage.builder()
                    .type("INVALID_INPUT")
                    .message("User not found. Please join first.")
                    .build());
            return;
        }

        // ── Step 3: Read current tile owner from Redis ────────────────────────
        String existingTileJson = (String) redisTemplate.opsForHash().get(GRID_KEY, tileId);
        String existingOwner = null;
        if (existingTileJson != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> existingTile = objectMapper.readValue(existingTileJson, Map.class);
                existingOwner = (String) existingTile.get("userId");
            } catch (JsonProcessingException e) {
                log.warn("Could not parse existing tile JSON for tileId={}: {}", tileId, e.getMessage());
            }
        }

        // ── Step 4: Three-way toggle logic ────────────────────────────────────
        if (userId.equals(existingOwner)) {
            // ► Case A: User clicks their own tile → DECOLOR (free it)
            handleDecolor(tileId, userId, sessionId);
        } else if (existingOwner == null) {
            // ► Case B: Tile is empty → CLAIM it
            handleClaim(tileId, userId, user, sessionId);
        } else {
            // ► Case C: Tile belongs to someone else → BLOCKED
            log.debug("Blocked claim: tileId={} owned by {}, requester={}", tileId, existingOwner, userId);
            sendError(sessionId, ErrorMessage.builder()
                    .type("OCCUPIED")
                    .message("This tile is already claimed by another player.")
                    .tileId(tileId)
                    .build());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DECOLOR — remove a tile the user already owns
    // ═══════════════════════════════════════════════════════════════════════

    private void handleDecolor(String tileId, String userId, String sessionId) {
        Long result = redisTemplate.execute(
                decolorTileScript,
                List.of(GRID_KEY, tileId),
                userId
        );

        if (result == null || result != 1L) {
            // Race: another thread already cleared/claimed the tile
            log.debug("Decolor race: tileId={} no longer owned by userId={}", tileId, userId);
            return;
        }

        leaderboardService.decrementScore(userId);

        // Broadcast null/empty tile to all clients
        TileUpdate tileUpdate = TileUpdate.builder()
                .tileId(tileId)
                .userId(null)
                .username(null)
                .color(null)
                .claimedAt(null)
                .build();
        messagingTemplate.convertAndSend(TOPIC_GRID, tileUpdate);
        log.debug("Broadcast decolor: tileId={} freed by userId={}", tileId, userId);

        List<LeaderboardEntry> leaderboard = leaderboardService.getTopEntries();
        messagingTemplate.convertAndSend(TOPIC_LEADERBOARD, (Object) Map.of("rankings", leaderboard));

        tilePersistenceService.deleteClaimAsync(tileId);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CLAIM — take an empty tile
    // ═══════════════════════════════════════════════════════════════════════

    private void handleClaim(String tileId, String userId, User user, String sessionId) {
        Instant claimedAt = Instant.now();
        String tileJson = buildTileJson(userId, user.getUsername(), user.getColor(), claimedAt);
        if (tileJson == null) {
            sendError(sessionId, ErrorMessage.builder()
                    .type("SERVER_ERROR")
                    .message("Internal error processing claim.")
                    .build());
            return;
        }

        Long result = redisTemplate.execute(
                claimTileScript,
                List.of(GRID_KEY, tileId),
                tileJson
        );

        if (result == null || result != 1L) {
            // Race: another user just claimed this empty tile
            log.debug("Claim race lost: tileId={} was taken simultaneously", tileId);
            sendError(sessionId, ErrorMessage.builder()
                    .type("OCCUPIED")
                    .message("This tile was just claimed by another player.")
                    .tileId(tileId)
                    .build());
            return;
        }

        leaderboardService.incrementScore(userId);

        TileUpdate tileUpdate = TileUpdate.builder()
                .tileId(tileId)
                .userId(userId)
                .username(user.getUsername())
                .color(user.getColor())
                .claimedAt(claimedAt)
                .build();
        messagingTemplate.convertAndSend(TOPIC_GRID, tileUpdate);
        log.debug("Broadcast tile claim: tileId={} userId={}", tileId, userId);

        List<LeaderboardEntry> leaderboard = leaderboardService.getTopEntries();
        messagingTemplate.convertAndSend(TOPIC_LEADERBOARD, (Object) Map.of("rankings", leaderboard));

        tilePersistenceService.persistClaimAsync(tileId, userId, claimedAt);
    }


    // ═══════════════════════════════════════════════════════════════════════
    // HEARTBEAT
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Refreshes the online presence TTL for a user.
     * Called from /app/heartbeat every 15 seconds client-side.
     */
    public void refreshOnlinePresence(String userId) {
        String key = "online:" + userId;
        redisTemplate.opsForValue().set(key, "1");
        redisTemplate.expire(key, java.time.Duration.ofSeconds(30));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // READ OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Returns the full current grid state from Redis.
     * Called once on page load — never over WebSocket.
     */
    public Map<String, Object> getGridState() {
        if (getGridHashSize() == 0) {
            log.info("getGridState: Redis is empty, triggering lazy database warm-up.");
            warmUpFromDatabase();
        }

        Map<Object, Object> rawEntries = redisTemplate.opsForHash().entries(GRID_KEY);
        Map<String, Object> result = new LinkedHashMap<>();

        for (Map.Entry<Object, Object> entry : rawEntries.entrySet()) {
            String fieldKey = entry.getKey().toString();
            String jsonValue = entry.getValue().toString();
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> tileData = objectMapper.readValue(jsonValue, Map.class);
                result.put(fieldKey, tileData);
            } catch (JsonProcessingException e) {
                log.warn("Skipping malformed tile entry for key={}: {}", fieldKey, e.getMessage());
            }
        }
        return result;
    }

    /**
     * Counts the number of currently online users by scanning Redis "online:*" keys.
     */
    public long getOnlineCount() {
        Set<String> keys = redisTemplate.keys("online:*");
        return keys != null ? keys.size() : 0;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // STARTUP WARM-UP SUPPORT
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Writes a tile entry directly into the Redis grid hash.
     * Used by GridInitializer during cold-start warm-up.
     */
    public void warmUpTile(String tileId, String userId, String username, String color, Instant claimedAt) {
        String json = buildTileJson(userId, username, color, claimedAt);
        if (json != null) {
            redisTemplate.opsForHash().put(GRID_KEY, tileId, json);
        }
    }

    /**
     * Returns the size of the Redis grid hash.
     * Used by GridInitializer to decide whether warm-up is needed.
     */
    public long getGridHashSize() {
        return redisTemplate.opsForHash().size(GRID_KEY);
    }

    /**
     * Rebuilds the Redis state from PostgreSQL.
     * Synchronized to prevent concurrent threads from running the query simultaneously.
     */
    public synchronized void warmUpFromDatabase() {
        long redisGridSize = getGridHashSize();
        if (redisGridSize > 0) {
            log.info("Redis grid already populated ({} tiles). Skipping warm-up.", redisGridSize);
            return;
        }

        log.info("Redis grid is empty. Loading from PostgreSQL...");
        List<Tile> claimedTiles = tileRepository.findAllClaimed();
        int tileCount = 0;

        for (Tile tile : claimedTiles) {
            if (tile.getOwner() == null) continue;

            String tileId   = tile.getTileId();
            String userId   = tile.getOwner().getId().toString();
            String username = tile.getOwner().getUsername();
            String color    = tile.getOwner().getColor();
            Instant claimedAt = tile.getClaimedAt();

            warmUpTile(tileId, userId, username, color, claimedAt);
            tileCount++;

            // Also warm up user cache in Redis
            userService.cacheUserInRedis(tile.getOwner());
        }

        // Rebuild leaderboard sorted set from PostgreSQL aggregation
        List<Object[]> ownerCounts = tileRepository.countTilesByOwner();
        for (Object[] row : ownerCounts) {
            String userId = row[0].toString();  // UUID
            long   score  = ((Number) row[1]).longValue();
            leaderboardService.setScore(userId, score);
        }

        log.info("=== Warm-up complete: {} tiles loaded, {} leaderboard entries rebuilt ===",
                tileCount, ownerCounts.size());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    private ErrorMessage validateClaim(String tileId, String userId) {
        if (tileId == null || !TILE_ID_PATTERN.matcher(tileId).matches()) {
            return ErrorMessage.builder()
                    .type("INVALID_INPUT")
                    .message("Invalid tileId format. Expected '{row}_{col}', e.g. '12_5'.")
                    .build();
        }
        String[] parts = tileId.split("_");
        int row, col;
        try {
            row = Integer.parseInt(parts[0]);
            col = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return ErrorMessage.builder()
                    .type("INVALID_INPUT")
                    .message("tileId coordinates must be integers.")
                    .build();
        }
        if (row < 0 || row >= GRID_ROWS || col < 0 || col >= GRID_COLS) {
            return ErrorMessage.builder()
                    .type("INVALID_INPUT")
                    .message(String.format("Tile coordinates out of range. Grid is %dx%d (0-based).", GRID_ROWS, GRID_COLS))
                    .build();
        }
        if (userId == null || userId.isBlank()) {
            return ErrorMessage.builder()
                    .type("INVALID_INPUT")
                    .message("userId must not be null or empty.")
                    .build();
        }
        return null;
    }

    private String buildTileJson(String userId, String username, String color, Instant claimedAt) {
        try {
            Map<String, Object> tileMap = new LinkedHashMap<>();
            tileMap.put("userId", userId);
            tileMap.put("username", username);
            tileMap.put("color", color);
            tileMap.put("claimedAt", claimedAt.toString());
            return objectMapper.writeValueAsString(tileMap);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize tile JSON for userId={}: {}", userId, e.getMessage());
            return null;
        }
    }

    private void sendError(String sessionId, ErrorMessage error) {
        // Route error to the specific user's private queue only.
        // We use the sessionId as the "user" principal name here;
        // the frontend subscribes to /user/queue/error.
        messagingTemplate.convertAndSendToUser(sessionId, USER_QUEUE_ERROR, (Object) error);
    }


}

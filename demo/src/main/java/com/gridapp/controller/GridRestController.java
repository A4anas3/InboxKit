package com.gridapp.controller;

import com.gridapp.model.dto.*;
import com.gridapp.service.GridService;
import com.gridapp.service.LeaderboardService;
import com.gridapp.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class GridRestController {

    private final UserService       userService;
    private final GridService       gridService;
    private final LeaderboardService leaderboardService;

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/users/join
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Joins (or re-joins) a user by username.
     *
     * Request:  { "username": "Alice" }
     * Response: { "userId": "uuid", "username": "Alice", "color": "#FF5733" }
     *
     * Idempotent: returns existing user data if username already exists.
     */
    @PostMapping("/users/join")
    public ResponseEntity<JoinResponse> joinUser(
            @RequestBody JoinRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        if (request == null || 
            ((request.getUsername() == null || request.getUsername().isBlank()) && 
             (request.getToken() == null || request.getToken().isBlank()) &&
             jwt == null)) {
            log.warn("Join request with both null/blank username and token");
            return ResponseEntity.badRequest().build();
        }

        if (request.getUsername() != null && request.getUsername().trim().length() > 50) {
            return ResponseEntity.badRequest().build();
        }

        log.info("Join request for username={} tokenPresent={} jwtPresent={}", 
                 request.getUsername(), request.getToken() != null && !request.getToken().isBlank(), jwt != null);
        JoinResponse response = userService.joinUser(request, jwt);
        return ResponseEntity.ok(response);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/grid
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the current full grid state.
     * Called ONCE on page load — never again during the session.
     *
     * Response: { "tiles": { "12_5": { "userId", "username", "color", "claimedAt" }, ... } }
     * Only claimed tiles are included; unclaimed tiles are absent.
     */
    @GetMapping("/grid")
    public ResponseEntity<GridResponse> getGrid() {
        Map<String, Object> tiles = gridService.getGridState();
        return ResponseEntity.ok(GridResponse.builder().tiles(tiles).build());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/leaderboard
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the current top-10 leaderboard.
     *
     * Response: { "rankings": [ { "rank", "userId", "username", "color", "score" }, ... ] }
     */
    @GetMapping("/leaderboard")
    public ResponseEntity<Map<String, Object>> getLeaderboard() {
        if (gridService.getGridHashSize() == 0) {
            gridService.warmUpFromDatabase();
        }
        List<LeaderboardEntry> rankings = leaderboardService.getTopEntries();
        return ResponseEntity.ok(Map.of("rankings", rankings));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/online-count
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the current count of online users.
     * Counts Redis keys matching pattern "online:*".
     *
     * Response: { "count": 42 }
     */
    @GetMapping("/online-count")
    public ResponseEntity<Map<String, Object>> getOnlineCount() {
        long count = gridService.getOnlineCount();
        return ResponseEntity.ok(Map.of("count", count));
    }
}

package com.gridapp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gridapp.model.dto.LeaderboardEntry;
import com.gridapp.model.entity.User;
import com.gridapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeaderboardService {

    private static final String LEADERBOARD_KEY = "leaderboard";
    private static final String USERS_HASH_KEY  = "users";
    private static final int    TOP_N            = 10;

    private final RedisTemplate<String, String> redisTemplate;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    /**
     * Increments a user's tile score in the Redis sorted set by 1.
     */
    public void incrementScore(String userId) {
        redisTemplate.opsForZSet().incrementScore(LEADERBOARD_KEY, userId, 1);
    }

    /**
     * Decrements a user's tile score by 1 when they decolor a tile.
     * Clamps at 0 to prevent negative scores.
     */
    public void decrementScore(String userId) {
        Double current = redisTemplate.opsForZSet().score(LEADERBOARD_KEY, userId);
        if (current != null && current > 0) {
            redisTemplate.opsForZSet().incrementScore(LEADERBOARD_KEY, userId, -1);
        }
    }

    /**
     * Seeds the initial score for a user in the leaderboard (used during startup warm-up).
     */
    public void setScore(String userId, long score) {
        redisTemplate.opsForZSet().add(LEADERBOARD_KEY, userId, score);
    }

    /**
     * Reads the top-N entries from the Redis leaderboard sorted set (ZREVRANGE + WITHSCORES),
     * enriches with username/color from Redis user cache or PostgreSQL.
     */
    public List<LeaderboardEntry> getTopEntries() {
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(LEADERBOARD_KEY, 0, TOP_N - 1);

        if (tuples == null || tuples.isEmpty()) {
            return Collections.emptyList();
        }

        List<LeaderboardEntry> entries = new ArrayList<>();
        int rank = 1;

        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            String userId   = tuple.getValue();
            double score    = tuple.getScore() != null ? tuple.getScore() : 0;

            // Resolve username + color
            String username = "Unknown";
            String color    = "#CCCCCC";

            // Try Redis user cache first
            String cachedUser = (String) redisTemplate.opsForHash().get(USERS_HASH_KEY, userId);
            if (cachedUser != null) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> userMap = objectMapper.readValue(cachedUser, Map.class);
                    username = (String) userMap.getOrDefault("username", username);
                    color    = (String) userMap.getOrDefault("color", color);
                } catch (JsonProcessingException e) {
                    log.warn("Could not parse cached user data for userId={}", userId);
                }
            } else {
                // Fallback to PostgreSQL
                try {
                    Optional<User> userOpt = userRepository.findById(UUID.fromString(userId));
                    if (userOpt.isPresent()) {
                        username = userOpt.get().getUsername();
                        color    = userOpt.get().getColor();
                    }
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid userId in leaderboard: {}", userId);
                }
            }

            entries.add(LeaderboardEntry.builder()
                    .rank(rank++)
                    .userId(userId)
                    .username(username)
                    .color(color)
                    .score((long) score)
                    .build());
        }

        return entries;
    }
}

package com.gridapp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gridapp.model.dto.JoinRequest;
import com.gridapp.model.dto.JoinResponse;
import com.gridapp.model.entity.User;
import com.gridapp.repository.UserRepository;
import jakarta.persistence.PersistenceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private static final String USERS_HASH_KEY = "users";

    private final UserRepository userRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final JwtService jwtService;

    @Autowired
    @Lazy
    private UserService self;

    /**
     * Non-transactional entry point that handles concurrent join races.
     * On optimistic locking or unique constraint failure (two threads racing
     * to insert the same user), the second thread simply fetches the user
     * that the first thread already persisted.
     */
    public JoinResponse joinUser(JoinRequest request, org.springframework.security.oauth2.jwt.Jwt jwt) {
        try {
            return self.joinUserTransactional(request, jwt);
        } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException
                | PersistenceException ex) {
            log.info("Concurrent join race detected. Waiting for winner to commit, then fetching user.");
            UUID userUuid = resolveUuid(request, jwt);
            // The winning thread may not have committed yet — retry with back-off
            for (int attempt = 1; attempt <= 6; attempt++) {
                try { Thread.sleep(50L * attempt); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                Optional<User> found = userRepository.findById(userUuid);
                if (found.isPresent()) {
                    User u = found.get();
                    log.info("Recovered user {} on attempt {}", u.getId(), attempt);
                    return JoinResponse.builder()
                            .userId(u.getId().toString())
                            .username(u.getUsername())
                            .color(u.getColor())
                            .build();
                }
            }
            throw new RuntimeException("User not found after concurrent join race — winner never committed?", ex);
        }
    }

    /** Resolves the deterministic UUID from the request / JWT without touching the DB. */
    private UUID resolveUuid(JoinRequest request, org.springframework.security.oauth2.jwt.Jwt jwt) {
        if (jwt != null) {
            return UUID.fromString(jwt.getSubject());
        } else if (request.getToken() != null && !request.getToken().isBlank()) {
            com.auth0.jwt.interfaces.DecodedJWT decodedJwt = jwtService.verifyToken(request.getToken());
            return UUID.fromString(jwtService.getUserIdFromToken(decodedJwt));
        } else {
            String username = request.getUsername() != null ? request.getUsername().trim() : "Player";
            return UUID.nameUUIDFromBytes(username.getBytes());
        }
    }

    @Transactional
    public JoinResponse joinUserTransactional(JoinRequest request, org.springframework.security.oauth2.jwt.Jwt jwt) {
        String username = request.getUsername() != null ? request.getUsername().trim() : "Player";

        UUID userUuid;
        if (jwt != null) {
            String userIdStr = jwt.getSubject();
            userUuid = UUID.fromString(userIdStr);

            if (request.getUsername() == null || request.getUsername().isBlank()) {
                String email = jwt.getClaimAsString("email");
                if (email != null && email.contains("@")) {
                    username = email.split("@")[0];
                }
            }
        } else if (request.getToken() != null && !request.getToken().isBlank()) {
            com.auth0.jwt.interfaces.DecodedJWT decodedJwt = jwtService.verifyToken(request.getToken());
            String userIdStr = jwtService.getUserIdFromToken(decodedJwt);
            userUuid = UUID.fromString(userIdStr);

            if (request.getUsername() == null || request.getUsername().isBlank()) {
                String email = jwtService.getEmailFromToken(decodedJwt);
                if (email != null && email.contains("@")) {
                    username = email.split("@")[0];
                }
            }
        } else {
            // Fallback for developer testing
            userUuid = UUID.nameUUIDFromBytes(username.getBytes());
        }

        // Check if user already exists by ID
        Optional<User> existingUser = userRepository.findById(userUuid);
        if (existingUser.isPresent()) {
            User user = existingUser.get();
            log.info("Returning existing user: {} ({})", user.getUsername(), user.getId());
            return JoinResponse.builder()
                    .userId(user.getId().toString())
                    .username(user.getUsername())
                    .color(user.getColor())
                    .build();
        }

        // Ensure username is unique (append suffix if conflict)
        String finalUsername = username;
        int suffix = 0;
        while (userRepository.findByUsername(finalUsername).isPresent()) {
            suffix++;
            finalUsername = username + suffix;
        }

        // Create new user with a distinct color
        String color = generateDistinctColor();
        User newUser = User.builder()
                .id(userUuid)
                .username(finalUsername)
                .color(color)
                .tilesOwned(0)
                .build();

        newUser = userRepository.save(newUser);
        log.info("Created new user: {} ({}) with color {}", newUser.getUsername(), newUser.getId(), newUser.getColor());

        // Cache user data in Redis hash "users"
        cacheUserInRedis(newUser);

        return JoinResponse.builder()
                .userId(newUser.getId().toString())
                .username(newUser.getUsername())
                .color(newUser.getColor())
                .build();
    }

    /**
     * Fetches a user by ID — first checks Redis cache, then falls back to PostgreSQL.
     * Returns null if not found.
     */
    public User getUserById(UUID userId) {
        // Try Redis first
        String cachedJson = (String) redisTemplate.opsForHash().get(USERS_HASH_KEY, userId.toString());
        if (cachedJson != null) {
            try {
                return objectMapper.readValue(cachedJson, User.class);
            } catch (JsonProcessingException e) {
                log.warn("Failed to deserialize cached user {}: {}", userId, e.getMessage());
            }
        }

        // Fallback to PostgreSQL
        Optional<User> userOpt = userRepository.findById(userId);
        userOpt.ifPresent(this::cacheUserInRedis);
        return userOpt.orElse(null);
    }

    /**
     * Caches a user's data in the Redis "users" hash field.
     */
    public void cacheUserInRedis(User user) {
        try {
            Map<String, Object> userMap = new LinkedHashMap<>();
            userMap.put("id", user.getId().toString());
            userMap.put("username", user.getUsername());
            userMap.put("color", user.getColor());
            String json = objectMapper.writeValueAsString(userMap);
            redisTemplate.opsForHash().put(USERS_HASH_KEY, user.getId().toString(), json);
        } catch (JsonProcessingException e) {
            log.error("Failed to cache user {} in Redis: {}", user.getId(), e.getMessage());
        }
    }

    /**
     * Generates a visually distinct HSL color.
     * Uses golden-ratio hue spacing to maximize perceptual difference.
     */
    private String generateDistinctColor() {
        // Count existing users to space hue evenly
        long userCount = userRepository.count();
        // Golden ratio hue spacing: 137.508° per step
        double hue = (userCount * 137.508) % 360;
        int h = (int) hue;
        // Vibrant saturation and medium-high lightness for readability
        return hslToHex(h, 70, 55);
    }

    /**
     * Converts HSL values to a hex color string (#RRGGBB).
     */
    private String hslToHex(int h, int s, int l) {
        double sNorm = s / 100.0;
        double lNorm = l / 100.0;

        double c = (1 - Math.abs(2 * lNorm - 1)) * sNorm;
        double x = c * (1 - Math.abs((h / 60.0) % 2 - 1));
        double m = lNorm - c / 2;

        double r, g, b;
        if (h < 60)      { r = c; g = x; b = 0; }
        else if (h < 120){ r = x; g = c; b = 0; }
        else if (h < 180){ r = 0; g = c; b = x; }
        else if (h < 240){ r = 0; g = x; b = c; }
        else if (h < 300){ r = x; g = 0; b = c; }
        else             { r = c; g = 0; b = x; }

        int ri = (int) Math.round((r + m) * 255);
        int gi = (int) Math.round((g + m) * 255);
        int bi = (int) Math.round((b + m) * 255);

        return String.format("#%02X%02X%02X", ri, gi, bi);
    }
}

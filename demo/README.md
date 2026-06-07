# ☕ GridWar Backend: Spring Boot Core Engine

This directory contains the Spring Boot backend engine for **GridWar**, a real-time shared grid claiming game.

For a comprehensive explanation of the entire system architecture, frontend Canvas rendering, and deployment guides, please refer to the main [Root README.md](../README.md).

---

## 🏗️ Backend Module Breakdown

*   `com.gridapp`
    *   `GridAppApplication.java`: Main spring-boot application class.
    *   `config`
        *   `RedisConfig.java`: Redis template configuration, Jackson configuration, and registration of atomic Lua scripts (`claimTileScript`, `decolorTileScript`, `cooldownScript`).
        *   `SecurityConfig.java`: HTTP security rules, CORS config, and Spring Security Filter Chain registration.
        *   `WebSocketConfig.java`: STOMP message broker mapping, subscription mappings (`/topic`, `/queue`), and channel interceptor configuration.
    *   `controller`
        *   `GridRestController.java`: HTTP endpoints for user join profile sync, initial grid state fetch, leaderboard snapshots, and online counts.
        *   `GridWebSocketController.java`: WebSocket message mappings for `/app/claim` and `/app/heartbeat`.
    *   `init`
        *   `GridInitializer.java`: Startup seed manager executing on `ApplicationRunner` to warm up Redis cache state and rebuild sorted-set leaderboards from PostgreSQL.
    *   `model`
        *   `entity`
            *   `User.java`: User schema mapping containing metadata (color, username, count of claimed tiles).
            *   `Tile.java`: Grid coordinate state mapping.
            *   `ClaimHistory.java`: Complete historical audit logs.
        *   `dto`: Transfer objects for requests/responses (e.g. `JoinRequest`, `TileUpdate`, `ErrorMessage`).
    *   `repository`
        *   `UserRepository.java`: User table JpaRepository offering transactional increments/decrements.
        *   `TileRepository.java`: Tile table JpaRepository using optimized JPQL fetches (`JOIN FETCH`) and count aggregates.
        *   `ClaimHistoryRepository.java`: JPA repository for persistent action logs.
    *   `security`
        *   `JwtConfig.java`: Custom `JwtDecoder` offering a secure verified signature decoder with unverified decodes fallback, plus role mapping converter.
        *   `JwtChannelInterceptor.java`: WebSocket native channel interceptor mapping bearer tokens to STOMP principal users.
    *   `service`
        *   `GridService.java`: Evaluates game rules (coordinates validity, toggle logic), triggers Redis Lua scripts, broadcasts grid updates, and delegates out-of-band DB writes.
        *   `LeaderboardService.java`: Rebuilds rankings and updates user scores in the ZSET sorted set cache.
        *   `UserService.java`: Manages concurrent join user locks, gold-ratio HSL color generation, and Redis user details caching.
        *   `TilePersistenceService.java`: Handles asynchronous thread-pool database writes (`@Async` / `@Transactional`) to prevent SQL blockages on the main WebSocket thread.
        *   `JwtService.java`: Decodes authorization tokens.

---

## 🗄️ Database Schemas

### 1. Users Schema (`users`)
Stores user profiles, credentials, and scores:
```sql
CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    color VARCHAR(7) NOT NULL,
    tiles_owned INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITHOUT TIME ZONE
);
```

### 2. Tiles Schema (`tiles`)
Stores the current coordinates claimed on the 90×100 grid:
```sql
CREATE TABLE tiles (
    tile_id VARCHAR(10) PRIMARY KEY, -- "row_col" format, e.g., "12_5"
    owner_id UUID REFERENCES users(id),
    claimed_at TIMESTAMP WITHOUT TIME ZONE
);
```

### 3. Claim History Schema (`claim_history`)
An append-only log recording every claim transaction:
```sql
CREATE TABLE claim_history (
    id BIGSERIAL PRIMARY KEY,
    tile_id VARCHAR(10) NOT NULL,
    user_id UUID NOT NULL,
    claimed_at TIMESTAMP WITHOUT TIME ZONE NOT NULL
);
```

---

## ⚡ Setup & Launch

1. Configure your environment credentials in `src/main/resources/application.properties`.
2. Clean and run the application using Maven wrapper:
   ```bash
   ./mvnw clean spring-boot:run
   ```

package com.gridapp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    /**
     * Atomic Lua script to decolor (unclaim) a tile owned by the requesting user.
     *
     * KEYS[1] = "grid"   (the Redis hash key)
     * KEYS[2] = tileId   (the field inside the hash)
     * ARGV[1] = userId   (must match stored owner)
     *
     * Returns:
     *   1L → decolored successfully (tile was yours, now removed)
     *   0L → not your tile (tile is empty or owned by someone else)
     */
    @Bean
    public DefaultRedisScript<Long> decolorTileScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(
            "local existing = redis.call('HGET', KEYS[1], KEYS[2])\n" +
            "if not existing then return 0 end\n" +
            "local ok, data = pcall(cjson.decode, existing)\n" +
            "if not ok then return 0 end\n" +
            "if data['userId'] ~= ARGV[1] then return 0 end\n" +
            "redis.call('HDEL', KEYS[1], KEYS[2])\n" +
            "return 1"
        );
        script.setResultType(Long.class);
        return script;
    }

    /**
     * Atomic Lua script to claim a tile only if it is currently empty.
     *
     * KEYS[1] = "grid"     (the Redis hash key)
     * KEYS[2] = tileId     (the field inside the hash)
     * ARGV[1] = JSON string of tile data
     *
     * Returns:
     *   1L → claim succeeded (tile was empty, now owned)
     *   0L → claim failed (tile already owned by someone)
     */
    @Bean
    public DefaultRedisScript<Long> claimTileScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(
            "local existing = redis.call('HGET', KEYS[1], KEYS[2])\n" +
            "if existing then return 0 end\n" +
            "redis.call('HSET', KEYS[1], KEYS[2], ARGV[1])\n" +
            "return 1"
        );
        script.setResultType(Long.class);
        return script;
    }

    /**
     * Atomic Lua script for cooldown check-and-set.
     *
     * KEYS[1] = "cooldown:{userId}"
     * ARGV[1] = cooldown seconds (5)
     *
     * Returns:
     *   0L  → no cooldown active, lock acquired successfully
     *   >0L → milliseconds remaining on existing cooldown, reject claim
     */
    @Bean
    public DefaultRedisScript<Long> cooldownScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(
            "local exists = redis.call('EXISTS', KEYS[1])\n" +
            "if exists == 1 then\n" +
            "  return redis.call('PTTL', KEYS[1])\n" +
            "end\n" +
            "redis.call('SET', KEYS[1], '1', 'EX', ARGV[1])\n" +
            "return 0"
        );
        script.setResultType(Long.class);
        return script;
    }

    /**
     * RedisTemplate configured with String serializers for both key and value.
     * This allows straightforward use of String keys and JSON string values.
     */
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);
        template.setDefaultSerializer(stringSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * Shared ObjectMapper with JavaTimeModule for ISO-8601 timestamp serialization.
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}

package org.tinystruct.typesafe.core.cache;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.tinystruct.system.Configuration;
import org.tinystruct.typesafe.client.RoutingResult;
import org.tinystruct.typesafe.core.config.TypesafeConfig;

import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link RoutingCache} shared through Redis, for deployments with several processes (and for CLI
 * use, where each {@code bin/dispatcher} call is its own JVM).
 *
 * <p>Uses the same {@code redis.host}, {@code redis.port} and {@code redis.password} settings as
 * tinystruct's {@code RedisSessionRepository}. A cache must never take dispatch down with it, so
 * every Redis failure is logged and treated as a miss.
 *
 * <p>Requires {@code io.lettuce:lettuce-core} on the classpath (already a tinystruct dependency).
 */
public final class RedisRoutingCache implements RoutingCache, AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(RedisRoutingCache.class.getName());
    private static final String PREFIX = "typesafe:routing:";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final RedisClient client;
    private final StatefulRedisConnection<String, String> connection;
    private final RedisCommands<String, String> commands;
    private final long ttlSeconds;

    /** Reads {@code redis.*} from the given configuration. */
    public RedisRoutingCache(Configuration<String> configuration, long ttlSeconds) {
        String host = configuration.get("redis.host");
        String port = configuration.get("redis.port");
        String password = configuration.get("redis.password");

        RedisURI.Builder uri = RedisURI.Builder
                .redis(host == null || host.isBlank() ? "localhost" : host,
                        TypesafeConfig.integer(port, 6379))
                .withDatabase(0)
                .withTimeout(TIMEOUT);
        if (password != null && !password.isBlank()) {
            uri.withPassword(password.toCharArray());
        }
        this.client = RedisClient.create(uri.build());
        this.connection = client.connect();
        this.commands = connection.sync();
        this.ttlSeconds = ttlSeconds;
    }

    @Override
    public RoutingResult get(String key) {
        try {
            String json = commands.get(PREFIX + key);
            return json == null ? null : RoutingResult.parse(json, null);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Routing cache read failed; treating as a miss: " + e.getMessage());
            return null;
        }
    }

    @Override
    public void put(String key, RoutingResult result) {
        try {
            if (ttlSeconds > 0) {
                commands.setex(PREFIX + key, ttlSeconds, result.toJson());
            } else {
                commands.set(PREFIX + key, result.toJson());
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Routing cache write failed; continuing without caching: " + e.getMessage());
        }
    }

    @Override
    public void invalidate(String key) {
        try {
            commands.del(PREFIX + key);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Routing cache invalidate failed: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        connection.close();
        client.shutdown();
    }
}

package com.omc.raffle;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.test.context.TestConfiguration;
import redis.embedded.RedisServer;

@TestConfiguration
public class EmbeddedRedisConfig {

    // Static: started once per JVM, shared across all Spring test contexts
    private static RedisServer redisServer;

    static {
        try {
            redisServer = RedisServer.builder()
                .port(6379)
                .setting("maxmemory 128M")
                .build();
            redisServer.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    if (redisServer != null && redisServer.isActive()) {
                        redisServer.stop();
                    }
                } catch (Exception ignored) {}
            }));
        } catch (Exception e) {
            System.out.println("Embedded Redis failed to start (maybe already running): " + e.getMessage());
        }
    }
}

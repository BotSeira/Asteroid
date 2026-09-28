package xyz.zcraft.asteroid.config;

public record WebserverConfig(
        int port,
        int maxThreads,
        int minThreads,
        int idleTimeout
) {
}

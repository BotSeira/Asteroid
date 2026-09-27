package xyz.zcraft.asteroid.config;

public record AppConfig(
        AsteroidConfig asteroid,
        WebserverConfig webserver
) {
}

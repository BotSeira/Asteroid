package xyz.zcraft.asteroid.config;

public record BraveSearchConfig(
        String apiKey,
        ProxyConfig proxy
) {
    public record ProxyConfig(
            boolean enabled,
            String host,
            int port
    ){}
}

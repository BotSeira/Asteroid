package xyz.zcraft.asteroid.config;

public record AppConfig(
        AsteroidConfig asteroid,
        WebserverConfig webserver,
        NcmConfig ncm,
        BraveSearchConfig braveSearch,
        BrowserConfig browser,
        CosConfig cos,
        GachaConfig gacha
) {
}

package xyz.zcraft.asteroid.gacha.data;

public record Own(
        String username,
        long count,
        String bestRarity,
        long value,
        long lowest
) {
}

package xyz.zcraft.asteroid.gacha.data;

public record GachaUser(
        String id,
        String username,
        String role,
        long osuUserID,
        String avatarURL,
        String coverURL,
        String countryCode,
        String createdAt,
        String lastSeenAt,
        boolean online,
        Guild guild,
        Object backer
) {
    public record Guild(
            String tag,
            long base,
            long accent
    ) {
    }
}

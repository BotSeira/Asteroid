package xyz.zcraft.asteroid.config;

public record NcmConfig(
        String endpoint,
        AudioFpConfig audioFp
) {
    public record AudioFpConfig(
            String endpoint,
            String token
    ) {
    }
}

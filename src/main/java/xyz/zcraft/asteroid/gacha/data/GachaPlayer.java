package xyz.zcraft.asteroid.gacha.data;

import java.util.List;

public record GachaPlayer(
        Long id,
        String username,
        String countryCode,
        String avatarUrl,
        String coverUrl,
        Long followerCount,
        Long mappingFollowerCount,
        Long rankedMaps,
        Long guestMaps,
        Long lovedMaps,
        Long popularity,
        Long globalRank, Double pp,
        String currentRarity,
        Long mintedCount,
        Boolean pullable,
        String lastSyncedAt,
        String cls,
        String skill,
        String passive,
        Boolean classOverridden,
        Boolean rarityOverridden,
        String name,
        Long founded,
        List<String> members
) {

    public static String getClassName(String cls) {
        return switch (cls) {
            case "dps" -> "输出";
            case "cc" -> "控制";
            case "support" -> "辅助";
            case "tank" -> "坦克";
            default -> "未知(" + cls + ")";
        };
    }

    public static String getRarityName(String rarity) {
        return switch (rarity) {
            case "divine" -> "神圣";
            case "mythic" -> "神话";
            case "legendary" -> "传说";
            case "epic" -> "史诗";
            case "rare" -> "稀有";
            case "uncommon" -> "优秀";
            case "common" -> "普通";
            default -> "未知(" + rarity + ")";
        };
    }
}

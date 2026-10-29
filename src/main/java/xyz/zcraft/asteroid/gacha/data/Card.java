package xyz.zcraft.asteroid.gacha.data;

import java.util.List;

public record Card(
        String id,
        long cardNo,
        long serialNo,
        String rarity,
        long value,
        boolean locked,
        String status,
        long level,
        long xp,
        long ascension,
        long eidolon,
        String skill,
        String passive,
        long rerolls,
        Object sig,
        List<Object> mods,
        long followersAtMint,
        long mapperSubsAtMint,
        long popularityAtMint,
        long rarityPopularity,
        long rulesetVersion,
        Object mintedAt,
        Object owner,
        Object player) {
}

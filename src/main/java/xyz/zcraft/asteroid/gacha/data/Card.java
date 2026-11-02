package xyz.zcraft.asteroid.gacha.data;

import xyz.zcraft.asteroid.gacha.data.market.Sellable;

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
        String mintedAt,
        GachaUser owner,
        GachaPlayer player) implements Sellable {
}

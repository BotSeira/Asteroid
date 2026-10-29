package xyz.zcraft.asteroid.gacha.data;

import java.util.List;

public record GachaProfile(
        GachaUser user,
        Object backerStyle,
        List<Object> badges,
        String bio,
        String bannerURL,
        Object bannerCardID,
        Object accent,
        Object layout,
        Object equipped,
        Object showcase,
        List<Object> pulls,
        Object stats) {
}
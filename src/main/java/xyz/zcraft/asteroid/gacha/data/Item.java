package xyz.zcraft.asteroid.gacha.data;

import xyz.zcraft.asteroid.gacha.data.market.Sellable;

public record Item(
        String id,
        long qty
) implements Sellable {
}

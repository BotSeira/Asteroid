package xyz.zcraft.asteroid.gacha.data.market;

import java.util.List;

public record MarketListing<T extends Sellable>(List<MarketOrder<T>> orders, int total) {
}

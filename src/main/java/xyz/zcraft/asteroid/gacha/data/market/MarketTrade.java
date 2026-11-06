package xyz.zcraft.asteroid.gacha.data.market;

import java.time.Instant;

public record MarketTrade(String id, Instant soldAt, double unitPrice, long qty) {
}

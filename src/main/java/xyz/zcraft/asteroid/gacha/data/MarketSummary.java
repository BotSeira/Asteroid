package xyz.zcraft.asteroid.gacha.data;

import com.google.gson.JsonObject;

import java.math.BigDecimal;
import java.time.Instant;

/** Latest market observation displayed above the charts. */
public record MarketSummary(
        BigDecimal listed,
        BigDecimal lowest,
        BigDecimal median,
        BigDecimal highest,
        BigDecimal sales,
        BigDecimal days,
        String updatedAt,
        String sampledAt
) {
    public static MarketSummary fromJson(JsonObject snapshot) {
        return new MarketSummary(
                number(snapshot, "listed"), number(snapshot, "lowest"),
                number(snapshot, "median"), number(snapshot, "highest"),
                number(snapshot, "sales"), number(snapshot, "days"),
                snapshot.get("updatedAt").getAsString(),
                Instant.ofEpochMilli(snapshot.get("time").getAsLong()).toString()
        );
    }

    private static BigDecimal number(JsonObject snapshot, String key) {
        var value = snapshot.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsBigDecimal();
    }
}

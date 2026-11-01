package xyz.zcraft.asteroid.gacha.data;

import com.google.gson.JsonObject;

public record MarketItem(String orderId, String itemId, long quantity, double price) {
    public MarketItem {
        if (orderId == null || orderId.isBlank() || itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("Market order and item IDs are required");
        }
        if (quantity <= 0 || !Double.isFinite(price) || price < 0) {
            throw new IllegalArgumentException("Invalid market order price or quantity");
        }
    }

    public static MarketItem fromJson(JsonObject order) {
        var item = order.getAsJsonObject("item");
        return new MarketItem(order.get("id").getAsString(), item.get("id").getAsString(),
                item.get("qty").getAsLong(), order.get("price").getAsDouble());
    }

    public double unitPrice() {
        return price / quantity;
    }
}

package xyz.zcraft.asteroid.gacha.data.market;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import xyz.zcraft.asteroid.gacha.data.GachaUser;
import xyz.zcraft.asteroid.gacha.data.Item;

import java.lang.reflect.Type;
import java.util.List;

public record MarketOrder<T extends Sellable>(
        String id,
        long price,
        long feePaid,
        String createdAt,
        GachaUser seller,
        T obj, // item / card / mod / stone
        String mode,
        Object auction
) {
    private static final Gson GSON = new Gson();

    public static <T extends Sellable> MarketOrder<T> parse(JsonElement elem, Class<T> itemClass) {
        if (!(elem instanceof JsonObject)) {
            throw new JsonParseException("Expected JsonObject");
        }

        JsonObject json = elem.getAsJsonObject();

        JsonObject normalized = json.deepCopy();

        JsonElement obj = null;

        for (String key : List.of("item", "card", "mod", "stone")) {
            JsonElement value = json.get(key);

            if (value != null && !value.isJsonNull()) {
                if (obj != null) {
                    throw new JsonParseException("Multiple sellable objects");
                }
                obj = value;
            }
        }

        if (obj == null) {
            throw new JsonParseException("Missing sellable object");
        }

        normalized.add("obj", obj);

        Type type = TypeToken.getParameterized(MarketOrder.class, itemClass).getType();

        return GSON.fromJson(normalized, type);
    }

    public static double getUnitPrice(MarketOrder<Item> item) {
        return (double) item.price() / item.obj().qty();
    }
}

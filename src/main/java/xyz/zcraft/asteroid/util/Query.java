package xyz.zcraft.asteroid.util;

import jakarta.annotation.Nullable;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

public class Query {
    private final HashMap<String, String> params = new HashMap<>();

    public static Query create() {
        return new Query();
    }

    public Query param(String key, String value) {
        params.put(key, URLEncoder.encode(value, StandardCharsets.UTF_8));
        return this;
    }

    public Query param(String key, @Nullable Object value) {
        if (value != null) {
            params.put(key, URLEncoder.encode(value.toString(), StandardCharsets.UTF_8));
        }
        return this;
    }

    public String build() {
        StringBuilder builder = new StringBuilder("?");
        params.forEach((key, value) -> builder.append(key).append("=").append(value).append("&"));
        builder.deleteCharAt(builder.length() - 1);
        return builder.toString();
    }
}

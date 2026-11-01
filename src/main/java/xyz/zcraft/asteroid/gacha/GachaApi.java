package xyz.zcraft.asteroid.gacha;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.zcraft.asteroid.gacha.data.GachaPlayer;
import xyz.zcraft.asteroid.gacha.data.Own;
import xyz.zcraft.asteroid.util.Query;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class GachaApi {
    private final static Logger LOG = LogManager.getLogger(GachaApi.class);
    private final static Gson GSON = new Gson();
    private final static HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    @Getter
    private final static String baseUrl = "https://osugacha.com";

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(CLIENT::close));
    }

    public static List<Own> getUserCardOwners(long uid) {
        final HttpResponse<String> response = get("/api/players/" + uid + "/owners");
        final JsonArray array = JsonParser.parseString(response.body()).getAsJsonArray();
        List<Own> result = new ArrayList<>(array.size());

        for (JsonElement elem : array) {
            result.add(GSON.fromJson(elem, Own.class));
        }

        return result;
    }

    public static List<GachaPlayer> searchPlayers(String query, int limit, int offset, String sort) {
        final HttpResponse<String> response = get("/api/players", Query.create()
                .param("q", query)
                .param("limit", String.valueOf(limit))
                .param("offset", String.valueOf(offset))
                .param("sort", sort)
        );

        final JsonArray array = JsonParser.parseString(response.body()).getAsJsonObject().get("items").getAsJsonArray();
        List<GachaPlayer> result = new ArrayList<>(array.size());

        for (JsonElement elem : array) {
            result.add(GSON.fromJson(elem, GachaPlayer.class));
        }

        return result;
    }

    public static com.google.gson.JsonObject getMarketGuide(String item) {
        return marketJson("/api/market/guide", Query.create().param("itemId", item));
    }

    public static com.google.gson.JsonObject getMarketListings(int offset) {
        return marketJson("/api/market/listings", Query.create().param("mode", "bin")
                .param("kind", "item").param("sort", "newest")
                .param("limit", "100").param("offset", String.valueOf(offset)));
    }

    private static com.google.gson.JsonObject marketJson(String path, Query query) {
        var response = get(path, query);
        if (response.statusCode() != 200) throw new IllegalStateException("Market HTTP " + response.statusCode());
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }
    public static String getProfilePageUrl(String username) {
        return baseUrl + "/u/" + username;
    }

    private static HttpResponse<String> get(String path) {
        return get(path, Query.create());
    }

    private static HttpResponse<String> get(String path, Query query) {
        try {
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path + query.build()))
                    .timeout(Duration.ofSeconds(30)).GET()
                    .build();

            final HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                LOG.warn("Unexpected status code {} when GET {}", response.statusCode(), path);
            }

            return response;
        } catch (IOException | InterruptedException e) {
            LOG.error("Failed to GET {}", path, e);
            throw new RuntimeException(e);
        }
    }
}

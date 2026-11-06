package xyz.zcraft.asteroid.gacha;

import com.google.gson.*;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import xyz.zcraft.asteroid.gacha.data.Card;
import xyz.zcraft.asteroid.gacha.data.GachaPlayer;
import xyz.zcraft.asteroid.gacha.data.Item;
import xyz.zcraft.asteroid.gacha.data.Own;
import xyz.zcraft.asteroid.gacha.data.market.*;
import xyz.zcraft.asteroid.util.Query;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
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

    public static List<Own> getUserCardOwners(String apiKey, long uid) {
        final HttpResponse<String> response = get(apiKey, "/api/players/" + uid + "/owners");
        final JsonArray array = JsonParser.parseString(response.body()).getAsJsonArray();
        List<Own> result = new ArrayList<>(array.size());

        for (JsonElement elem : array) {
            result.add(GSON.fromJson(elem, Own.class));
        }

        return result;
    }

    public static List<GachaPlayer> searchPlayers(String apiKey, String query, int limit, int offset, String sort) {
        final HttpResponse<String> response = get(apiKey, "/api/players", Query.create()
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

    public static Guide getItemGuide(String apiKey, String itemId) {
        return getGuide(apiKey, GuideIdentifier.Item.of(itemId));
    }

    public static Guide getCardGuide(String apiKey, String username, boolean sig) {
        return getCardGuide(apiKey, Identifier.Card.of(username, sig));
    }

    public static Guide getCardGuide(String apiKey, Identifier.Card identifier) {
        final MarketListing<Card> newest = getMarketItemListings(apiKey, 10, 0, "newest", identifier, Card.class);
        final List<MarketOrder<Card>> marketOrders = newest.orders();
        return marketOrders.stream()
                .filter(e -> identifier.getUsername().equalsIgnoreCase(e.obj().player().username()))
                .findFirst()
                .map(MarketOrder::obj)
                .map(GuideIdentifier.Card::from)
                .map(id -> getGuide(apiKey, id))
                .orElse(Guide.empty());
    }

    public static JsonObject getMarketItemListings(String apiKey, int offset) {
        return getSafe(apiKey, "/api/market/listings", Query.create().param("mode", "bin")
                .param("kind", "item").param("sort", "newest")
                .param("limit", "100").param("offset", String.valueOf(offset)));
    }

    public static List<MarketTrade> getMarketHistory(String apiKey, Identifier identifier, Instant from, Instant to) {
        Query query = Query.create().param("mode", "bin").param("from", from).param("to", to).param("limit", 100);
        if (identifier instanceof Identifier.Item item) {
            query.param("kind", "item").param("itemId", item.getItemId());
        } else if (identifier instanceof Identifier.Card card) {
            query.param("kind", "card").param("player", card.getUsername());
        } else {
            throw new IllegalArgumentException("Missing market identifier");
        }

        var trades = new ArrayList<MarketTrade>();
        var ids = new HashSet<String>();
        var cursors = new HashSet<String>();
        while (true) {
            var page = getSafe(apiKey, "/api/market/history", query);
            for (var element : page.getAsJsonArray("items")) {
                var row = element.getAsJsonObject();
                if (identifier instanceof Identifier.Card card) {
                    var details = row.getAsJsonObject("card");
                    boolean signed = details.has("sig") && !details.get("sig").isJsonNull();
                    if (signed != card.isSig() || !details.get("player").getAsString().equalsIgnoreCase(card.getUsername()))
                        continue;
                }
                var soldAt = Instant.parse(row.get("soldAt").getAsString());
                var id = row.get("id").getAsString();
                if (!soldAt.isBefore(from) && soldAt.isBefore(to) && ids.add(id)) {
                    trades.add(new MarketTrade(id, soldAt, row.get("unitPrice").getAsDouble(), row.get("qty").getAsLong()));
                }
            }
            var next = page.get("next");
            if (next == null || next.isJsonNull() || next.getAsString().isBlank()) break;
            if (!cursors.add(next.getAsString())) throw new IllegalStateException("Repeated market history cursor");
            query.param("cursor", next.getAsString());
        }
        trades.sort(Comparator.comparing(MarketTrade::soldAt).thenComparing(MarketTrade::id));
        return trades;
    }

    static Guide getGuide(String apiKey, GuideIdentifier identifier) {
        LOG.debug("Getting guide for {}", identifier);
        return GSON.fromJson(getSafe(apiKey, "/api/market/guide", identifier.getGuideQueryParam()), Guide.class);
    }

    public static <T extends Sellable> MarketListing<T> getMarketItemListings(
            String apiKey, int limit, int offset, String sort, @NotNull Identifier identifier, Class<T> itemClass
    ) {
        LOG.debug("Scanning market listings with {} {} {} {} {}", limit, offset, sort, identifier, itemClass.getName());
        String kind;

        if (Item.class == itemClass) {
            kind = "item";
        } else if (Card.class == itemClass) {
            kind = "card";
        } else {
            throw new IllegalArgumentException("Unsupported item class" + itemClass.getName());
        }

        final JsonObject result = getSafe(apiKey, "/api/market/listings", Query.create()
                .param("mode", "bin")
                .param("kind", kind)
                .param("sort", sort)
                .param("limit", limit)
                .param("offset", offset)
                .merge(identifier.getListingQueryParam())
        );

        final JsonArray itemsArr = result.getAsJsonArray("items");

        List<MarketOrder<T>> marketOrders = new ArrayList<>();
        for (JsonElement elem : itemsArr) {
            marketOrders.add(MarketOrder.parse(elem, itemClass));
        }

        return new MarketListing<>(marketOrders, result.get("total").getAsInt());
    }

    private static JsonObject getSafe(String apiKey, String path, Query query) {
        var response = get(apiKey, path, query);
        if (response.statusCode() != 200) throw new IllegalStateException("HTTP " + response.statusCode());
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    public static String getProfilePageUrl(String username) {
        return baseUrl + "/u/" + username;
    }

    private static HttpResponse<String> get(String apiKey, String path) {
        return get(apiKey, path, Query.create());
    }

    private static HttpResponse<String> get(String apiKey, String path, Query query) {
        try {
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path + query.build()))
                    .header("Authorization", "Bearer " + apiKey)
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

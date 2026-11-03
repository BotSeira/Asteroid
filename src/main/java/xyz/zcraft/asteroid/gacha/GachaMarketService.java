package xyz.zcraft.asteroid.gacha;

import com.google.gson.JsonArray;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.zcraft.asteroid.config.GachaConfig;
import xyz.zcraft.asteroid.gacha.data.Card;
import xyz.zcraft.asteroid.gacha.data.Item;
import xyz.zcraft.asteroid.gacha.data.market.*;
import xyz.zcraft.asteroid.util.StrictRateGate;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

public final class GachaMarketService implements AutoCloseable {
    public static final Set<String> ITEMS = Set.of(
            "replay_1", "replay_2", "replay_3",
            "boss_trophy", "platinum_trophy", "diamond_trophy", "emerald_trophy",
            "insignia_tank_1", "insignia_tank_2", "insignia_tank_3", "insignia_tank_4",
            "insignia_dps_1", "insignia_dps_2", "insignia_dps_3", "insignia_dps_4",
            "insignia_support_1", "insignia_support_2", "insignia_support_3", "insignia_support_4",
            "insignia_cc_1", "insignia_cc_2", "insignia_cc_3", "insignia_cc_4",
            "fuel", "mod_fragment", "banner_key"
    );

    private static final Logger LOG = LogManager.getLogger(GachaMarketService.class);
    public final GachaMarketStore store = new GachaMarketStore("data/gacha-market.db");
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final String apiKey;
    private final StrictRateGate rateGate = new StrictRateGate(Duration.ofSeconds(1).toNanos());

    public GachaMarketService(GachaConfig conf) {
        this.apiKey = conf.apiKey();
        int seconds = conf.marketRefreshInterval();
        if (seconds < 30) throw new IllegalArgumentException("Market interval must be at least 30 seconds");
        scheduler.scheduleWithFixedDelay(this::collect, 0, seconds, TimeUnit.SECONDS);
    }

    public static String getItemName(String id) {
        return switch (id) {
            case "replay_1" -> "铜回放";
            case "replay_2" -> "银回放";
            case "replay_3" -> "金回放";
            case "boss_trophy" -> "金奖杯";
            case "platinum_trophy" -> "白金奖杯";
            case "diamond_trophy" -> "钻石奖杯";
            case "emerald_trophy" -> "翡翠奖杯";
            case "insignia_tank_1" -> "坦克职业徽章 I";
            case "insignia_tank_2" -> "坦克职业徽章 II";
            case "insignia_tank_3" -> "坦克职业徽章 III";
            case "insignia_tank_4" -> "坦克职业徽章 IV";
            case "insignia_dps_1" -> "DPS职业徽章 I";
            case "insignia_dps_2" -> "DPS职业徽章 II";
            case "insignia_dps_3" -> "DPS职业徽章 III";
            case "insignia_dps_4" -> "DPS职业徽章 IV";
            case "insignia_support_1" -> "支援职业徽章 I";
            case "insignia_support_2" -> "支援职业徽章 II";
            case "insignia_support_3" -> "支援职业徽章 III";
            case "insignia_support_4" -> "支援职业徽章 IV";
            case "insignia_cc_1" -> "控制职业徽章 I";
            case "insignia_cc_2" -> "控制职业徽章 II";
            case "insignia_cc_3" -> "控制职业徽章 III";
            case "insignia_cc_4" -> "控制职业徽章 IV";
            case "fuel" -> "燃料";
            case "mod_fragment" -> "Mod 碎片";
            case "banner_key" -> "卡池钥匙";
            default -> throw new IllegalArgumentException();
        };
    }

    static <T extends Sellable> Map<String, MarketOrder<T>> scanMarketListings(IntFunction<MarketListing<T>> fetch) {
        final int pageSize = 100;
        final int stride = 90;
        final int maxPages = 1114;

        Map<String, MarketOrder<T>> orders = new LinkedHashMap<>();
        int budget = maxPages;

        for (int pageIndex = 0; pageIndex < budget; pageIndex++) {
            if (Thread.currentThread().isInterrupted())
                throw new java.util.concurrent.CancellationException("Market collection interrupted");

            final MarketListing<T> listing = fetch.apply(pageIndex * stride);
            var items = listing.orders();
            if (items == null || items.size() > pageSize)
                throw new IllegalStateException("Invalid market page");
            if (pageIndex == 0) {
                int total = listing.total();
                if (total < 0 || total > 100000)
                    throw new IllegalStateException("Market exceeds scan limit");

                budget = Math.min(maxPages, (total + stride - 1) / stride + 2);
            }
            for (var entry : items) {
                orders.put(entry.id(), entry);
            }

            if (items.size() < pageSize) return orders;
        }
        throw new IllegalStateException("Market scan limit reached before the last page");
    }

    public void fetchItemNow(Identifier.Item identifier) {
        if (!ITEMS.contains(identifier.getItemId())) {
            throw new IllegalArgumentException();
        }

        Map<String, MarketOrder<Item>> itemOrders = scanMarketListings((i) -> {
                    try {
                        rateGate.acquire();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    return GachaApi.getMarketItemListings(apiKey, 100, i, "newest", identifier, Item.class);
                }
        );

        final Guide itemGuide = GachaApi.getItemGuide(apiKey, identifier.getItemId());

        final TreeMap<Double, Long> group = new TreeMap<>();
        for (var order : itemOrders.values()) {
            group.merge(MarketOrder.getUnitPrice(order), order.obj().qty(), Long::sum);
        }

        final JsonArray distribution = new JsonArray();
        group.forEach((price, qty) -> {
            var point = new JsonArray();
            point.add(price);
            point.add(qty);
            distribution.add(point);
        });

        store.save(identifier.getItemId(), itemGuide, distribution);
    }

    public void fetchCardNow(Identifier.Card identifier) {
        Map<String, MarketOrder<Card>> cardOrders = scanMarketListings((i) -> {
                    try {
                        rateGate.acquire();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    return GachaApi.getMarketItemListings(apiKey, 100, i, "newest", identifier, Card.class);
                }
        );

        final Guide itemGuide = GachaApi.getCardGuide(apiKey, identifier);

        final TreeMap<Double, Long> group = new TreeMap<>();
        for (var order : cardOrders.values()) {
            group.merge((double) order.price(), 1L, Long::sum);
        }

        final JsonArray distribution = new JsonArray();
        group.forEach((price, qty) -> {
            var point = new JsonArray();
            point.add(price);
            point.add(qty);
            distribution.add(point);
        });

        store.save(identifier.getUniqueId(), itemGuide, distribution);
    }

    private void collect() {
        LOG.info("Collecting gacha market...");
        try {
            Map<String, MarketOrder<Item>> itemOrders = scanMarketListings((i) ->
                    GachaApi.getMarketItemListings(apiKey, 100, i, "newest", Identifier.empty(), Item.class)
            );

            Map<String, MarketOrder<Card>> cardOrders = scanMarketListings((i) ->
                    GachaApi.getMarketItemListings(apiKey, 100, i, "newest", Identifier.empty(), Card.class)
            );

            Map<Identifier, TreeMap<Double, Long>> groups = new HashMap<>();

            ITEMS.forEach(e -> groups.putIfAbsent(Identifier.Item.of(e), new TreeMap<>()));

            for (var order : itemOrders.values()) {
                groups.computeIfAbsent(Identifier.Item.from(order.obj()), _ -> new TreeMap<>())
                        .merge(MarketOrder.getUnitPrice(order), order.obj().qty(), Long::sum);
            }

            for (var order : cardOrders.values()) {
                groups.computeIfAbsent(Identifier.Card.from(order.obj()), _ -> new TreeMap<>())
                        .merge((double) order.price(), 1L, Long::sum);
            }

            for (Identifier id : groups.keySet()) {
                if (Thread.currentThread().isInterrupted()) return;

                try {
                    Guide guide = null;
                    if (!(id instanceof Identifier.Card c) || c.shouldFetchGuide()) {
                        rateGate.acquire();
                        guide = GachaApi.getGuide(apiKey, id.getGuideIdentifier());
                    }
                    var distribution = new JsonArray();
                    groups.getOrDefault(id, new TreeMap<>()).forEach((price, qty) -> {
                        var point = new JsonArray();
                        point.add(price);
                        point.add(qty);
                        distribution.add(point);
                    });
                    store.save(id.getUniqueId(), guide, distribution);
                } catch (Exception e) {
                    LOG.warn("Market guide collection failed for {}", id, e);
                }
            }
            LOG.info("Gacha market data saved for all items");
        } catch (Exception e) {
            LOG.warn("Market collection failed; retaining previous snapshots", e);
        }
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }
}

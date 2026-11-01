package xyz.zcraft.asteroid.gacha;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.zcraft.asteroid.gacha.data.MarketItem;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class MarketService implements AutoCloseable {
    public static final Set<String> ITEMS = Set.of(
            "replay_1", "replay_2", "replay_3",
            "boss_trophy", "platinum_trophy", "diamond_trophy", "emerald_trophy",
            "insignia_tank_1", "insignia_tank_2", "insignia_tank_3", "insignia_tank_4",
            "insignia_dps_1", "insignia_dps_2", "insignia_dps_3", "insignia_dps_4",
            "insignia_support_1", "insignia_support_2", "insignia_support_3", "insignia_support_4",
            "insignia_cc_1", "insignia_cc_2", "insignia_cc_3", "insignia_cc_4",
            "fuel", "mod_fragment", "banner_key"
    );

    public final MarketStore store = new MarketStore("data/gacha-market.db");
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private static final Logger LOG = LogManager.getLogger(MarketService.class);

    public MarketService() {
        long seconds = Long.getLong("asteroid.gacha.market.intervalSeconds", 150);
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

    static Map<String, MarketItem> scanListings(java.util.function.IntFunction<JsonObject> fetch) {
        final int pageSize = 100;
        final int stride = 90;
        final int maxPages = 1114;

        Map<String, MarketItem> orders = new LinkedHashMap<>();
        int budget = maxPages;

        for (int pageIndex = 0; pageIndex < budget; pageIndex++) {
            if (Thread.currentThread().isInterrupted())
                throw new java.util.concurrent.CancellationException("Market collection interrupted");

            var page = fetch.apply(pageIndex * stride);
            var items = page.getAsJsonArray("items");
            if (items == null || items.size() > pageSize)
                throw new IllegalStateException("Invalid market page");
            if (pageIndex == 0) {
                int total = page.get("total").getAsInt();
                if (total < 0 || total > 100000)
                    throw new IllegalStateException("Market exceeds scan limit");

                budget = Math.min(maxPages, (total + stride - 1) / stride + 2);
            }
            for (var entry : items) {
                var order = MarketItem.fromJson(entry.getAsJsonObject());
                orders.put(order.orderId(), order);
            }

            if (items.size() < pageSize) return orders;
        }
        throw new IllegalStateException("Market scan limit reached before the last page");
    }
    private void collect() {
        LOG.info("Collecting gacha market...");
        try {
            Map<String, MarketItem> orders = scanListings(GachaApi::getMarketListings);
            Map<String, TreeMap<Double, Long>> groups = new HashMap<>();

            for (var order : orders.values()) {
                groups.computeIfAbsent(order.itemId(), k -> new TreeMap<>())
                        .merge(order.unitPrice(), order.quantity(), Long::sum);
            }

            for (String id : ITEMS) {
                if (Thread.currentThread().isInterrupted()) return;
                try {
                    var guide = GachaApi.getMarketGuide(id);
                    for (String key : List.of("listed", "sales", "days", "updatedAt"))
                        if (!guide.has(key) || guide.get(key).isJsonNull())
                            throw new IllegalStateException("Incomplete guide");
                    var distribution = new JsonArray();
                    groups.getOrDefault(id, new TreeMap<>()).forEach((price, qty) -> {
                        var point = new JsonArray();
                        point.add(price);
                        point.add(qty);
                        distribution.add(point);
                    });
                    store.save(id, guide, distribution);
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

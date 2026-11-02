package xyz.zcraft.asteroid.gacha.data.market;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;

public record MarketTrendData(
        long rangeStart, long rangeEnd,
        List<List<Number>> lowest, List<List<Number>> median, List<List<Number>> highest,
        List<List<Number>> candles, List<List<Number>> distribution, List<List<Number>> sales,
        int candleSamples, boolean hasPrices, double candleMin, double candleMax
) {
    private static final long DISPLAY_OFFSET = 8 * 3600000L;

    public static MarketTrendData fromHistory(JsonArray history, Instant start, Instant end) {
        List<JsonObject> all = new ArrayList<>();
        for (var entry : history) {
            var row = entry.getAsJsonObject();
            if (time(row) <= end.toEpochMilli()) all.add(row);
        }
        all.sort(Comparator.comparingLong(MarketTrendData::time));
        var recent = all.stream().filter(row -> time(row) >= start.toEpochMilli()).toList();
        long duration = end.toEpochMilli() - start.toEpochMilli();
        if (duration < 20) throw new IllegalArgumentException("Trend range must contain 20 periods");
        List<List<JsonObject>> periods = new ArrayList<>(20);
        List<Long> centers = new ArrayList<>(20);
        int cursor = 0;
        for (int i = 0; i < 20; i++) {
            long from = start.toEpochMilli() + duration * i / 20;
            long to = start.toEpochMilli() + duration * (i + 1) / 20;
            List<JsonObject> period = new ArrayList<>();
            // [from, to), with the final period including the request instant.
            while (cursor < recent.size() && (time(recent.get(cursor)) < to
                    || i == 19 && time(recent.get(cursor)) == to)) {
                period.add(recent.get(cursor++));
            }
            periods.add(period);
            centers.add(from + (to - from) / 2 + DISPLAY_OFFSET);
        }
        var lowest = prices(periods, centers, "lowest");
        var median = prices(periods, centers, "median");
        var highest = prices(periods, centers, "highest");
        boolean hasPrices = java.util.stream.Stream.of(lowest, median, highest)
                .flatMap(List::stream).anyMatch(point -> point.get(1) != null);

        List<List<Number>> candles = new ArrayList<>(20);
        int candleSamples = 0;
        double low = Double.POSITIVE_INFINITY, high = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < periods.size(); i++) {
            Double open = null, close = null, min = null, max = null;
            for (var row : periods.get(i)) {
                Double value = number(row, "lowest");
                if (value == null || value < 0) continue;
                candleSamples++;
                if (open == null) {
                    open = value;
                    min = value;
                    max = value;
                }
                close = value;
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
            // [time, open, high, low, close]; an empty period retains its timestamp.
            candles.add(Arrays.asList(centers.get(i), open, max, min, close));
            if (open != null) {
                low = Math.min(low, min);
                high = Math.max(high, max);
            }
        }
        double padding = candleSamples == 0 ? 0 : high > low ? (high - low) * 0.08 : Math.max(high * 0.02, 0.01);
        List<List<Number>> distribution = new ArrayList<>();
        if (!all.isEmpty() && all.getLast().has("distribution")) {
            for (var entry : all.getLast().getAsJsonArray("distribution")) {
                var pair = entry.getAsJsonArray();
                distribution.add(List.of(pair.get(0).getAsDouble(), pair.get(1).getAsDouble()));
            }
        }
        var daily = new TreeMap<LocalDate, JsonObject>();
        for (var row : all) {
            if (time(row) >= end.toEpochMilli() - 366L * 86400000)
                daily.put(Instant.ofEpochMilli(time(row)).atZone(ZoneId.of("Asia/Shanghai")).toLocalDate(), row);
        }
        List<List<Number>> sales = new ArrayList<>();
        daily.forEach((date, row) -> sales.add(Arrays.asList(
                date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), number(row, "sales"))));
        return new MarketTrendData(start.toEpochMilli() + DISPLAY_OFFSET, end.toEpochMilli() + DISPLAY_OFFSET,
                lowest, median, highest, candles, distribution, sales, candleSamples, hasPrices,
                candleSamples == 0 ? 0 : Math.max(0, low - padding), candleSamples == 0 ? 1 : high + padding);
    }

    private static List<List<Number>> prices(List<List<JsonObject>> periods, List<Long> centers, String key) {
        List<List<Number>> result = new ArrayList<>(20);
        for (int i = 0; i < periods.size(); i++) {
            var period = periods.get(i);
            Double value = period.isEmpty() ? null : number(period.getLast(), key);
            result.add(Arrays.asList(centers.get(i), value != null && value > 0 ? value : null));
        }
        return result;
    }
    private static long time(JsonObject row) {
        return row.get("time").getAsLong();
    }

    private static long displayTime(JsonObject row) {
        return time(row) + DISPLAY_OFFSET;
    }

    private static Double number(JsonObject row, String key) {
        var value = row.get(key);
        if (value == null || value.isJsonNull()) return null;
        double number = value.getAsDouble();
        return Double.isFinite(number) ? number : null;
    }
}

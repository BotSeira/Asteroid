package xyz.zcraft.asteroid.gacha.data.market;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;
import static xyz.zcraft.asteroid.gacha.data.market.MarketTrendSettings.*;

public record MarketTrendData(
        long rangeStart, long rangeEnd,
        List<List<Number>> lowest, List<List<Number>> median, List<List<Number>> highest,
        List<List<Number>> candles, List<List<Number>> distribution, List<List<Number>> sales,
        int candleSamples, boolean hasPrices, double candleMin, double candleMax,
        double distributionMin, double distributionMax
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
        var pricePeriods = periods(recent, start, end, PRICE_PERIODS);
        var lowest = prices(pricePeriods, "lowest");
        var median = prices(pricePeriods, "median");
        var highest = prices(pricePeriods, "highest");
        boolean hasPrices = java.util.stream.Stream.of(lowest, median, highest)
                .flatMap(List::stream).anyMatch(point -> point.get(1) != null);

        var candlePeriods = periods(recent, start, end, CANDLE_PERIODS);
        List<List<Number>> candles = new ArrayList<>(CANDLE_PERIODS);
        int candleSamples = 0;
        double low = Double.POSITIVE_INFINITY, high = Double.NEGATIVE_INFINITY;
        for (var period : candlePeriods) {
            Double open = null, close = null, min = null, max = null;
            for (var row : period.rows()) {
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
            candles.add(Arrays.asList(period.center(), open, max, min, close));
            if (open != null) {
                low = Math.min(low, min);
                high = Math.max(high, max);
            }
        }
        double padding = candleSamples == 0 ? 0 : high > low ? (high - low) * CANDLE_PADDING_RATIO : Math.max(high * FLAT_CANDLE_PADDING_RATIO, MIN_CANDLE_PADDING);
        List<List<Number>> distribution = new ArrayList<>(DISTRIBUTION_BINS);
        double distributionMin = 0, distributionMax = 1;
        if (!all.isEmpty() && all.getLast().has("distribution")) {
            List<double[]> prices = new ArrayList<>();
            double minPrice = Double.POSITIVE_INFINITY, maxPrice = Double.NEGATIVE_INFINITY;
            for (var entry : all.getLast().getAsJsonArray("distribution")) {
                var pair = entry.getAsJsonArray();
                double price = pair.get(0).getAsDouble(), quantity = pair.get(1).getAsDouble();
                if (!Double.isFinite(price) || !Double.isFinite(quantity) || price < 0 || quantity <= 0) continue;
                prices.add(new double[]{price, quantity});
                minPrice = Math.min(minPrice, price);
                maxPrice = Math.max(maxPrice, price);
            }
            if (!prices.isEmpty()) {
                distributionMin = Math.max(0, minPrice - DISTRIBUTION_MARGIN);
                distributionMax = maxPrice + DISTRIBUTION_MARGIN;
                double width = (distributionMax - distributionMin) / DISTRIBUTION_BINS;
                double[] quantities = new double[DISTRIBUTION_BINS];
                for (var price : prices) {
                    int index = Math.clamp((int) ((price[0] - distributionMin) / width), 0, DISTRIBUTION_BINS - 1);
                    quantities[index] += price[1];
                }
                for (int i = 0; i < DISTRIBUTION_BINS; i++) {
                    double from = distributionMin + i * width;
                    double to = i == DISTRIBUTION_BINS - 1 ? distributionMax : distributionMin + (i + 1) * width;
                    distribution.add(List.of((from + to) / 2, quantities[i], from, to));
                }
            }
        }
        var daily = new TreeMap<LocalDate, JsonObject>();
        for (var row : all) {
            if (time(row) >= salesSince(end)) {
                var date = Instant.ofEpochMilli(time(row)).atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
                var bucket = LocalDate.ofEpochDay(Math.floorDiv(date.toEpochDay(), SALES_BUCKET_DAYS) * SALES_BUCKET_DAYS);
                daily.put(bucket, row);
            }
        }
        List<List<Number>> sales = new ArrayList<>();
        daily.forEach((date, row) -> sales.add(Arrays.asList(
                date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), number(row, "sales"))));
        return new MarketTrendData(start.toEpochMilli() + DISPLAY_OFFSET, end.toEpochMilli() + DISPLAY_OFFSET,
                lowest, median, highest, candles, distribution, sales, candleSamples, hasPrices,
                candleSamples == 0 ? 0 : Math.max(0, low - padding), candleSamples == 0 ? 1 : high + padding,
                distributionMin, distributionMax);
    }

    private record Period(long center, List<JsonObject> rows) {}

    private static List<Period> periods(List<JsonObject> rows, Instant start, Instant end, int count) {
        long duration = end.toEpochMilli() - start.toEpochMilli();
        if (count <= 0 || duration < count) throw new IllegalArgumentException("Invalid chart period count or range");
        List<Period> result = new ArrayList<>(count);
        int cursor = 0;
        for (int i = 0; i < count; i++) {
            long from = start.toEpochMilli() + duration * i / count;
            long to = start.toEpochMilli() + duration * (i + 1) / count;
            List<JsonObject> part = new ArrayList<>();
            while (cursor < rows.size() && (time(rows.get(cursor)) < to
                    || i == count - 1 && time(rows.get(cursor)) == to)) {
                part.add(rows.get(cursor++));
            }
            result.add(new Period(from + (to - from) / 2 + DISPLAY_OFFSET, part));
        }
        return result;
    }

    private static List<List<Number>> prices(List<Period> periods, String key) {
        List<List<Number>> result = new ArrayList<>(periods.size());
        for (var period : periods) {
            Double value = null;
            if (!period.rows().isEmpty()) {
                value = switch (PRICE_VALUE) {
                    case FIRST -> number(period.rows().getFirst(), key);
                    case LAST -> number(period.rows().getLast(), key);
                    case AVERAGE -> period.rows().stream().map(row -> number(row, key))
                            .filter(Objects::nonNull).mapToDouble(Double::doubleValue).average().stream().boxed().findFirst().orElse(null);
                };
            }
            result.add(Arrays.asList(period.center(), value != null && value > 0 ? value : null));
        }
        return result;
    }
    private static long time(JsonObject row) {
        return row.get("time").getAsLong();
    }

private static Double number(JsonObject row, String key) {
        var value = row.get(key);
        if (value == null || value.isJsonNull()) return null;
        double number = value.getAsDouble();
        return Double.isFinite(number) ? number : null;
    }
}

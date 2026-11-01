package xyz.zcraft.asteroid.contoller;

import io.javalin.http.Context;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import xyz.zcraft.asteroid.config.GachaConfig;
import xyz.zcraft.asteroid.exception.ApiException;
import xyz.zcraft.asteroid.gacha.MarketService;
import xyz.zcraft.asteroid.gacha.data.MarketSummary;
import xyz.zcraft.asteroid.network.ErrorCode;
import xyz.zcraft.image.HtmlRenderer;

public final class MarketController implements AutoCloseable {
    private final MarketService service;
    private final TemplateEngine engine = new TemplateEngine();
    private final HtmlRenderer renderer = new HtmlRenderer(2, 32, 30000, c -> {
    });

    public MarketController(GachaConfig conf) {
        this.service = new MarketService(conf);

        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("/template/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        resolver.setTemplateMode("HTML");
        engine.setTemplateResolver(resolver);
    }

    public static String validateScale(String scale) {
        if (scale == null) return "1d";
        if (!scale.matches("[1-9][0-9]{0,3}(min|h|d|mo)"))
            throw new ApiException(ErrorCode.ILLEGAL_ARGUMENT, "scale must be a positive interval such as 30min, 2h, 1d or 1mo (maximum 9999 units)");
        return scale;
    }

    public static java.time.Instant rangeStart(java.time.Instant end, String scale) {
        var match = java.util.regex.Pattern.compile("([1-9][0-9]{0,3})(min|h|d|mo)").matcher(validateScale(scale));
        match.matches();
        int count = Integer.parseInt(match.group(1));
        var local = end.atZone(java.time.ZoneId.of("Asia/Shanghai"));
        return switch (match.group(2)) {
            case "min" -> local.minusMinutes(count).toInstant();
            case "h" -> local.minusHours(count).toInstant();
            case "d" -> local.minusDays(count).toInstant();
            default -> local.minusMonths(count).toInstant();
        };
    }

    public void trend(Context ctx) {
        String id = ctx.pathParam("item_id");
        if (!MarketService.ITEMS.contains(id))
            throw new ApiException(ErrorCode.ILLEGAL_ARGUMENT, "Unknown market item");
        String scale = validateScale(ctx.queryParam("scale"));
        var end = java.time.Instant.now();
        var start = rangeStart(end, scale);
        var history = service.store.history(id, Math.min(start.toEpochMilli(), end.toEpochMilli() - 366L * 86400000));
        var trendRows = new com.google.gson.JsonArray();
        int candleSamples = 0;
        for (var entry : history) {
            var row = entry.getAsJsonObject();
            long time = row.get("time").getAsLong();
            if (time < start.toEpochMilli() || time > end.toEpochMilli()) continue;
            trendRows.add(row);
            var low = row.get("lowest");
            if (low != null && !low.isJsonNull() && Double.isFinite(low.getAsDouble()) && low.getAsDouble() >= 0)
                candleSamples++;
        }

        var data = new org.thymeleaf.context.Context();
        data.setVariable("name", MarketService.getItemName(id));
        data.setVariable("scale", scale);
        data.setVariable("history", history.toString());
        data.setVariable("trendRows", trendRows.toString());
        data.setVariable("rangeStart", start.toEpochMilli());
        data.setVariable("rangeEnd", end.toEpochMilli());
        data.setVariable("candleSamples", candleSamples);
        if (!history.isEmpty()) {
            var latest = history.get(history.size() - 1).getAsJsonObject();
            data.setVariable("summary", MarketSummary.fromJson(latest));
        }

        ctx.future(() -> renderer.render(engine.process("market-trend", data))
                .thenAccept(bytes -> ctx.contentType("image/png").result(bytes)));
    }

    @Override
    public void close() {
        service.close();
        renderer.close();
    }
}

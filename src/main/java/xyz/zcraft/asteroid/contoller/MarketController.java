package xyz.zcraft.asteroid.contoller;

import com.microsoft.playwright.Route;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import xyz.zcraft.asteroid.config.GachaConfig;
import xyz.zcraft.asteroid.exception.ApiException;
import xyz.zcraft.asteroid.gacha.GachaApi;
import xyz.zcraft.asteroid.gacha.GachaMarketService;
import xyz.zcraft.asteroid.gacha.data.GachaPlayer;
import xyz.zcraft.asteroid.gacha.data.market.Guide;
import xyz.zcraft.asteroid.gacha.data.market.Identifier;
import xyz.zcraft.asteroid.gacha.data.market.MarketSummary;
import xyz.zcraft.asteroid.gacha.data.market.MarketTrendData;
import xyz.zcraft.asteroid.network.ErrorCode;
import xyz.zcraft.image.HtmlRenderer;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.regex.Pattern;

import static xyz.zcraft.asteroid.util.RequestUtil.*;

public final class MarketController implements AutoCloseable {
    private final GachaMarketService service;
    private final TemplateEngine engine = new TemplateEngine();
    private static final byte[] ECHARTS = loadECharts();
    private final HtmlRenderer renderer = new HtmlRenderer(2, 32, 30000,
            browser -> browser.route("http://asteroid-assets/echarts.js",
                    route -> route.fulfill(new Route.FulfillOptions().setContentType("application/javascript").setBodyBytes(ECHARTS))
            )
    );

    private static byte[] loadECharts() {
        try (var stream = MarketController.class.getResourceAsStream("/vendor/echarts/echarts.min.js")) {
            if (stream == null) throw new IllegalStateException("Missing bundled ECharts");
            return stream.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load ECharts", e);
        }
    }
    private final String apiKey;

    public MarketController(GachaConfig conf) {
        this.service = new GachaMarketService(conf);
        this.apiKey = conf.apiKey();

        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("/template/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        resolver.setTemplateMode("HTML");
        engine.setTemplateResolver(resolver);
    }

    public static String validateScale(String scale) {
        if (scale == null) return "12h";
        if (!scale.matches("[1-9][0-9]{0,3}(min|h|d|mo)"))
            throw new ApiException(ErrorCode.ILLEGAL_ARGUMENT, "scale must be a positive interval such as 30min, 2h, 1d or 1mo (maximum 9999 units)");
        return scale;
    }

    public static Instant rangeStart(Instant end, String scale) {
        var match = Pattern.compile("([1-9][0-9]{0,3})(min|h|d|mo)").matcher(validateScale(scale));
        if (!match.matches()) {
            throw new ApiException(ErrorCode.ILLEGAL_ARGUMENT, "Invalid scale");
        }
        int count = Integer.parseInt(match.group(1));
        var local = end.atZone(ZoneId.of("Asia/Shanghai"));
        return switch (match.group(2)) {
            case "min" -> local.minusMinutes(count).toInstant();
            case "h" -> local.minusHours(count).toInstant();
            case "d" -> local.minusDays(count).toInstant();
            default -> local.minusMonths(count).toInstant();
        };
    }

    public void itemTrend(Context ctx) {
        String id = ctx.pathParam("item_id");
        if (!GachaMarketService.ITEMS.contains(id))
            throw new ApiException(ErrorCode.ILLEGAL_ARGUMENT, "Unknown market item");

        var identifier = Identifier.Item.of(id);
        service.fetchItemNow(identifier);

        String scale = validateScale(ctx.queryParam("scale"));
        var end = Instant.now();
        var start = rangeStart(end, scale);
        var history = service.store.history(id, start.toEpochMilli());
        var trend = MarketTrendData.fromHistory(history, GachaApi.getMarketHistory(apiKey, identifier, start, end), start, end);
        var data = new org.thymeleaf.context.Context();
        data.setVariable("name", GachaMarketService.getItemName(id));
        data.setVariable("type", "物品");
        data.setVariable("scale", scale);
        data.setVariable("chartData", new com.google.gson.Gson().toJson(trend));
        data.setVariable("candleSamples", trend.candleSamples());
        if (!history.isEmpty()) {
            var latest = history.get(history.size() - 1).getAsJsonObject();
            data.setVariable("summary", MarketSummary.fromJson(latest));
        }

        ctx.future(() -> renderer.render(engine.process("gacha-market-item-trend", data))
                .thenAccept(bytes -> ctx.contentType("image/png").result(bytes)));
    }

    @Override
    public void close() {
        service.close();
        renderer.close();
    }

    public void itemGuide(@NotNull Context ctx) {
        String id = ctx.pathParam("item_id");
        if (!GachaMarketService.ITEMS.contains(id))
            throw new ApiException(ErrorCode.ILLEGAL_ARGUMENT, "Unknown market item");

        final Guide guide = GachaApi.getItemGuide(apiKey, id);

        putResult(ctx, guide);
    }

    public void playerGuide(@NotNull Context ctx) {
        final String username = requirePathString(ctx, "username");
        final boolean sig = optionalBoolean(ctx, "sig", false);

        final Guide cardGuide = GachaApi.getCardGuide(apiKey, username, sig);

        putResult(ctx, cardGuide);
    }

    public void playerTrend(@NotNull Context ctx) {
        final String username = requirePathString(ctx, "username");
        final boolean sig = optionalBoolean(ctx, "sig", false);


        final GachaPlayer player = GachaApi.searchPlayers(apiKey, username, 10, 0, "popular").stream()
                .filter(e -> username.equalsIgnoreCase(e.username()))
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Player not found"));

        final String rarityName = GachaPlayer.getRarityName(player.currentRarity());
        final String className = GachaPlayer.getClassName(player.cls());

        final Identifier.Card identifier = Identifier.Card.of(player.username(), sig);

        service.fetchCardNow(identifier);

        String scale = validateScale(ctx.queryParam("scale"));
        var end = Instant.now();
        var start = rangeStart(end, scale);
        var history = service.store.history(identifier.getUniqueId(), start.toEpochMilli());
        var trend = MarketTrendData.fromHistory(history, GachaApi.getMarketHistory(apiKey, identifier, start, end), start, end);
        var data = new org.thymeleaf.context.Context();
        data.setVariable("name", player.username());
        data.setVariable("type", (sig ? "签名" : rarityName) + " · " + className);
        data.setVariable("scale", scale);
        data.setVariable("chartData", new com.google.gson.Gson().toJson(trend));
        data.setVariable("candleSamples", trend.candleSamples());
        if (!history.isEmpty()) {
            var latest = history.get(history.size() - 1).getAsJsonObject();
            data.setVariable("summary", MarketSummary.fromJson(latest));
        }

        ctx.future(() -> renderer.render(engine.process("gacha-market-item-trend", data))
                .thenAccept(bytes -> ctx.contentType("image/png").result(bytes)));
    }
}

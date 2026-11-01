package xyz.zcraft.asteroid.contoller;

import io.javalin.http.Context;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import xyz.zcraft.asteroid.exception.ApiException;
import xyz.zcraft.asteroid.gacha.MarketService;
import xyz.zcraft.asteroid.gacha.data.MarketSummary;
import xyz.zcraft.asteroid.network.ErrorCode;
import xyz.zcraft.image.HtmlRenderer;

public final class MarketController implements AutoCloseable {
    private final MarketService service = new MarketService();
    private final TemplateEngine engine = new TemplateEngine();
    private final HtmlRenderer renderer = new HtmlRenderer(2, 32, 30000, c -> {
    });

    public MarketController() {
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

    public void trend(Context ctx) {
        String id = ctx.pathParam("item_id");
        if (!MarketService.ITEMS.contains(id))
            throw new ApiException(ErrorCode.ILLEGAL_ARGUMENT, "Unknown market item");
        String scale = validateScale(ctx.queryParam("scale"));
        var history = service.store.history(id, System.currentTimeMillis() - 366L * 86400000);

        var data = new org.thymeleaf.context.Context();
        data.setVariable("name", MarketService.getItemName(id));
        data.setVariable("scale", scale);
        data.setVariable("history", history.toString());
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

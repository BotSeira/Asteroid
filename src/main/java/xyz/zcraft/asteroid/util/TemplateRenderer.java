package xyz.zcraft.asteroid.util;

import com.microsoft.playwright.Route;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import xyz.zcraft.asteroid.contoller.MarketController;
import xyz.zcraft.image.HtmlRenderer;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class TemplateRenderer implements Closeable {
    private static final Map<String, String> BUNDLED_DEPENDENCIES = Map.of(
            "/vendor/echarts/echarts.min.js", "echarts.js"
    );

    private static final Map<String, byte[]> BUNDLED = loadBundled();
    private final TemplateEngine engine = new TemplateEngine();
    private final HtmlRenderer renderer = new HtmlRenderer(
            2, 32, 30000,
            browser -> BUNDLED.forEach((name, bytes) ->
                    browser.route(
                            "http://asteroid-assets/" + name,
                            route -> route.fulfill(new Route.FulfillOptions().setContentType("application/javascript").setBodyBytes(bytes))
                    )
            )
    );

    public TemplateRenderer() {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("/template/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        resolver.setTemplateMode("HTML");
        engine.setTemplateResolver(resolver);
    }

    private static Map<String, byte[]> loadBundled() {
        final Map<String, byte[]> deps = new HashMap<>();

        BUNDLED_DEPENDENCIES.forEach((res, name) -> {
            try (var stream = MarketController.class.getResourceAsStream(res)) {
                if (stream == null) throw new IllegalStateException("Missing bundled " + name);
                deps.put(name, stream.readAllBytes());
            } catch (IOException e) {
                throw new IllegalStateException("Cannot load ECharts", e);
            }
        });

        return deps;
    }

    public CompletableFuture<byte[]> render(String template, org.thymeleaf.context.Context context) {
        return renderer.render(engine.process(template, context));
    }

    public CompletableFuture<byte[]> renderGif(String template, org.thymeleaf.context.Context context, int fps, int frames) {
        String html = engine.process(template, context);

        return renderer.renderPage(html, page -> GifEncoder.capture(page, fps, frames));
    }

    @Override
    public void close() {
        renderer.close();
    }
}

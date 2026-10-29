package xyz.zcraft.asteroid.contoller;

import com.google.gson.Gson;
import com.microsoft.playwright.Route;
import io.javalin.http.*;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templateresolver.FileTemplateResolver;
import xyz.zcraft.image.HtmlRenderer;
import xyz.zcraft.asteroid.exception.ApiException;
import xyz.zcraft.asteroid.network.ErrorCode;
import com.google.gson.reflect.TypeToken;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.nio.file.*;
import java.util.Map;

/** Renders operator-installed templates with caller-provided data. */
public final class TemplateController implements AutoCloseable {
    private final Path templates = Path.of("templates").toAbsolutePath().normalize();
    private final TemplateEngine engine = new TemplateEngine();
    private final HtmlRenderer renderer;
    public TemplateController() {
        FileTemplateResolver resolver = new FileTemplateResolver();
        resolver.setPrefix(templates.toString() + "/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCacheable(false);
        engine.setTemplateResolver(resolver);
        renderer = new HtmlRenderer(Integer.getInteger("asteroid.render.workers", 2),
            Integer.getInteger("asteroid.render.queueCapacity", 64),
            Integer.getInteger("asteroid.render.timeoutMillis", 30000), context -> {
                context.route("http://local-asset/**", route -> {
                    try {
                        Path assets = templates.resolve("assets").toRealPath();
                        Path asset = assets.resolve(URI.create(route.request().url()).getPath().substring(1)).toRealPath();
                        if (!asset.startsWith(assets)) {
                            route.abort();
                            return;
                        }
                        route.fulfill(new Route.FulfillOptions().setPath(asset));
                    } catch (Exception e) {
                        route.abort();
                    }
                });
            });
    }
    public void render(Context ctx) {
        String name = ctx.pathParam("templateName");
        if (!name.matches("[a-zA-Z0-9_-]+")) {
            throw new ApiException(ErrorCode.ILLEGAL_ARGUMENT, "Invalid template name");
        }
        if (!Files.isRegularFile(templates.resolve(name + ".html"))) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Template not found");
        }
        Map<String, Object> variables;
        try {
            variables = new Gson().fromJson(ctx.body(), new TypeToken<Map<String, Object>>() {}.getType());
            if (variables == null) {
                throw new IllegalArgumentException("Missing template data");
            }
        } catch (RuntimeException e) {
            throw new ApiException(ErrorCode.ILLEGAL_ARGUMENT, "Expected a JSON object");
        }
        var data = new org.thymeleaf.context.Context();
        data.setVariables(variables);
        try {
            ctx.future(() -> CompletableFuture.supplyAsync(() ->
                renderer.screenshot(engine.process(name, data)), renderer.executor())
                .thenAccept(bytes -> ctx.contentType("image/png").result(bytes)));
        } catch (RejectedExecutionException e) {
            throw new ApiException(ErrorCode.FETCH_FAILED, "Renderer busy");
        }
    }
    @Override
    public void close() {
        renderer.close();
    }
}

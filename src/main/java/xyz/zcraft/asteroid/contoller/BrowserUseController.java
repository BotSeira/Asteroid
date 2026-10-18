package xyz.zcraft.asteroid.contoller;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.microsoft.playwright.Response;
import io.javalin.http.Context;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.zcraft.asteroid.browser.BrowserInstance;
import xyz.zcraft.asteroid.browser.BrowserService;
import xyz.zcraft.asteroid.config.BrowserConfig;
import xyz.zcraft.asteroid.exception.ApiException;
import xyz.zcraft.asteroid.network.ErrorCode;

import java.util.Map;
import java.util.UUID;

import static xyz.zcraft.asteroid.util.RequestUtil.*;

public class BrowserUseController {
    private final static Logger LOG = LogManager.getLogger(BrowserUseController.class);
    private final static Gson GSON = new Gson();
    private final BrowserService browserService;

    public BrowserUseController(BrowserConfig config) {
        this.browserService = new BrowserService(config.proxy());
    }

    public void createBrowser(Context ctx) {
        final BrowserInstance browser = browserService.createBrowser();
        putResult(ctx, "browser_uuid", browser.getUuid().toString());
    }

    public void closeBrowser(Context ctx) {
        final UUID uuid = UUID.fromString(requirePathString(ctx, "browserUuid"));
        browserService.closeBrowser(uuid);
        putResult(ctx, "browser_uuid", uuid.toString());
    }

    public void createPage(Context ctx) {
        final UUID uuid = UUID.fromString(requirePathString(ctx, "browserUuid"));
        final BrowserInstance browser = browserService.getBrowser(uuid);
        final UUID page = browser.createPage();
        putResult(ctx, "page_uuid", page.toString());
    }

    public void closePage(Context ctx) {
        final UUID uuid = UUID.fromString(requirePathString(ctx, "browserUuid"));
        final UUID pageUuid = UUID.fromString(requirePathString(ctx, "pageUuid"));
        final BrowserInstance browser = browserService.getBrowser(uuid);
        browser.closePage(pageUuid);
        putResult(ctx, "page_uuid", pageUuid.toString());
    }

    public void getPages(Context ctx) {
        final UUID uuid = UUID.fromString(requirePathString(ctx, "browserUuid"));
        final BrowserInstance browser = browserService.getBrowser(uuid);
        final Map<String, Map.Entry<String, String>> pages = browser.getPages();
        JsonObject jsonObject = new JsonObject();
        pages.forEach((key, value) -> {
            JsonObject obj = new JsonObject();
            obj.addProperty("title", value.getKey());
            obj.addProperty("url", value.getValue());
            jsonObject.add(key, obj);
        });
        putResult(ctx, jsonObject);
    }

    public void getPageContent(Context ctx) {
        final UUID uuid = UUID.fromString(requirePathString(ctx, "browserUuid"));
        final UUID pageUuid = UUID.fromString(requirePathString(ctx, "pageUuid"));
        final boolean purify = optionalBoolean(ctx, "purify", true);
        final BrowserInstance browser = browserService.getBrowser(uuid);
        final String selector = optionalString(ctx, "selector");
        if (selector != null && !selector.isBlank()) {
            putResult(ctx, "result", browser.getPageContent(pageUuid, selector, purify));
        } else {
            putResult(ctx, "result", browser.getPageContent(pageUuid, purify));
        }
    }

    public void evalPage(Context ctx) {
        final EvalRequest request = GSON.fromJson(JsonParser.parseString(ctx.body()), EvalRequest.class);

        if (request == null || request.expr() == null) {
            throw new ApiException(ErrorCode.ILLEGAL_ARGUMENT);
        }

        final UUID uuid = UUID.fromString(requirePathString(ctx, "browserUuid"));
        final UUID pageUuid = UUID.fromString(requirePathString(ctx, "pageUuid"));
        final BrowserInstance browser = browserService.getBrowser(uuid);
        final Object result = browser.pageEval(pageUuid, request.expr());

        putResult(ctx, result);
    }

    public void navigatePage(Context ctx) {
        final JsonElement jsonElement = JsonParser.parseString(ctx.body());
        final NavigateRequest navigateRequest = GSON.fromJson(jsonElement, NavigateRequest.class);

        if (navigateRequest.url() == null || navigateRequest.url().isBlank()) {
            throw new ApiException(ErrorCode.ILLEGAL_ARGUMENT);
        }

        final UUID uuid = UUID.fromString(requirePathString(ctx, "browserUuid"));
        final UUID pageUuid = UUID.fromString(requirePathString(ctx, "pageUuid"));
        final BrowserInstance browser = browserService.getBrowser(uuid);
        final Response response = browser.navigatePage(pageUuid, navigateRequest.url());

        putResult(ctx, Map.of(
                "ok", response.ok(),
                "status", response.status(),
                "statusText", response.statusText(),
                "allHeaders", response.allHeaders()
        ));
    }

    public void waitForPage(Context ctx) {
        final int timeout = requirePositiveInt(ctx, "timeout");
        final UUID uuid = UUID.fromString(requirePathString(ctx, "browserUuid"));
        final UUID pageUuid = UUID.fromString(requirePathString(ctx, "pageUuid"));
        final BrowserInstance browser = browserService.getBrowser(uuid);
        browser.waitForPage(pageUuid, timeout);
        putResult(ctx, "page_uuid", pageUuid.toString());
    }

    public record NavigateRequest(String url) {
    }

    public record EvalRequest(String expr) {
    }
}

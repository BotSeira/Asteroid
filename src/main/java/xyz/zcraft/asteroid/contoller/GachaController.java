package xyz.zcraft.asteroid.contoller;

import com.google.gson.Gson;
import io.javalin.http.Context;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.zcraft.asteroid.browser.BrowserInstance;
import xyz.zcraft.asteroid.browser.BrowserService;
import xyz.zcraft.asteroid.config.GachaConfig;
import xyz.zcraft.asteroid.gacha.GachaApi;
import xyz.zcraft.asteroid.gacha.data.GachaPlayer;
import xyz.zcraft.asteroid.gacha.data.Own;

import java.util.List;
import java.util.UUID;

import static xyz.zcraft.asteroid.util.RequestUtil.*;

public class GachaController {
    private static final Logger LOG = LogManager.getLogger(GachaController.class);
    private static final Gson GSON = new Gson();

    private final BrowserInstance browser;
    private final String apiKey;

    public GachaController(BrowserService browserService, GachaConfig config) {
        this.browser = browserService.createBrowser(true);
        this.apiKey = config.apiKey();
    }

    public void getUserProfile(Context ctx) {
        final String username = requirePathString(ctx, "username");

        final UUID page = browser.createPage(GachaApi.getProfilePageUrl(username));
        browser.waitForPage(page, 10 * 1000);
        browser.pageEval(page, "document.querySelector('.pf-country')?.remove()");
        final byte[] screenshot = browser.getScreenshot(page, ".pf");
        browser.closePage(page);

        ctx.status(200).contentType("image/png").result(screenshot);
    }

    public void getPlayerCardOwners(Context ctx) {
        final long uid = requirePathLong(ctx, "uid");

        final List<Own> owners = GachaApi.getUserCardOwners(apiKey, uid);

        putResult(ctx, owners);
    }

    public void searchPlayer(Context ctx) {
        final String q = requireString(ctx, "q");
        final Integer limit = optionalInteger(ctx, "limit", 10);
        final Integer offset = optionalInteger(ctx, "offset", 0);
        final String sort = optionalString(ctx, "sort", "popular");

        final List<GachaPlayer> owners = GachaApi.searchPlayers(apiKey, q, limit, offset, sort);

        putResult(ctx, owners);
    }
}


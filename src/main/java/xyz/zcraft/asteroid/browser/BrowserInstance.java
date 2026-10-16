package xyz.zcraft.asteroid.browser;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import lombok.Getter;

import java.io.Closeable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BrowserInstance implements Closeable {
    private final BrowserContext context;
    private final Browser browser;
    private final HashMap<UUID, Page> pages = new HashMap<>();
    @Getter
    private final UUID uuid;

    public BrowserInstance(Browser browser) {
        this.browser = browser;
        context = browser.newContext(new Browser.NewContextOptions());
        this.uuid = UUID.randomUUID();
    }

    public UUID createPage() {
        final UUID uuid = UUID.randomUUID();
        pages.put(uuid, context.newPage());
        return uuid;
    }

    public Response navigatePage(UUID uuid, String url) {
        final Page page = pages.get(uuid);
        return page.navigate(url);
    }

    public String getPageContent(UUID uuid) {
        final Page page = pages.get(uuid);
        return page.content();
    }

    public String getPageContent(UUID uuid, String selector) {
        final Page page = pages.get(uuid);
        return page.innerHTML(selector);
    }

    public void waitForPage(UUID uuid, long timeout) {
        final Page page = pages.get(uuid);
        page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(timeout));
    }

    public Map<String, Map.Entry<String, String>> getPages() {
        HashMap<String, Map.Entry<String, String>> map = new HashMap<>();
        pages.forEach((key, value) -> map.put(key.toString(), Map.entry(value.title(), value.url())));
        return map;
    }

    public boolean closePage(UUID uuid) {
        final Page remove = pages.remove(uuid);
        if (remove != null) {
            remove.close();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void close() {
        browser.close();
    }
}

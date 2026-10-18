package xyz.zcraft.asteroid.browser;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import lombok.Getter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.Closeable;
import java.util.*;

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

    public String getPageContent(UUID uuid, boolean removeCssAndJs) {
        final Page page = pages.get(uuid);
        String content = page.content();
        if (removeCssAndJs) {
            content = removeCssAndJs(content);
        }
        return content;
    }

    public static String removeCssAndJs(String html) {
        Document doc = Jsoup.parse(html);

        doc.select("style, script, link[rel~=(?i)stylesheet]").remove();
        doc.select("[style]").removeAttr("style");

        for (Element element : doc.getAllElements()) {
            for (Attribute attr : new ArrayList<>(element.attributes().asList())) {
                if (attr.getKey().toLowerCase(Locale.ROOT).startsWith("on")) {
                    element.removeAttr(attr.getKey());
                }
            }
        }

        return doc.outerHtml();
    }

    public Object pageEval(UUID uuid, String expression) {
        final Page page = pages.get(uuid);
        return page.evaluate(expression);
    }

    public String getPageContent(UUID uuid, String selector, boolean removeCssAndJs) {
        final Page page = pages.get(uuid);
        String content = page.innerHTML(selector);
        if (removeCssAndJs) {
            content = removeCssAndJs(content);
        }
        return content;
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

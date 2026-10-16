package xyz.zcraft.asteroid.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;

import java.util.HashMap;
import java.util.UUID;

public class BrowserService {
    private final HashMap<UUID, BrowserInstance> browsers = new HashMap<>();
    private final Playwright playwright;
    private final String proxy;

    public BrowserService(String proxy) {
        this.playwright = Playwright.create();
        this.proxy = proxy;
    }

    public BrowserInstance createBrowser() {
        final BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                .setHeadless(true);

        if (proxy != null && !proxy.isBlank()) {
            launchOptions.setProxy(proxy);
        }

        final Browser browser = playwright.chromium().launch(launchOptions);

        final BrowserInstance browserInstance = new BrowserInstance(browser);

        browsers.put(browserInstance.getUuid(), browserInstance);

        return browserInstance;
    }

    public BrowserInstance getBrowser(UUID uuid) {
        return browsers.get(uuid);
    }

    public void closeBrowser(UUID uuid) {
        final BrowserInstance browserInstance = browsers.get(uuid);
        browserInstance.close();
        browsers.remove(uuid);
    }
}

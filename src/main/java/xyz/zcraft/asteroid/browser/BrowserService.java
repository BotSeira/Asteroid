package xyz.zcraft.asteroid.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;

import java.util.UUID;
import java.util.concurrent.*;

public class BrowserService {
    private final ConcurrentHashMap<UUID, BrowserInstance> browsers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ScheduledFuture<?>> cleanups = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final Playwright playwright;
    private final String proxy;
    private final Object LOCK = new Object();

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

        resetCleanups(browserInstance.getUuid());

        return browserInstance;
    }

    private void resetCleanups(UUID uuid) {
        synchronized (LOCK) {
            final ScheduledFuture<?> remove = cleanups.remove(uuid);

            if (remove != null) {
                remove.cancel(true);
            }

            final ScheduledFuture<?> schedule = scheduler.schedule(() -> {
                synchronized (LOCK) {
                    this.closeBrowser(uuid);
                }
            }, 10, TimeUnit.MINUTES);

            cleanups.put(uuid, schedule);
        }
    }

    public BrowserInstance getBrowser(UUID uuid) {
        resetCleanups(uuid);
        return browsers.get(uuid);
    }

    public void closeBrowser(UUID uuid) {
        final BrowserInstance browserInstance = browsers.get(uuid);
        browserInstance.close();
        browsers.remove(uuid);
    }
}

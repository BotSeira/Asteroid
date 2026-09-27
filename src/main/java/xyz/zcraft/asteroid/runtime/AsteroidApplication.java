package xyz.zcraft.asteroid.runtime;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.zcraft.asteroid.config.AppConfig;
import xyz.zcraft.asteroid.network.WebServer;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class AsteroidApplication implements AutoCloseable {
    private static final Logger LOG = LogManager.getLogger(AsteroidApplication.class);
    private final AppConfig config;
    private final CountDownLatch stopSignal = new CountDownLatch(1);
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile WebServer server;
    private volatile Thread runner;

    public AsteroidApplication(AppConfig config) {
        this.config = config;
    }

    public void run() throws IOException, InterruptedException {
        runner = Thread.currentThread();
        if (stopSignal.getCount() == 0) return;
        WebServer created = new WebServer(config);
        server = created;
        created.start();
        LOG.info("Asteroid is ready");
        stopSignal.await();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            stopSignal.countDown();
            WebServer current = server;
            if (current != null) current.close();
        }
    }
}

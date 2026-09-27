package xyz.zcraft.asteroid;

import lombok.Getter;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import xyz.zcraft.asteroid.config.AppConfig;
import xyz.zcraft.asteroid.config.ConfigLoader;
import xyz.zcraft.asteroid.runtime.AsteroidApplication;

import java.io.IOException;

public class Asteroid {
    private static final Logger LOG =  LogManager.getLogger(Asteroid.class);

    @Getter
    private static AppConfig conf;

    static void main() {
        LOG.info("Loading configuration...");

        if (!ConfigLoader.configExists()) {
            LOG.warn("Config file does not exist, copying default config. Please check it before restarting.");
            try {
                ConfigLoader.copyDefaultConfig();
            } catch (IOException e) {
                LOG.error("Failed to copy default config", e);
            }
            return;
        }

        try {
            conf = ConfigLoader.loadConfig();
        } catch (RuntimeException e) {
            LOG.error("Invalid configuration. Please check config.yml.", e);
            System.exit(1);
            return;
        }

        if (conf.asteroid().debugMode()) {
            Configurator.setRootLevel(Level.DEBUG);
            LOG.warn("Debug mode is enabled. Disable it in production.");
        }

        try (AsteroidApplication app = new AsteroidApplication(conf)) {
            Thread shutdownHook = new Thread(app::close, "asteroid-shutdown");
            Runtime.getRuntime().addShutdownHook(shutdownHook);
            try {
                app.run();
            } finally {
                try {
                    Runtime.getRuntime().removeShutdownHook(shutdownHook);
                } catch (IllegalStateException ignored) {
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.info("Asteroid shutdown requested");
        } catch (IOException | RuntimeException e) {
            LOG.error("Failed to run Asteroid", e);
            System.exit(1);
        }
    }
}

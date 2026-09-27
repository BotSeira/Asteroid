package xyz.zcraft.asteroid.network;

import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.zcraft.asteroid.config.AppConfig;
import xyz.zcraft.asteroid.contoller.MinecraftController;

public class Router {
    private static final Logger LOG = LogManager.getLogger(Router.class);
    private static final Gson GSON = new Gson();
    public final AppConfig conf;

    final MinecraftController minecraftController;

    public Router(AppConfig conf) {
        this.conf = conf;

        this.minecraftController = new MinecraftController();

        LOG.info("Router created");
    }
}

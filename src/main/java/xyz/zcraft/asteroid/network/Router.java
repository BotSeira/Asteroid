package xyz.zcraft.asteroid.network;

import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.zcraft.asteroid.config.AppConfig;
import xyz.zcraft.asteroid.contoller.*;
import xyz.zcraft.asteroid.util.CosService;

public class Router {
    private static final Logger LOG = LogManager.getLogger(Router.class);
    private static final Gson GSON = new Gson();
    public final AppConfig conf;

    final MinecraftController minecraftController;
    final NcmController ncmController;
    final MiscController miscController;
    final BraveSearchController braveSearchController;
    final BrowserUseController browserUseController;
    final CosService cos;

    public Router(AppConfig conf) {
        this.conf = conf;
        this.cos = new CosService(conf.cos());

        this.minecraftController = new MinecraftController();
        this.ncmController = new NcmController();
        this.miscController = new MiscController();
        this.braveSearchController = new BraveSearchController(conf.braveSearch());
        this.browserUseController = new BrowserUseController(conf.browser(), cos);

        LOG.info("Router created");
    }
}

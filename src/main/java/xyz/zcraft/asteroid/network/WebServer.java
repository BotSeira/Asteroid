package xyz.zcraft.asteroid.network;

import io.javalin.Javalin;
import io.javalin.http.UnauthorizedResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import xyz.zcraft.asteroid.config.AppConfig;
import xyz.zcraft.asteroid.exception.ApiException;

import java.io.Closeable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class WebServer implements Closeable {
    private static final Logger LOG = LogManager.getLogger(WebServer.class);

    private final AppConfig conf;
    private final Javalin app;
    private final Router router;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicLong requests = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();

    public WebServer(AppConfig conf) {
        this.conf = conf;
        this.router = new Router(conf);

        app = Javalin.create(cfg -> {
            final QueuedThreadPool threadPool = new QueuedThreadPool(
                    Math.max(8, conf.webserver().maxThreads() + 3),
                    Math.max(2, conf.webserver().minThreads()),
                    Math.max(1000, conf.webserver().idleTimeout())
            );
            threadPool.setName("ServPool");
            cfg.jetty.threadPool = threadPool;

            cfg.routes.beforeMatched(ctx -> {
                final String token = conf.asteroid().token();
                if (token != null && !token.isBlank()) {
                    final String header = ctx.header("Authorization");
                    if (header == null || !header.equals("Bearer " + token)) {
                        ctx.status(401).result("Unauthorized");
                        throw new UnauthorizedResponse();
                    }
                }
            });

            cfg.routes.before(ctx -> {
                requests.incrementAndGet();
                LOG.debug("{} {} {}", ctx.method(), ctx.path(), ctx.queryString());
            });

            cfg.routes
                    .get("/minecraft/servers/{server}/status", router.minecraftController::serverStatus);


            cfg.routes
                    .exception(ApiException.class, (e, ctx) -> {
                        failures.incrementAndGet();

                        if ("body".equalsIgnoreCase(ctx.header("X-Error-Mode"))) {
                            ctx.status(200)
                                    .contentType("application/json")
                                    .result(new Response(false, e.getMessage(), e.getErrorCode().toJson()).toString());
                        } else {
                            ctx.status(e.getErrorCode().getHttpCode())
                                    .contentType("application/json")
                                    .result(new Response(false, e.getMessage(), e.getErrorCode().toJson()).toString());
                        }

                        if (e.getWrappedException() != null) {
                            LOG.error("API error occurred while processing request: {} - {}", ctx.queryString(), e.getMessage(), e.getWrappedException());
                        } else {
                            LOG.error("API error occurred while processing request: {} - {}", ctx.queryString(), e.getMessage());
                        }
                    })
                    .exception(Exception.class, (e, ctx) -> {
                        failures.incrementAndGet();
                        ctx.status(500).contentType("application/json").result(new Response(false, "An error occurred while processing the request!", null).toString());
                        LOG.error("An error occurred while processing request: {}", ctx.queryString(), e);
                    });
        });
    }

    public void start() {
        app.start(conf.webserver().port());
        LOG.info("Started web server on port {}", conf.webserver().port());
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            app.stop();
        }
    }
}

package xyz.zcraft.asteroid.contoller;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.javalin.http.Context;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.zcraft.asteroid.config.BraveSearchConfig;
import xyz.zcraft.asteroid.exception.ApiException;
import xyz.zcraft.asteroid.network.ErrorCode;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static xyz.zcraft.asteroid.util.RequestUtil.putResult;
import static xyz.zcraft.asteroid.util.RequestUtil.requireString;

public class BraveSearchController {
    private final static Logger LOG = LogManager.getLogger(BraveSearchController.class);
    private final static Gson GSON = new Gson();

    private final BraveSearchConfig config;

    public BraveSearchController(BraveSearchConfig config) {
        this.config = config;
    }

    public void search(Context ctx) {
        final String query = requireString(ctx, "q");

        final var builder = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(10));

        if (config.proxy().enabled()) {
            builder.proxy(ProxySelector.of(new InetSocketAddress(config.proxy().host(), config.proxy().port())));
        }

        try (HttpClient client = builder.build()) {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.search.brave.com/res/v1/web/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)))
                    .header("Accept", "application/json")
                    .header("X-Subscription-Token", config.apiKey())
                    .build();

            var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) throw new ApiException(ErrorCode.FETCH_FAILED);

            final JsonElement data = JsonParser.parseString(response.body());

            putResult(ctx, data);
        } catch (IOException | InterruptedException e) {
            LOG.error(e);
            throw new ApiException(ErrorCode.FETCH_FAILED);
        }
    }
}

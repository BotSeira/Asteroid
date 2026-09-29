package xyz.zcraft.asteroid.contoller;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import io.javalin.http.Context;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.zcraft.asteroid.exception.ApiException;
import xyz.zcraft.asteroid.network.ErrorCode;
import xyz.zcraft.asteroid.util.VersionInfo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static xyz.zcraft.asteroid.util.RequestUtil.putResult;
import static xyz.zcraft.asteroid.util.RequestUtil.requireString;

public class MiscController {
    private final static Logger LOG = LogManager.getLogger(MiscController.class);
    private final static Gson GSON = new Gson();

    public void serverStatus(Context ctx) {
        putResult(ctx, Map.of("version", VersionInfo.getVersion()));
    }

    public void nbnhhsh(Context ctx) {
        final String text = requireString(ctx, "text");

        try {
            final var request = HttpRequest.newBuilder()
                    .uri(URI.create("https://lab.magiconch.com/api/nbnhhsh/guess"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(Map.of("text", text))))
                    .build();

            try (final var client = HttpClient.newHttpClient()) {
                final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                final JsonArray data = JsonParser.parseString(response.body()).getAsJsonArray();

                putResult(ctx, GSON.toJsonTree(Map.of("result", data)));
            }
        } catch (Exception e) {
            LOG.error("Error occurred while processing nbnhhsh request", e);
            throw new ApiException(ErrorCode.FETCH_FAILED, "Failed to query text");
        }
    }
}

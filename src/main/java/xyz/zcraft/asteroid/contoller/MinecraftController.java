package xyz.zcraft.asteroid.contoller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.javalin.http.Context;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.zcraft.asteroid.exception.ApiException;
import xyz.zcraft.asteroid.network.ErrorCode;
import xyz.zcraft.asteroid.util.MinecraftServerProbe;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static xyz.zcraft.asteroid.util.RequestUtil.putResult;

public class MinecraftController {
    private final static Logger LOG = LogManager.getLogger(MinecraftController.class);
    private final static Gson GSON = new Gson();

    private static final Pattern SERVER_PATTERN = Pattern.compile("^(?:https?://)?([a-zA-Z0-9-]+(?:\\.[a-zA-Z0-9-]+)+)(?::([0-9]+))?$");

    public void serverStatus(Context ctx) {
        final String serverUrl = URLDecoder.decode(ctx.pathParam("server"), StandardCharsets.UTF_8);

        final Matcher matcher = SERVER_PATTERN.matcher(serverUrl);

        if (!matcher.matches()) {
            throw new ApiException(ErrorCode.ILLEGAL_ARGUMENT, "Invalid server URL");
        }

        final String host = matcher.group(1);
        final Integer port = (matcher.groupCount() == 2 && matcher.group(2) != null) ? Integer.parseInt(matcher.group(2)) : null;


        if (port != null && (port < 1 || port > 65535)) {
            throw new ApiException(ErrorCode.ILLEGAL_ARGUMENT, "Invalid port");
        }

        try {
            final MinecraftServerProbe.Result probe = (port == null ? MinecraftServerProbe.probe(host) : MinecraftServerProbe.probe(host, port));

            JsonObject obj = new JsonObject();

            obj.addProperty("host", probe.host());
            obj.addProperty("port", probe.port());
            obj.addProperty("latency", probe.latencyMs());

            JsonObject statusObj = new JsonObject();

            statusObj.add("version", GSON.toJsonTree(probe.status().version()));
            statusObj.add("players", GSON.toJsonTree(probe.status().players()));
            statusObj.addProperty("description", probe.status().descriptionText());
            statusObj.add("descriptionRaw", probe.status().description());
            statusObj.addProperty("favicon", probe.status().favicon());

            obj.add("status", statusObj);

            putResult(ctx, obj);
        } catch (IOException e) {
            throw new ApiException(ErrorCode.FETCH_FAILED, "Failed to probe server");
        }
    }
}

package xyz.zcraft.asteroid.contoller;

import com.google.gson.Gson;
import io.javalin.http.Context;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.zcraft.asteroid.exception.ApiException;
import xyz.zcraft.asteroid.network.ErrorCode;
import xyz.zcraft.asteroid.util.MinecraftServerProbe;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
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
        final int port = (matcher.groupCount() == 2 && matcher.group(2) != null) ? Integer.parseInt(matcher.group(2)) : 25565;

        if (port < 1 || port > 65535) {
            throw new ApiException(ErrorCode.ILLEGAL_ARGUMENT, "Invalid port");
        }

        try {
            final MinecraftServerProbe.Result probe = MinecraftServerProbe.probe(host, port);

            putResult(ctx, Map.of(
                    "host", probe.host(),
                    "port", probe.port(),
                    "latency", probe.latencyMs(),
                    "status", Map.of(
                            "version", probe.status().version(),
                            "players", probe.status().players(),
                            "description", probe.status().descriptionText(),
                            "descriptionRaw", probe.status().description(),
                            "favicon", probe.status().favicon()
                    )
            ));
        } catch (IOException e) {
            throw new ApiException(ErrorCode.FETCH_FAILED, "Failed to probe server");
        }
    }
}

package xyz.zcraft.asteroid.ncm;

import com.google.gson.*;
import jakarta.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import xyz.zcraft.asteroid.Asteroid;
import xyz.zcraft.asteroid.config.NcmConfig;
import xyz.zcraft.asteroid.exception.ApiException;
import xyz.zcraft.asteroid.ncm.data.MatchResult;
import xyz.zcraft.asteroid.ncm.data.Playlist;
import xyz.zcraft.asteroid.ncm.data.Song;
import xyz.zcraft.asteroid.network.ErrorCode;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

public class NcmApi {
    private final static Logger LOG = LogManager.getLogger(NcmApi.class);
    private final static Gson GSON = new Gson();
    private final static HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    private final static String endpoint;
    private final static NcmConfig config;

    static {
        config = Asteroid.getConf().ncm();
        endpoint = config.endpoint();

        Runtime.getRuntime().addShutdownHook(new Thread(CLIENT::close));
    }

    @NotNull
    public static List<Song> searchSong(String keywords) {
        final HttpResponse<String> response = get("/search", Query.create().param("keywords", keywords));
        final JsonObject obj = JsonParser.parseString(response.body()).getAsJsonObject();

        final JsonElement jsonElement = obj.get("result").getAsJsonObject().get("songs");

        if (!jsonElement.isJsonArray()) {
            return List.of();
        }

        final JsonArray songsArr = jsonElement.getAsJsonArray();

        final List<Song> songs = new ArrayList<>(songsArr.size());

        for (JsonElement elem : songsArr) {
            songs.add(GSON.fromJson(elem, Song.class));
        }

        return songs;
    }

    @NotNull
    public static List<Song> getPlaylistSongs(long playlistId, @Nullable Integer limit, @Nullable Integer offset) {
        final HttpResponse<String> response = get("/playlist/detail", Query.create()
                .param("id", playlistId)
                .param("limit", limit)
                .param("offset", offset)
        );

        final JsonObject obj = JsonParser.parseString(response.body()).getAsJsonObject();

        final JsonElement jsonElement = obj.get("songs").getAsJsonArray();

        if (!jsonElement.isJsonArray()) {
            return List.of();
        }

        final JsonArray songsArr = jsonElement.getAsJsonArray();

        final List<Song> songs = new ArrayList<>(songsArr.size());

        for (JsonElement elem : songsArr) {
            songs.add(GSON.fromJson(elem, Song.class));
        }

        return songs;
    }

    public static Playlist getPlaylistInfo(long playlistId) {
        try {
            final HttpResponse<String> response = get("/playlist/detail", Query.create().param("id", playlistId));
            final JsonObject obj = JsonParser.parseString(response.body()).getAsJsonObject();
            final JsonElement jsonElement = obj.get("playlist");
            if (!jsonElement.isJsonObject()) {
                throw new ApiException(ErrorCode.NOT_FOUND, "No match found");
            }
            return GSON.fromJson(jsonElement, Playlist.class);
        } catch (Exception e) {
            LOG.error("Error getting playlist info", e);
            throw new RuntimeException("Error getting playlist info", e);
        }
    }

    public static Optional<String> getSongUrl(long id) {
        try {
            final HttpResponse<String> response = get("/song/url/match", Query.create().param("id", id));
            final JsonObject obj = JsonParser.parseString(response.body()).getAsJsonObject();
            return Optional.ofNullable(obj.get("data").getAsString());
        } catch (Exception e) {
            LOG.error("Error getting song url for {}", id, e);
        }
        return Optional.empty();
    }

    @NotNull
    public static List<MatchResult> matchSong(int duration, String audioFP) {
        try {
            final HttpResponse<String> response = get("/audio/match", Query.create().param("duration", duration).param("audioFP", audioFP));
            final JsonObject obj = JsonParser.parseString(response.body()).getAsJsonObject();
            final JsonElement jsonElement = obj.get("data").getAsJsonObject().get("result");
            if (!jsonElement.isJsonArray()) {
                throw new ApiException(ErrorCode.NOT_FOUND, "No match found");
            }
            final JsonArray resultArr = jsonElement.getAsJsonArray();
            final List<MatchResult> results = new ArrayList<>(resultArr.size());
            for (JsonElement elem : resultArr) {
                results.add(GSON.fromJson(elem, MatchResult.class));
            }
            return results;
        } catch (Exception e) {
            LOG.error("Error matching song url", e);
            throw new RuntimeException("Error matching song url", e);
        }
    }

    public static String getAudioFp(String audioUrl, int startSeconds, int duration) {
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("url", audioUrl);
            payload.addProperty("startSeconds", startSeconds);
            payload.addProperty("duration", duration);
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.audioFp().endpoint() + "/api/audiofp"))
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .header("Authorization", "Bearer " + config.audioFp().token())
                    .header("Content-Type", "application/json")
                    .build();

            final HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            return JsonParser.parseString(response.body()).getAsJsonObject().get("audioFP").getAsString();
        } catch (IOException | InterruptedException e) {
            LOG.error("Error getting audioFp", e);
            throw new RuntimeException(e);
        }
    }

    private static HttpResponse<String> get(String path, Query query) {
        try {
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + path + query.build()))
                    .GET()
                    .build();

            final HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                LOG.warn("Unexpected status code {} when GET {}", response.statusCode(), path);
            }

            return response;
        } catch (IOException | InterruptedException e) {
            LOG.error("Failed to GET {}", path, e);
            throw new RuntimeException(e);
        }
    }

    private static class Query {
        private final HashMap<String, String> params = new HashMap<>();

        public static Query create() {
            return new Query();
        }

        public Query param(String key, String value) {
            params.put(key, URLEncoder.encode(value, StandardCharsets.UTF_8));
            return this;
        }

        public Query param(String key, @Nullable Object value) {
            if (value != null) {
                params.put(key, URLEncoder.encode(value.toString(), StandardCharsets.UTF_8));
            }
            return this;
        }

        public String build() {
            StringBuilder builder = new StringBuilder("?");
            params.forEach((key, value) -> builder.append(key).append("=").append(value).append("&"));
            builder.deleteCharAt(builder.length() - 1);
            return builder.toString();
        }
    }
}

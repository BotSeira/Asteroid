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
import xyz.zcraft.asteroid.util.Query;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
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
    public static List<Song> searchSong(String cookie, String keywords) {
        final HttpResponse<String> response = get("/search", Query.create().param("keywords", keywords), cookie);
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
    public static List<Song> getPlaylistSongs(String cookie, long playlistId, @Nullable Integer limit, @Nullable Integer offset) {
        final HttpResponse<String> response = get(
                "/playlist/track/all",
                Query.create().param("id", playlistId).param("limit", limit).param("offset", offset),
                cookie
        );

        final JsonObject obj = JsonParser.parseString(response.body()).getAsJsonObject();

        final JsonElement jsonElement = obj.get("songs");

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

    public static Playlist getPlaylistInfo(String cookie, long playlistId) {
        try {
            final HttpResponse<String> response = get("/playlist/detail", Query.create().param("id", playlistId), cookie);
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

    public static Optional<String> getSongUrl(String cookie, long id) {
        try {
            final HttpResponse<String> response = get("/song/url/match", Query.create().param("id", id), cookie);
            final JsonObject obj = JsonParser.parseString(response.body()).getAsJsonObject();
            return Optional.ofNullable(obj.get("data").getAsString());
        } catch (Exception e) {
            LOG.error("Error getting song url for {}", id, e);
        }
        return Optional.empty();
    }

    @NotNull
    public static List<MatchResult> matchSong(String cookie, int duration, String audioFP) {
        try {
            final HttpResponse<String> response = get(
                    "/audio/match",
                    Query.create().param("duration", duration).param("audioFP", audioFP),
                    cookie
            );
            final JsonObject obj = JsonParser.parseString(response.body()).getAsJsonObject();
            final JsonElement jsonElement = obj.get("data").getAsJsonObject().get("result");
            if (!jsonElement.isJsonArray()) {
                return List.of();
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

    public static String loginAnonymous() {
        try {
            final HttpResponse<String> response = get("/register/anonimous", Query.create(), null);
            final JsonObject obj = JsonParser.parseString(response.body()).getAsJsonObject();
            return obj.get("cookie").getAsString();
        } catch (Exception e) {
            LOG.error("Error logging in anonymously", e);
            throw new RuntimeException("Error logging in anonymously", e);
        }
    }

    private static HttpResponse<String> get(String path, Query query, String cookie) {
        try {
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + path + query.build()))
                    .header("Cookie", cookie == null ? "" : cookie)
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
}

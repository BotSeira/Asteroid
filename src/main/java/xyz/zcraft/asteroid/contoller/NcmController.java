package xyz.zcraft.asteroid.contoller;

import com.google.gson.Gson;
import io.javalin.http.Context;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import xyz.zcraft.asteroid.exception.ApiException;
import xyz.zcraft.asteroid.ncm.NcmApi;
import xyz.zcraft.asteroid.ncm.NcmClient;
import xyz.zcraft.asteroid.ncm.data.MatchResult;
import xyz.zcraft.asteroid.ncm.data.Playlist;
import xyz.zcraft.asteroid.ncm.data.Song;
import xyz.zcraft.asteroid.network.ErrorCode;

import java.util.List;
import java.util.Map;

import static xyz.zcraft.asteroid.util.RequestUtil.*;

public class NcmController {
    private final static Logger LOG = LogManager.getLogger(NcmController.class);
    private final static Gson GSON = new Gson();
    private final NcmClient client;

    public NcmController() {
        this.client = NcmClient.create();
        LOG.info("NcmController initialized");
    }

    public void searchSong(Context ctx) {
        final String keywords = requireString(ctx, "q");
        final List<Song> songs = client.searchSong(keywords);
        putResult(ctx, Map.of("result", songs));
    }

    public void getSongUrl(Context ctx) {
        final long id = requirePathLong(ctx, "id");
        final String url = client.getSongUrl(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Could not get song url"));
        putResult(ctx, Map.of("url", url));
    }

    public void matchSong(Context ctx) {
        final SongMatchPayload payload = GSON.fromJson(ctx.body(), SongMatchPayload.class);
        if (payload == null || payload.url() == null || payload.url().isEmpty()) {
            throw new ApiException(ErrorCode.ILLEGAL_ARGUMENT, "Missing song url");
        }

        final String audioFp = NcmApi.getAudioFp(payload.url(), payload.startSeconds(), payload.duration());
        final List<MatchResult> matchResults = client.matchSong(payload.duration(), audioFp);
        putResult(ctx, Map.of("result", matchResults));
    }

    public void getPlaylistSongs(@NotNull Context ctx) {
        final long id = requirePathLong(ctx, "id");
        final Integer limit = optionalInteger(ctx, "limit", null);
        final Integer offset = optionalInteger(ctx, "offset", null);

        final List<Song> playlistSongs = client.getPlaylistSongs(id, limit, offset);

        putResult(ctx, Map.of("result", playlistSongs));
    }

    public void getPlaylistInfo(@NotNull Context ctx) {
        final long id = requirePathLong(ctx, "id");
        final Playlist playlistInfo = client.getPlaylistInfo(id);
        putResult(ctx, playlistInfo);
    }

    private record SongMatchPayload(String url, int startSeconds, int duration){}
}

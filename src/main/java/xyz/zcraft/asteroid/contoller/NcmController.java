package xyz.zcraft.asteroid.contoller;

import com.google.gson.Gson;
import io.javalin.http.Context;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.zcraft.asteroid.exception.ApiException;
import xyz.zcraft.asteroid.ncm.NcmApi;
import xyz.zcraft.asteroid.ncm.data.MatchResult;
import xyz.zcraft.asteroid.ncm.data.Song;
import xyz.zcraft.asteroid.network.ErrorCode;

import java.util.List;
import java.util.Map;

import static xyz.zcraft.asteroid.util.RequestUtil.*;

public class NcmController {
    private final static Logger LOG = LogManager.getLogger(NcmController.class);
    private final static Gson GSON = new Gson();

    public void searchSong(Context ctx) {
        final String keywords = requireString(ctx, "q");
        final List<Song> songs = NcmApi.searchSong(keywords);
        putResult(ctx, Map.of("result", songs));
    }

    public void getSongUrl(Context ctx) {
        final long id = requirePathLong(ctx, "id");
        final String url = NcmApi.getSongUrl(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Could not get song url"));
        putResult(ctx, Map.of("url", url));
    }

    public void matchSong(Context ctx) {
        final SongMatchPayload payload = GSON.fromJson(ctx.body(), SongMatchPayload.class);
        if (payload == null || payload.url() == null || payload.url().isEmpty()) {
            throw new ApiException(ErrorCode.ILLEGAL_ARGUMENT, "Missing song url");
        }

        final String audioFp = NcmApi.getAudioFp(payload.url(), payload.startSeconds(), payload.duration());
        final List<MatchResult> matchResults = NcmApi.matchSong(payload.duration(), audioFp);
        putResult(ctx, Map.of("result", matchResults));
    }

    private record SongMatchPayload(String url, int startSeconds, int duration){}
}

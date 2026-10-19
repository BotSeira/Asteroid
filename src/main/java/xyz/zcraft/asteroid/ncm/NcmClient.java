package xyz.zcraft.asteroid.ncm;

import jakarta.annotation.Nullable;
import xyz.zcraft.asteroid.ncm.data.MatchResult;
import xyz.zcraft.asteroid.ncm.data.Playlist;
import xyz.zcraft.asteroid.ncm.data.Song;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class NcmClient {
    private final AtomicReference<String> cookie = new AtomicReference<>(null);

    private NcmClient(String cookie) {
        this.cookie.set(cookie);
    }

    public static NcmClient create() {
        final String s = NcmApi.loginAnonymous();
        return new NcmClient(s);
    }

    public List<Song> searchSong(String keywords) {
        try {
            return NcmApi.searchSong(cookie.get(), keywords);
        } catch (Exception e) {
            refreshCookie();
            return NcmApi.searchSong(cookie.get(), keywords);
        }
    }

    public List<Song> getPlaylistSongs(long playlistId, @Nullable Integer limit, @Nullable Integer offset) {
        try {
            return NcmApi.getPlaylistSongs(cookie.get(), playlistId, limit, offset);
        } catch (Exception e) {
            refreshCookie();
            return NcmApi.getPlaylistSongs(cookie.get(), playlistId, limit, offset);
        }
    }

    public Playlist getPlaylistInfo(long playlistId) {
        try {
            return NcmApi.getPlaylistInfo(cookie.get(), playlistId);
        } catch (Exception e) {
            refreshCookie();
            return NcmApi.getPlaylistInfo(cookie.get(), playlistId);
        }
    }

    public Optional<String> getSongUrl(long id) {
        try {
            return NcmApi.getSongUrl(cookie.get(), id);
        } catch (Exception e) {
            refreshCookie();
            return NcmApi.getSongUrl(cookie.get(), id);
        }
    }

    public List<MatchResult> matchSong(int duration, String audioFP) {
        try {
            return NcmApi.matchSong(cookie.get(), duration, audioFP);
        } catch (Exception e) {
            refreshCookie();
            return NcmApi.matchSong(cookie.get(), duration, audioFP);
        }
    }

    public void refreshCookie() {
        this.cookie.set(NcmApi.loginAnonymous());
    }
}

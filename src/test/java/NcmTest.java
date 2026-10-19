import org.junit.jupiter.api.Test;
import xyz.zcraft.asteroid.Asteroid;
import xyz.zcraft.asteroid.ncm.NcmApi;
import xyz.zcraft.asteroid.ncm.data.Artist;
import xyz.zcraft.asteroid.ncm.data.MatchResult;
import xyz.zcraft.asteroid.ncm.data.Song;

import java.util.List;
import java.util.stream.Collectors;

public class NcmTest {
    static {
        Asteroid.loadConfig();
    }

    @Test
    public void testSearch() {
        final List<Song> songs = NcmApi.searchSong(null, "AD ASTRA");
        for (Song song : songs) {
            System.out.println(song.name());
            System.out.println("   - id=" + song.id());
            System.out.println("   - alias=" + String.join(",", song.alias()));
            System.out.println("   - artists=" + song.artists().stream().map(Artist::name).collect(Collectors.joining(",")));
        }
    }

    @Test
    public void testGetUrl() {
        Long[] ids = {1417885192L, 1921984875L, 2745637336L};
        for (Long id : ids) {
            NcmApi.getSongUrl(null, id)
                    .ifPresentOrElse(
                            url -> System.out.println("URL for " + id + ": " + url),
                            () -> System.out.println("No URL found for " + id)
                    );
        }
    }

    @Test
    public void testMatch() {
        final String audioFp = NcmApi.getAudioFp("https://b.ppy.sh/preview/2375676.mp3", 0, 3);
        final List<MatchResult> matchResults = NcmApi.matchSong(null, 3, audioFp);
        for (MatchResult matchResult : matchResults) {
            System.out.println(matchResult.song().name() + "@" + matchResult.startTime());
        }
    }
}

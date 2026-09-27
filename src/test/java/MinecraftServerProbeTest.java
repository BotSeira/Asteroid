import org.junit.jupiter.api.Test;
import xyz.zcraft.asteroid.util.MinecraftServerProbe;

import java.io.IOException;
import java.util.List;

public class MinecraftServerProbeTest {
    private static final List<String> servers = List.of(
            "mc.hypixel.net",
            "play.molean.com"
    );

    @Test
    public void testServerProbe() throws IOException {
        for (String server : servers) {
            System.out.println("Testing server probe for " + server);
            final MinecraftServerProbe.Result probe = MinecraftServerProbe.probe(server, 25565);
            System.out.printf("""
                        - Host: %s
                        - Port: %s
                        - Latency: %d ms
                        - Status:
                            - Version: %s
                            - Players: %s / %s
                            - Description: %s
                    %n""",
                    probe.host(),
                    probe.port(),
                    probe.latencyMs(),
                    probe.status().version(),
                    probe.status().players().online(),
                    probe.status().players().max(),
                    probe.status().descriptionText()
            );
        }
    }
}

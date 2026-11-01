package xyz.zcraft.asteroid.gacha;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class MarketStore {
    private final String url;

    public MarketStore(String path) {
        try {
            Path file = Path.of(path).toAbsolutePath();
            Files.createDirectories(file.getParent());
            url = "jdbc:sqlite:" + file;
            try (var c = open(); var s = c.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL");
                s.execute("CREATE TABLE IF NOT EXISTS market_snapshot(item TEXT NOT NULL, time INTEGER NOT NULL, guide TEXT NOT NULL, distribution TEXT NOT NULL, PRIMARY KEY(item,time))");
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cannot initialize market database", e);
        }
    }

    private Connection open() throws SQLException {
        var c = DriverManager.getConnection(url);
        try (var s = c.createStatement()) {
            s.execute("PRAGMA busy_timeout=5000");
        }
        return c;
    }

    public void save(String item, JsonObject guide, JsonArray distribution) {
        try (var c = open(); var s = c.prepareStatement("INSERT INTO market_snapshot VALUES(?,?,?,?)")) {
            s.setString(1, item);
            s.setLong(2, System.currentTimeMillis());
            s.setString(3, guide.toString());
            s.setString(4, distribution.toString());
            s.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot save market snapshot", e);
        }
    }

    public JsonArray history(String item, long since) {
        var result = new JsonArray();
        try (var c = open(); var s = c.prepareStatement("SELECT time,guide,distribution FROM market_snapshot WHERE item=? AND time>=? ORDER BY time")) {
            s.setString(1, item);
            s.setLong(2, since);
            try (var r = s.executeQuery()) {
                String latestDistribution = "[]";
                while (r.next()) {
                    var row = JsonParser.parseString(r.getString(2)).getAsJsonObject();
                    row.addProperty("time", r.getLong(1));
                    latestDistribution = r.getString(3);
                    result.add(row);
                }
                if (!result.isEmpty())
                    result.get(result.size() - 1).getAsJsonObject().add("distribution", JsonParser.parseString(latestDistribution));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot read market snapshots", e);
        }
        return result;
    }
}

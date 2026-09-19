package mlnplus.hu.strangers;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class StrangersDatabase {

    private final Strangers plugin;
    private Connection connection;

    public StrangersDatabase(Strangers plugin) {
        this.plugin = plugin;
    }

    public synchronized boolean initialize() {
        try {
            Class.forName("org.sqlite.JDBC");
            File dbFile = new File(plugin.getDataFolder(), "database.db");
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS skin_cache (" +
                        "player_name TEXT PRIMARY KEY, " +
                        "value TEXT NOT NULL, " +
                        "signature TEXT NOT NULL, " +
                        "timestamp INTEGER NOT NULL" +
                        ")");
                stmt.execute("CREATE TABLE IF NOT EXISTS player_lives (" +
                        "uuid TEXT PRIMARY KEY, " +
                        "player_name TEXT NOT NULL, " +
                        "lives INTEGER NOT NULL, " +
                        "has_been_revived INTEGER NOT NULL DEFAULT 0" +
                        ")");
                stmt.execute("CREATE TABLE IF NOT EXISTS player_original_skins (" +
                        "uuid TEXT PRIMARY KEY, " +
                        "player_name TEXT NOT NULL, " +
                        "value TEXT NOT NULL, " +
                        "signature TEXT NOT NULL, " +
                        "timestamp INTEGER NOT NULL" +
                        ")");
            }
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize SQLite database for Strangers: " + e.getMessage(), e);
            return false;
        }
    }

    public synchronized void shutdown() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error closing Strangers database connection: " + e.getMessage());
        }
    }

    public synchronized LivesData getLivesData(UUID uuid) {
        if (connection == null) return null;
        String sql = "SELECT player_name, lives, has_been_revived FROM player_lives WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new LivesData(uuid, rs.getString("player_name"), rs.getInt("lives"), rs.getInt("has_been_revived") == 1);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error loading lives data for " + uuid + ": " + e.getMessage());
        }
        return null;
    }

    public synchronized LivesData getLivesDataByName(String playerName) {
        if (connection == null) return null;
        String sql = "SELECT uuid, player_name, lives, has_been_revived FROM player_lives WHERE LOWER(player_name) = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerName.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    return new LivesData(uuid, rs.getString("player_name"), rs.getInt("lives"), rs.getInt("has_been_revived") == 1);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error loading lives data for name " + playerName + ": " + e.getMessage());
        }
        return null;
    }

    public synchronized Map<UUID, LivesData> getAllLivesData() {
        Map<UUID, LivesData> map = new java.util.HashMap<>();
        if (connection == null) return map;
        String sql = "SELECT uuid, player_name, lives, has_been_revived FROM player_lives";
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                try {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    map.put(uuid, new LivesData(uuid, rs.getString("player_name"), rs.getInt("lives"), rs.getInt("has_been_revived") == 1));
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error loading all lives data: " + e.getMessage());
        }
        return map;
    }

    public synchronized void saveLivesData(UUID uuid, String playerName, int lives, boolean hasBeenRevived) {
        if (connection == null) return;
        String sql = "INSERT OR REPLACE INTO player_lives (uuid, player_name, lives, has_been_revived) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, playerName);
            ps.setInt(3, lives);
            ps.setInt(4, hasBeenRevived ? 1 : 0);
            ps.executeUpdate();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error saving lives data for " + playerName + ": " + e.getMessage());
        }
    }

    public synchronized CachedSkin getCachedSkin(String playerName) {
        if (connection == null) return null;
        String sql = "SELECT value, signature FROM skin_cache WHERE player_name = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerName.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new CachedSkin(rs.getString("value"), rs.getString("signature"));
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error loading skin from SQLite database: " + e.getMessage());
        }
        return null;
    }

    public synchronized void saveCachedSkin(String playerName, String value, String signature) {
        if (connection == null) return;
        String sql = "INSERT OR REPLACE INTO skin_cache (player_name, value, signature, timestamp) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerName.toLowerCase());
            ps.setString(2, value);
            ps.setString(3, signature);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error saving skin to SQLite database: " + e.getMessage());
        }
    }

    public synchronized String getOriginalPlayerName(UUID uuid) {
        if (connection == null || uuid == null) return null;
        String sql = "SELECT player_name FROM player_original_skins WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("player_name");
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public synchronized CachedSkin getOriginalSkin(UUID uuid) {
        if (connection == null || uuid == null) return null;
        String sql = "SELECT value, signature FROM player_original_skins WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new CachedSkin(rs.getString("value"), rs.getString("signature"));
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error loading original skin for " + uuid + ": " + e.getMessage());
        }
        return null;
    }

    public synchronized void saveOriginalSkin(UUID uuid, String playerName, String value, String signature) {
        if (connection == null || uuid == null || value == null || signature == null) return;
        String sql = "INSERT OR REPLACE INTO player_original_skins (uuid, player_name, value, signature, timestamp) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, playerName != null ? playerName : "Unknown");
            ps.setString(3, value);
            ps.setString(4, signature);
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error saving original skin for " + playerName + ": " + e.getMessage());
        }
    }

    public static class CachedSkin {
        public final String value;
        public final String signature;

        public CachedSkin(String value, String signature) {
            this.value = value;
            this.signature = signature;
        }
    }

    public static class LivesData {
        public final UUID uuid;
        public final String playerName;
        public int lives;
        public boolean hasBeenRevived;

        public LivesData(UUID uuid, String playerName, int lives, boolean hasBeenRevived) {
            this.uuid = uuid;
            this.playerName = playerName;
            this.lives = lives;
            this.hasBeenRevived = hasBeenRevived;
        }
    }
}

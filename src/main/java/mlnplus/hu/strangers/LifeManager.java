package mlnplus.hu.strangers;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.BanList;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings({"deprecation", "removal", "null", "unchecked"})
public class LifeManager {

    private final Strangers plugin;
    private final Map<UUID, StrangersDatabase.LivesData> livesCache = new ConcurrentHashMap<>();
    private BukkitTask actionbarTask;

    public LifeManager(Strangers plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        // Load existing data from database
        Map<UUID, StrangersDatabase.LivesData> dataMap = plugin.getDatabase().getAllLivesData();
        livesCache.putAll(dataMap);

        startActionbarTask();
    }

    public void shutdown() {
        if (actionbarTask != null && !actionbarTask.isCancelled()) {
            actionbarTask.cancel();
        }
        // Save cached lives data to DB
        for (StrangersDatabase.LivesData data : livesCache.values()) {
            plugin.getDatabase().saveLivesData(data.uuid, data.playerName, data.lives, data.hasBeenRevived);
        }
    }

    public int getDefaultLives() {
        return plugin.getConfig().getInt("lives-system.default-lives", 3);
    }

    public StrangersDatabase.LivesData getOrCreateData(UUID uuid, String name) {
        return livesCache.computeIfAbsent(uuid, k -> {
            StrangersDatabase.LivesData dbData = plugin.getDatabase().getLivesData(uuid);
            if (dbData != null) {
                return dbData;
            }
            StrangersDatabase.LivesData newData = new StrangersDatabase.LivesData(uuid, name, getDefaultLives(), false);
            plugin.getDatabase().saveLivesData(uuid, name, newData.lives, newData.hasBeenRevived);
            return newData;
        });
    }

    public StrangersDatabase.LivesData getLivesDataByName(String playerName) {
        for (StrangersDatabase.LivesData data : livesCache.values()) {
            if (data.playerName.equalsIgnoreCase(playerName)) {
                return data;
            }
        }
        StrangersDatabase.LivesData dbData = plugin.getDatabase().getLivesDataByName(playerName);
        if (dbData != null) {
            livesCache.put(dbData.uuid, dbData);
        }
        return dbData;
    }

    public int getLives(Player player) {
        return getOrCreateData(player.getUniqueId(), plugin.getRealName(player.getUniqueId())).lives;
    }

    public void setLives(UUID uuid, String name, int lives) {
        StrangersDatabase.LivesData data = getOrCreateData(uuid, name);
        data.lives = lives;
        plugin.getDatabase().saveLivesData(uuid, name, data.lives, data.hasBeenRevived);
    }

    public void addLives(UUID uuid, String name, int amount) {
        StrangersDatabase.LivesData data = getOrCreateData(uuid, name);
        data.lives += amount;
        plugin.getDatabase().saveLivesData(uuid, name, data.lives, data.hasBeenRevived);
    }

    public boolean removeLives(UUID uuid, String name, int amount) {
        StrangersDatabase.LivesData data = getOrCreateData(uuid, name);
        data.lives = Math.max(0, data.lives - amount);
        plugin.getDatabase().saveLivesData(uuid, name, data.lives, data.hasBeenRevived);
        return data.lives == 0;
    }

    public boolean hasBeenRevived(UUID uuid, String name) {
        return getOrCreateData(uuid, name).hasBeenRevived;
    }

    public void setRevived(UUID uuid, String name, boolean revived) {
        StrangersDatabase.LivesData data = getOrCreateData(uuid, name);
        data.hasBeenRevived = revived;
        plugin.getDatabase().saveLivesData(uuid, name, data.lives, data.hasBeenRevived);
    }

    public List<StrangersDatabase.LivesData> getEliminatedPlayers() {
        List<StrangersDatabase.LivesData> list = new ArrayList<>();
        // Fetch all from DB to be complete
        Map<UUID, StrangersDatabase.LivesData> all = plugin.getDatabase().getAllLivesData();
        for (StrangersDatabase.LivesData data : all.values()) {
            if (data.lives <= 0) {
                list.add(data);
            }
        }
        return list;
    }

    public boolean isEliminated(UUID uuid) {
        if (uuid == null) return false;
        StrangersDatabase.LivesData data = livesCache.get(uuid);
        if (data != null) {
            return data.lives <= 0;
        }
        return false;
    }

    public void handlePlayerDeath(Player victim, Player killer) {
        boolean pvpOnly = plugin.getConfig().getBoolean("lives-system.pvp-only", true);

        // If pvp-only is enabled and killer is null or not a player, skip life deduction
        if (pvpOnly && killer == null) {
            return;
        }

        StrangersDatabase.LivesData data = getOrCreateData(victim.getUniqueId(), plugin.getRealName(victim.getUniqueId()));
        data.lives = Math.max(0, data.lives - 1);
        plugin.getDatabase().saveLivesData(victim.getUniqueId(), plugin.getRealName(victim.getUniqueId()), data.lives, data.hasBeenRevived);

        if (data.lives > 0) {
            String msg = plugin.getMessage("life-lost", "&c💔 You lost a life! &7(Remaining lives: &c&l{LIVES}&7)")
                    .replace("{LIVES}", String.valueOf(data.lives));
            victim.sendMessage(plugin.parseComponent(msg));

            boolean soundEnabled = plugin.getConfig().getBoolean("lives-system.life-lost-sound.enabled", true);
            if (soundEnabled) {
                String soundName = plugin.getConfig().getString("lives-system.life-lost-sound.sound", "ENTITY_WARDEN_HEARTBEAT");
                float volume = (float) plugin.getConfig().getDouble("lives-system.life-lost-sound.volume", 1.2);
                float pitch = (float) plugin.getConfig().getDouble("lives-system.life-lost-sound.pitch", 0.8);
                boolean broadcastSound = plugin.getConfig().getBoolean("lives-system.life-lost-sound.broadcast-to-all", true);

                org.bukkit.Sound sound;
                try {
                    sound = org.bukkit.Sound.valueOf(soundName.toUpperCase());
                } catch (Exception e) {
                    sound = org.bukkit.Sound.ENTITY_WARDEN_HEARTBEAT;
                }

                if (broadcastSound) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p == null) continue;
                        p.playSound(p.getLocation(), sound, volume, pitch);
                        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.7f, 0.6f);
                    }
                } else {
                    victim.playSound(victim.getLocation(), sound, volume, pitch);
                    victim.playSound(victim.getLocation(), org.bukkit.Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.7f, 0.6f);
                }

                plugin.sendTitle(victim, "", "<#ff2a4b><bold>💔 -1 LIFE</bold></#ff2a4b> <gray>(" + getHeartsDisplay(data.lives) + ")</gray>", 5, 25, 10);
            }

            boolean broadcast = plugin.getConfig().getBoolean("lives-system.broadcast-life-lost",
                    plugin.getConfig().getBoolean("lives-system.broadcast-death-message", true));
            if (broadcast) {
                boolean revealName = plugin.getConfig().getBoolean("lives-system.reveal-name-on-death", false);
                String displayName = revealName ? plugin.getRealName(victim.getUniqueId()) : plugin.getAnonymousName();
                String killerName = killer != null ? (revealName ? plugin.getRealName(killer.getUniqueId()) : plugin.getAnonymousName()) : "";
                String broadcastMsg = plugin.getMessage("life-lost-broadcast",
                        "<#ff2a4b>☠ <gradient:#ff3355:#ff6688><bold>{NAME}</bold></gradient> <#ff4d6d>lost a life!</#ff4d6d>")
                        .replace("{NAME}", displayName)
                        .replace("{LIVES}", String.valueOf(data.lives))
                        .replace("{KILLER}", killerName);
                Bukkit.broadcast(plugin.parseComponent(broadcastMsg));
            }
        } else {
            // Elimination!
            String realName = plugin.getRealName(victim.getUniqueId());
            
            if (plugin.getConfig().getBoolean("lives-system.broadcast-life-lost-on-elimination", false)) {
                String killerName = killer != null ? plugin.getRealName(killer.getUniqueId()) : "";
                String broadcastMsg = plugin.getMessage("life-lost-broadcast",
                        "<#ff2a4b>☠ <gradient:#ff3355:#ff6688><bold>{NAME}</bold></gradient> <#ff4d6d>lost a life!</#ff4d6d>")
                        .replace("{NAME}", realName)
                        .replace("{LIVES}", String.valueOf(data.lives))
                        .replace("{KILLER}", killerName);
                Bukkit.broadcast(plugin.parseComponent(broadcastMsg));
            }

            // 1. Play identity reveal animation to all players
            try {
                plugin.playRevealAnimation(realName);
            } catch (Throwable t) {
                plugin.getLogger().severe("Error playing reveal animation for " + realName + ": " + t.getMessage());
            }

            // 2. Broadcast elimination message to server
            String elimMsg = plugin.getMessage("eliminated-broadcast", "&c☠ &l{NAME} &chas been eliminated!")
                    .replace("{NAME}", realName);
            Bukkit.broadcast(plugin.parseComponent(elimMsg));

            // 3. Ban player
            String banReason = plugin.getMessage("ban-reason", "&c☠ You ran out of lives! Your identity was revealed.")
                    .replace("{NAME}", realName);
            net.kyori.adventure.text.Component banComp = plugin.parseComponent(banReason);
            String legacyBanReason = LegacyComponentSerializer.legacySection().serialize(banComp);
            try {
                Bukkit.getBanList(BanList.Type.NAME).addBan(realName, legacyBanReason, (Date) null, "Console");
            } catch (Throwable t) {
                plugin.getLogger().severe("Failed to add BanList(NAME) for " + realName + ": " + t.getMessage());
            }

            // Kick player after brief delay so they see the reveal title
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (victim.isOnline()) {
                    victim.kick(banComp);
                }
            }, 100L); // 5 seconds
        }
    }

    public String getHeartsDisplay(int lives) {
        if (lives >= 3) {
            String defaultThree = "<#ffd700>❤ ❤ ❤</#ffd700>";
            if (lives > 3) {
                return "<#ffd700>" + "❤ ".repeat(lives).trim() + "</#ffd700>";
            }
            return plugin.getConfig().getString("actionbar.hearts.three", defaultThree);
        } else if (lives == 2) {
            return plugin.getConfig().getString("actionbar.hearts.two", "<#ff3333>❤ ❤</#ff3333>");
        } else if (lives == 1) {
            return plugin.getConfig().getString("actionbar.hearts.one", "<#8b0000>❤</#8b0000>");
        } else {
            return plugin.getConfig().getString("actionbar.hearts.zero", "<dark_gray>☠</dark_gray>");
        }
    }

    private void startActionbarTask() {
        actionbarTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!plugin.isPluginEnabled() || !plugin.getConfig().getBoolean("actionbar.enabled", true)) {
                    return;
                }
                String defaultFormat = plugin.getConfig().getString("actionbar.format", "{HEARTS}");
                String format = plugin.getMessage("actionbar-format", defaultFormat);
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player == null || !player.isOnline()) continue;
                    if (plugin.getTrackerManager() != null && plugin.getTrackerManager().hasActiveTracker(player.getUniqueId())) {
                        continue; // Tracking HUD handles combined hearts and navigation seamlessly
                    }
                    int lives = getLives(player);
                    String hearts = getHeartsDisplay(lives);
                    String text = format.replace("{HEARTS}", hearts).replace("{LIVES}", String.valueOf(lives));
                    player.sendActionBar(plugin.parseComponent(text));
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Every 1 second
    }

    public void resetAllLivesAndPardon() {
        int defaultLives = getDefaultLives();
        Map<UUID, StrangersDatabase.LivesData> allData = plugin.getDatabase().getAllLivesData();
        for (StrangersDatabase.LivesData data : allData.values()) {
            data.lives = defaultLives;
            data.hasBeenRevived = false;
            livesCache.put(data.uuid, data);
            plugin.getDatabase().saveLivesData(data.uuid, data.playerName, defaultLives, false);
            try {
                Bukkit.getBanList(BanList.Type.NAME).pardon(data.playerName);
            } catch (Exception ignored) {
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p == null) continue;
            StrangersDatabase.LivesData data = getOrCreateData(p.getUniqueId(), plugin.getRealName(p.getUniqueId()));
            data.lives = defaultLives;
            data.hasBeenRevived = false;
            plugin.getDatabase().saveLivesData(data.uuid, data.playerName, defaultLives, false);
        }
    }
}

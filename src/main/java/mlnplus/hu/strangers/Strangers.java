package mlnplus.hu.strangers;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.BanList;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings({"deprecation", "removal", "null", "unchecked"})
public class Strangers extends JavaPlugin implements Listener {

    private String lang;
    private String anonymousName;

    private boolean pluginEnabled;
    private StrangersDatabase database;
    private LifeManager lifeManager;
    private ReviveManager reviveManager;
    private GameManager gameManager;
    private TrackerManager trackerManager;

    private volatile String cachedSkinValue;
    private volatile String cachedSkinSignature;

    // Caches to restore players' original appearance/names when disabled
    private final Map<UUID, PlayerProfile> originalProfiles = new ConcurrentHashMap<>();
    private final Map<UUID, Component> originalDisplayNames = new ConcurrentHashMap<>();
    private final Map<UUID, Component> originalListNames = new ConcurrentHashMap<>();

    // Cache to map UUIDs to their real player names
    private final Map<UUID, String> realNames = new ConcurrentHashMap<>();

    // Dedicated immutable cache for original player skins (textures value + signature)
    private final Map<UUID, StrangersDatabase.CachedSkin> realSkins = new ConcurrentHashMap<>();

    public void recordOriginalSkin(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();

        String rName = player.getName();
        if (!rName.equalsIgnoreCase(anonymousName) && !rName.equalsIgnoreCase("Stranger")) {
            realNames.put(uuid, rName);
        }

        // Don't overwrite if we already have a valid real skin in memory
        StrangersDatabase.CachedSkin existing = realSkins.get(uuid);
        if (existing != null && existing.value != null && !existing.value.isEmpty() && !existing.value.equals(cachedSkinValue)) {
            return;
        }

        // Try getting from player's profile
        PlayerProfile profile = player.getPlayerProfile();
        if (profile != null) {
            for (ProfileProperty prop : profile.getProperties()) {
                if ("textures".equalsIgnoreCase(prop.getName())) {
                    String val = prop.getValue();
                    String sig = prop.getSignature();
                    if (val != null && !val.isEmpty() && !val.equals(cachedSkinValue)) {
                        StrangersDatabase.CachedSkin skin = new StrangersDatabase.CachedSkin(val, sig);
                        realSkins.put(uuid, skin);
                        String realName = getRealName(uuid);
                        if (realName.equals("Unknown") || realName.equalsIgnoreCase(anonymousName) || realName.equalsIgnoreCase("Stranger")) {
                            realName = player.getName();
                        }
                        if (database != null) {
                            database.saveOriginalSkin(uuid, realName, val, sig);
                        }
                        return;
                    }
                }
            }
        }
    }

    public StrangersDatabase.CachedSkin fetchSkinFromMojang(String playerName) {
        if (playerName == null || playerName.isEmpty() || playerName.equalsIgnoreCase(anonymousName) || playerName.equalsIgnoreCase("Stranger")) {
            return null;
        }
        try {
            // 1. Get UUID from Player Name
            URL url = URI.create("https://api.mojang.com/users/profiles/minecraft/" + playerName).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            if (conn.getResponseCode() != 200) return null;

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) builder.append(line);
            reader.close();

            String response = builder.toString();
            String uuidStr = extractJsonKey(response, "id");
            if (uuidStr == null) return null;

            // 2. Get Profile skin properties
            URL profileUrl = URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + uuidStr + "?unsigned=false").toURL();
            HttpURLConnection profileConn = (HttpURLConnection) profileUrl.openConnection();
            profileConn.setRequestMethod("GET");
            profileConn.setConnectTimeout(4000);
            profileConn.setReadTimeout(4000);
            if (profileConn.getResponseCode() != 200) return null;

            BufferedReader profileReader = new BufferedReader(new InputStreamReader(profileConn.getInputStream()));
            StringBuilder profileBuilder = new StringBuilder();
            while ((line = profileReader.readLine()) != null) profileBuilder.append(line);
            profileReader.close();

            String profileResponse = profileBuilder.toString();
            String textVal = extractJsonKey(profileResponse, "value");
            String textSig = extractJsonKey(profileResponse, "signature");
            if (textVal != null && !textVal.isEmpty()) {
                return new StrangersDatabase.CachedSkin(textVal, textSig);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public String getRealName(UUID uuid) {
        if (uuid == null) return "Unknown";
        String name = realNames.get(uuid);
        if (name != null && !name.equals("Unknown") && !name.equalsIgnoreCase(anonymousName)) {
            return name;
        }
        if (database != null) {
            String dbName = database.getOriginalPlayerName(uuid);
            if (dbName != null && !dbName.isEmpty() && !dbName.equalsIgnoreCase(anonymousName) && !dbName.equalsIgnoreCase("Stranger")) {
                realNames.put(uuid, dbName);
                return dbName;
            }
        }
        Player online = Bukkit.getPlayer(uuid);
        if (online != null && online.isOnline() && !online.getName().equalsIgnoreCase(anonymousName) && !online.getName().equalsIgnoreCase("Stranger")) {
            realNames.put(uuid, online.getName());
            return online.getName();
        }
        org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        String opName = op.getName();
        if (opName != null && !opName.equalsIgnoreCase(anonymousName) && !opName.equalsIgnoreCase("Stranger")) {
            realNames.put(uuid, opName);
            return opName;
        }
        return "Unknown";
    }

    public StrangersDatabase.CachedSkin getOriginalSkin(UUID uuid) {
        if (uuid == null) return null;
        // 1. Check in-memory realSkins
        StrangersDatabase.CachedSkin memSkin = realSkins.get(uuid);
        if (memSkin != null && memSkin.value != null && !memSkin.value.equals(cachedSkinValue)) {
            return memSkin;
        }
        // 2. Check SQLite database
        if (database != null) {
            StrangersDatabase.CachedSkin dbSkin = database.getOriginalSkin(uuid);
            if (dbSkin != null && dbSkin.value != null && !dbSkin.value.equals(cachedSkinValue)) {
                realSkins.put(uuid, dbSkin);
                return dbSkin;
            }
        }
        // 3. Check online player profile
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            for (ProfileProperty prop : player.getPlayerProfile().getProperties()) {
                if ("textures".equalsIgnoreCase(prop.getName()) && !prop.getValue().equals(cachedSkinValue)) {
                    StrangersDatabase.CachedSkin skin = new StrangersDatabase.CachedSkin(prop.getValue(), prop.getSignature());
                    realSkins.put(uuid, skin);
                    String rName = getRealName(uuid);
                    if (rName.equals("Unknown") || rName.equalsIgnoreCase(anonymousName) || rName.equalsIgnoreCase("Stranger")) {
                        rName = player.getName();
                    }
                    if (database != null) {
                        database.saveOriginalSkin(uuid, rName, prop.getValue(), prop.getSignature());
                    }
                    return skin;
                }
            }
        }
        // 4. Check originalProfiles fallback
        PlayerProfile profile = originalProfiles.get(uuid);
        if (profile != null) {
            for (ProfileProperty prop : profile.getProperties()) {
                if ("textures".equalsIgnoreCase(prop.getName()) && !prop.getValue().equals(cachedSkinValue)) {
                    StrangersDatabase.CachedSkin skin = new StrangersDatabase.CachedSkin(prop.getValue(), prop.getSignature());
                    realSkins.put(uuid, skin);
                    return skin;
                }
            }
        }
        return null;
    }

    public Component getOriginalListName(UUID uuid) {
        if (uuid == null) return null;
        Component listName = originalListNames.get(uuid);
        if (listName != null) {
            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(listName);
            if (!plain.equalsIgnoreCase(getAnonymousName()) && !plain.equalsIgnoreCase("Stranger")) {
                return listName;
            }
        }
        String realName = getRealName(uuid);
        if (!realName.equals("Unknown") && !realName.equalsIgnoreCase(getAnonymousName()) && !realName.equalsIgnoreCase("Stranger")) {
            return Component.text(realName);
        }
        return null;
    }

    public void ensureOriginalSkinLoaded(UUID uuid, String realName) {
        if (uuid == null) return;
        if (realName == null || realName.equalsIgnoreCase(getAnonymousName()) || realName.equalsIgnoreCase("Stranger") || realName.equals("Unknown")) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                realName = p.getName();
            }
        }
        if (realName == null || realName.equalsIgnoreCase(getAnonymousName()) || realName.equalsIgnoreCase("Stranger") || realName.equals("Unknown")) {
            return;
        }
        if (getOriginalSkin(uuid) != null) {
            return;
        }
        final String finalRealName = realName;
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            StrangersDatabase.CachedSkin fetched = null;
            try {
                PlayerProfile cleanProfile = Bukkit.createProfileExact(uuid, finalRealName);
                cleanProfile.complete(true);
                for (ProfileProperty prop : cleanProfile.getProperties()) {
                    if ("textures".equalsIgnoreCase(prop.getName()) && !prop.getValue().equals(cachedSkinValue)) {
                        fetched = new StrangersDatabase.CachedSkin(prop.getValue(), prop.getSignature());
                        break;
                    }
                }
            } catch (Throwable ignored) {
            }

            if (fetched == null) {
                fetched = fetchSkinFromMojang(finalRealName);
            }

            if (fetched != null && fetched.value != null && !fetched.value.equals(cachedSkinValue)) {
                realSkins.put(uuid, fetched);
                if (database != null) {
                    database.saveOriginalSkin(uuid, finalRealName, fetched.value, fetched.signature);
                }
                getServer().getScheduler().runTask(this, () -> {
                    Player target = Bukkit.getPlayer(uuid);
                    if (target != null && target.isOnline()) {
                        if (isBypassed(target)) {
                            removeAnonymizationForPlayer(target);
                        }
                        refreshPlayerForViewers(target);
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            if (isBypassed(p)) {
                                refreshPlayerForViewer(p, target);
                            }
                        }
                    }
                });
            }
        });
    }

    public void refreshPlayerForViewer(Player viewer, Player target) {
        if (viewer == null || !viewer.isOnline() || target == null || !target.isOnline()) return;
        if (viewer.equals(target)) return;
        viewer.hidePlayer(this, target);
        getServer().getScheduler().runTaskLater(this, () -> {
            if (viewer.isOnline() && target.isOnline()) {
                viewer.showPlayer(this, target);
            }
        }, 2L);
    }

    public Player getPlayerByRealOrCurrentName(String name) {
        if (name == null || name.isEmpty()) return null;
        Player p = Bukkit.getPlayerExact(name);
        if (p != null) return p;
        p = Bukkit.getPlayer(name);
        if (p != null) return p;
        for (Map.Entry<UUID, String> entry : realNames.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(name)) {
                return Bukkit.getPlayer(entry.getKey());
            }
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online == null) continue;
            if (getRealName(online.getUniqueId()).equalsIgnoreCase(name)) {
                return online;
            }
        }
        return null;
    }

    // Set of player UUIDs who currently have bypass enabled
    private final Set<UUID> bypassedPlayers = ConcurrentHashMap.newKeySet();

    public boolean isBypassed(CommandSender sender) {
        if (sender == null)
            return false;
        if (!(sender instanceof Player player))
            return true;
        if (!player.isOp() && !player.hasPermission("strangers.admin") && !player.hasPermission("strangers.bypass")) {
            return false;
        }
        return bypassedPlayers.contains(player.getUniqueId());
    }

    public boolean isBypassed(UUID uuid) {
        return bypassedPlayers.contains(uuid);
    }

    public String getAnonymousName() {
        return anonymousName != null ? anonymousName : "Stranger";
    }

    public String getCachedSkinValue() {
        return cachedSkinValue;
    }

    public String getCachedSkinSignature() {
        return cachedSkinSignature;
    }

    public String getLang() {
        return lang != null ? lang : "en";
    }

    private final Set<String> blockedCommands = new HashSet<>(Arrays.asList(
            "msg", "tell", "w", "whisper", "r", "reply", "me", "list", "team", "trigger",
            "near", "who", "online", "plist", "listplayers"));

    @Override
    public void onEnable() {
        long startTime = System.currentTimeMillis();

        // Initialize SQLite database cache
        this.database = new StrangersDatabase(this);
        this.database.initialize();

        // Initialize Life & Revive Managers
        this.lifeManager = new LifeManager(this);
        this.lifeManager.initialize();

        this.reviveManager = new ReviveManager(this);
        this.reviveManager.registerRecipe();

        this.gameManager = new GameManager(this);

        this.trackerManager = new TrackerManager(this);
        this.trackerManager.registerRecipe();
        getServer().getPluginManager().registerEvents(this.trackerManager, this);

        // Save and load config
        saveDefaultConfig();
        reloadPluginConfig();

        // Register Scoreboard Team to hide name tags
        setupScoreboardTeam();

        // Fetch Skin from Mojang API asynchronously
        loadSkinAsync();

        // Register event listeners
        getServer().getPluginManager().registerEvents(this, this);

        // Register Command & Tab Completer
        PluginCommand strangersCmd = getCommand("strangers");
        if (strangersCmd != null) {
            strangersCmd.setExecutor(this);
            strangersCmd.setTabCompleter(this);
        }

        // Hook into Simple Voice Chat if present
        if (getServer().getPluginManager().getPlugin("voicechat") != null) {
            VoiceChatHookLoader.register(this);
        } else {
            getLogger().info("Simple Voice Chat not found. Voice changer will be disabled.");
        }

        // Hook into ProtocolLib if present
        ProtocolLibHookLoader.register(this);

        // Hook into PacketEvents if present
        PacketEventsHookLoader.register(this);

        long elapsed = System.currentTimeMillis() - startTime;
        printAsciiArt(elapsed);
    }

    @Override
    public void onDisable() {
        if (pluginEnabled) {
            disableAnonymizationForAll();
        }

        // Cleanup Voice Chat hook
        if (getServer().getPluginManager().getPlugin("voicechat") != null) {
            VoiceChatHookLoader.shutdown();
        }

        // Cleanup ProtocolLib hook
        ProtocolLibHookLoader.shutdown();

        // Cleanup PacketEvents hook
        PacketEventsHookLoader.shutdown();

        // Shutdown TrackerManager
        if (this.trackerManager != null) {
            this.trackerManager.shutdown();
        }

        // Shutdown LifeManager
        if (this.lifeManager != null) {
            this.lifeManager.shutdown();
        }

        // Close SQLite database cache
        if (this.database != null) {
            this.database.shutdown();
        }

        getLogger().info("\u00A7c\u25cf \u00A77Strangers plugin disabled.");
    }

    public void reloadPluginConfig() {
        reloadConfig();
        this.lang = getConfig().getString("language", "en");
        this.pluginEnabled = getConfig().getBoolean("enabled", true);

        String configAnonName = getConfig().getString("messages." + this.lang + ".anonymous-name");
        if (configAnonName == null || configAnonName.isEmpty()) {
            configAnonName = getConfig().getString("anonymity.name", this.lang.equalsIgnoreCase("hu") ? "Ismeretlen" : "Stranger");
        }
        this.anonymousName = configAnonName;

        List<String> cfgBlocked = getConfig().getStringList("blocked-commands");
        if (cfgBlocked != null && !cfgBlocked.isEmpty()) {
            this.blockedCommands.clear();
            for (String cmd : cfgBlocked) {
                this.blockedCommands.add(cmd.toLowerCase());
            }
        }

        if (this.reviveManager != null) {
            this.reviveManager.registerRecipe();
        }
        if (this.trackerManager != null) {
            this.trackerManager.registerRecipe();
        }

        if (Bukkit.getPluginManager().isPluginEnabled("voicechat")) {
            try {
                VoiceChatHookLoader.reloadSettings();
            } catch (Throwable ignored) {
            }
        }
    }

    public boolean isPluginEnabled() {
        return pluginEnabled;
    }

    private void setupScoreboardTeam() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam("strangers_team");
        if (team == null) {
            team = scoreboard.registerNewTeam("strangers_team");
        }
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
        team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.ALWAYS);
        team.setCanSeeFriendlyInvisibles(false);
        team.addEntry(anonymousName);
    }

    private Team getOrCreateTeam() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam("strangers_team");
        if (team == null) {
            team = scoreboard.registerNewTeam("strangers_team");
        }
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
        team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.ALWAYS);
        team.setCanSeeFriendlyInvisibles(false);
        team.addEntry(anonymousName);
        return team;
    }

    private void loadSkinAsync() {
        String skinPlayer = getConfig().getString("skin-player-name", "");
        String fallbackVal = getConfig().getString("fallback-skin-value", "");
        String fallbackSig = getConfig().getString("fallback-skin-signature", "");

        // Pre-populate with fallback from config immediately so cachedSkinValue is never null
        if (!fallbackVal.isEmpty() && !fallbackSig.isEmpty() && (this.cachedSkinValue == null || this.cachedSkinSignature == null)) {
            this.cachedSkinValue = fallbackVal;
            this.cachedSkinSignature = fallbackSig;
        }

        if (skinPlayer.isEmpty() || skinPlayer.equalsIgnoreCase("none")) {
            if (!fallbackVal.isEmpty() && !fallbackSig.isEmpty()) {
                this.cachedSkinValue = fallbackVal;
                this.cachedSkinSignature = fallbackSig;
                getLogger().info("Using hardcoded fallback skin from config.yml.");
                if (pluginEnabled) {
                    getServer().getScheduler().runTask(this, () -> {
                        for (Player p : getServer().getOnlinePlayers()) {
                            if (!isBypassed(p)) {
                                applySkinAndAnonymize(p);
                            }
                        }
                        refreshAllPlayers();
                    });
                }
            } else {
                getLogger().warning("No skin player name or fallback values specified in config.yml!");
            }
            return;
        }

        // Try to load from SQLite cache first
        StrangersDatabase.CachedSkin cached = database != null ? database.getCachedSkin(skinPlayer) : null;
        if (cached != null) {
            this.cachedSkinValue = cached.value;
            this.cachedSkinSignature = cached.signature;
            getLogger().info("Loaded cached skin properties for '" + skinPlayer + "' from database.db.");
            if (pluginEnabled) {
                getServer().getScheduler().runTask(this, () -> {
                    for (Player p : getServer().getOnlinePlayers()) {
                        if (!isBypassed(p)) {
                            applySkinAndAnonymize(p);
                        }
                    }
                    refreshAllPlayers();
                });
            }
            return;
        }

        // Fetch asynchronously from Mojang API
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                getLogger().info("Fetching skin properties for '" + skinPlayer + "' from Mojang API...");

                // 1. Get UUID from Player Name
                URL url = URI.create("https://api.mojang.com/users/profiles/minecraft/" + skinPlayer).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                if (conn.getResponseCode() != 200) {
                    throw new Exception("Mojang UUID API returned response code " + conn.getResponseCode());
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }
                reader.close();

                String response = builder.toString();
                String uuidStr = extractJsonKey(response, "id");
                if (uuidStr == null) {
                    throw new Exception("Could not find UUID (id) in response: " + response);
                }

                // 2. Get Profile skin properties
                URL profileUrl = URI.create(
                        "https://sessionserver.mojang.com/session/minecraft/profile/" + uuidStr + "?unsigned=false").toURL();
                HttpURLConnection profileConn = (HttpURLConnection) profileUrl.openConnection();
                profileConn.setRequestMethod("GET");
                profileConn.setConnectTimeout(5000);
                profileConn.setReadTimeout(5000);

                if (profileConn.getResponseCode() != 200) {
                    throw new Exception("Mojang profile API returned response code " + profileConn.getResponseCode());
                }

                BufferedReader profileReader = new BufferedReader(new InputStreamReader(profileConn.getInputStream()));
                StringBuilder profileBuilder = new StringBuilder();
                while ((line = profileReader.readLine()) != null) {
                    profileBuilder.append(line);
                }
                profileReader.close();

                String profileResponse = profileBuilder.toString();

                // Parse "value" and "signature"
                String textVal = extractJsonKey(profileResponse, "value");
                if (textVal == null) {
                    throw new Exception("Could not find texture value in JSON.");
                }

                String textSig = extractJsonKey(profileResponse, "signature");
                if (textSig == null) {
                    throw new Exception("Could not find texture signature in JSON.");
                }

                // Cache skin values in memory
                this.cachedSkinValue = textVal;
                this.cachedSkinSignature = textSig;
                getLogger().info("Successfully loaded skin for player '" + skinPlayer + "' from Mojang API!");

                // Save to SQLite database cache
                if (database != null) {
                    database.saveCachedSkin(skinPlayer, textVal, textSig);
                    getLogger().info("Saved skin properties for '" + skinPlayer + "' to database.db cache.");
                }

                // Re-apply skin to online players
                if (pluginEnabled) {
                    getServer().getScheduler().runTask(this, () -> {
                        for (Player p : getServer().getOnlinePlayers()) {
                            if (!isBypassed(p)) {
                                applySkinAndAnonymize(p);
                            }
                        }
                        refreshAllPlayers();
                    });
                }

            } catch (Exception e) {
                getLogger().warning("Error loading skin asynchronously for '" + skinPlayer + "': " + e.getMessage());
                // Fallback to config values if fetch failed
                if (!fallbackVal.isEmpty() && !fallbackSig.isEmpty()) {
                    this.cachedSkinValue = fallbackVal;
                    this.cachedSkinSignature = fallbackSig;
                    getLogger().info("Successfully loaded fallback skin from config.yml.");

                    if (pluginEnabled) {
                        getServer().getScheduler().runTask(this, () -> {
                            for (Player p : getServer().getOnlinePlayers()) {
                                if (!isBypassed(p)) {
                                    applySkinAndAnonymize(p);
                                }
                            }
                            refreshAllPlayers();
                        });
                    }
                } else {
                    getLogger().severe("No fallback skin available to load!");
                }
            }
        });
    }

    private void applySkinAndAnonymize(Player player) {
        // 1. Ensure real name is recorded before changing profile
        String realName = getRealName(player.getUniqueId());
        if ("Unknown".equals(realName) || realName.equalsIgnoreCase(anonymousName)) {
            realName = player.getName();
            if (!realName.equalsIgnoreCase(anonymousName)) {
                realNames.put(player.getUniqueId(), realName);
            }
        }

        // 2. Override names
        player.displayName(Component.text(anonymousName));
        player.playerListName(Component.text(anonymousName));

        // 3. Scoreboard team with ALWAYS visibility ensures nametags are always visible
        Team team = getOrCreateTeam();
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
        team.addEntry(anonymousName);
        team.addEntry(player.getName());
        if (!realName.equals("Unknown")) {
            team.addEntry(realName);
        }

        // 4. Cache real skin before anonymizing if not already cached
        recordOriginalSkin(player);

        // 5. Apply Stranger skin texture to player profile
        // This ensures the player themselves in F5/inventory sees the Stranger skin,
        // and Paper native entity tracker uses the Stranger skin textures.
        // PacketEvents/ProtocolLib will intercept and restore real skin for revealed viewers.
        try {
            PlayerProfile profile = Bukkit.createProfileExact(player.getUniqueId(), realName);
            if (cachedSkinValue != null && cachedSkinSignature != null) {
                profile.setProperty(new ProfileProperty("textures", cachedSkinValue, cachedSkinSignature));
            }
            player.setPlayerProfile(profile);
        } catch (Exception e) {
            getLogger().warning("Failed to apply skin profile to player " + player.getName() + ": " + e.getMessage());
        }
    }

    public void enableAnonymizationForAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p == null) continue;
            recordOriginalSkin(p);
            originalDisplayNames.putIfAbsent(p.getUniqueId(), p.displayName());
            originalListNames.putIfAbsent(p.getUniqueId(), p.playerListName());

            if (!isBypassed(p)) {
                applySkinAndAnonymize(p);
            }
        }
        refreshAllPlayers();
    }

    public void removeAnonymizationForPlayer(Player p) {
        UUID uuid = p.getUniqueId();
        String realName = getRealName(uuid);
        if (realName.equals("Unknown") || realName.equalsIgnoreCase(anonymousName) || realName.equalsIgnoreCase("Stranger")) {
            realName = p.getName();
        }

        // 1. Remove real name entries from scoreboard team if any
        Team team = getOrCreateTeam();
        team.removeEntry(p.getName());
        team.removeEntry(realName);

        // 2. Reset display name, custom name and player list name to default null
        p.displayName(null);
        p.playerListName(null);

        // 3. Restore skin profile from realSkins or database
        StrangersDatabase.CachedSkin realSkin = getOriginalSkin(uuid);
        boolean restored = false;
        if (realSkin != null && realSkin.value != null && !realSkin.value.equals(cachedSkinValue)) {
            try {
                PlayerProfile profile = Bukkit.createProfileExact(uuid, realName);
                if (realSkin.signature != null && !realSkin.signature.isEmpty()) {
                    profile.setProperty(new ProfileProperty("textures", realSkin.value, realSkin.signature));
                } else {
                    profile.setProperty(new ProfileProperty("textures", realSkin.value));
                }
                p.setPlayerProfile(profile);
                restored = true;
            } catch (Exception e) {
                getLogger().warning("Failed to restore skin profile for " + p.getName() + ": " + e.getMessage());
            }
        }

        if (!restored) {
            ensureOriginalSkinLoaded(uuid, realName);
        }
    }

    public void disableAnonymizationForAll() {
        Team team = getOrCreateTeam();
        team.removeEntry(anonymousName);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p != null) {
                removeAnonymizationForPlayer(p);
            }
        }
        refreshAllPlayers();
    }

    public void refreshViewersForPlayer(Player viewer) {
        if (viewer == null || !viewer.isOnline()) return;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target != null && !viewer.equals(target)) {
                viewer.hidePlayer(this, target);
            }
        }
        getServer().getScheduler().runTaskLater(this, () -> {
            if (viewer.isOnline()) {
                for (Player target : Bukkit.getOnlinePlayers()) {
                    if (target != null && target.isOnline() && !viewer.equals(target)) {
                        viewer.showPlayer(this, target);
                    }
                }
            }
        }, 2L);
    }

    public void refreshPlayerForViewers(Player target) {
        if (target == null || !target.isOnline()) return;
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer != null && !viewer.equals(target)) {
                viewer.hidePlayer(this, target);
            }
        }
        getServer().getScheduler().runTaskLater(this, () -> {
            if (target.isOnline()) {
                for (Player viewer : Bukkit.getOnlinePlayers()) {
                    if (viewer != null && viewer.isOnline() && !viewer.equals(target)) {
                        viewer.showPlayer(this, target);
                    }
                }
            }
        }, 2L);
    }

    public void refreshAllPlayers() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p != null) {
                refreshViewersForPlayer(p);
            }
        }
    }

    public void setPluginEnabled(boolean enabled) {
        if (this.pluginEnabled == enabled)
            return;
        this.pluginEnabled = enabled;
        getConfig().set("enabled", enabled);
        saveConfig();
        if (enabled) {
            enableAnonymizationForAll();
        } else {
            disableAnonymizationForAll();
        }
        refreshAllPlayers();
    }

    public StrangersDatabase getDatabase() {
        return database;
    }

    public LifeManager getLifeManager() {
        return lifeManager;
    }

    public ReviveManager getReviveManager() {
        return reviveManager;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public TrackerManager getTrackerManager() {
        return trackerManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.isOp() && !sender.hasPermission("strangers.admin") && !sender.hasPermission("strangers.bypass")) {
            sender.sendMessage(parseComponent(getMessage("no-permission", "&cYou do not have permission to execute this command!")));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(parseComponent(getMessage("usage",
                    "&eUsage: &f/strangers [on|off|reload|bypass|lives|revive|giverevivebook|deathmessage|deathsound|start|reset]")));
            return true;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("status") || sub.equals("info") || sub.equals("dashboard")) {
            sendStatusDashboard(sender);
            return true;
        }

        if (sub.equals("bypass")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(parseComponent(getMessage("only-players", "<gradient:#ff3355:#ff6688><bold>STRANGERS</bold></gradient> <dark_gray>»</dark_gray> <#ff4444>Only in-game players can execute this command!")));
                return true;
            }

            boolean enableBypass;
            if (args.length >= 2) {
                String mode = args[1].toLowerCase();
                if (mode.equals("on") || mode.equals("true")) {
                    enableBypass = true;
                } else if (mode.equals("off") || mode.equals("false")) {
                    enableBypass = false;
                } else {
                    sender.sendMessage(
                            parseComponent(getMessage("bypass-usage", "&eUsage: &f/strangers bypass [on|off]")));
                    return true;
                }
            } else {
                enableBypass = !bypassedPlayers.contains(player.getUniqueId());
            }

            if (enableBypass) {
                bypassedPlayers.add(player.getUniqueId());
                removeAnonymizationForPlayer(player);
                refreshViewersForPlayer(player);
                refreshPlayerForViewers(player);
                sender.sendMessage(parseComponent(getMessage("bypass-enabled",
                        "&aBypass enabled! You can see real names and your own skin.")));
                player.playSound(player.getLocation(), org.bukkit.Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 1.2f);
            } else {
                bypassedPlayers.remove(player.getUniqueId());
                if (pluginEnabled) {
                    applySkinAndAnonymize(player);
                }
                refreshViewersForPlayer(player);
                refreshPlayerForViewers(player);
                sender.sendMessage(
                        parseComponent(getMessage("bypass-disabled", "&cBypass disabled! You are anonymous again.")));
                player.playSound(player.getLocation(), org.bukkit.Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 0.8f);
            }
            return true;
        }

        if (sub.equals("on")) {
            if (pluginEnabled) {
                sender.sendMessage(parseComponent(getMessage("already-enabled", "&cThe plugin is already enabled!")));
                return true;
            }
            setPluginEnabled(true);
            sender.sendMessage(parseComponent(getMessage("plugin-enabled", "&aStrangers has been enabled!")));
            if (sender instanceof Player p) p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.2f);
        } else if (sub.equals("off")) {
            if (!pluginEnabled) {
                sender.sendMessage(parseComponent(getMessage("already-disabled", "&cThe plugin is already disabled!")));
                return true;
            }
            setPluginEnabled(false);
            sender.sendMessage(parseComponent(getMessage("plugin-disabled", "&cStrangers has been disabled!")));
            if (sender instanceof Player p) p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.8f);
        } else if (sub.equals("reload")) {
            reloadPluginConfig();
            loadSkinAsync();
            if (reviveManager != null) {
                reviveManager.registerRecipe();
            }
            if (trackerManager != null) {
                trackerManager.registerRecipe();
            }
            if (pluginEnabled) {
                enableAnonymizationForAll();
            }
            sender.sendMessage(parseComponent(getMessage("plugin-reloaded", "&aConfiguration has been reloaded!")));
            if (sender instanceof Player p) p.playSound(p.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.4f);
        } else if (sub.equals("lives")) {
            if (args.length < 3) {
                sender.sendMessage(parseComponent(getMessage("lives-usage",
                        "&eUsage: &f/strangers lives <set|add|remove|get> <player> [amount]")));
                return true;
            }
            String action = args[1].toLowerCase();
            String targetName = args[2];
            Player onlineTarget = getPlayerByRealOrCurrentName(targetName);
            UUID targetUuid;
            if (onlineTarget != null) {
                targetUuid = onlineTarget.getUniqueId();
                targetName = getRealName(targetUuid);
            } else {
                org.bukkit.OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetName);
                targetUuid = targetPlayer.getUniqueId();
            }

            if (action.equals("get")) {
                StrangersDatabase.LivesData data = lifeManager.getOrCreateData(targetUuid, targetName);
                String yesStr = getMessage("yes", "&cYes");
                String noStr = getMessage("no", "&aNo");
                String text = getMessage("lives-info", "&e{NAME}'s &flives: &c{LIVES} &7(Revived: {REVIVED}&7)")
                        .replace("{NAME}", targetName)
                        .replace("{LIVES}", String.valueOf(data.lives))
                        .replace("{REVIVED}", data.hasBeenRevived ? yesStr : noStr);
                sender.sendMessage(parseComponent(text));
                return true;
            }

            if (args.length < 4) {
                sender.sendMessage(parseComponent(getMessage("specify-amount", "&cPlease specify the amount!")));
                return true;
            }

            int amount;
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage(parseComponent(getMessage("invalid-number", "&cInvalid number!")));
                return true;
            }

            if (action.equals("set")) {
                lifeManager.setLives(targetUuid, targetName, amount);
                String text = getMessage("lives-set", "&aSet &f{NAME}'s &flives to: &e{LIVES}")
                        .replace("{NAME}", targetName)
                        .replace("{LIVES}", String.valueOf(amount));
                sender.sendMessage(parseComponent(text));
            } else if (action.equals("add")) {
                lifeManager.addLives(targetUuid, targetName, amount);
                StrangersDatabase.LivesData data = lifeManager.getOrCreateData(targetUuid, targetName);
                String text = getMessage("lives-added",
                        "&aAdded &e{AMOUNT} &flives for &f{NAME}&a. New lives: &e{LIVES}")
                        .replace("{AMOUNT}", String.valueOf(amount))
                        .replace("{NAME}", targetName)
                        .replace("{LIVES}", String.valueOf(data.lives));
                sender.sendMessage(parseComponent(text));
            } else if (action.equals("remove")) {
                lifeManager.removeLives(targetUuid, targetName, amount);
                StrangersDatabase.LivesData data = lifeManager.getOrCreateData(targetUuid, targetName);
                String text = getMessage("lives-removed",
                        "&cRemoved &e{AMOUNT} &flives from &f{NAME}&c. New lives: &e{LIVES}")
                        .replace("{AMOUNT}", String.valueOf(amount))
                        .replace("{NAME}", targetName)
                        .replace("{LIVES}", String.valueOf(data.lives));
                sender.sendMessage(parseComponent(text));
            } else {
                sender.sendMessage(parseComponent(getMessage("lives-usage",
                        "&eUsage: &f/strangers lives <set|add|remove|get> <player> [amount]")));
            }
        } else if (sub.equals("revive")) {
            if (args.length < 2) {
                sender.sendMessage(
                        parseComponent(getMessage("revive-usage", "&eUsage: &f/strangers revive <player>")));
                return true;
            }
            String targetName = args[1];
            reviveManager.revivePlayer(sender, targetName);
                } else if (sub.equals("givetracker") || sub.equals("tracker")) {
            Player target = null;
            int count = 1;
            if (args.length >= 2) {
                target = getPlayerByRealOrCurrentName(args[1]);
                if (target == null && !(sender instanceof Player)) {
                    sender.sendMessage(
                            parseComponent(getMessage("player-not-found", "<gradient:#ff3355:#ff6688><bold>STRANGERS</bold></gradient> <dark_gray>»</dark_gray> <#ff4444>Player not found!")));
                    return true;
                }
            }
            if (target == null && sender instanceof Player) {
                target = (Player) sender;
            }
            if (args.length >= 3) {
                try {
                    count = Integer.parseInt(args[2]);
                } catch (NumberFormatException ignored) {
                }
            }
            if (target != null && trackerManager != null) {
                target.getInventory().addItem(trackerManager.getTrackerItem(count));
                target.playSound(target.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
                String textMsg = getMessage("tracker-given", "<gradient:#ff3355:#ff6688><bold>STRANGERS</bold></gradient> <dark_gray>»</dark_gray> <#55ff99>Given <white><b>{AMOUNT}x Identity Tracker</b></white> to <color:#FFA737><b>{NAME}</b></color>.")
                        .replace("{AMOUNT}", String.valueOf(count))
                        .replace("{NAME}", getRealName(target.getUniqueId()));
                sender.sendMessage(parseComponent(textMsg));
            }
            return true;
        } else if (sub.equals("giverevivebook")) {
            Player target = null;
            int count = 1;
            if (args.length >= 2) {
                target = getPlayerByRealOrCurrentName(args[1]);
                if (target == null && !(sender instanceof Player)) {
                    sender.sendMessage(
                            parseComponent(getMessage("player-not-found", "<gradient:#ff3355:#ff6688><bold>STRANGERS</bold></gradient> <dark_gray>»</dark_gray> <#ff4444>Player not found!")));
                    return true;
                }
            }
            if (target == null && sender instanceof Player) {
                target = (Player) sender;
            }
            if (args.length >= 3) {
                try {
                    count = Integer.parseInt(args[2]);
                } catch (NumberFormatException ignored) {
                }
            }
            if (target != null) {
                target.getInventory().addItem(reviveManager.getReviveBook(count));
                String text = getMessage("book-given", "&aGiven &e{AMOUNT}x Revive Book &ato &f{NAME}&a.")
                        .replace("{AMOUNT}", String.valueOf(count))
                        .replace("{NAME}", getRealName(target.getUniqueId()));
                sender.sendMessage(parseComponent(text));
            } else {
                sender.sendMessage(
                        parseComponent(getMessage("specify-player", "<gradient:#ff3355:#ff6688><bold>STRANGERS</bold></gradient> <dark_gray>»</dark_gray> <#ff4444>You must specify an online player!")));
            }
        } else if (sub.equals("deathmessage") || sub.equals("deathmessages")) {
            if (args.length >= 2 && (args[1].equalsIgnoreCase("sound") || args[1].equalsIgnoreCase("sounds"))) {
                boolean current = getConfig().getBoolean("lives-system.life-lost-sound.enabled", true);
                boolean newMode;
                if (args.length >= 3) {
                    String mode = args[2].toLowerCase();
                    if (mode.equals("on") || mode.equals("true") || mode.equals("enable")) {
                        newMode = true;
                    } else if (mode.equals("off") || mode.equals("false") || mode.equals("disable")) {
                        newMode = false;
                    } else {
                        sender.sendMessage(parseComponent(
                                getMessage("death-sound-usage", "&eUsage: &f/strangers deathsound [on|off]")));
                        return true;
                    }
                } else {
                    newMode = !current;
                }
                getConfig().set("lives-system.life-lost-sound.enabled", newMode);
                saveConfig();
                if (newMode) {
                    sender.sendMessage(parseComponent(getMessage("death-sound-enabled",
                            "&aDeath/life lost sound effects have been enabled!")));
                } else {
                    sender.sendMessage(parseComponent(getMessage("death-sound-disabled",
                            "&cDeath/life lost sound effects have been disabled!")));
                }
                return true;
            }
            boolean current = getConfig().getBoolean("lives-system.broadcast-life-lost", true);
            boolean newMode;
            if (args.length >= 2) {
                String mode = args[1].toLowerCase();
                if (mode.equals("on") || mode.equals("true") || mode.equals("enable")) {
                    newMode = true;
                } else if (mode.equals("off") || mode.equals("false") || mode.equals("disable")) {
                    newMode = false;
                } else {
                    sender.sendMessage(parseComponent(
                            getMessage("death-message-usage", "&eUsage: &f/strangers deathmessage [on|off]")));
                    return true;
                }
            } else {
                newMode = !current;
            }
            getConfig().set("lives-system.broadcast-life-lost", newMode);
            saveConfig();
            if (newMode) {
                sender.sendMessage(parseComponent(getMessage("death-message-enabled",
                        "&aDeath messages (life lost broadcast) have been enabled!")));
            } else {
                sender.sendMessage(parseComponent(getMessage("death-message-disabled",
                        "&cDeath messages (life lost broadcast) have been disabled!")));
            }
        } else if (sub.equals("deathsound") || sub.equals("deathsounds")) {
            boolean current = getConfig().getBoolean("lives-system.life-lost-sound.enabled", true);
            boolean newMode;
            if (args.length >= 2) {
                String mode = args[1].toLowerCase();
                if (mode.equals("on") || mode.equals("true") || mode.equals("enable")) {
                    newMode = true;
                } else if (mode.equals("off") || mode.equals("false") || mode.equals("disable")) {
                    newMode = false;
                } else {
                    sender.sendMessage(parseComponent(
                            getMessage("death-sound-usage", "&eUsage: &f/strangers deathsound [on|off]")));
                    return true;
                }
            } else {
                newMode = !current;
            }
            getConfig().set("lives-system.life-lost-sound.enabled", newMode);
            saveConfig();
            if (newMode) {
                sender.sendMessage(parseComponent(getMessage("death-sound-enabled",
                        "&aDeath/life lost sound effects have been enabled!")));
            } else {
                sender.sendMessage(parseComponent(getMessage("death-sound-disabled",
                        "&cDeath/life lost sound effects have been disabled!")));
            }
        } else if (sub.equals("start")) {
            if (!sender.isOp() && !sender.hasPermission("strangers.admin")) {
                sender.sendMessage(parseComponent(getMessage("no-permission", "&cYou do not have permission to execute this command!")));
                return true;
            }
            boolean hasConfirm = args.length >= 2 && args[1].equalsIgnoreCase("confirm");
            if (gameManager != null) {
                gameManager.handleStartCommand(sender, hasConfirm);
            }
            return true;
        } else if (sub.equals("reset")) {
            if (!sender.isOp() && !sender.hasPermission("strangers.admin")) {
                sender.sendMessage(parseComponent(getMessage("no-permission", "&cYou do not have permission to execute this command!")));
                return true;
            }
            boolean hasConfirm = args.length >= 2 && args[1].equalsIgnoreCase("confirm");
            if (gameManager != null) {
                gameManager.handleResetCommand(sender, hasConfirm);
            }
            return true;
        } else {
            sender.sendMessage(parseComponent(getMessage("usage",
                    "&eUsage: &f/strangers [on|off|reload|bypass|lives|revive|giverevivebook|deathmessage|deathsound|start|reset]")));
        }

        return true;
    }

    private void sendStatusDashboard(CommandSender sender) {
        sender.sendMessage(parseComponent("<dark_gray>──────── <gradient:#ff3355:#ff6688><b>STRANGERS</b></gradient> <dark_gray>•</dark_gray> <gray>System Dashboard</gray> <dark_gray>────────</dark_gray>"));
        sender.sendMessage(Component.empty());

        // Plugin & Anonymity Status
        String statusBadge = pluginEnabled ? "<#55ff99><b>ACTIVE</b></#55ff99> <dark_gray>(Identities Masked)</dark_gray>" : "<#ff5555><b>INACTIVE</b></#55555> <dark_gray>(Real Identities)</dark_gray>";
        String skinName = getConfig().getString("skin-player-name", "Velenci");
        sender.sendMessage(parseComponent(" <#ff4d6d>✦</#ff4d6d> <white><b>Anonymity:</b></white> " + statusBadge + " <dark_gray>•</dark_gray> <gray>Skin:</gray> <white>" + skinName + "</white>"));

        // World Border
        double borderSize = getConfig().getDouble("game.border-size", 8000.0);
        double margin = getConfig().getDouble("game.scatter-safe-margin", 200.0);
        sender.sendMessage(parseComponent(" <#ff4d6d>✦</#ff4d6d> <white><b>World Border:</b></white> <#ff4d6d><bold>" + (int) borderSize + " blocks</bold></#ff4d6d> <dark_gray>•</dark_gray> <gray>Safe Margin:</gray> <white>" + (int) margin + "b</white>"));

        // Lives System
        boolean livesEnabled = getConfig().getBoolean("lives-system.enabled", true);
        int defaultLives = getConfig().getInt("lives-system.default-lives", 3);
        int eliminated = (lifeManager != null) ? lifeManager.getEliminatedPlayers().size() : 0;
        String livesBadge = livesEnabled ? "<#55ff99>ACTIVE</#55ff99>" : "<red>DISABLED</red>";
        sender.sendMessage(parseComponent(" <#ff4d6d>✦</#ff4d6d> <white><b>Lives System:</b></white> " + livesBadge + " <dark_gray>•</dark_gray> <gray>Default:</gray> <#ff4466>" + defaultLives + " ❤</#ff4466> <dark_gray>•</dark_gray> <gray>Eliminated:</gray> <#ff4444>" + eliminated + "</#ff4444>"));

        // Voice Changer
        boolean voiceEnabled = getConfig().getBoolean("voice-changer.enabled", true);
        double pitch = getConfig().getDouble("voice-changer.pitch-ratio", 0.67);
        boolean vcHook = getServer().getPluginManager().getPlugin("voicechat") != null;
        String voiceBadge = (voiceEnabled && vcHook) ? ("<#55ff99>HOOKED</#55ff99> <dark_gray>(" + pitch + "x pitch)</dark_gray>") : (vcHook ? "<yellow>DISABLED</yellow>" : "<dark_gray>VoiceChat not installed</dark_gray>");
        sender.sendMessage(parseComponent(" <#ff4d6d>✦</#ff4d6d> <white><b>Voice Changer:</b></white> " + voiceBadge));

        // Toggles
        boolean deathMsg = getConfig().getBoolean("lives-system.broadcast-life-lost", true);
        boolean deathSound = getConfig().getBoolean("lives-system.life-lost-sound.enabled", true);
        sender.sendMessage(parseComponent(" <#ff4d6d>✦</#ff4d6d> <white><b>Death Reveals:</b></white> " + (deathMsg ? "<#55ff99>ON</#55ff99>" : "<#ff5555>OFF</#55555>") + " <dark_gray>•</dark_gray> <white><b>Death Sounds:</b></white> " + (deathSound ? "<#55ff99>ON</#55ff99>" : "<#ff5555>OFF</#55555>")));

        sender.sendMessage(Component.empty());
        sender.sendMessage(parseComponent(" <dark_gray>Quick Controls:</dark_gray>"));
        sender.sendMessage(parseComponent("  <click:run_command:/strangers start><hover:show_text:'<green>Open Start Confirmation GUI</green>'><gradient:#ff3355:#ff7733><b>[▶ START EVENT]</b></gradient></hover></click>   <click:run_command:/strangers reset><hover:show_text:'<yellow>Open Reset Confirmation GUI</yellow>'><gradient:#ffaa00:#ff6600><b>[↺ RESET EVENT]</b></gradient></hover></click>   <click:run_command:/strangers reload><hover:show_text:'<aqua>Reload plugin config & skins</aqua>'><gradient:#00ffaa:#00aa77><b>[⟳ RELOAD]</b></gradient></hover></click>   <click:run_command:/strangers bypass><hover:show_text:'<gray>Toggle admin bypass</gray>'><gradient:#aaccff:#88aaff><b>[👁 BYPASS]</b></gradient></hover></click>"));

        sender.sendMessage(Component.empty());
        sender.sendMessage(parseComponent("<dark_gray>────────────────────────────────────────────</dark_gray>"));

        if (sender instanceof Player p) {
            p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.isOp() && !sender.hasPermission("strangers.admin") && !sender.hasPermission("strangers.bypass")) {
            return Collections.emptyList();
        }
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (String sub : Arrays.asList("status", "on", "off", "reload", "bypass", "lives", "revive", "giverevivebook", "givetracker", "deathmessage", "deathsound", "start", "reset", "info")) {
                if (sub.startsWith(partial))
                    completions.add(sub);
            }
            return completions;
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            String partial = args[1].toLowerCase();
            if (sub.equals("start") || sub.equals("reset")) {
                if ("confirm".startsWith(partial)) {
                    completions.add("confirm");
                }
            } else if (sub.equals("bypass") || sub.equals("deathsound") || sub.equals("deathsounds")) {
                for (String mode : Arrays.asList("on", "off")) {
                    if (mode.startsWith(partial))
                        completions.add(mode);
                }
            } else if (sub.equals("deathmessage") || sub.equals("deathmessages")) {
                for (String mode : Arrays.asList("on", "off", "sound")) {
                    if (mode.startsWith(partial))
                        completions.add(mode);
                }
            } else if (sub.equals("lives")) {
                for (String action : Arrays.asList("set", "add", "remove", "get")) {
                    if (action.startsWith(partial))
                        completions.add(action);
                }
            } else if (sub.equals("revive")) {
                for (StrangersDatabase.LivesData data : lifeManager.getEliminatedPlayers()) {
                    if (data.playerName.toLowerCase().startsWith(partial)) {
                        completions.add(data.playerName);
                    }
                }
            } else if (sub.equals("giverevivebook") || sub.equals("givetracker")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p != null && p.getName().toLowerCase().startsWith(partial)) {
                        completions.add(p.getName());
                    }
                }
            }
            return completions;
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("deathmessage") || args[0].equalsIgnoreCase("deathmessages")) && (args[1].equalsIgnoreCase("sound") || args[1].equalsIgnoreCase("sounds"))) {
            String partial = args[2].toLowerCase();
            for (String mode : Arrays.asList("on", "off")) {
                if (mode.startsWith(partial))
                    completions.add(mode);
            }
            return completions;
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("givetracker") || args[0].equalsIgnoreCase("giverevivebook"))) {
            String partial = args[2].toLowerCase();
            for (String amt : Arrays.asList("1", "2", "3", "5", "64")) {
                if (amt.startsWith(partial)) {
                    completions.add(amt);
                }
            }
            return completions;
        } else if (args.length == 3 && args[0].equalsIgnoreCase("lives")) {
            String partial = args[2].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p != null && p.getName().toLowerCase().startsWith(partial)) {
                    completions.add(p.getName());
                }
            }
            return completions;
        }
        return Collections.emptyList();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncPlayerPreLogin(org.bukkit.event.player.AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        String realName = event.getName();
        if (realName != null && !realName.equalsIgnoreCase(anonymousName) && !realName.equalsIgnoreCase("Stranger")) {
            realNames.put(uuid, realName);
        }

        // Cache original values immediately upon pre-login if properties are already loaded
        PlayerProfile origProfile = event.getPlayerProfile();
        if (origProfile != null) {
            for (ProfileProperty prop : origProfile.getProperties()) {
                if ("textures".equalsIgnoreCase(prop.getName())) {
                    String val = prop.getValue();
                    String sig = prop.getSignature();
                    if (val != null && !val.isEmpty() && !val.equals(cachedSkinValue)) {
                        realSkins.put(uuid, new StrangersDatabase.CachedSkin(val, sig));
                        if (database != null) {
                            database.saveOriginalSkin(uuid, realName, val, sig);
                        }
                    }
                    break;
                }
            }
        }

        ensureOriginalSkinLoaded(uuid, realName);

        if (!PacketEventsHookLoader.isAvailable() && !ProtocolLibHookLoader.isAvailable()) {
            if (pluginEnabled && cachedSkinValue != null && cachedSkinSignature != null) {
                try {
                    PlayerProfile profile = Bukkit.createProfile(uuid, realName);
                    profile.setProperty(new ProfileProperty("textures", cachedSkinValue, cachedSkinSignature));
                    event.setPlayerProfile(profile);
                } catch (Exception e) {
                    getLogger().warning(
                            "Failed to apply pre-login skin profile to player " + realName + ": " + e.getMessage());
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncPlayerPreLoginBanCheck(org.bukkit.event.player.AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() == org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result.KICK_BANNED) {
            Component kickMsg = event.kickMessage();
            if (kickMsg != null) {
                String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(kickMsg);
                if (plain.contains("<") && plain.contains(">")) {
                    event.kickMessage(parseComponent(plain));
                }
            } else if (lifeManager != null && lifeManager.isEliminated(event.getUniqueId())) {
                String banReason = getMessage("ban-reason", "<gradient:#ff2244:#880000><bold>☠ IDENTITY EXPOSED ☠</bold></gradient>\n\n<gray>You ran out of lives. Your mask was stripped away.</gray>").replace("{NAME}", event.getName());
                event.kickMessage(parseComponent(banReason));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLoginBanCheck(org.bukkit.event.player.PlayerLoginEvent event) {
        if (event.getResult() == org.bukkit.event.player.PlayerLoginEvent.Result.KICK_BANNED) {
            Component kickMsg = event.kickMessage();
            if (kickMsg != null) {
                String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(kickMsg);
                if (plain.contains("<") && plain.contains(">")) {
                    event.kickMessage(parseComponent(plain));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!player.getName().equalsIgnoreCase(anonymousName) && !player.getName().equalsIgnoreCase("Stranger")) {
            realNames.put(uuid, player.getName());
        }

        // 1. Record original skin immediately BEFORE applying any disguises!
        recordOriginalSkin(player);

        originalDisplayNames.putIfAbsent(uuid, player.displayName());
        originalListNames.putIfAbsent(uuid, player.playerListName());

        if (pluginEnabled) {
            // Mute default join message completely for total anonymity
            event.joinMessage(null);

            // Apply skin, display names and name tag hiding only if not bypassed
            if (!isBypassed(player)) {
                applySkinAndAnonymize(player);
            }
        }

        ensureOriginalSkinLoaded(uuid, getRealName(uuid));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Clear UI cached values (preserve realNames and realSkins in memory so they are never lost!)
        originalProfiles.remove(uuid);
        originalDisplayNames.remove(uuid);
        originalListNames.remove(uuid);

        // Clear player voice processing states
        if (getServer().getPluginManager().getPlugin("voicechat") != null) {
            VoiceChatHookLoader.handlePlayerQuit(uuid);
        }

        if (pluginEnabled) {
            // Mute default quit message completely for total anonymity
            event.quitMessage(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!pluginEnabled) {
            return;
        }
        Player sourcePlayer = event.getPlayer();
        String realSenderName = getRealName(sourcePlayer.getUniqueId());
        boolean maskRealNames = getConfig().getBoolean("chat.mask-real-names", true);

        event.renderer((source, sourceDisplayName, message, viewer) -> {
            String rawMessage = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(message);
            if (maskRealNames) {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (online == null) continue;
                    String onlineName = online.getName();
                    rawMessage = rawMessage.replaceAll("(?i)\\b" + java.util.regex.Pattern.quote(onlineName) + "\\b", anonymousName);
                }
            }

            boolean isRevealedToViewer = viewer instanceof Player viewerPlayer &&
                    (isBypassed(viewerPlayer) || (trackerManager != null && trackerManager.isTrackerRevealed(viewerPlayer, sourcePlayer)));

            String format;
            if (isRevealedToViewer) {
                format = getConfig().getString("messages." + this.lang + ".chat-format-admin",
                        "<dark_gray>[</dark_gray><gradient:#FFA737:#FFD200><bold>{NAME}</bold></gradient><dark_gray>]</dark_gray> <dark_gray>»</dark_gray> <white>{MESSAGE}</white>");
                format = format.replace("{NAME}", realSenderName);
            } else {
                format = getConfig().getString("messages." + this.lang + ".chat-format");
                if (format == null || format.isEmpty()) {
                    format = getConfig().getString("messages.chat-format",
                            "<dark_gray>[</dark_gray><gradient:#ff3355:#ff6688><bold>{NAME}</bold></gradient><dark_gray>]</dark_gray> <dark_gray>»</dark_gray> <white>{MESSAGE}</white>");
                }
                format = format.replace("{NAME}", anonymousName);
            }

            if (format.contains("{MESSAGE}")) {
                String cleanFormat = format.replace("<white>{MESSAGE}</white>", "{MESSAGE}")
                                           .replace("<white>{MESSAGE}", "{MESSAGE}")
                                           .replace("{MESSAGE}</white>", "{MESSAGE}");
                int idx = cleanFormat.indexOf("{MESSAGE}");
                String prefix = cleanFormat.substring(0, idx);
                String suffix = cleanFormat.substring(idx + "{MESSAGE}".length());
                net.kyori.adventure.text.Component comp = parseComponent(prefix)
                        .append(Component.text(rawMessage).color(net.kyori.adventure.text.format.NamedTextColor.WHITE));
                if (!suffix.isEmpty() && !suffix.equalsIgnoreCase("</white>")) {
                    comp = comp.append(parseComponent(suffix));
                }
                return comp;
            } else {
                return parseComponent(format).append(Component.space()).append(Component.text(rawMessage));
            }
        });
    }

    @EventHandler
    public void onPlayerInteract(org.bukkit.event.player.PlayerInteractEvent event) {
        if (!pluginEnabled)
            return;
        if (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                || event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            ItemStack item = event.getItem();
            if (reviveManager != null && reviveManager.isReviveBook(item)) {
                event.setCancelled(true);
                reviveManager.openReviveGUI(event.getPlayer());
            }
        }
    }

    @EventHandler
    public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player clicker))
            return;

        if (event.getInventory().getHolder() instanceof ConfirmationGUI.ConfirmationHolder holder) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot == 11) {
                clicker.closeInventory();
                clicker.playSound(clicker.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.4f);
                if (holder.getType() == ConfirmationGUI.ConfirmationType.START) {
                    if (gameManager != null) {
                        gameManager.startGame(clicker);
                    }
                } else if (holder.getType() == ConfirmationGUI.ConfirmationType.RESET) {
                    if (gameManager != null) {
                        gameManager.performReset(clicker);
                    }
                }
            } else if (slot == 15) {
                clicker.closeInventory();
                clicker.playSound(clicker.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 0.8f);
                clicker.playSound(clicker.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                clicker.sendMessage(parseComponent(getMessage("action-cancelled",
                        "<gradient:#ff3355:#ff6688><bold>STRANGERS</bold></gradient> <dark_gray>»</dark_gray> <#ff5555>Action cancelled.</#ff5555>")));
            }
            return;
        }
        if (event.getInventory().getHolder() instanceof ReviveManager.ReviveHolder) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && clicked.hasItemMeta()) {
                String uuidStr = clicked.getItemMeta().getPersistentDataContainer()
                        .get(reviveManager.getTargetUuidKey(), org.bukkit.persistence.PersistentDataType.STRING);
                if (uuidStr != null) {
                    try {
                        UUID targetUuid = UUID.fromString(uuidStr);
                        StrangersDatabase.LivesData targetData = database.getLivesData(targetUuid);
                        if (targetData != null) {
                            if (targetData.hasBeenRevived) {
                                clicker.sendMessage(parseComponent(getMessage("already-revived",
                                        "&cThis player has already been revived once!")));
                                clicker.playSound(clicker.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f,
                                        1.0f);
                            } else {
                                ItemStack inHand = clicker.getInventory().getItemInMainHand();
                                ItemStack offHand = clicker.getInventory().getItemInOffHand();
                                if (reviveManager.isReviveBook(inHand)) {
                                    inHand.setAmount(inHand.getAmount() - 1);
                                } else if (reviveManager.isReviveBook(offHand)) {
                                    offHand.setAmount(offHand.getAmount() - 1);
                                } else {
                                    clicker.sendMessage(parseComponent(getMessage("need-revive-book", "<gradient:#ff3355:#ff6688><bold>STRANGERS</bold></gradient> <dark_gray>»</dark_gray> <#ff4444>You need a Revive Book in your hand to resurrect someone!")));
                                    clicker.closeInventory();
                                    return;
                                }
                                clicker.closeInventory();
                                reviveManager.revivePlayer(clicker, targetData.playerName);
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!pluginEnabled) {
            return;
        }
        Player player = event.getPlayer();

        // Mute default chat death message completely
        event.deathMessage(null);

        if (getConfig().getBoolean("lives-system.enabled", true) && lifeManager != null) {
            lifeManager.handlePlayerDeath(player, player.getKiller());
        } else {
            String realName = getRealName(player.getUniqueId());
            playRevealAnimation(realName);
            String action = getConfig().getString("death-action.mode", "BAN").toUpperCase();
            if ("BAN".equals(action)) {
                String banReason = getMessage("ban-reason", "&cYou ran out of lives! Your identity was revealed.").replace("{NAME}",
                        realName);
                Component banComp = parseComponent(banReason);
                String legacyBanReason = LegacyComponentSerializer.legacySection().serialize(banComp);
                Bukkit.getBanList(BanList.Type.NAME).addBan(realName, legacyBanReason, (java.util.Date) null, "Console");
                getServer().getScheduler().runTaskLater(this, () -> {
                    if (player.isOnline())
                        player.kick(banComp);
                }, 5L);
            }
        }
    }

    public void playRevealAnimation(String realName) {
        if (!getConfig().getBoolean("death-title.enabled", true) || realName == null) {
            return;
        }
        String subtitleText = getMessage("death-subtitle", "&4☠ &c&lDIED &4☠");
        String scrambleColor = getConfig().getString("death-title.scramble-color", "&#330a0a");
        String revealedColor = getConfig().getString("death-title.revealed-color", "&#ff0033");
        String finalFormat = getConfig().getString("death-title.final-title-format",
                "<gradient:#ff2a2a:#990000:#400000><bold>{NAME}</bold></gradient>");

        new RevealAnimationTask(this, realName, subtitleText, scrambleColor, revealedColor, finalFormat)
                .runTaskTimer(this, 0L, 2L);
    }

    public static class RevealAnimationTask extends org.bukkit.scheduler.BukkitRunnable {
        private final Strangers plugin;
        private final String realName;
        private final String subtitleText;
        private final String scrambleColor;
        private final String revealedColor;
        private final String finalFormat;
        private final int length;
        private int step = -1;
        private int ticksInStep = 0;

        public RevealAnimationTask(Strangers plugin, String realName, String subtitleText,
                String scrambleColor, String revealedColor, String finalFormat) {
            this.plugin = plugin;
            this.realName = realName;
            this.subtitleText = subtitleText;
            this.scrambleColor = scrambleColor;
            this.revealedColor = revealedColor;
            this.finalFormat = finalFormat;
            this.length = realName != null ? realName.length() : 0;
        }

        @Override
        public void run() {
            if (realName == null || step > length) {
                cancel();
                return;
            }

            // Phase 1: Initial Scrambling (lasts for 20 ticks / 1 second)
            if (step == -1) {
                if (ticksInStep == 0) {
                    plugin.playSoundToAll(org.bukkit.Sound.ENTITY_WITHER_DEATH, 1.0f, 0.7f);
                    plugin.playSoundToAll(org.bukkit.Sound.BLOCK_ANVIL_LAND, 0.8f, 0.2f);
                }

                // No subtitle during scramble
                plugin.sendDeathTitle(scrambleColor + "&k" + realName, "", 0, 10, 0);

                ticksInStep += 2;
                if (ticksInStep >= 20) {
                    step = 1; // Start decoding with 1st letter
                    ticksInStep = 0;
                }
                return;
            }

            // Phase 2: Decoding letters (runs every 10 ticks / 0.5 seconds per letter)
            if (step < length) {
                if (ticksInStep == 0) {
                    plugin.playSoundToAll(org.bukkit.Sound.UI_BUTTON_CLICK, 0.8f, 1.8f);
                }

                String revealed = realName.substring(0, step);
                String obfuscated = realName.substring(step);
                String titleText = revealedColor + "&l" + revealed + scrambleColor + "&k" + obfuscated;

                // No subtitle during letter decoding
                plugin.sendDeathTitle(titleText, "", 0, 10, 0);

                ticksInStep += 2;
                if (ticksInStep >= 10) {
                    step++;
                    ticksInStep = 0;
                }
                return;
            }

            // Phase 3: Final Reveal (displays full bold name + subtitle + sound effects)
            if (step == length) {
                plugin.playSoundToAll(org.bukkit.Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.7f);
                plugin.playSoundToAll(org.bukkit.Sound.ENTITY_WITHER_SPAWN, 0.6f, 0.5f);
                plugin.playSoundToAll(org.bukkit.Sound.BLOCK_END_PORTAL_SPAWN, 0.7f, 0.6f);

                String finalTitle = finalFormat.replace("{NAME}", realName);
                plugin.sendDeathTitle(finalTitle, subtitleText, 0, 50, 25);

                step++;
            }
        }
    }

    public void playSoundToAll(org.bukkit.Sound sound, float volume, float pitch) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), sound, volume, pitch);
        }
    }

    public void sendDeathTitle(String titleText, String subtitleText, int fadeInTicks, int stayTicks,
            int fadeOutTicks) {
        Component titleComp = parseComponent(titleText);
        Component subComp = parseComponent(subtitleText);

        net.kyori.adventure.title.Title.Times times = net.kyori.adventure.title.Title.Times.times(
                java.time.Duration.ofMillis(fadeInTicks * 50L),
                java.time.Duration.ofMillis(stayTicks * 50L),
                java.time.Duration.ofMillis(fadeOutTicks * 50L));
        net.kyori.adventure.title.Title title = net.kyori.adventure.title.Title.title(titleComp, subComp, times);

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer != null) {
                onlinePlayer.showTitle(title);
            }
        }
    }

    public void sendTitle(Player player, String titleText, String subtitleText, int fadeInTicks, int stayTicks,
            int fadeOutTicks) {
        if (player == null || !player.isOnline()) return;
        Component titleComp = parseComponent(titleText);
        Component subComp = parseComponent(subtitleText);

        net.kyori.adventure.title.Title.Times times = net.kyori.adventure.title.Title.Times.times(
                java.time.Duration.ofMillis(fadeInTicks * 50L),
                java.time.Duration.ofMillis(stayTicks * 50L),
                java.time.Duration.ofMillis(fadeOutTicks * 50L));
        net.kyori.adventure.title.Title title = net.kyori.adventure.title.Title.title(titleComp, subComp, times);
        player.showTitle(title);
    }

    public void sendTitleToAll(String titleText, String subtitleText, int fadeInTicks, int stayTicks,
            int fadeOutTicks) {
        Component titleComp = parseComponent(titleText);
        Component subComp = parseComponent(subtitleText);

        net.kyori.adventure.title.Title.Times times = net.kyori.adventure.title.Title.Times.times(
                java.time.Duration.ofMillis(fadeInTicks * 50L),
                java.time.Duration.ofMillis(stayTicks * 50L),
                java.time.Duration.ofMillis(fadeOutTicks * 50L));
        net.kyori.adventure.title.Title title = net.kyori.adventure.title.Title.title(titleComp, subComp, times);

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer != null) {
                onlinePlayer.showTitle(title);
            }
        }
    }

    public Component parseComponent(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }

        if (text.contains("<") && text.contains(">")) {
            try {
                String converted = text.replaceAll("(?i)&#([a-f0-9]{6})", "<#$1>");
                converted = convertLegacyToMiniMessage(converted);
                return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(converted);
            } catch (Exception ignored) {
            }
        }

        String processed = translateHexColorCodes("&#", "", text);
        processed = processed.replace('&', '\u00A7');
        return LegacyComponentSerializer.legacySection().deserialize(processed);
    }

    private String convertLegacyToMiniMessage(String message) {
        if (message == null || (!message.contains("&") && !message.contains("\u00A7"))) {
            return message;
        }
        return message
                .replaceAll("(?i)[&§]0", "<black>")
                .replaceAll("(?i)[&§]1", "<dark_blue>")
                .replaceAll("(?i)[&§]2", "<dark_green>")
                .replaceAll("(?i)[&§]3", "<dark_aqua>")
                .replaceAll("(?i)[&§]4", "<dark_red>")
                .replaceAll("(?i)[&§]5", "<dark_purple>")
                .replaceAll("(?i)[&§]6", "<gold>")
                .replaceAll("(?i)[&§]7", "<gray>")
                .replaceAll("(?i)[&§]8", "<dark_gray>")
                .replaceAll("(?i)[&§]9", "<blue>")
                .replaceAll("(?i)[&§]a", "<green>")
                .replaceAll("(?i)[&§]b", "<aqua>")
                .replaceAll("(?i)[&§]c", "<red>")
                .replaceAll("(?i)[&§]d", "<light_purple>")
                .replaceAll("(?i)[&§]e", "<yellow>")
                .replaceAll("(?i)[&§]f", "<white>")
                .replaceAll("(?i)[&§]k", "<obfuscated>")
                .replaceAll("(?i)[&§]l", "<bold>")
                .replaceAll("(?i)[&§]m", "<strikethrough>")
                .replaceAll("(?i)[&§]n", "<underlined>")
                .replaceAll("(?i)[&§]o", "<italic>")
                .replaceAll("(?i)[&§]r", "<reset>");
    }

    private String translateHexColorCodes(String startTag, String endTag, String message) {
        if (message == null)
            return "";
        final java.util.regex.Pattern hexPattern = java.util.regex.Pattern
                .compile(startTag + "([A-Fa-f0-9]{6})" + endTag);
        java.util.regex.Matcher matcher = hexPattern.matcher(message);
        StringBuilder builder = new StringBuilder(message.length() + 32);
        while (matcher.find()) {
            String group = matcher.group(1);
            StringBuilder replacement = new StringBuilder("\u00A7x");
            for (char c : group.toCharArray()) {
                replacement.append('\u00A7').append(c);
            }
            matcher.appendReplacement(builder, java.util.regex.Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    public String getMessage(String key, String fallback) {
        String msg = getConfig().getString("messages." + lang + "." + key);
        if (msg == null || msg.isEmpty()) {
            msg = getConfig().getString("messages." + key);
        }
        if (msg == null || msg.isEmpty()) {
            return colorize(fallback);
        }
        return colorize(msg);
    }

    private String colorize(String text) {
        if (text == null)
            return "";
        return text.replace('&', '\u00A7');
    }

    private String extractJsonKey(String json, String key) {
        String pattern = "\"" + key + "\"";
        int keyIndex = json.indexOf(pattern);
        if (keyIndex == -1)
            return null;

        int colonIndex = json.indexOf(":", keyIndex + pattern.length());
        if (colonIndex == -1)
            return null;

        int quoteStart = json.indexOf("\"", colonIndex + 1);
        if (quoteStart == -1)
            return null;

        int quoteEnd = json.indexOf("\"", quoteStart + 1);
        if (quoteEnd == -1)
            return null;

        return json.substring(quoteStart + 1, quoteEnd);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommandPreprocess(org.bukkit.event.player.PlayerCommandPreprocessEvent event) {
        if (!pluginEnabled) {
            return;
        }
        Player sender = event.getPlayer();
        if (isBypassed(sender)) {
            return;
        }

        String message = event.getMessage().trim();
        if (message.length() <= 1 || !message.startsWith("/")) {
            return;
        }

        // Split by whitespace and examine arguments
        String[] tokens = message.substring(1).split("\\s+");
        if (tokens.length == 0) {
            return;
        }

        String firstToken = tokens[0].toLowerCase();
        // Handle namespace prefix, e.g. minecraft:msg -> msg, essentials:msg -> msg
        if (firstToken.contains(":")) {
            firstToken = firstToken.substring(firstToken.indexOf(":") + 1);
        }

        // 1. Block command execution if it is in the blocked list
        if (blockedCommands.contains(firstToken)) {
            event.setCancelled(true);
            sender.sendMessage(parseComponent(getMessage("no-permission", "&cYou do not have permission to execute this command!")));
            return;
        }

        // 2. Block target selectors for normal players (@a, @p, @r, @e)
        for (int i = 1; i < tokens.length; i++) {
            String token = tokens[i].toLowerCase();
            if (token.startsWith("@p") || token.startsWith("@a") || token.startsWith("@r") || token.startsWith("@e")) {
                event.setCancelled(true);
                sender.sendMessage(parseComponent(getMessage("no-permission", "&cYou do not have permission to execute this command!")));
                return;
            }
        }

        // 3. Block any command execution if any argument matches an online player's real name
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online == null) continue;
            String realName = getRealName(online.getUniqueId());
            if (realName.equals("Unknown") || realName.equalsIgnoreCase(anonymousName)) {
                continue;
            }
            String realNameLower = realName.toLowerCase();
            for (int i = 1; i < tokens.length; i++) {
                String cleanToken = tokens[i].replaceAll("[^a-zA-Z0-9_]", "").toLowerCase();
                if (cleanToken.equals(realNameLower)) {
                    event.setCancelled(true);
                    sender.sendMessage(parseComponent(getMessage("no-permission", "&cYou do not have permission to execute this command!")));
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onAsyncTabComplete(com.destroystokyo.paper.event.server.AsyncTabCompleteEvent event) {
        if (!pluginEnabled) {
            return;
        }
        CommandSender sender = event.getSender();
        if (isBypassed(sender)) {
            return;
        }

        List<String> completions = new ArrayList<>(event.getCompletions());
        boolean modified = false;

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online == null) continue;
            String realName = online.getName();
            if (completions.removeIf(c -> c.equalsIgnoreCase(realName))) {
                modified = true;
            }
        }

        if (modified) {
            event.setCompletions(completions);
        }

        try {
            List<com.destroystokyo.paper.event.server.AsyncTabCompleteEvent.Completion> compList = new ArrayList<>(event.completions());
            boolean compModified = false;
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online == null) continue;
                String realName = online.getName();
                if (compList.removeIf(c -> c.suggestion().equalsIgnoreCase(realName))) {
                    compModified = true;
                }
            }
            if (compModified) {
                event.completions(compList);
            }
        } catch (Throwable ignored) {
        }
    }

    @EventHandler
    public void onTabComplete(org.bukkit.event.server.TabCompleteEvent event) {
        if (!pluginEnabled) {
            return;
        }
        CommandSender sender = event.getSender();
        if (isBypassed(sender)) {
            return;
        }

        List<String> completions = new ArrayList<>(event.getCompletions());
        boolean modified = false;

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online == null) continue;
            String realName = online.getName();
            if (completions.removeIf(c -> c.equalsIgnoreCase(realName))) {
                modified = true;
            }
        }

        if (modified) {
            event.setCompletions(completions);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPaperServerListPing(com.destroystokyo.paper.event.server.PaperServerListPingEvent event) {
        if (!pluginEnabled) {
            return;
        }
        if (getConfig().getBoolean("server-list.hide-player-sample", true)) {
            event.setHidePlayers(true);
            try {
                event.getPlayerSample().clear();
                event.getListedPlayers().clear();
            } catch (Throwable ignored) {
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onServerListPing(org.bukkit.event.server.ServerListPingEvent event) {
        if (!pluginEnabled) {
            return;
        }
        if (getConfig().getBoolean("server-list.hide-player-sample", true)) {
            try {
                Iterator<Player> it = event.iterator();
                while (it.hasNext()) {
                    it.next();
                    it.remove();
                }
            } catch (Throwable ignored) {
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerAdvancementDone(org.bukkit.event.player.PlayerAdvancementDoneEvent event) {
        if (!pluginEnabled) {
            return;
        }
        Player player = event.getPlayer();
        if (isBypassed(player)) {
            return;
        }

        String mode = getConfig().getString("advancements.mode", "ANONYMOUS").toUpperCase();
        if ("HIDE".equals(mode)) {
            event.message(null);
        } else if ("ANONYMOUS".equals(mode)) {
            Component original = event.message();
            if (original != null) {
                String legacy = LegacyComponentSerializer.legacySection().serialize(original);
                String realName = player.getName();
                if (legacy.contains(realName)) {
                    legacy = legacy.replace(realName, anonymousName);
                    event.message(LegacyComponentSerializer.legacySection().deserialize(legacy));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerEditBook(org.bukkit.event.player.PlayerEditBookEvent event) {
        if (!pluginEnabled) {
            return;
        }
        if (isBypassed(event.getPlayer())) {
            return;
        }

        if (event.isSigning()) {
            org.bukkit.inventory.meta.BookMeta meta = event.getNewBookMeta();
            meta.setAuthor(anonymousName);
            event.setNewBookMeta(meta);
        }
    }

    private void printAsciiArt(long loadTime) {
        final String R = "\033[0m"; // Reset
        final String B = "\033[1m"; // Bold
        final String c1 = "\033[38;2;255;51;85m"; // #FF3355 — Crimson
        final String c2 = "\033[38;2;255;102;136m"; // #FF6688 — Rose
        final String c3 = "\033[38;2;255;153;170m"; // #FF99AA — Soft Pink

        org.bukkit.Bukkit.getConsoleSender().sendMessage("");
        org.bukkit.Bukkit.getConsoleSender().sendMessage(
                c1 + B + "  ███████╗████████╗██████╗  █████╗ ███╗   ██╗ ██████╗ ███████╗██████╗ ███████╗" + R);
        org.bukkit.Bukkit.getConsoleSender().sendMessage(
                c1 + B + "  ██╔════╝╚══██╔══╝██╔══██╗██╔══██╗████╗  ██║██╔════╝ ██╔════╝██╔══██╗██╔════╝" + R);
        org.bukkit.Bukkit.getConsoleSender().sendMessage(
                c2 + B + "  ███████╗   ██║   ██████╔╝███████║██╔██╗ ██║██║  ███╗█████╗  ██████╔╝███████╗" + R);
        org.bukkit.Bukkit.getConsoleSender().sendMessage(
                c2 + B + "  ╚════██║   ██║   ██╔══██╗██╔══██║██║╚██╗██║██║   ██║██╔══╝  ██╔══██╗╚════██║" + R);
        org.bukkit.Bukkit.getConsoleSender().sendMessage(
                c3 + B + "  ███████║   ██║   ██║  ██║██║  ██║██║ ╚████║╚██████╔╝███████╗██║  ██║███████║" + R);
        org.bukkit.Bukkit.getConsoleSender().sendMessage(
                c3 + B + "  ╚══════╝   ╚═╝   ╚═╝  ╚═╝╚═╝  ╚═╝╚═╝  ╚═══╝ ╚═════╝ ╚══════╝╚═╝  ╚═╝╚══════╝" + R);
        org.bukkit.Bukkit.getConsoleSender().sendMessage("");

        int width = 78;
        org.bukkit.Bukkit.getConsoleSender().sendMessage(c1 + "\u256d" + "\u2500".repeat(width + 2) + "\u256e" + R);
        printBoxedLine("&cStrangers &8| &7Premium Anonymity & Social Stealth &8• &fmlnplus", width, c1,
                R);
        org.bukkit.Bukkit.getConsoleSender().sendMessage(c1 + "\u251c" + "\u2500".repeat(width + 2) + "\u2524" + R);
        printBoxedLine(" &e\u2726 &fVersion: &b" + getPluginMeta().getVersion(), width, c1, R);
        printBoxedLine(" &e\u2726 &fEnvironment: &7Paper 1.20+", width, c1, R);
        printBoxedLine(" &e\u2726 &fLanguage: &7" + (lang.equalsIgnoreCase("hu") ? "Magyar (HU)" : "English (EN)"),
                width, c1, R);

        // Detect other active mlnplus plugins dynamically
        List<String> active = new ArrayList<>();
        for (org.bukkit.plugin.Plugin p : org.bukkit.Bukkit.getPluginManager().getPlugins()) {
            if (p.getName().equalsIgnoreCase(getName())) continue;
            boolean isMlnplus = false;
            String website = p.getPluginMeta().getWebsite();
            if (p.getPluginMeta().getAuthors().contains("mlnplus")) {
                isMlnplus = true;
            } else if (website != null && (website.contains("mlnplus") || website.contains("mln.plus"))) {
                isMlnplus = true;
            } else if (p.getClass().getName().startsWith("mln.plus") || p.getClass().getName().startsWith("mlnplus.hu")) {
                isMlnplus = true;
            }
            if (isMlnplus) {
                active.add(p.getName() + " v" + p.getPluginMeta().getVersion());
            }
        }
        if (!active.isEmpty()) {
            printBoxedLine(" &e\u2726 &fLinks: &a" + String.join("&7, &a", active), width, c1, R);
        } else {
            printBoxedLine(" &e\u2726 &fLinks: &cNone detected", width, c1, R);
        }
        printBoxedLine(" &e\u2726 &fWebsite: &bmln.plus", width, c1, R);

        printBoxedLine(" &e\u2726 &fStatus: &aSuccessfully enabled &7(" + loadTime + "ms)", width, c1, R);
        org.bukkit.Bukkit.getConsoleSender().sendMessage(c1 + "\u2570" + "\u2500".repeat(width + 2) + "\u256f" + R);
        org.bukkit.Bukkit.getConsoleSender().sendMessage("");
    }

    private void printBoxedLine(String text, int width, String borderColorCode, String resetCode) {
        String coloredText = text.replaceAll("&([0-9a-fk-orA-FK-ORxX])", "\u00A7$1");
        int visibleLength = stripColors(coloredText).length();
        int padding = Math.max(0, width - visibleLength);
        String line = borderColorCode + "\u2502 " + resetCode + coloredText + " ".repeat(padding) + borderColorCode
                + " \u2502" + resetCode;
        org.bukkit.Bukkit.getConsoleSender().sendMessage(line);
    }

    private String stripColors(String text) {
        if (text == null)
            return "";
        String stripped = text.replaceAll("[\u00A7&][0-9a-fk-orA-FK-ORxX]", "");
        stripped = stripped.replaceAll("\033\\[[0-9;]*[mK]", "");
        return stripped;
    }
}

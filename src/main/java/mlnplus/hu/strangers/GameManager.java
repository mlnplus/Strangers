package mlnplus.hu.strangers;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings("null")
public class GameManager {

    private final Strangers plugin;
    private final AtomicBoolean isStarting = new AtomicBoolean(false);

    private volatile UUID startInitiatorUuid = null;
    private volatile boolean startInitiatorIsConsole = false;

    private Double previousBorderSize = null;
    private Double previousCenterX = null;
    private Double previousCenterZ = null;

    public GameManager(Strangers plugin) {
        this.plugin = plugin;
    }

    public void handleStartCommand(CommandSender sender, boolean hasConfirmArg) {
        if (hasConfirmArg) {
            startGame(sender);
            return;
        }

        if (sender instanceof Player player) {
            ConfirmationGUI.open(plugin, player, ConfirmationGUI.ConfirmationType.START);
            return;
        }

        // Console fallback
        sender.sendMessage(plugin.parseComponent(plugin.getMessage("start-console-prompt",
                "<gradient:#ff3355:#ff6688><bold>STRANGERS</bold></gradient> <dark_gray>»</dark_gray> <gray>To confirm event start from console, run: <white>/strangers start confirm</white></gray>")));
    }

    public void startGame(CommandSender sender) {
                if (!isStarting.compareAndSet(false, true)) {
            sender.sendMessage(plugin.parseComponent(plugin.getMessage("already-starting", "&cGame start is already in progress!")));
            return;
        }

        if (sender instanceof Player p) {
            startInitiatorUuid = p.getUniqueId();
            startInitiatorIsConsole = false;
        } else {
            startInitiatorUuid = null;
            startInitiatorIsConsole = true;
        }

        World world = getTargetWorld(sender);
        if (world == null) {
            sender.sendMessage(plugin.parseComponent(plugin.getMessage("world-not-found", "<gradient:#ff3355:#ff6688><bold>STRANGERS</bold></gradient> <dark_gray>»</dark_gray> <#ff4444>Could not find a valid world to start the event!")));
            isStarting.set(false);
            return;
        }

        double borderSize = Math.max(1.0, Math.min(plugin.getConfig().getDouble("game.border-size", 8000.0), 59999968.0));
        double centerX = plugin.getConfig().getDouble("game.border-center-x", 0.0);
        double centerZ = plugin.getConfig().getDouble("game.border-center-z", 0.0);
        double margin = plugin.getConfig().getDouble("game.scatter-safe-margin", 200.0);
        double scatterRadius = Math.max(100.0, (borderSize / 2.0) - margin);

        // 1. Save previous border size and apply new border (8k total, 4k in every direction from center)
        WorldBorder border = world.getWorldBorder();
        if (previousBorderSize == null) {
            previousBorderSize = border.getSize();
            previousCenterX = border.getCenter().getX();
            previousCenterZ = border.getCenter().getZ();
        }
        border.setCenter(centerX, centerZ);
        border.setSize(borderSize);

        // 2. Enable Strangers anonymization
        plugin.setPluginEnabled(true);

        // 3. Notify sender
        String prepMsg = plugin.getMessage("start-preparing",
                "<gradient:#ff3355:#ff6688><bold>STRANGERS</bold></gradient> <dark_gray>»</dark_gray> <gray>Setting world border to <#ff4d6d><bold>{BORDER}</bold></#ff4d6d><gray> and scattering players safely on the surface...")
                .replace("{BORDER}", String.valueOf((int) borderSize));
        sender.sendMessage(plugin.parseComponent(prepMsg));

        // 4. Scatter online players
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.isEmpty()) {
            isStarting.set(false);
            sender.sendMessage(plugin.parseComponent(plugin.getMessage("start-no-players", "<gradient:#ff3355:#ff6688><bold>STRANGERS</bold></gradient> <dark_gray>»</dark_gray> <#ffaa00>Event started! <gray>(No online players to scatter)</gray></#ffaa00>")));
            return;
        }

        // Shuffle players so sector assignments are randomized
        Collections.shuffle(players);

        double configuredMinDist = plugin.getConfig().getDouble("game.min-player-distance", 800.0);

        Map<UUID, Location> targetLocations = new ConcurrentHashMap<>();
        List<Location> assignedLocations = Collections.synchronizedList(new ArrayList<>());

        scatterPlayersSequentially(world, players, 0, targetLocations, assignedLocations,
                centerX, centerZ, scatterRadius, configuredMinDist, borderSize);
    }

    private void teleportAllScatteredPlayers(World world, List<Player> players, Map<UUID, Location> targetLocations, double borderSize) {
        int protectionSeconds = plugin.getConfig().getInt("game.teleport-protection-seconds", 10);
        int countdownSeconds = plugin.getConfig().getInt("game.countdown-seconds", 3);
        boolean fancyFx = plugin.getConfig().getBoolean("game.fancy-teleport-effects", true);
        boolean cinematicTitles = plugin.getConfig().getBoolean("game.cinematic-titles", true);

        // Pre-teleport water safety assertion: ensure no assigned destination touches water
        for (Player player : players) {
            if (!player.isOnline()) continue;
            Location loc = targetLocations.get(player.getUniqueId());
            if (loc == null) continue;

            Block bFeet = loc.getBlock();
            Block bGround = loc.clone().subtract(0, 1, 0).getBlock();
            Block bHead = loc.clone().add(0, 1, 0).getBlock();
            if (isWaterOrLiquid(bFeet) || isWaterOrLiquid(bGround) || isWaterOrLiquid(bHead)) {
                Location safe = findEmergencyDryLand(world, loc.getBlockX(), loc.getBlockZ(), new ArrayList<>(targetLocations.values()));
                if (safe != null) {
                    targetLocations.put(player.getUniqueId(), safe);
                }
            }
        }

        if (countdownSeconds <= 0 || !fancyFx) {
            executeTeleportAndArrival(world, players, targetLocations, borderSize, protectionSeconds, fancyFx, cinematicTitles);
            return;
        }

        // Cinematic Audio-Visual Countdown (3.. 2.. 1..)
        new org.bukkit.scheduler.BukkitRunnable() {
            int secondsLeft = countdownSeconds;

            @Override
            public void run() {
                if (secondsLeft > 0) {
                    String numeral = switch (secondsLeft) {
                        case 5 -> "<gradient:#ffa737:#ff2a4b>❺</gradient>";
                        case 4 -> "<gradient:#ffa737:#ff2a4b>❹</gradient>";
                        case 3 -> "<gradient:#ffa737:#ff2a4b>❸</gradient>";
                        case 2 -> "<gradient:#ffa737:#ff2a4b>❷</gradient>";
                        case 1 -> "<gradient:#ff2a4b:#990000>❶</gradient>";
                        default -> "<gradient:#ffa737:#ff2a4b>" + secondsLeft + "</gradient>";
                    };
                    String defaultSub = switch (secondsLeft) {
                        case 3 -> (plugin.getLang().equalsIgnoreCase("hu")) ? "<gray>Játékosok szétszórása...</gray>" : "<gray>Scattering players...</gray>";
                        case 2 -> (plugin.getLang().equalsIgnoreCase("hu")) ? "<gray>Kilétek elfedése...</gray>" : "<gray>Concealing identities...</gray>";
                        case 1 -> (plugin.getLang().equalsIgnoreCase("hu")) ? "<#ff4455>Ne bízz senkiben...</#ff4455>" : "<#ff4455>Trust no one...</#ff4455>";
                        default -> (plugin.getLang().equalsIgnoreCase("hu")) ? "<gray>Szétszórás...</gray>" : "<gray>Scattering...</gray>";
                    };
                    String sub = plugin.getMessage("countdown-" + secondsLeft + "-subtitle", defaultSub);

                    float notePitch = 0.5f + (countdownSeconds - secondsLeft) * 0.35f;
                    float anchorPitch = 0.8f + (countdownSeconds - secondsLeft) * 0.3f;

                    for (Player p : players) {
                        if (p == null || !p.isOnline()) continue;
                        plugin.sendTitle(p, numeral, sub, 0, 22, 5);
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, notePitch);
                        p.playSound(p.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.8f, anchorPitch);

                        // Swirling portal dust particles
                        Location pLoc = p.getLocation();
                        for (double a = 0; a < 2 * Math.PI; a += Math.PI / 6) {
                            p.spawnParticle(Particle.PORTAL, pLoc.getX() + Math.cos(a) * 1.2, pLoc.getY() + 0.3, pLoc.getZ() + Math.sin(a) * 1.2, 4, 0, 0, 0, 0.05);
                            p.spawnParticle(Particle.SOUL_FIRE_FLAME, pLoc.getX() + Math.cos(a) * 0.8, pLoc.getY() + 0.1, pLoc.getZ() + Math.sin(a) * 0.8, 1, 0, 0.02, 0, 0.01);
                        }
                    }
                    secondsLeft--;
                } else {
                    cancel();

                    // Screen transition: brief darkness / blindness fade
                    for (Player p : players) {
                        if (!p.isOnline()) continue;
                        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 1, false, false, false));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 30, 1, false, false, false));
                        p.playSound(p.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 0.6f, 1.3f);
                    }

                    // Execute teleport after 10 ticks (0.5s) for smooth fade-in
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        executeTeleportAndArrival(world, players, targetLocations, borderSize, protectionSeconds, fancyFx, cinematicTitles);
                    }, 10L);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void executeTeleportAndArrival(
            World world,
            List<Player> players,
            Map<UUID, Location> targetLocations,
            double borderSize,
            int protectionSeconds,
            boolean fancyFx,
            boolean cinematicTitles
    ) {
        for (Player player : players) {
            if (!player.isOnline()) continue;
            Location loc = targetLocations.get(player.getUniqueId());
            if (loc == null) continue;

            if (player.isInsideVehicle()) {
                player.leaveVehicle();
            }
            player.eject();

            player.teleportAsync(loc).thenRun(() -> {
                player.setFallDistance(0.0f);
                if (protectionSeconds > 0) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, protectionSeconds * 20, 4));
                }
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 80, 0));

                if (fancyFx) {
                    Location arrival = player.getLocation();
                    player.spawnParticle(Particle.PORTAL, arrival, 100, 0.6, 1.0, 0.6, 0.5);
                    player.spawnParticle(Particle.SOUL_FIRE_FLAME, arrival.clone().add(0, 0.8, 0), 40, 0.5, 0.6, 0.5, 0.05);
                    player.spawnParticle(Particle.FLASH, arrival.clone().add(0, 1.2, 0), 2, 0.1, 0.1, 0.1, 0.0);
                    player.spawnParticle(Particle.SMOKE, arrival.clone().add(0, 0.5, 0), 30, 0.5, 0.5, 0.5, 0.05);

                    player.playSound(arrival, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f);
                    player.playSound(arrival, Sound.ITEM_CHORUS_FRUIT_TELEPORT, 1.0f, 0.9f);
                    player.playSound(arrival, Sound.BLOCK_BELL_RESONATE, 0.7f, 0.6f);
                }
            });
        }

        isStarting.set(false);
        finishGameStart(world, borderSize, protectionSeconds, cinematicTitles);
    }

    private void finishGameStart(World world, double borderSize, int protectionSeconds, boolean cinematicTitles) {
        // Direct admin feedback to initiator (no global chat announcement spam)
        CommandSender initiator = null;
        if (startInitiatorIsConsole) {
            initiator = Bukkit.getConsoleSender();
        } else if (startInitiatorUuid != null) {
            initiator = Bukkit.getPlayer(startInitiatorUuid);
        }

        if (initiator != null) {
            String successMsg = plugin.getMessage("start-success",
                    "<gradient:#ff3355:#ff6688><bold>STRANGERS</bold></gradient> <dark_gray>»</dark_gray> <#55ff99>Event successfully initiated! <gray>World border active (<#ff4d6d><bold>{BORDER} blocks</bold></#ff4d6d>), identities concealed, and players scattered safely.</gray></#55ff99>")
                    .replace("{BORDER}", String.valueOf((int) borderSize));
            initiator.sendMessage(plugin.parseComponent(successMsg));
        }

        if (!cinematicTitles) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p != null) {
                    p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 1.0f, 1.0f);
                }
            }
            String titleStr = plugin.getMessage("start-title", "<gradient:#ff3355:#ff6688><bold>⚔ STRANGERS ⚔</bold></gradient>");
            String subStr = plugin.getMessage("start-subtitle", "");
            plugin.sendTitleToAll(titleStr, subStr, 10, 60, 20);
            return;
        }

        // Phase 1 (Instant): Dramatic Horns & Sound Stingers + Cool STRANGERS Title
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p != null) {
                p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 1.0f, 0.85f);
                p.playSound(p.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.6f, 0.7f);
                try {
                    p.playSound(p.getLocation(), Sound.ITEM_GOAT_HORN_SOUND_0, 0.8f, 0.9f);
                } catch (Throwable ignored) {}
            }
        }

        String startTitle = plugin.getMessage("start-title", "<gradient:#ff3355:#ff6688><bold>⚔ STRANGERS ⚔</bold></gradient>");
        String startSub = plugin.getMessage("start-subtitle", "");
        plugin.sendTitleToAll(startTitle, startSub, 8, 45, 10);

        // Phase 2 (Delayed by 50 ticks / 2.5s): TRUST NO ONE Title
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p != null) {
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.9f);
                    p.playSound(p.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.6f, 0.7f);
                }
            }
            String defaultTitle2 = (plugin.getLang().equalsIgnoreCase("hu"))
                    ? "<gradient:#ff4466:#ff2244><bold>⚔ NE BÍZZ SENKIBEN ⚔</bold></gradient>"
                    : "<gradient:#ff4466:#ff2244><bold>⚔ TRUST NO ONE ⚔</bold></gradient>";
            String title2 = plugin.getMessage("start-stage2-title", defaultTitle2);
            String sub2 = plugin.getMessage("start-stage2-subtitle", "");
            plugin.sendTitleToAll(title2, sub2, 6, 50, 15);

            // Actionbar indication
            String actionbarMsg = (plugin.getLang().equalsIgnoreCase("hu"))
                    ? "<gradient:#ff2a4b:#ffa737><b>⚔ SZÉTSZÓRÁS KÉSZ</b></gradient> <dark_gray>▪</dark_gray> <gray>Védelem aktív: <#55ff99>" + protectionSeconds + "s</#55ff99></gray>"
                    : "<gradient:#ff2a4b:#ffa737><b>⚔ SCATTER COMPLETE</b></gradient> <dark_gray>▪</dark_gray> <gray>Resistance active: <#55ff99>" + protectionSeconds + "s</#55ff99></gray>";
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p != null) {
                    p.sendActionBar(plugin.parseComponent(actionbarMsg));
                }
            }
        }, 50L);
    }

    public void handleResetCommand(CommandSender sender, boolean hasConfirmArg) {
        if (hasConfirmArg) {
            performReset(sender);
            return;
        }

        if (sender instanceof Player player) {
            ConfirmationGUI.open(plugin, player, ConfirmationGUI.ConfirmationType.RESET);
            return;
        }

        // Console fallback
        sender.sendMessage(plugin.parseComponent(plugin.getMessage("reset-console-prompt",
                "<gradient:#ffaa00:#ffcc00><bold>STRANGERS</bold></gradient> <dark_gray>»</dark_gray> <gray>To confirm event reset from console, run: <white>/strangers reset confirm</white></gray>")));
    }

    public void performReset(CommandSender sender) {
        World world = getTargetWorld(sender);
        if (world == null) {
            sender.sendMessage(plugin.parseComponent(plugin.getMessage("world-not-found", "<gradient:#ff3355:#ff6688><bold>STRANGERS</bold></gradient> <dark_gray>»</dark_gray> <#ff4444>Could not find a valid world to reset!")));
            return;
        }

        // 1. Reset World Border
        double resetSize = plugin.getConfig().getDouble("game.reset-border-size", 59999968.0);
        double resetX = 0.0;
        double resetZ = 0.0;
        if (previousBorderSize != null) {
            resetSize = previousBorderSize;
            resetX = previousCenterX != null ? previousCenterX : 0.0;
            resetZ = previousCenterZ != null ? previousCenterZ : 0.0;
        }
        resetSize = Math.max(1.0, Math.min(resetSize, 59999968.0));

        WorldBorder border = world.getWorldBorder();
        try {
            border.setCenter(resetX, resetZ);
            border.setSize(resetSize);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to reset world border size to " + resetSize + ": " + e.getMessage());
            border.setSize(59999968.0);
        }

        // 2. Teleport everyone back to world spawn
        Location spawnLoc = world.getSpawnLocation().clone().add(0.5, 0.0, 0.5);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null) continue;
            if (player.isInsideVehicle()) {
                player.leaveVehicle();
            }
            player.eject();
            player.teleportAsync(spawnLoc);
        }

        // 3. Disable Strangers if configured
        if (plugin.getConfig().getBoolean("game.reset-disable-strangers", true)) {
            plugin.setPluginEnabled(false);
        }

        // 4. Restore lives and unban eliminated players if configured
        if (plugin.getConfig().getBoolean("game.reset-restore-lives", true) && plugin.getLifeManager() != null) {
            plugin.getLifeManager().resetAllLivesAndPardon();
        }

        // 5. Sound & Broadcast
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p != null) {
                p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.0f);
            }
        }

        String resetSuccess = plugin.getMessage("reset-success",
                "<gradient:#ffaa00:#ffcc00><bold>STRANGERS</bold></gradient> <dark_gray>»</dark_gray> <#55ff99>Event reset completed! <gray>World border restored to default, players returned to spawn, and identities revealed.</gray></#55ff99>");
        sender.sendMessage(plugin.parseComponent(resetSuccess));
    }

    private void scatterPlayersSequentially(
            World world,
            List<Player> players,
            int playerIndex,
            Map<UUID, Location> targetLocations,
            List<Location> assignedLocations,
            double centerX,
            double centerZ,
            double scatterRadius,
            double configuredMinDist,
            double borderSize
    ) {
        if (playerIndex >= players.size()) {
            teleportAllScatteredPlayers(world, players, targetLocations, borderSize);
            return;
        }

        Player player = players.get(playerIndex);
        if (!player.isOnline()) {
            scatterPlayersSequentially(world, players, playerIndex + 1, targetLocations, assignedLocations,
                    centerX, centerZ, scatterRadius, configuredMinDist, borderSize);
            return;
        }

        int totalPlayers = players.size();
        double baseAngle = (2.0 * Math.PI * playerIndex) / totalPlayers;
        double sectorWidth = (2.0 * Math.PI) / totalPlayers;

        // Dynamic minimum distance that gracefully scales with player count
        double dynamicMinDist = Math.min(configuredMinDist, (scatterRadius * 2.0 * Math.PI) / (totalPlayers + 1));
        double effectiveMinDist = Math.max(150.0, dynamicMinDist);

        findSafeSurfaceLocationAsync(world, centerX, centerZ, scatterRadius, baseAngle, sectorWidth, assignedLocations, effectiveMinDist, 0)
                .thenAccept(loc -> {
                    targetLocations.put(player.getUniqueId(), loc);
                    assignedLocations.add(loc);

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        scatterPlayersSequentially(world, players, playerIndex + 1, targetLocations, assignedLocations,
                                centerX, centerZ, scatterRadius, configuredMinDist, borderSize);
                    });
                })
                .exceptionally(ex -> {
                    plugin.getLogger().warning("Error finding scatter location for " + player.getName() + ": " + ex.getMessage());
                    Location fallback = getSafeSpawnFallback(world, assignedLocations);
                    targetLocations.put(player.getUniqueId(), fallback);
                    assignedLocations.add(fallback);

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        scatterPlayersSequentially(world, players, playerIndex + 1, targetLocations, assignedLocations,
                                centerX, centerZ, scatterRadius, configuredMinDist, borderSize);
                    });
                    return null;
                });
    }

    /**
     * Asynchronously samples candidate chunks and guarantees finding a safe, open-air surface position
     * on solid ground, away from oceans, seas, rivers, liquids, and other players.
     */
    private CompletableFuture<Location> findSafeSurfaceLocationAsync(
            World world,
            double centerX,
            double centerZ,
            double scatterRadius,
            double baseAngle,
            double sectorWidth,
            List<Location> assignedLocations,
            double minDistance,
            int attempt
    ) {
        Random random = new Random();
        double angle;
        double radius;

        if (attempt < 8) {
            // Stagger within the player's assigned angular sector for uniform distribution
            angle = baseAngle + (random.nextDouble() - 0.5) * (sectorWidth * 0.85);
            radius = scatterRadius * (0.25 + 0.70 * Math.sqrt(random.nextDouble()));
        } else {
            // If assigned sector is largely ocean/water, explore any angle across the map
            angle = random.nextDouble() * 2.0 * Math.PI;
            radius = scatterRadius * (0.20 + 0.75 * Math.sqrt(random.nextDouble()));
        }

        // Decay minDistance slightly after many attempts to guarantee convergence
        double currentMinDist = (attempt >= 20) ? Math.max(100.0, minDistance * 0.75) : minDistance;

        double targetX = centerX + radius * Math.cos(angle);
        double targetZ = centerZ + radius * Math.sin(angle);

        int chunkX = ((int) Math.floor(targetX)) >> 4;
        int chunkZ = ((int) Math.floor(targetZ)) >> 4;

        CompletableFuture<Location> future = new CompletableFuture<>();

        world.getChunkAtAsync(chunkX, chunkZ).thenAccept(chunk -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                // 1. Chunk Biome check: reject ocean, river, swamp, beach, and shore chunks
                String b1 = chunk.getBlock(8, 64, 8).getBiome().getKey().getKey().toUpperCase(Locale.ROOT);
                String b2 = chunk.getBlock(2, 64, 2).getBiome().getKey().getKey().toUpperCase(Locale.ROOT);
                String b3 = chunk.getBlock(13, 64, 13).getBiome().getKey().getKey().toUpperCase(Locale.ROOT);
                if (isWaterBiome(b1) || isWaterBiome(b2) || isWaterBiome(b3)) {
                    retryOrFallback(world, centerX, centerZ, scatterRadius, baseAngle, sectorWidth, assignedLocations, minDistance, attempt, future);
                    return;
                }

                // 2. Scan chunk for a safe surface spot away from other players
                Location safeLoc = findSafeInChunk(world, chunk, (int) Math.floor(targetX), (int) Math.floor(targetZ), assignedLocations, currentMinDist);
                if (safeLoc != null) {
                    future.complete(safeLoc);
                } else {
                    retryOrFallback(world, centerX, centerZ, scatterRadius, baseAngle, sectorWidth, assignedLocations, minDistance, attempt, future);
                }
            });
        }).exceptionally(ex -> {
            retryOrFallback(world, centerX, centerZ, scatterRadius, baseAngle, sectorWidth, assignedLocations, minDistance, attempt, future);
            return null;
        });

        return future;
    }

    private void retryOrFallback(
            World world,
            double centerX,
            double centerZ,
            double scatterRadius,
            double baseAngle,
            double sectorWidth,
            List<Location> assignedLocations,
            double minDistance,
            int attempt,
            CompletableFuture<Location> future
    ) {
        if (attempt < 80) {
            findSafeSurfaceLocationAsync(world, centerX, centerZ, scatterRadius, baseAngle, sectorWidth, assignedLocations, minDistance, attempt + 1)
                    .thenAccept(future::complete)
                    .exceptionally(ex -> {
                        future.complete(getSafeSpawnFallback(world, assignedLocations));
                        return null;
                    });
        } else {
            future.complete(getSafeSpawnFallback(world, assignedLocations));
        }
    }

    private Location findSafeInChunk(World world, Chunk chunk, int targetX, int targetZ, List<Location> assignedLocations, double minDistance) {
        int minX = chunk.getX() << 4;
        int minZ = chunk.getZ() << 4;
        int clampedX = Math.max(minX + 2, Math.min(minX + 13, targetX));
        int clampedZ = Math.max(minZ + 2, Math.min(minZ + 13, targetZ));

        // 1. Check target point
        Location loc = findSurfaceGround(world, clampedX, clampedZ);
        if (loc != null && isFarEnough(loc, assignedLocations, minDistance)) {
            return loc;
        }

        // 2. Scan a grid across the loaded chunk
        for (int dx = 2; dx <= 14; dx += 3) {
            for (int dz = 2; dz <= 14; dz += 3) {
                loc = findSurfaceGround(world, minX + dx, minZ + dz);
                if (loc != null && isFarEnough(loc, assignedLocations, minDistance)) {
                    return loc;
                }
            }
        }
        return null;
    }

    private boolean isFarEnough(Location candidate, List<Location> assignedLocations, double minDistance) {
        if (assignedLocations == null || assignedLocations.isEmpty()) return true;
        double minDistanceSq = minDistance * minDistance;
        for (Location other : assignedLocations) {
            if (other != null && other.getWorld() != null && other.getWorld().equals(candidate.getWorld())) {
                if (candidate.distanceSquared(other) < minDistanceSq) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isWaterBiome(String biome) {
        if (biome == null) return false;
        String b = biome.toUpperCase();
        return b.contains("OCEAN") || b.contains("RIVER") || b.contains("SWAMP") || b.contains("BEACH") || b.contains("SHORE");
    }

    private Location findSurfaceGround(World world, int x, int z) {
        // 1. Biome check: reject all ocean, river, swamp, beach, and shore biomes
        String biomeName = world.getBiome(x, 64, z).getKey().getKey().toUpperCase(Locale.ROOT);
        if (isWaterBiome(biomeName)) {
            return null;
        }

        int minY = world.getMinHeight() + 5;
        int maxY = world.getMaxHeight() - 5;

        // Start from highest motion-blocking block
        int surfaceY = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING);
        if (surfaceY < minY || surfaceY > maxY) {
            return null;
        }

        // Check downwards from surfaceY by at most 16 blocks
        for (int y = surfaceY; y >= Math.max(minY, surfaceY - 16); y--) {
            Block ground = world.getBlockAt(x, y, z);
            Material type = ground.getType();

            if (!type.isSolid()) continue;

            // Reject tree leaves, logs, wood, bamboo, mushrooms, stems
            if (type.name().endsWith("_LEAVES") || type.name().endsWith("_LOG") || type.name().endsWith("_WOOD")
                    || type == Material.BAMBOO || type == Material.MUSHROOM_STEM
                    || type.name().endsWith("_MUSHROOM_BLOCK")) {
                continue;
            }

            // Reject liquid and ice
            if (isWaterOrLiquid(ground)) {
                return null;
            }

            // Reject hazards
            if (isHazard(type)) {
                return null;
            }

            // Check feet, head, and overhead spaces
            Block feet = world.getBlockAt(x, y + 1, z);
            Block head = world.getBlockAt(x, y + 2, z);
            Block aboveHead = world.getBlockAt(x, y + 3, z);

            if (feet.getType().isSolid() || head.getType().isSolid()) continue;
            if (isWaterOrLiquid(feet) || isWaterOrLiquid(head) || isWaterOrLiquid(aboveHead)) continue;
            if (isHazard(feet.getType()) || isHazard(head.getType())) continue;

            // Strict 7x7 surrounding water perimeter check across ground, feet, and head levels
            boolean waterNearby = false;
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    for (int dy = -1; dy <= 2; dy++) {
                        Block nb = world.getBlockAt(x + dx, y + dy, z + dz);
                        if (isWaterOrLiquid(nb)) {
                            waterNearby = true;
                            break;
                        }
                    }
                    if (waterNearby) break;
                }
                if (waterNearby) break;
            }
            if (waterNearby) return null;

            // Ensure open sky above head (no caves or ceilings)
            boolean clearSky = true;
            for (int up = 3; up <= 10; up++) {
                Block above = world.getBlockAt(x, y + up, z);
                if (above.getType().isSolid() || isWaterOrLiquid(above)) {
                    clearSky = false;
                    break;
                }
            }
            if (!clearSky) continue;

            // Completely solid, dry, open-air surface ground
            return new Location(world, x + 0.5, y + 1.0, z + 0.5);
        }

        return null;
    }

    private boolean isWaterOrLiquid(Block block) {
        if (block == null) return false;
        if (block.isLiquid()) return true;
        Material mat = block.getType();
        if (mat == Material.WATER || mat == Material.LAVA || mat == Material.BUBBLE_COLUMN) return true;
        if (mat == Material.ICE || mat == Material.PACKED_ICE || mat == Material.BLUE_ICE || mat == Material.FROSTED_ICE) return true;
        if (mat == Material.SEAGRASS || mat == Material.TALL_SEAGRASS || mat == Material.KELP || mat == Material.KELP_PLANT || mat == Material.LILY_PAD) return true;
        if (block.getBlockData() instanceof org.bukkit.block.data.Waterlogged wl && wl.isWaterlogged()) return true;
        return false;
    }

    private boolean isHazard(Material material) {
        return material == Material.FIRE || material == Material.SOUL_FIRE ||
               material == Material.LAVA || material == Material.SWEET_BERRY_BUSH ||
               material == Material.POWDER_SNOW || material == Material.WITHER_ROSE ||
               material == Material.CACTUS || material == Material.MAGMA_BLOCK;
    }

    private Location findEmergencyDryLand(World world, int startX, int startZ, List<Location> assignedLocations) {
        for (int r = 5; r <= 300; r += 10) {
            for (int i = 0; i < 8; i++) {
                double angle = (2.0 * Math.PI * i) / 8.0;
                int x = startX + (int) (r * Math.cos(angle));
                int z = startZ + (int) (r * Math.sin(angle));
                Location candidate = findSurfaceGround(world, x, z);
                if (candidate != null && isFarEnough(candidate, assignedLocations, 50.0)) {
                    return candidate;
                }
            }
        }
        return getSafeSpawnFallback(world, assignedLocations);
    }

    private Location getSafeSpawnFallback(World world, List<Location> assignedLocations) {
        Location spawn = world.getSpawnLocation();
        Random random = new Random();
        for (int radius = 10; radius <= 500; radius += 15) {
            for (int i = 0; i < 8; i++) {
                double angle = (2.0 * Math.PI * i) / 8.0 + (random.nextDouble() - 0.5) * 0.4;
                int ox = spawn.getBlockX() + (int) (radius * Math.cos(angle));
                int oz = spawn.getBlockZ() + (int) (radius * Math.sin(angle));
                Location candidate = findSurfaceGround(world, ox, oz);
                if (candidate != null && isFarEnough(candidate, assignedLocations, 40.0)) {
                    return candidate;
                }
            }
        }
        for (int r = 0; r < 200; r += 5) {
            Location candidate = findSurfaceGround(world, spawn.getBlockX() + r, spawn.getBlockZ() + r);
            if (candidate != null) return candidate;
        }
        return spawn.clone().add(0.5, 1.0, 0.5);
    }

    private World getTargetWorld(CommandSender sender) {
        if (sender instanceof Player p) {
            return p.getWorld();
        }
        String worldName = plugin.getConfig().getString("game.world", "");
        if (!worldName.isEmpty()) {
            World w = Bukkit.getWorld(worldName);
            if (w != null) return w;
        }
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
    }
}

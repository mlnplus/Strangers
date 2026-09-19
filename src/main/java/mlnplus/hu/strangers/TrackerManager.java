package mlnplus.hu.strangers;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerInitializeWorldBorder;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TrackerManager implements Listener {

    private final Strangers plugin;
    private final NamespacedKey trackerKey;
    private final NamespacedKey targetUuidKey;
    private final NamespacedKey targetNameKey;
    private final NamespacedKey recipeKey;

    private final Map<UUID, ActiveTracker> activeTrackers = new ConcurrentHashMap<>();
    private BukkitTask trackingTask;

    public static class TrackerSelectionHolder implements InventoryHolder {
        private Inventory inventory;

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public static class ActiveTracker {
        public final UUID trackerUuid;
        public final UUID targetUuid;
        public final String targetName;
        public final long totalDurationMillis;
        public long remainingMillis;
        public long lastActiveTimestamp;
        public boolean isPaused;
        public BossBar hunterBossBar;
        public BossBar huntedBossBar;

        public ActiveTracker(UUID trackerUuid, UUID targetUuid, String targetName, long durationMillis) {
            this.trackerUuid = trackerUuid;
            this.targetUuid = targetUuid;
            this.targetName = targetName;
            this.totalDurationMillis = durationMillis;
            this.remainingMillis = durationMillis;
            this.lastActiveTimestamp = System.currentTimeMillis();
            this.isPaused = false;
        }

        public boolean isExpired() {
            return remainingMillis <= 0;
        }

        public float getProgress() {
            if (totalDurationMillis <= 0) return 0.0f;
            return Math.max(0.0f, Math.min(1.0f, (float) remainingMillis / (float) totalDurationMillis));
        }
    }

    public TrackerManager(Strangers plugin) {
        this.plugin = plugin;
        this.trackerKey = new NamespacedKey(plugin, "identity_tracker");
        this.targetUuidKey = new NamespacedKey(plugin, "tracker_target_uuid");
        this.targetNameKey = new NamespacedKey(plugin, "tracker_target_name");
        this.recipeKey = new NamespacedKey(plugin, "tracker_recipe");

        startTrackingTask();
    }

    public void registerRecipe() {
        try {
            Bukkit.removeRecipe(recipeKey);
        } catch (Exception ignored) {
        }

        if (!plugin.getConfig().getBoolean("nametag-tracker.recipe.enabled", true)) {
            return;
        }

        ItemStack tracker = getTrackerItem(1);
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, tracker);

        List<String> shapeList = plugin.getConfig().getStringList("nametag-tracker.recipe.shape");
        if (shapeList != null && shapeList.size() == 3 && shapeList.get(0).length() == 3 && shapeList.get(1).length() == 3 && shapeList.get(2).length() == 3) {
            recipe.shape(shapeList.get(0), shapeList.get(1), shapeList.get(2));
        } else {
            recipe.shape("ECE", "CNC", "ECE");
        }

        Map<Character, Material> ingMap = new HashMap<>();
        ingMap.put('N', Material.NAME_TAG);
        ingMap.put('E', Material.ENDER_EYE);
        ingMap.put('C', Material.COMPASS);

        org.bukkit.configuration.ConfigurationSection ingredientsSec = plugin.getConfig().getConfigurationSection("nametag-tracker.recipe.ingredients");
        if (ingredientsSec != null) {
            for (String keyStr : ingredientsSec.getKeys(false)) {
                if (keyStr.isEmpty()) continue;
                char keyChar = keyStr.charAt(0);
                String matName = ingredientsSec.getString(keyStr, "");
                Material mat = Material.matchMaterial(matName);
                if (mat != null) {
                    ingMap.put(keyChar, mat);
                } else {
                    plugin.getLogger().warning("Invalid material '" + matName + "' for key '" + keyChar + "' in tracker recipe ingredients!");
                }
            }
        }

        for (Map.Entry<Character, Material> entry : ingMap.entrySet()) {
            try {
                recipe.setIngredient(entry.getKey(), entry.getValue());
            } catch (Exception ignored) {
            }
        }

        try {
            Bukkit.addRecipe(recipe);
            plugin.getLogger().info("Registered Identity Tracker NameTag crafting recipe.");
            for (Player player : Bukkit.getOnlinePlayers()) {
                try {
                    player.discoverRecipe(recipeKey);
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not register Identity Tracker recipe: " + e.getMessage());
        }
    }

    public boolean matchesTrackerRecipe(ItemStack[] matrix) {
        if (!plugin.getConfig().getBoolean("nametag-tracker.recipe.enabled", true)) {
            return false;
        }
        if (matrix == null || matrix.length < 9) return false;

        List<String> shapeList = plugin.getConfig().getStringList("nametag-tracker.recipe.shape");
        if (shapeList == null || shapeList.size() != 3) {
            shapeList = List.of("ECE", "CNC", "ECE");
        }

        Map<Character, Material> ingMap = new HashMap<>();
        ingMap.put('N', Material.NAME_TAG);
        ingMap.put('E', Material.ENDER_EYE);
        ingMap.put('C', Material.COMPASS);

        org.bukkit.configuration.ConfigurationSection ingredientsSec = plugin.getConfig().getConfigurationSection("nametag-tracker.recipe.ingredients");
        if (ingredientsSec != null) {
            for (String key : ingredientsSec.getKeys(false)) {
                if (key.isEmpty()) continue;
                Material m = Material.matchMaterial(ingredientsSec.getString(key, ""));
                if (m != null) ingMap.put(key.charAt(0), m);
            }
        }

        for (int row = 0; row < 3; row++) {
            String shapeRow = shapeList.get(row);
            for (int col = 0; col < 3; col++) {
                int index = row * 3 + col;
                char symbol = col < shapeRow.length() ? shapeRow.charAt(col) : ' ';
                ItemStack item = matrix[index];

                if (symbol == ' ') {
                    if (item != null && item.getType() != Material.AIR) return false;
                } else {
                    Material expected = ingMap.get(symbol);
                    if (expected == null) return false;
                    if (item == null || item.getType() != expected) return false;
                }
            }
        }
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(org.bukkit.event.inventory.PrepareItemCraftEvent event) {
        if (event.getInventory() == null) return;
        ItemStack[] matrix = event.getInventory().getMatrix();
        if (matchesTrackerRecipe(matrix)) {
            event.getInventory().setResult(getTrackerItem(1));
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        try {
            player.discoverRecipe(recipeKey);
        } catch (Exception ignored) {}

        // Check if player is a target of an active tracker
        for (ActiveTracker active : activeTrackers.values()) {
            if (active.targetUuid.equals(player.getUniqueId()) && !active.isExpired()) {
                active.isPaused = false;
                active.lastActiveTimestamp = System.currentTimeMillis();
                if (active.huntedBossBar != null) {
                    player.showBossBar(active.huntedBossBar);
                }
                if (plugin.getConfig().getBoolean("nametag-tracker.target-vignette", true)) {
                    sendRedBorderVignette(player);
                }
                if (plugin.getConfig().getBoolean("nametag-tracker.target-title", true)) {
                    String targetTitle = plugin.getMessage("tracker-target-hunted-title",
                            plugin.getMessage("tracker-target-notified-title",
                                    "<gradient:#ff0033:#880015><bold>⚠ YOU ARE BEING HUNTED ⚠</bold></gradient>"));
                    String targetSubtitle = plugin.getMessage("tracker-target-hunted-subtitle",
                            plugin.getMessage("tracker-target-notified-subtitle",
                                    "<gray>An unknown hunter is tracking your movements!</gray>"));
                    plugin.sendTitle(player, targetTitle, targetSubtitle, 10, 60, 20);
                }
                Player hunter = Bukkit.getPlayer(active.trackerUuid);
                if (hunter != null && hunter.isOnline()) {
                    String resumeMsg = plugin.getMessage("tracker-target-reconnected",
                            "<gradient:#55ff99:#00d2ff><b>TRACKER</b></gradient> <dark_gray>»</dark_gray> <color:#FFA737><b>{TARGET}</b></color> <#55ff99>has reconnected! Tracking has resumed.</#55ff99>")
                            .replace("{TARGET}", getTargetDisplayName(active));
                    hunter.sendMessage(plugin.parseComponent(resumeMsg));
                    hunter.playSound(hunter.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.2f);
                }
            }
            if (active.trackerUuid.equals(player.getUniqueId()) && !active.isExpired()) {
                if (active.hunterBossBar != null) {
                    player.showBossBar(active.hunterBossBar);
                }
            }
        }
    }

    public ItemStack getTrackerItem(int amount) {
        return getTrackerItem(amount, null, null);
    }

    public ItemStack getTrackerItem(int amount, UUID targetUuid, String targetName) {
        ItemStack item = new ItemStack(Material.NAME_TAG, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(trackerKey, PersistentDataType.BYTE, (byte) 1);

            if (targetUuid != null && targetName != null) {
                pdc.set(targetUuidKey, PersistentDataType.STRING, targetUuid.toString());
                pdc.set(targetNameKey, PersistentDataType.STRING, targetName);

                String boundTitle = plugin.getMessage("tracker-bound-name", "<gradient:#00d2ff:#3a7bd5><bold>Identity Tracker</bold></gradient> <dark_gray>•</dark_gray> <color:#FFA737><b>{TARGET}</b></color>")
                        .replace("{TARGET}", targetName);
                meta.displayName(plugin.parseComponent(boundTitle));

                List<Component> lore = new ArrayList<>();
                lore.add(plugin.parseComponent("<dark_gray>▪</dark_gray> <gray>Target Soul:</gray> <color:#FFA737><b>" + targetName + "</b></color>"));
                boolean isOnline = Bukkit.getPlayer(targetUuid) != null && Bukkit.getPlayer(targetUuid).isOnline();
                String statusStr = isOnline ? "<#55ff99>● Online</#55ff99>" : "<#888888>○ Offline</#888888>";
                lore.add(plugin.parseComponent("<dark_gray>▪</dark_gray> <gray>Target Status:</gray> " + statusStr));
                lore.add(Component.empty());
                lore.add(plugin.parseComponent("<yellow>Right-Click:</yellow> <gray>Check target status</gray>"));
                lore.add(plugin.parseComponent("<#ff5577><bold>Shift + Right-Click:</bold></#ff5577> <#55ff99>Activate Tracking & Reveal</#55ff99>"));
                meta.lore(lore);
            } else {
                String unboundTitle = plugin.getMessage("tracker-unbound-name", "<gradient:#00d2ff:#3a7bd5><bold>Identity Tracker</bold></gradient>");
                meta.displayName(plugin.parseComponent(unboundTitle));

                List<Component> lore = new ArrayList<>();
                lore.add(plugin.parseComponent("<dark_gray>▪</dark_gray> <gray>An ancient name tag infused with locator magic.</gray>"));
                lore.add(plugin.parseComponent("<dark_gray>▪</dark_gray> <#ffaa00>Target:</#ffaa00> <white>None (Unbound)</white>"));
                lore.add(Component.empty());
                lore.add(plugin.parseComponent("<yellow>Right-Click to choose a target!</yellow>"));
                meta.lore(lore);
            }

            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isTrackerItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        Byte val = item.getItemMeta().getPersistentDataContainer().get(trackerKey, PersistentDataType.BYTE);
        return val != null && val == (byte) 1;
    }

    public boolean isBound(ItemStack item) {
        if (!isTrackerItem(item)) return false;
        return item.getItemMeta().getPersistentDataContainer().has(targetUuidKey, PersistentDataType.STRING);
    }

    public UUID getBoundTargetUuid(ItemStack item) {
        if (!isBound(item)) return null;
        String str = item.getItemMeta().getPersistentDataContainer().get(targetUuidKey, PersistentDataType.STRING);
        if (str == null) return null;
        try {
            return UUID.fromString(str);
        } catch (Exception e) {
            return null;
        }
    }

    public String getBoundTargetName(ItemStack item) {
        if (!isBound(item)) return null;
        return item.getItemMeta().getPersistentDataContainer().get(targetNameKey, PersistentDataType.STRING);
    }

    public void openSelectionGUI(Player player) {
        Map<UUID, StrangersDatabase.LivesData> allLives = plugin.getDatabase().getAllLivesData();

        // Collect all non-eliminated players (both online and offline)
        List<StrangersDatabase.LivesData> nonEliminated = new ArrayList<>();
        Set<UUID> seenUuids = new HashSet<>();

        for (StrangersDatabase.LivesData data : allLives.values()) {
            if (data.lives > 0 && !data.uuid.equals(player.getUniqueId())) {
                nonEliminated.add(data);
                seenUuids.add(data.uuid);
            }
        }

        // Also ensure any online players not yet in SQLite are included
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!seenUuids.contains(online.getUniqueId()) && !online.getUniqueId().equals(player.getUniqueId())) {
                int lives = plugin.getLifeManager().getLives(online);
                if (lives > 0) {
                    nonEliminated.add(new StrangersDatabase.LivesData(
                            online.getUniqueId(),
                            plugin.getRealName(online.getUniqueId()),
                            lives,
                            false
                    ));
                    seenUuids.add(online.getUniqueId());
                }
            }
        }

        // Sort alphabetically by player name
        nonEliminated.sort(Comparator.comparing(a -> a.playerName.toLowerCase()));

        String guiTitle = plugin.getMessage("tracker-gui-title", "<gradient:#00d2ff:#3a7bd5><bold>Select Target to Track</bold></gradient>");
        int size = Math.min(54, Math.max(9, ((nonEliminated.size() / 9) + 1) * 9));

        TrackerSelectionHolder holder = new TrackerSelectionHolder();
        Inventory inv = Bukkit.createInventory(holder, size, plugin.parseComponent(guiTitle));
        holder.setInventory(inv);

        // Fill background with dark gray stained glass panes
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.displayName(Component.empty());
            filler.setItemMeta(fillerMeta);
        }
        for (int i = 0; i < size; i++) {
            inv.setItem(i, filler.clone());
        }

        for (int i = 0; i < nonEliminated.size() && i < size; i++) {
            StrangersDatabase.LivesData data = nonEliminated.get(i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(data.uuid));
                meta.displayName(plugin.parseComponent("<color:#FFA737><b>" + data.playerName + "</b></color>"));

                boolean isOnline = Bukkit.getPlayer(data.uuid) != null && Bukkit.getPlayer(data.uuid).isOnline();
                String statusStr = isOnline ? "<#55ff99>● Online</#55ff99>" : "<#888888>○ Offline</#888888>";

                List<Component> lore = new ArrayList<>();
                lore.add(plugin.parseComponent("<gray>Status:</gray> " + statusStr));
                lore.add(plugin.parseComponent("<gray>Lives:</gray> " + plugin.getLifeManager().getHeartsDisplay(data.lives)));
                lore.add(Component.empty());
                lore.add(plugin.parseComponent("<yellow>Click to bind Name Tag to this player!</yellow>"));
                meta.lore(lore);

                meta.getPersistentDataContainer().set(targetUuidKey, PersistentDataType.STRING, data.uuid.toString());
                meta.getPersistentDataContainer().set(targetNameKey, PersistentDataType.STRING, data.playerName);
                head.setItemMeta(meta);
            }
            inv.setItem(i, head);
        }

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (event.getInventory().getHolder() instanceof TrackerSelectionHolder) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;

            PersistentDataContainer pdc = clicked.getItemMeta().getPersistentDataContainer();
            String uuidStr = pdc.get(targetUuidKey, PersistentDataType.STRING);
            String targetName = pdc.get(targetNameKey, PersistentDataType.STRING);

            if (uuidStr == null || targetName == null) return;

            UUID targetUuid;
            try {
                targetUuid = UUID.fromString(uuidStr);
            } catch (Exception e) {
                return;
            }

            // Find tracker item in player's main or off hand
            ItemStack inMain = player.getInventory().getItemInMainHand();
            ItemStack inOff = player.getInventory().getItemInOffHand();

            if (isTrackerItem(inMain)) {
                bindItemToPlayer(player, inMain, targetUuid, targetName, EquipmentSlot.HAND);
            } else if (isTrackerItem(inOff)) {
                bindItemToPlayer(player, inOff, targetUuid, targetName, EquipmentSlot.OFF_HAND);
            } else {
                player.sendMessage(plugin.parseComponent(plugin.getMessage("tracker-not-in-hand",
                        "<gradient:#ff3355:#ff6688><bold>STRANGERS</bold></gradient> <dark_gray>»</dark_gray> <#ff4444>You must be holding the Name Tag to bind it!")));
            }

            player.closeInventory();
        }
    }

    private void bindItemToPlayer(Player player, ItemStack item, UUID targetUuid, String targetName, EquipmentSlot slot) {
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
            ItemStack bound = getTrackerItem(1, targetUuid, targetName);
            HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(bound);
            if (!overflow.isEmpty()) {
                for (ItemStack drop : overflow.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
            }
        } else {
            ItemStack bound = getTrackerItem(1, targetUuid, targetName);
            if (slot == EquipmentSlot.HAND) {
                player.getInventory().setItemInMainHand(bound);
            } else {
                player.getInventory().setItemInOffHand(bound);
            }
        }

        player.playSound(player.getLocation(), Sound.ITEM_LODESTONE_COMPASS_LOCK, 1.0f, 1.2f);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.4f);

        String msg = plugin.getMessage("tracker-bound-success",
                "<gradient:#ff3355:#ff6688><bold>STRANGERS</bold></gradient> <dark_gray>»</dark_gray> <#55ff99>Identity Tracker successfully bound to:</#55ff99> <color:#FFA737><b>{TARGET}</b></color> <gray>(Shift + Right-Click to activate)</gray>")
                .replace("{TARGET}", targetName);
        player.sendMessage(plugin.parseComponent(msg));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || !isTrackerItem(item)) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();

        if (!isBound(item)) {
            // Unbound: Open selection GUI
            openSelectionGUI(player);
            return;
        }

        UUID targetUuid = getBoundTargetUuid(item);
        String targetName = getBoundTargetName(item);
        if (targetUuid == null || targetName == null) {
            openSelectionGUI(player);
            return;
        }

        // Bound item interaction
        if (!player.isSneaking()) {
            // Normal Right-Click: Prompt confirmation!
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 0.8f);
            String confirmMsg = plugin.getMessage("tracker-confirm-prompt",
                    "<gradient:#ffaa00:#ffd200><b>⚠ Shift + Right-Click to activate tracking for {TARGET}!</b></gradient>")
                    .replace("{TARGET}", targetName);
            player.sendActionBar(plugin.parseComponent(confirmMsg));
            return;
        }

        // Shift + Right-Click: Attempt activation
        Player targetPlayer = Bukkit.getPlayer(targetUuid);
        if (targetPlayer == null || !targetPlayer.isOnline()) {
            // Offline prevention: Do NOT consume item!
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.9f, 1.0f);
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_LOCKED, 0.8f, 0.9f);

            String offlineMsg = plugin.getMessage("tracker-target-offline",
                    "<gradient:#ff3355:#ff6688><bold>STRANGERS</bold></gradient> <dark_gray>»</dark_gray> <#ff4444>Cannot activate tracking: <color:#FFA737><b>{TARGET}</b></color> <#ff4444>is currently offline!</#ff4444>")
                    .replace("{TARGET}", targetName);
            player.sendMessage(plugin.parseComponent(offlineMsg));
            player.sendActionBar(plugin.parseComponent("<#ff4444>Target is offline!</#ff4444>"));
            return;
        }

        // Target is online: Consume 1 item and activate safely!
        if (item.getAmount() <= 1) {
            if (event.getHand() == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(null);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
        } else {
            item.setAmount(item.getAmount() - 1);
        }

        int durationSeconds = plugin.getConfig().getInt("nametag-tracker.duration-seconds", 300);
        long durationMillis = durationSeconds * 1000L;

        // If hunter already had an active tracker, clean it up first
        ActiveTracker existing = activeTrackers.remove(player.getUniqueId());
        if (existing != null) {
            cleanupTracker(existing);
        }

        ActiveTracker newTracker = new ActiveTracker(player.getUniqueId(), targetUuid, targetName, durationMillis);
        String targetDisplayName = getTargetDisplayName(newTracker);

        // Initialize Hunter BossBar
        String arrow = calculateDirectionArrow(player, targetPlayer);
        double distance = player.getLocation().distance(targetPlayer.getLocation());
        String timeFormatted = String.format("%02d:%02d", durationSeconds / 60, durationSeconds % 60);

        String hunterBarTitleStr = plugin.getMessage("tracker-bossbar-title",
                "<gradient:#00d2ff:#3a7bd5><b>TRACKER</b></gradient> <dark_gray>»</dark_gray> <white><b>{TARGET}</b></white> <dark_gray>•</dark_gray> <#55ff99><bold>{ARROW}</bold> {DIST}m</#55ff99>")
                .replace("{TARGET}", targetDisplayName)
                .replace("{ARROW}", arrow)
                .replace("{DIST}", String.valueOf((int) distance))
                .replace("{TIME}", timeFormatted);

        newTracker.hunterBossBar = BossBar.bossBar(
                plugin.parseComponent(hunterBarTitleStr),
                1.0f,
                BossBar.Color.BLUE,
                BossBar.Overlay.PROGRESS
        );
        player.showBossBar(newTracker.hunterBossBar);

        // Initialize Hunted BossBar if enabled
        if (plugin.getConfig().getBoolean("nametag-tracker.target-bossbar", true)) {
            String huntedBarTitleStr = plugin.getMessage("tracker-hunted-bossbar-title",
                    "<gradient:#ff2244:#ff6688><bold>⚠ YOU ARE BEING HUNTED ⚠</bold></gradient>")
                    .replace("{TIME}", timeFormatted);

            newTracker.huntedBossBar = BossBar.bossBar(
                    plugin.parseComponent(huntedBarTitleStr),
                    1.0f,
                    BossBar.Color.RED,
                    BossBar.Overlay.PROGRESS
            );
            targetPlayer.showBossBar(newTracker.huntedBossBar);
        }

        activeTrackers.put(player.getUniqueId(), newTracker);

        // Audio-visual feedback for hunter
        player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 0.8f, 1.2f);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);
        player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.4f, 1.3f);

        int minutes = durationSeconds / 60;
        String title = plugin.getMessage("tracker-active-title", "<gradient:#00d2ff:#3a7bd5><bold>TRACKING ACTIVATED</bold></gradient>");
        String subtitle = plugin.getMessage("tracker-active-subtitle", "<gray>Target: <color:#FFA737><b>{TARGET}</b></color> <dark_gray>•</dark_gray> <#55ff99>{MINUTES}m remaining</#55ff99>")
                .replace("{TARGET}", targetDisplayName)
                .replace("{MINUTES}", String.valueOf(minutes));
        plugin.sendTitle(player, title, subtitle, 10, 60, 20);

        String startMsg = plugin.getMessage("tracker-activated-chat",
                "<gradient:#ff3355:#ff6688><bold>STRANGERS</bold></gradient> <dark_gray>»</dark_gray> <#55ff99>Tracking activated for <color:#FFA737><b>{TARGET}</b></color>! <gray>Follow particle indicators and compass HUD for <yellow>{MINUTES} minutes</yellow>.</gray>")
                .replace("{TARGET}", targetDisplayName)
                .replace("{MINUTES}", String.valueOf(minutes));
        player.sendMessage(plugin.parseComponent(startMsg));

        // Target notification & Red Border Vignette
        if (plugin.getConfig().getBoolean("nametag-tracker.target-vignette", true)) {
            sendRedBorderVignette(targetPlayer);
        }
        targetPlayer.playSound(targetPlayer.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.8f, 1.0f);
        targetPlayer.playSound(targetPlayer.getLocation(), Sound.BLOCK_BELL_RESONATE, 1.0f, 0.8f);

        if (plugin.getConfig().getBoolean("nametag-tracker.target-title", true)) {
            String targetTitle = plugin.getMessage("tracker-target-hunted-title",
                    plugin.getMessage("tracker-target-notified-title",
                            "<gradient:#ff0033:#880015><bold>⚠ YOU ARE BEING HUNTED ⚠</bold></gradient>"));
            String targetSubtitle = plugin.getMessage("tracker-target-hunted-subtitle",
                    plugin.getMessage("tracker-target-notified-subtitle",
                            "<gray>An unknown hunter is tracking your movements!</gray>"));
            plugin.sendTitle(targetPlayer, targetTitle, targetSubtitle, 10, 70, 20);
        }

        String targetChat = plugin.getMessage("tracker-target-notified-chat",
                "<gradient:#ff3355:#ff6688><bold>STRANGERS</bold></gradient> <dark_gray>»</dark_gray> <#ff4444>You feel an ominous presence... <b>Someone is tracking your identity and movements!</b></#ff4444>");
        targetPlayer.sendMessage(plugin.parseComponent(targetChat));

        // Reveal target to hunter immediately (refresh skin and display name)
        plugin.refreshPlayerForViewer(player, targetPlayer);
    }

    public boolean isTrackerRevealed(Player viewer, UUID targetUuid) {
        if (viewer == null || targetUuid == null) return false;
        ActiveTracker tracker = activeTrackers.get(viewer.getUniqueId());
        if (tracker == null) return false;
        return tracker.targetUuid.equals(targetUuid) && !tracker.isExpired();
    }

    public boolean isTrackerRevealed(Player viewer, Player target) {
        if (viewer == null || target == null) return false;
        return isTrackerRevealed(viewer, target.getUniqueId());
    }

    public boolean hasActiveTracker(UUID trackerUuid) {
        if (trackerUuid == null) return false;
        ActiveTracker active = activeTrackers.get(trackerUuid);
        return active != null && !active.isExpired();
    }

    public boolean isTargetBeingTracked(UUID targetUuid) {
        return isTargetBeingTracked(targetUuid, null);
    }

    public boolean isTargetBeingTracked(UUID targetUuid, UUID excludeTrackerUuid) {
        if (targetUuid == null) return false;
        for (ActiveTracker other : activeTrackers.values()) {
            if (excludeTrackerUuid != null && other.trackerUuid.equals(excludeTrackerUuid)) {
                continue;
            }
            if (other.targetUuid.equals(targetUuid) && !other.isExpired()) {
                return true;
            }
        }
        return false;
    }

    public String getTargetDisplayName(ActiveTracker active) {
        if (active == null) return "Unknown";
        String real = plugin.getRealName(active.targetUuid);
        if (real != null && !real.equals("Unknown") && !real.equalsIgnoreCase("Stranger") && !real.equalsIgnoreCase(plugin.getAnonymousName())) {
            return real;
        }
        if (active.targetName != null && !active.targetName.equalsIgnoreCase("Stranger") && !active.targetName.equalsIgnoreCase(plugin.getAnonymousName())) {
            return active.targetName;
        }
        Player p = Bukkit.getPlayer(active.targetUuid);
        if (p != null && !p.getName().equalsIgnoreCase("Stranger") && !p.getName().equalsIgnoreCase(plugin.getAnonymousName())) {
            return p.getName();
        }
        return active.targetName != null ? active.targetName : "Unknown";
    }

    public void sendRedBorderVignette(Player player) {
        if (player == null || !player.isOnline()) return;
        if (!PacketEventsHookLoader.isAvailable()) return;
        try {
            Location loc = player.getLocation();
            WrapperPlayServerInitializeWorldBorder packet = new WrapperPlayServerInitializeWorldBorder(
                    loc.getX(),
                    loc.getZ(),
                    10000.0,
                    10000.0,
                    0L,
                    29999984,
                    15000,
                    15
            );
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not send red border vignette packet: " + t.getMessage());
        }
    }

    public void restoreWorldBorder(Player player) {
        if (player == null || !player.isOnline()) return;
        if (!PacketEventsHookLoader.isAvailable()) return;
        try {
            org.bukkit.WorldBorder wb = player.getWorld().getWorldBorder();
            WrapperPlayServerInitializeWorldBorder packet = new WrapperPlayServerInitializeWorldBorder(
                    wb.getCenter().getX(),
                    wb.getCenter().getZ(),
                    wb.getSize(),
                    wb.getSize(),
                    0L,
                    29999984,
                    wb.getWarningDistance(),
                    wb.getWarningTime()
            );
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not restore world border: " + t.getMessage());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        for (ActiveTracker active : activeTrackers.values()) {
            if (active.targetUuid.equals(player.getUniqueId()) && !active.isExpired()) {
                if (!active.isPaused) {
                    active.isPaused = true;
                    Player hunter = Bukkit.getPlayer(active.trackerUuid);
                    if (hunter != null && hunter.isOnline()) {
                        String logoffMsg = plugin.getMessage("tracker-target-logged-off",
                                "<gradient:#ffaa00:#ffd200><b>TRACKER</b></gradient> <dark_gray>»</dark_gray> <color:#FFA737><b>{TARGET}</b></color> <#ffaa00>has logged off! Tracking is paused until they reconnect.</#ffaa00>")
                                .replace("{TARGET}", getTargetDisplayName(active));
                        hunter.sendMessage(plugin.parseComponent(logoffMsg));
                        hunter.playSound(hunter.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.2f);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        UUID deadUuid = player.getUniqueId();

        for (Iterator<Map.Entry<UUID, ActiveTracker>> it = activeTrackers.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, ActiveTracker> entry = it.next();
            ActiveTracker active = entry.getValue();

            // 1. If the dead player was the target being tracked -> cancel & stop tracking immediately
            if (active.targetUuid.equals(deadUuid)) {
                it.remove();

                Player hunter = Bukkit.getPlayer(active.trackerUuid);
                String targetDisplayName = getTargetDisplayName(active);

                if (hunter != null && hunter.isOnline()) {
                    if (active.hunterBossBar != null) {
                        hunter.hideBossBar(active.hunterBossBar);
                    }
                    hunter.playSound(hunter.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.0f);
                    String diedMsg = plugin.getMessage("tracker-target-died",
                            "<gradient:#ff3355:#ff6688><bold>STRANGERS</bold></gradient> <dark_gray>»</dark_gray> <color:#FFA737><b>{TARGET}</b></color> <#ff5555>has died! Tracking has ended.</#ff5555>")
                            .replace("{TARGET}", targetDisplayName);
                    hunter.sendMessage(plugin.parseComponent(diedMsg));

                    if (!plugin.isBypassed(hunter)) {
                        plugin.refreshPlayerForViewer(hunter, player);
                    }
                }

                if (active.huntedBossBar != null) {
                    player.hideBossBar(active.huntedBossBar);
                }
                restoreWorldBorder(player);
            } else if (active.trackerUuid.equals(deadUuid)) {
                // 2. If the dead player was the hunter -> cancel & stop tracking immediately
                it.remove();

                if (active.hunterBossBar != null) {
                    player.hideBossBar(active.hunterBossBar);
                }

                Player target = Bukkit.getPlayer(active.targetUuid);
                if (target != null && target.isOnline()) {
                    if (active.huntedBossBar != null) {
                        target.hideBossBar(active.huntedBossBar);
                    }
                    if (!isTargetBeingTracked(active.targetUuid, active.trackerUuid)) {
                        restoreWorldBorder(target);
                        String untrackedTitle = plugin.getMessage("tracker-target-untracked-title",
                                "<gradient:#55ff99:#00d2ff><bold>TRACKING ENDED</bold></gradient>");
                        String untrackedSubtitle = plugin.getMessage("tracker-target-untracked-subtitle",
                                "<gray>The hunter has died and lost your trail.</gray>");
                        plugin.sendTitle(target, untrackedTitle, untrackedSubtitle, 10, 50, 20);
                        target.playSound(target.getLocation(), Sound.ITEM_TOTEM_USE, 0.4f, 1.8f);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (isTargetBeingTracked(player.getUniqueId(), null)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && isTargetBeingTracked(player.getUniqueId(), null)) {
                    if (plugin.getConfig().getBoolean("nametag-tracker.target-vignette", true)) {
                        sendRedBorderVignette(player);
                    }
                }
            }, 5L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (isTargetBeingTracked(player.getUniqueId(), null)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && isTargetBeingTracked(player.getUniqueId(), null)) {
                    if (plugin.getConfig().getBoolean("nametag-tracker.target-vignette", true)) {
                        sendRedBorderVignette(player);
                    }
                }
            }, 5L);
        }
    }

    private void startTrackingTask() {
        int intervalTicks = Math.max(5, plugin.getConfig().getInt("nametag-tracker.particle-interval-ticks", 10));

        trackingTask = new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                Iterator<Map.Entry<UUID, ActiveTracker>> it = activeTrackers.entrySet().iterator();

                while (it.hasNext()) {
                    Map.Entry<UUID, ActiveTracker> entry = it.next();
                    ActiveTracker active = entry.getValue();

                    Player trackerPlayer = Bukkit.getPlayer(active.trackerUuid);
                    Player targetPlayer = Bukkit.getPlayer(active.targetUuid);
                    String targetDisplayName = getTargetDisplayName(active);

                    // Check pause/resume state based on target presence
                    boolean targetOnline = targetPlayer != null && targetPlayer.isOnline();
                    if (!targetOnline) {
                        if (!active.isPaused) {
                            active.isPaused = true;
                            if (trackerPlayer != null && trackerPlayer.isOnline()) {
                                String pausedChat = plugin.getMessage("tracker-target-logged-off",
                                        "<gradient:#ffaa00:#ffd200><b>TRACKER</b></gradient> <dark_gray>»</dark_gray> <color:#FFA737><b>{TARGET}</b></color> <#ffaa00>has logged off! Tracking is paused until they reconnect.</#ffaa00>")
                                        .replace("{TARGET}", targetDisplayName);
                                trackerPlayer.sendMessage(plugin.parseComponent(pausedChat));
                                trackerPlayer.playSound(trackerPlayer.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.2f);
                            }
                        }
                    } else {
                        if (active.isPaused) {
                            active.isPaused = false;
                            active.lastActiveTimestamp = now;
                            if (trackerPlayer != null && trackerPlayer.isOnline()) {
                                String resumedChat = plugin.getMessage("tracker-target-reconnected",
                                        "<gradient:#55ff99:#00d2ff><b>TRACKER</b></gradient> <dark_gray>»</dark_gray> <color:#FFA737><b>{TARGET}</b></color> <#55ff99>has reconnected! Tracking has resumed.</#55ff99>")
                                        .replace("{TARGET}", targetDisplayName);
                                trackerPlayer.sendMessage(plugin.parseComponent(resumedChat));
                                trackerPlayer.playSound(trackerPlayer.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.2f);
                            }
                            if (active.huntedBossBar != null) {
                                targetPlayer.showBossBar(active.huntedBossBar);
                            }
                            if (plugin.getConfig().getBoolean("nametag-tracker.target-vignette", true)) {
                                sendRedBorderVignette(targetPlayer);
                            }
                            if (plugin.getConfig().getBoolean("nametag-tracker.target-title", true)) {
                                String targetTitle = plugin.getMessage("tracker-target-hunted-title",
                                        plugin.getMessage("tracker-target-notified-title",
                                                "<gradient:#ff0033:#880015><bold>⚠ YOU ARE BEING HUNTED ⚠</bold></gradient>"));
                                String targetSubtitle = plugin.getMessage("tracker-target-hunted-subtitle",
                                        plugin.getMessage("tracker-target-notified-subtitle",
                                                "<gray>An unknown hunter is tracking your movements!</gray>"));
                                plugin.sendTitle(targetPlayer, targetTitle, targetSubtitle, 10, 60, 20);
                            }
                        }
                    }

                    // Update time only if active and not paused
                    if (!active.isPaused && targetOnline) {
                        long elapsed = now - active.lastActiveTimestamp;
                        active.remainingMillis -= elapsed;
                        active.lastActiveTimestamp = now;
                    } else {
                        active.lastActiveTimestamp = now;
                    }

                    // Check expiration
                    if (active.isExpired()) {
                        it.remove();
                        cleanupTracker(active);
                        continue;
                    }

                    long remainingSec = Math.max(0, active.remainingMillis / 1000L);
                    String timeFormatted = String.format("%02d:%02d", remainingSec / 60, remainingSec % 60);
                    float progress = active.getProgress();

                    // Update Hunted BossBar if target is online
                    if (targetOnline && active.huntedBossBar != null) {
                        if (active.isPaused) {
                            String huntedPausedTitle = plugin.getMessage("tracker-hunted-bossbar-paused",
                                    "<gradient:#ffaa00:#ffd200><bold>⚠ TRACKING PAUSED ⚠</bold></gradient>")
                                    .replace("{TIME}", timeFormatted);
                            active.huntedBossBar.name(plugin.parseComponent(huntedPausedTitle));
                            active.huntedBossBar.color(BossBar.Color.YELLOW);
                        } else {
                            String huntedTitle = plugin.getMessage("tracker-hunted-bossbar-title",
                                    "<gradient:#ff2244:#ff6688><bold>⚠ YOU ARE BEING HUNTED ⚠</bold></gradient>")
                                    .replace("{TIME}", timeFormatted);
                            active.huntedBossBar.name(plugin.parseComponent(huntedTitle));
                            active.huntedBossBar.color(BossBar.Color.RED);
                        }
                        active.huntedBossBar.progress(progress);
                    }

                    // If tracker player is offline, keep tracker paused until they return or target returns
                    if (trackerPlayer == null || !trackerPlayer.isOnline()) {
                        continue;
                    }

                    String hudType = plugin.getConfig().getString("nametag-tracker.hud-type", "BOSSBAR").toUpperCase();
                    boolean useBossBar = hudType.contains("BOSSBAR") || hudType.equals("BOTH");
                    boolean useActionBar = hudType.contains("ACTIONBAR") || hudType.equals("BOTH");

                    int lives = plugin.getLifeManager() != null ? plugin.getLifeManager().getLives(trackerPlayer) : 3;
                    String hearts = plugin.getLifeManager() != null ? plugin.getLifeManager().getHeartsDisplay(lives) : "";
                    String heartsPrefix = hearts.isEmpty() ? "" : hearts + " <dark_gray>•</dark_gray> ";

                    // Handle target offline
                    if (!targetOnline) {
                        if (useBossBar && active.hunterBossBar != null) {
                            String pausedBossBar = plugin.getMessage("tracker-bossbar-paused",
                                    "<gradient:#00d2ff:#3a7bd5><b>TRACKER</b></gradient> <dark_gray>»</dark_gray> <white><b>{TARGET}</b></white> <dark_gray>•</dark_gray> <yellow>[PAUSED - OFFLINE]</yellow>")
                                    .replace("{TARGET}", targetDisplayName)
                                    .replace("{TIME}", timeFormatted);
                            active.hunterBossBar.name(plugin.parseComponent(pausedBossBar));
                            active.hunterBossBar.progress(progress);
                            active.hunterBossBar.color(BossBar.Color.YELLOW);
                        }
                        if (useActionBar) {
                            String offlineHud = plugin.getMessage("tracker-hud-paused",
                                    "<gradient:#00d2ff:#3a7bd5><b>TRACKER</b></gradient> <dark_gray>»</dark_gray> <white><b>{TARGET}</b></white> <yellow>[PAUSED - OFFLINE]</yellow> <dark_gray>•</dark_gray> <gray>Time left: {TIME}</gray>")
                                    .replace("{TARGET}", targetDisplayName)
                                    .replace("{TIME}", timeFormatted);
                            trackerPlayer.sendActionBar(plugin.parseComponent(heartsPrefix + offlineHud));
                        }
                        continue;
                    }

                    // Check dimension mismatch
                    if (!trackerPlayer.getWorld().equals(targetPlayer.getWorld())) {
                        String dimName = getFriendlyWorldName(targetPlayer.getWorld().getName());
                        if (useBossBar && active.hunterBossBar != null) {
                            String dimBossBar = plugin.getMessage("tracker-bossbar-dimension",
                                    "<gradient:#00d2ff:#3a7bd5><b>TRACKER</b></gradient> <dark_gray>»</dark_gray> <white><b>{TARGET}</b></white> <dark_gray>•</dark_gray> <#ffaa00>Target in {DIMENSION}</#ffaa00>")
                                    .replace("{TARGET}", targetDisplayName)
                                    .replace("{DIMENSION}", dimName)
                                    .replace("{TIME}", timeFormatted);
                            active.hunterBossBar.name(plugin.parseComponent(dimBossBar));
                            active.hunterBossBar.progress(progress);
                            active.hunterBossBar.color(BossBar.Color.BLUE);
                        }
                        if (useActionBar) {
                            String dimHud = plugin.getMessage("tracker-hud-dimension",
                                    "<gradient:#00d2ff:#3a7bd5><b>TRACKER</b></gradient> <dark_gray>»</dark_gray> <white><b>{TARGET}</b></white> <dark_gray>•</dark_gray> <#ffaa00>Target in {DIMENSION}</#ffaa00> <dark_gray>•</dark_gray> <gray>{TIME}</gray>")
                                    .replace("{TARGET}", targetDisplayName)
                                    .replace("{DIMENSION}", dimName)
                                    .replace("{TIME}", timeFormatted);
                            trackerPlayer.sendActionBar(plugin.parseComponent(heartsPrefix + dimHud));
                        }
                        continue;
                    }

                    // Same dimension: compute distance and direction vector from torso
                    Location trackerTorso = trackerPlayer.getLocation().add(0, 1.0, 0);
                    Location targetTorso = targetPlayer.getLocation().add(0, 1.0, 0);

                    double distance = trackerTorso.distance(targetTorso);
                    Vector direction = targetTorso.toVector().subtract(trackerTorso.toVector()).normalize();
                    String arrow = calculateDirectionArrow(trackerPlayer, targetPlayer);

                    // Update Hunter BossBar
                    if (useBossBar && active.hunterBossBar != null) {
                        String bossBarTitle = plugin.getMessage("tracker-bossbar-title",
                                "<gradient:#00d2ff:#3a7bd5><b>TRACKER</b></gradient> <dark_gray>»</dark_gray> <white><b>{TARGET}</b></white> <dark_gray>•</dark_gray> <#55ff99><bold>{ARROW}</bold> {DIST}m</#55ff99>")
                                .replace("{TARGET}", targetDisplayName)
                                .replace("{ARROW}", arrow)
                                .replace("{DIST}", String.valueOf((int) distance))
                                .replace("{TIME}", timeFormatted);
                        active.hunterBossBar.name(plugin.parseComponent(bossBarTitle));
                        active.hunterBossBar.progress(progress);
                        active.hunterBossBar.color(remainingSec < 30 ? BossBar.Color.RED : BossBar.Color.BLUE);
                    }

                    // Optional Actionbar HUD
                    if (useActionBar) {
                        String activeHud = plugin.getMessage("tracker-hud-active",
                                "<gradient:#00d2ff:#3a7bd5><b>TRACKER</b></gradient> <dark_gray>»</dark_gray> <white><b>{TARGET}</b></white> <dark_gray>•</dark_gray> <#55ff99><bold>{ARROW}</bold> {DIST}m</#55ff99> <dark_gray>•</dark_gray> <yellow>{TIME}</yellow>")
                                .replace("{TARGET}", targetDisplayName)
                                .replace("{ARROW}", arrow)
                                .replace("{DIST}", String.valueOf((int) distance))
                                .replace("{TIME}", timeFormatted);
                        trackerPlayer.sendActionBar(plugin.parseComponent(heartsPrefix + activeHud));
                    }

                    // Directional Crimson Red Particle Trail from torso (Spawned ONLY for tracker)
                    renderTrackingParticles(trackerPlayer, trackerTorso, direction);
                }
            }
        }.runTaskTimer(plugin, 20L, intervalTicks);
    }

    private void renderTrackingParticles(Player tracker, Location torso, Vector dir) {
        String particleName = plugin.getConfig().getString("nametag-tracker.particle-type", "RED_DUST");

        if (particleName.equalsIgnoreCase("RED_DUST") || particleName.equalsIgnoreCase("REDSTONE")) {
            org.bukkit.Color crimsonRed = org.bukkit.Color.fromRGB(255, 30, 60);
            Particle.DustOptions crimsonDust = new Particle.DustOptions(crimsonRed, 1.0f);

            // Forward crimson beam starting cleanly from the torso extending towards target
            for (double d = 0.6; d <= 2.6; d += 0.25) {
                Location pLoc = torso.clone().add(dir.clone().multiply(d));
                tracker.spawnParticle(Particle.DUST, pLoc, 1, 0.0, 0.0, 0.0, 0.0, crimsonDust);
                if (d >= 1.2 && d <= 2.2) {
                    tracker.spawnParticle(Particle.CRIMSON_SPORE, pLoc, 1, 0.01, 0.01, 0.01, 0.005);
                }
            }
        } else {
            Particle particle;
            try {
                particle = Particle.valueOf(particleName.toUpperCase());
            } catch (Exception e) {
                particle = Particle.SOUL_FIRE_FLAME;
            }

            for (double d = 0.6; d <= 2.6; d += 0.3) {
                Location pLoc = torso.clone().add(dir.clone().multiply(d));
                tracker.spawnParticle(particle, pLoc, 1, 0.01, 0.01, 0.01, 0.01);
            }
        }
    }

    private String calculateDirectionArrow(Player tracker, Player target) {
        Location tLoc = tracker.getLocation();
        Location oLoc = target.getLocation();

        double dx = oLoc.getX() - tLoc.getX();
        double dz = oLoc.getZ() - tLoc.getZ();

        double angle = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
        double diff = (angle - tLoc.getYaw()) % 360.0;
        if (diff < -180.0) diff += 360.0;
        if (diff > 180.0) diff -= 360.0;

        // diff: 0 = straight ahead, 90 = right, 180 = behind, -90 = left
        if (diff >= -22.5 && diff < 22.5) return "⬆";
        if (diff >= 22.5 && diff < 67.5) return "⬈";
        if (diff >= 67.5 && diff < 112.5) return "➡";
        if (diff >= 112.5 && diff < 157.5) return "⬊";
        if (diff >= -67.5 && diff < -22.5) return "⬉";
        if (diff >= -112.5 && diff < -67.5) return "⬅";
        if (diff >= -157.5 && diff < -112.5) return "⬋";
        return "⬇";
    }

    private String getFriendlyWorldName(String worldName) {
        String lower = worldName.toLowerCase();
        if (lower.contains("nether")) return "The Nether";
        if (lower.contains("the_end") || lower.contains("end")) return "The End";
        return "Overworld";
    }

    public void cleanupTracker(ActiveTracker active) {
        if (active == null) return;
        Player hunter = Bukkit.getPlayer(active.trackerUuid);
        Player target = Bukkit.getPlayer(active.targetUuid);
        String targetDisplayName = getTargetDisplayName(active);

        if (hunter != null && hunter.isOnline()) {
            if (active.hunterBossBar != null) {
                hunter.hideBossBar(active.hunterBossBar);
            }
            hunter.playSound(hunter.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.0f);
            String expiredMsg = plugin.getMessage("tracker-expired",
                    "<gradient:#ff3355:#ff6688><bold>STRANGERS</bold></gradient> <dark_gray>»</dark_gray> <#ffaa00>Tracking session for <color:#FFA737><b>{TARGET}</b></color> has expired.</#ffaa00>")
                    .replace("{TARGET}", targetDisplayName);
            hunter.sendMessage(plugin.parseComponent(expiredMsg));

            if (target != null && target.isOnline() && !plugin.isBypassed(hunter)) {
                plugin.refreshPlayerForViewer(hunter, target);
            }
        }

        if (active.huntedBossBar != null && target != null && target.isOnline()) {
            target.hideBossBar(active.huntedBossBar);
        }

        if (target != null && target.isOnline() && !isTargetBeingTracked(active.targetUuid, active.trackerUuid)) {
            restoreWorldBorder(target);
            String untrackedTitle = plugin.getMessage("tracker-target-untracked-title",
                    "<gradient:#55ff99:#00d2ff><bold>TRACKING ENDED</bold></gradient>");
            String untrackedSubtitle = plugin.getMessage("tracker-target-untracked-subtitle",
                    "<gray>The hunter has lost your trail.</gray>");
            plugin.sendTitle(target, untrackedTitle, untrackedSubtitle, 10, 50, 20);

            String untrackedChat = plugin.getMessage("tracker-target-untracked-chat",
                    "<gradient:#ff3355:#ff6688><bold>STRANGERS</bold></gradient> <dark_gray>»</dark_gray> <#55ff99>The ominous presence has vanished. <b>You are no longer being tracked.</b></#55ff99>");
            target.sendMessage(plugin.parseComponent(untrackedChat));
            target.playSound(target.getLocation(), Sound.ITEM_TOTEM_USE, 0.4f, 1.8f);
        }
    }

    public void shutdown() {
        if (trackingTask != null) {
            trackingTask.cancel();
            trackingTask = null;
        }
        for (ActiveTracker active : activeTrackers.values()) {
            Player hunter = Bukkit.getPlayer(active.trackerUuid);
            if (hunter != null && hunter.isOnline() && active.hunterBossBar != null) {
                hunter.hideBossBar(active.hunterBossBar);
            }
            Player target = Bukkit.getPlayer(active.targetUuid);
            if (target != null && target.isOnline()) {
                if (active.huntedBossBar != null) {
                    target.hideBossBar(active.huntedBossBar);
                }
                restoreWorldBorder(target);
            }
        }
        activeTrackers.clear();
    }

    public NamespacedKey getTrackerKey() {
        return trackerKey;
    }

    public NamespacedKey getTargetUuidKey() {
        return targetUuidKey;
    }

    public NamespacedKey getTargetNameKey() {
        return targetNameKey;
    }
}

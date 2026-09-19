package mlnplus.hu.strangers;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.BanList;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ReviveManager {

    private final Strangers plugin;
    private final NamespacedKey bookKey;
    private final NamespacedKey recipeKey;
    private final NamespacedKey targetUuidKey;

    public ReviveManager(Strangers plugin) {
        this.plugin = plugin;
        this.bookKey = new NamespacedKey(plugin, "revive_book");
        this.recipeKey = new NamespacedKey(plugin, "revive_book_recipe");
        this.targetUuidKey = new NamespacedKey(plugin, "target_uuid");
    }

    public void registerRecipe() {
        // Remove recipe if already registered
        try {
            Bukkit.removeRecipe(recipeKey);
        } catch (Exception ignored) {
        }

        if (!plugin.getConfig().getBoolean("revive-recipe.enabled", true)) {
            return;
        }

        ItemStack book = getReviveBook(1);
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, book);

        List<String> shapeList = plugin.getConfig().getStringList("revive-recipe.shape");
        if (shapeList.isEmpty()) {
            shapeList = List.of("NNN", "ESE", "NBN");
        }
        recipe.shape(shapeList.toArray(new String[0]));

        org.bukkit.configuration.ConfigurationSection ingredientsSec = plugin.getConfig().getConfigurationSection("revive-recipe.ingredients");
        if (ingredientsSec != null) {
            for (String keyStr : ingredientsSec.getKeys(false)) {
                if (keyStr.isEmpty()) continue;
                char keyChar = keyStr.charAt(0);
                String matName = ingredientsSec.getString(keyStr, "");
                Material mat = Material.matchMaterial(matName);
                if (mat != null) {
                    recipe.setIngredient(keyChar, mat);
                } else {
                    plugin.getLogger().warning("Invalid material '" + matName + "' for key '" + keyChar + "' in revive-recipe ingredients!");
                }
            }
        } else {
            recipe.setIngredient('N', Material.NETHERITE_INGOT);
            recipe.setIngredient('E', Material.ENCHANTED_GOLDEN_APPLE);
            recipe.setIngredient('S', Material.NETHER_STAR);
            recipe.setIngredient('B', Material.BOOK);
        }

        try {
            Bukkit.addRecipe(recipe);
            plugin.getLogger().info("Registered Revive Book crafting recipe.");
        } catch (Exception e) {
            plugin.getLogger().warning("Could not register Revive Book recipe: " + e.getMessage());
        }
    }

    public ItemStack getReviveBook(int amount) {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = plugin.getMessage("revive-book-name", "&d&lRevive Book");
            meta.displayName(plugin.parseComponent(name));

            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(plugin.parseComponent(plugin.getMessage("revive-book-lore-1", "&7Use this to revive an eliminated player.")));
            lore.add(plugin.parseComponent(""));
            lore.add(plugin.parseComponent(plugin.getMessage("revive-book-lore-2", "&e⚠ Each player can only be revived &c1 time!")));
            lore.add(plugin.parseComponent(plugin.getMessage("revive-book-lore-3", "&a✦ Receives &f1 life &aupon revival.")));
            meta.lore(lore);

            meta.getPersistentDataContainer().set(bookKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isReviveBook(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        Byte val = item.getItemMeta().getPersistentDataContainer().get(bookKey, PersistentDataType.BYTE);
        return val != null && val == (byte) 1;
    }

    public static class ReviveHolder implements org.bukkit.inventory.InventoryHolder {
        private Inventory inventory;

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public void openReviveGUI(Player player) {
        List<StrangersDatabase.LivesData> eliminated = plugin.getLifeManager().getEliminatedPlayers();
        String guiTitle = plugin.getMessage("revive-gui-title", "&d&lRevive Player");
        
        int size = Math.min(54, Math.max(9, ((eliminated.size() / 9) + 1) * 9));
        ReviveHolder holder = new ReviveHolder();
        Inventory inv = Bukkit.createInventory(holder, size, plugin.parseComponent(guiTitle));
        holder.setInventory(inv);

        for (int i = 0; i < eliminated.size() && i < size; i++) {
            StrangersDatabase.LivesData data = eliminated.get(i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(data.uuid));
                meta.displayName(plugin.parseComponent("&d&l" + data.playerName));

                List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                lore.add(plugin.parseComponent(plugin.getMessage("gui-status-eliminated", "&7Status: &cEliminated")));
                lore.add(plugin.parseComponent(plugin.getMessage("gui-lives", "<gray>Lives:</gray> {HEARTS}").replace("{HEARTS}", plugin.getLifeManager().getHeartsDisplay(data.lives)).replace("{LIVES}", String.valueOf(data.lives))));
                if (data.hasBeenRevived) {
                    lore.add(plugin.parseComponent(plugin.getMessage("gui-already-revived", "&c❌ Already revived once!")));
                } else {
                    lore.add(plugin.parseComponent(plugin.getMessage("gui-revivable", "&a✔ Revivable!")));
                    lore.add(plugin.parseComponent(plugin.getMessage("gui-click-to-revive", "&eClick to revive!")));
                }
                meta.lore(lore);

                meta.getPersistentDataContainer().set(targetUuidKey, PersistentDataType.STRING, data.uuid.toString());
                head.setItemMeta(meta);
            }
            inv.setItem(i, head);
        }

        player.openInventory(inv);
    }

    public boolean revivePlayer(CommandSender reviver, String targetName) {
        StrangersDatabase.LivesData data = plugin.getLifeManager().getLivesDataByName(targetName);
        if (data == null) {
            if (reviver != null) {
                String msg = plugin.getMessage("player-not-found", "<gradient:#ff3355:#ff6688><bold>STRANGERS</bold></gradient> <dark_gray>»</dark_gray> <#ff4444>Player not found!");
                reviver.sendMessage(plugin.parseComponent(msg));
            }
            return false;
        }

        if (data.lives > 0) {
            if (reviver != null) {
                String msg = plugin.getMessage("player-not-eliminated", "<gradient:#ff3355:#ff6688><bold>STRANGERS</bold></gradient> <dark_gray>»</dark_gray> <#ff4444>This player is not eliminated!");
                reviver.sendMessage(plugin.parseComponent(msg));
            }
            return false;
        }

        if (data.hasBeenRevived) {
            if (reviver != null) {
                String msg = plugin.getMessage("already-revived", "<gradient:#ff3355:#ff6688><bold>STRANGERS</bold></gradient> <dark_gray>»</dark_gray> <#ff4444>This player has already been revived once!");
                reviver.sendMessage(plugin.parseComponent(msg));
            }
            return false;
        }

        // Pardon ban
        try {
            Bukkit.getBanList(BanList.Type.NAME).pardon(data.playerName);
        } catch (Exception ignored) {
        }

        // Set lives to 1 and revived status to true
        plugin.getLifeManager().setLives(data.uuid, data.playerName, 1);
        plugin.getLifeManager().setRevived(data.uuid, data.playerName, true);

        // Reviver display name
        String reviverDisplayName;
        if (reviver instanceof Player p) {
            reviverDisplayName = plugin.isBypassed(p) ? plugin.getRealName(p.getUniqueId()) : plugin.getAnonymousName();
        } else {
            reviverDisplayName = "Console";
        }

        // Admin feedback to reviver
        if (reviver != null) {
            String successMsg = plugin.getMessage("revive-success",
                    "<gradient:#ff3355:#ff6688><bold>STRANGERS</bold></gradient> <dark_gray>»</dark_gray> <#55ff99>Successfully resurrected <white><b>{NAME}</b></white>! <gray>Granted 1 life.</gray>")
                    .replace("{NAME}", data.playerName);
            reviver.sendMessage(plugin.parseComponent(successMsg));
        }

        // Broadcast revive message with proper MiniMessage formatting
        String broadcastMsg = plugin.getMessage("revived-broadcast",
                "<#00ffaa>✦ <gradient:#00ffaa:#00aa66><bold>{NAME}</bold></gradient> <#aaffcc>has been resurrected from the dead!")
                .replace("{NAME}", data.playerName)
                .replace("{REVIVER}", reviverDisplayName);
        Bukkit.broadcast(plugin.parseComponent(broadcastMsg));

        // Play revive sounds to online players
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
        }

        return true;
    }

    public NamespacedKey getTargetUuidKey() {
        return targetUuidKey;
    }
}

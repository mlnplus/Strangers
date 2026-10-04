package mlnplus.hu.strangers;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SuppressWarnings("null")
public class ConfirmationGUI {

    // Minecraft textures for green checkmark (tick) and red cross (X)
    public static final String TICK_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTkyZTMxZmZiNTljOTBhYjA4ZmM5ZGMxZmUyNjgwMjAzNWEzYTQ3YzQyZmVlNjM0MjNiY2RiNDI2MmVjYjliNiJ9fX0=";
    public static final String X_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmViNTg4YjIxYTZmOThhZDFmZjRlMDg1YzU1MmRjYjA1MGVmYzljYWI0MjdmNDYwNDhmMThmYzgwMzQ3NWY3In19fQ==";

    public enum ConfirmationType {
        START,
        RESET
    }

    public static class ConfirmationHolder implements InventoryHolder {
        private final ConfirmationType type;
        private Inventory inventory;

        public ConfirmationHolder(ConfirmationType type) {
            this.type = type;
        }

        public ConfirmationType getType() {
            return type;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public static void open(Strangers plugin, Player player, ConfirmationType type) {
        String titleKey = (type == ConfirmationType.START) ? "gui-confirm-start-title" : "gui-confirm-reset-title";
        String defaultTitle = (type == ConfirmationType.START)
                ? "<gradient:#ff3355:#ff6688><bold>Start Strangers Event?</bold></gradient>"
                : "<gradient:#ffaa00:#ffcc00><bold>Reset Strangers Event?</bold></gradient>";
        Component titleComp = plugin.parseComponent(plugin.getMessage(titleKey, defaultTitle));

        ConfirmationHolder holder = new ConfirmationHolder(type);
        Inventory inv = Bukkit.createInventory(holder, 27, titleComp);
        holder.setInventory(inv);

        // Filler gray stained glass pane
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.displayName(Component.empty());
            filler.setItemMeta(fillerMeta);
        }
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler.clone());
        }

        // Slot 11: Green Tick Head (Confirm)
        ItemStack tickHead = createCustomHead(TICK_TEXTURE, Material.LIME_CONCRETE);
        ItemMeta tickMeta = tickHead.getItemMeta();
        if (tickMeta != null) {
            String tickName = plugin.getMessage("gui-confirm-tick-name", "<#55ff99><bold>✔ CONFIRM</bold></#55ff99>");
            tickMeta.displayName(plugin.parseComponent(tickName));

            List<Component> lore = new ArrayList<>();
            if (type == ConfirmationType.START) {
                lore.add(plugin.parseComponent("<dark_gray>▪</dark_gray> <gray>Click to initiate the event.</gray>"));
                lore.add(plugin.parseComponent("<dark_gray>▪</dark_gray> <#55ff99>Scatters all players safely on dry land.</#55ff99>"));
            } else {
                lore.add(plugin.parseComponent("<dark_gray>▪</dark_gray> <gray>Click to reset the event.</gray>"));
                lore.add(plugin.parseComponent("<dark_gray>▪</dark_gray> <#ffaa00>Restores border & returns players to spawn.</#ffaa00>"));
            }
            tickMeta.lore(lore);
            tickHead.setItemMeta(tickMeta);
        }
        inv.setItem(11, tickHead);

        // Slot 13: Event Overview / Info Item
        if (type == ConfirmationType.START) {
            ItemStack info = new ItemStack(Material.NETHER_STAR);
            ItemMeta infoMeta = info.getItemMeta();
            if (infoMeta != null) {
                infoMeta.displayName(plugin.parseComponent("<gradient:#ff3355:#ff7788><bold>⚔ EVENT OVERVIEW ⚔</bold></gradient>"));
                double borderSize = plugin.getConfig().getDouble("game.border-size", 8000.0);
                int prot = plugin.getConfig().getInt("game.teleport-protection-seconds", 10);

                List<Component> lore = new ArrayList<>();
                lore.add(plugin.parseComponent("<gray>Summary of actions to apply:</gray>"));
                lore.add(Component.empty());
                lore.add(plugin.parseComponent("<#ff3355>▪</#ff3355> <gray>World Border:</gray> <#ff4d6d><bold>" + (int) borderSize + " blocks</bold></#ff4d6d>"));
                lore.add(plugin.parseComponent("<#ff3355>▪</#ff3355> <gray>Scattering:</gray>   <#55ff99>Open-air safe surface ground</#55ff99>"));
                lore.add(plugin.parseComponent("<#ff3355>▪</#ff3355> <gray>Anonymity:</gray>    <#ff6688>Names, skins & voices concealed</#ff6688>"));
                lore.add(plugin.parseComponent("<#ff3355>▪</#ff3355> <gray>Protection:</gray>   <#aaccff>" + prot + "s Resistance on arrival</#aaccff>"));
                infoMeta.lore(lore);
                infoMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                info.setItemMeta(infoMeta);
            }
            inv.setItem(13, info);
        } else {
            ItemStack info = new ItemStack(Material.RECOVERY_COMPASS);
            ItemMeta infoMeta = info.getItemMeta();
            if (infoMeta != null) {
                infoMeta.displayName(plugin.parseComponent("<gradient:#ffaa00:#ffcc00><bold>⚠ RESET OVERVIEW ⚠</bold></gradient>"));
                List<Component> lore = new ArrayList<>();
                lore.add(plugin.parseComponent("<gray>Summary of reset actions:</gray>"));
                lore.add(Component.empty());
                lore.add(plugin.parseComponent("<#ffaa00>▪</#ffaa00> <gray>World Border:</gray> <#55ff99>Restored to default size</#55ff99>"));
                lore.add(plugin.parseComponent("<#ffaa00>▪</#ffaa00> <gray>All Players:</gray>  <#55ff99>Teleported to world spawn</#55ff99>"));
                lore.add(plugin.parseComponent("<#ffaa00>▪</#ffaa00> <gray>Anonymity:</gray>    <#ff5555>Disabled (True identities revealed)</#ff5555>"));
                lore.add(plugin.parseComponent("<#ffaa00>▪</#ffaa00> <gray>Lives & Bans:</gray> <#55ff99>Pardoned and reset to default</#55ff99>"));
                infoMeta.lore(lore);
                infoMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                info.setItemMeta(infoMeta);
            }
            inv.setItem(13, info);
        }

        // Slot 15: Red X Head (Cancel)
        ItemStack xHead = createCustomHead(X_TEXTURE, Material.RED_CONCRETE);
        ItemMeta xMeta = xHead.getItemMeta();
        if (xMeta != null) {
            String cancelName = plugin.getMessage("gui-confirm-cancel-name", "<#ff4444><bold>✖ CANCEL</bold></#ff4444>");
            xMeta.displayName(plugin.parseComponent(cancelName));

            List<Component> lore = new ArrayList<>();
            lore.add(plugin.parseComponent("<dark_gray>▪</dark_gray> <gray>Click to abort and close menu.</gray>"));
            xMeta.lore(lore);
            xHead.setItemMeta(xMeta);
        }
        inv.setItem(15, xHead);

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
    }

    public static ItemStack createCustomHead(String textureBase64, Material fallback) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        try {
            SkullMeta meta = (SkullMeta) item.getItemMeta();
            if (meta != null) {
                UUID profileUuid = UUID.nameUUIDFromBytes(textureBase64.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                PlayerProfile profile = Bukkit.createProfile(profileUuid, "CustomHead");
                profile.setProperty(new ProfileProperty("textures", textureBase64));
                meta.setPlayerProfile(profile);
                item.setItemMeta(meta);
                return item;
            }
        } catch (Throwable ignored) {
        }
        return new ItemStack(fallback != null ? fallback : Material.PLAYER_HEAD);
    }
}

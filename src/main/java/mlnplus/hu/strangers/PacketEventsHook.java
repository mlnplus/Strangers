package mlnplus.hu.strangers;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate.PlayerInfo;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTabComplete;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSystemChatMessage;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class PacketEventsHook implements PacketListener {

    private final Strangers plugin;
    private PacketListenerCommon registeredListener = null;

    public PacketEventsHook(Strangers plugin) {
        this.plugin = plugin;
    }

    public void register() {
        try {
            this.registeredListener = PacketEvents.getAPI().getEventManager().registerListener(this, PacketListenerPriority.HIGHEST);
            plugin.getLogger().info("Successfully hooked into PacketEvents for per-viewer anonymization!");
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to register PacketEvents packet listener: " + t.getMessage());
        }
    }

    public void shutdown() {
        if (registeredListener != null) {
            try {
                PacketEvents.getAPI().getEventManager().unregisterListener(registeredListener);
            } catch (Throwable ignored) {
            }
            registeredListener = null;
        }
    }

    public boolean isRegistered() {
        return registeredListener != null;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        // Not modifying inbound packets
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (!plugin.isPluginEnabled()) {
            return;
        }

        if (!(event.getPlayer() instanceof Player receiver)) {
            return;
        }

        var packetType = event.getPacketType();

        // 1. Intercept PlayerInfoUpdate
        if (packetType == PacketType.Play.Server.PLAYER_INFO_UPDATE) {
            try {
                WrapperPlayServerPlayerInfoUpdate wrapper = new WrapperPlayServerPlayerInfoUpdate(event);
                List<PlayerInfo> entries = wrapper.getEntries();
                if (entries == null || entries.isEmpty()) {
                    return;
                }

                String anonName = plugin.getAnonymousName();
                String skinVal = plugin.getCachedSkinValue();
                String skinSig = plugin.getCachedSkinSignature();

                List<TextureProperty> disguisedTextures = new ArrayList<>();
                if (skinVal != null && skinSig != null) {
                    disguisedTextures.add(new TextureProperty("textures", skinVal, skinSig));
                }

                boolean receiverBypassed = plugin.isBypassed(receiver);
                boolean modified = false;

                for (PlayerInfo entry : entries) {
                    java.util.UUID targetUuid = entry.getProfileId();
                    UserProfile profile = entry.getGameProfile();
                    if (targetUuid == null && profile != null) {
                        targetUuid = profile.getUUID();
                    }
                    if (targetUuid == null) {
                        continue;
                    }

                    boolean isRevealed = receiverBypassed
                            || plugin.isBypassed(targetUuid)
                            || (plugin.getTrackerManager() != null && plugin.getTrackerManager().isTrackerRevealed(receiver, targetUuid));

                    if (isRevealed) {
                        // Revealed: Show REAL name, REAL skin, and REAL tab display name!
                        String realName = plugin.getRealName(targetUuid);
                        if (realName.equals("Unknown") || realName.equalsIgnoreCase(anonName) || realName.equalsIgnoreCase("Stranger")) {
                            Player targetPlayer = Bukkit.getPlayer(targetUuid);
                            if (targetPlayer != null && !targetPlayer.getName().equalsIgnoreCase(anonName) && !targetPlayer.getName().equalsIgnoreCase("Stranger")) {
                                realName = targetPlayer.getName();
                            }
                        }

                        if (!realName.equals("Unknown") && !realName.equalsIgnoreCase(anonName) && !realName.equalsIgnoreCase("Stranger")) {
                            if (profile != null) {
                                profile.setName(realName);
                            }
                        }

                        StrangersDatabase.CachedSkin realSkin = plugin.getOriginalSkin(targetUuid);
                        if (realSkin != null && realSkin.value != null && profile != null) {
                            List<TextureProperty> realTextures = new ArrayList<>();
                            realTextures.add(new TextureProperty("textures", realSkin.value, realSkin.signature != null ? realSkin.signature : ""));
                            profile.setTextureProperties(realTextures);
                        }

                        Component realTabName = plugin.getOriginalListName(targetUuid);
                        if (realTabName == null && !realName.equals("Unknown") && !realName.equalsIgnoreCase("Stranger") && !realName.equalsIgnoreCase(anonName)) {
                            realTabName = Component.text(realName);
                        }
                        if (realTabName != null) {
                            entry.setDisplayName(realTabName);
                        } else if (!realName.equals("Unknown") && !realName.equalsIgnoreCase("Stranger") && !realName.equalsIgnoreCase(anonName)) {
                            entry.setDisplayName(Component.text(realName));
                        }
                        modified = true;
                    } else {
                        // Disguised: Stranger name, Stranger skin, Stranger tab name
                        if (profile != null) {
                            profile.setName(anonName);
                            if (!disguisedTextures.isEmpty()) {
                                profile.setTextureProperties(disguisedTextures);
                            }
                        }
                        if (entry.getDisplayName() != null
                                || wrapper.getActions().contains(WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME)
                                || wrapper.getActions().contains(WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER)) {
                            entry.setDisplayName(Component.text(anonName));
                        }
                        modified = true;
                    }
                }

                if (modified) {
                    wrapper.setEntries(entries);
                    event.markForReEncode(true);
                }
            } catch (Throwable ignored) {
            }
            return;
        }

        // For tab complete and chat packets: bypassed players receive raw unfiltered packets
        if (plugin.isBypassed(receiver)) {
            return;
        }

        // 2. Intercept Tab Complete to hide real player names from non-bypassed players
        if (packetType == PacketType.Play.Server.TAB_COMPLETE) {
            try {
                WrapperPlayServerTabComplete wrapper = new WrapperPlayServerTabComplete(event);
                List<WrapperPlayServerTabComplete.CommandMatch> matches = wrapper.getCommandMatches();
                if (matches != null && !matches.isEmpty()) {
                    boolean modified = false;
                    List<WrapperPlayServerTabComplete.CommandMatch> filtered = new ArrayList<>(matches.size());
                    for (WrapperPlayServerTabComplete.CommandMatch match : matches) {
                        String text = match.getText();
                        boolean isRealName = false;
                        for (Player online : Bukkit.getOnlinePlayers()) {
                            if (online == null) continue;
                            String realName = plugin.getRealName(online.getUniqueId());
                            if (realName.equalsIgnoreCase("Unknown") || realName.equalsIgnoreCase(plugin.getAnonymousName())) {
                                realName = online.getName();
                            }
                            if (text.equalsIgnoreCase(realName) || text.equalsIgnoreCase("/" + realName)) {
                                isRealName = true;
                                break;
                            }
                        }
                        if (!isRealName) {
                            filtered.add(match);
                        } else {
                            modified = true;
                        }
                    }
                    if (modified) {
                        wrapper.setCommandMatches(filtered);
                        event.markForReEncode(true);
                    }
                }
            } catch (Throwable ignored) {
            }
            return;
        }

        // 3. Intercept System Chat to mask real player names from non-bypassed players
        if (packetType == PacketType.Play.Server.SYSTEM_CHAT_MESSAGE) {
            try {
                if (plugin.isBypassed(receiver)) {
                    return;
                }
                WrapperPlayServerSystemChatMessage wrapper = new WrapperPlayServerSystemChatMessage(event);
                Component msg = wrapper.getMessage();
                if (msg != null) {
                    String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(msg);

                    // Do not modify intentional broadcasts and tracker messages from Strangers plugin
                    if (plain.contains("lost a life") || plain.contains("elvesztett egy életet")
                            || plain.contains("eliminated") || plain.contains("kiesett")
                            || plain.contains("resurrected") || plain.contains("feltámasztva")
                            || plain.contains("TRACKER") || plain.contains("KÖVETŐ")
                            || plain.contains("Tracking") || plain.contains("követés") || plain.contains("Követés")
                            || plain.contains("has died!") || plain.contains("meghalt!")
                            || plain.contains("has logged off") || plain.contains("kilépett")
                            || plain.contains("has reconnected") || plain.contains("újra csatlakozott")
                            || plain.contains("Identity Tracker") || plain.contains("Személyazonosság Követő")) {
                        return;
                    }

                    Component updatedMsg = msg;
                    boolean modified = false;
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (online == null) continue;
                        if (plugin.getLifeManager() != null && plugin.getLifeManager().isEliminated(online.getUniqueId())) {
                            continue;
                        }
                        // If receiver is tracking this player, receiver knows their true identity
                        if (plugin.getTrackerManager() != null && plugin.getTrackerManager().isTrackerRevealed(receiver, online.getUniqueId())) {
                            continue;
                        }
                        String realName = plugin.getRealName(online.getUniqueId());
                        if (realName.equalsIgnoreCase("Unknown") || realName.equalsIgnoreCase(plugin.getAnonymousName())) {
                            realName = online.getName();
                        }
                        if (realName != null && !realName.isEmpty() && !realName.equalsIgnoreCase(plugin.getAnonymousName())) {
                            if (plain.contains(realName)) {
                                final String nameToReplace = realName;
                                updatedMsg = updatedMsg.replaceText(builder -> builder
                                        .match("(?i)\\b" + java.util.regex.Pattern.quote(nameToReplace) + "\\b")
                                        .replacement(plugin.getAnonymousName()));
                                modified = true;
                            }
                        }
                    }
                    if (modified) {
                        wrapper.setMessage(updatedMsg);
                        event.markForReEncode(true);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }
}

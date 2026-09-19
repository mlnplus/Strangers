package mlnplus.hu.strangers;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.comphenix.protocol.wrappers.WrappedSignedProperty;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ProtocolLibHook {

    private final Strangers plugin;
    private ProtocolManager protocolManager;
    private PacketAdapter packetListener;

    public ProtocolLibHook(Strangers plugin) {
        this.plugin = plugin;
    }

    public boolean isRegistered() {
        return packetListener != null;
    }

    public void register() {
        try {
            this.protocolManager = ProtocolLibrary.getProtocolManager();
            this.packetListener = new PacketAdapter(plugin, ListenerPriority.HIGHEST,
                    PacketType.Play.Server.PLAYER_INFO,
                    PacketType.Play.Server.TAB_COMPLETE,
                    PacketType.Play.Server.SYSTEM_CHAT,
                    PacketType.Play.Server.DISGUISED_CHAT) {
                @Override
                public void onPacketSending(PacketEvent event) {
                    if (!ProtocolLibHook.this.plugin.isPluginEnabled()) {
                        return;
                    }

                    Player receiver = event.getPlayer();
                    if (receiver == null) {
                        return;
                    }

                    PacketType type = event.getPacketType();

                    // 1. Anonymize or reveal GameProfiles & PlayerInfo in network packets
                    if (type == PacketType.Play.Server.PLAYER_INFO) {
                        try {
                            List<PlayerInfoData> currentData = event.getPacket().getPlayerInfoDataLists().readSafely(0);
                            if (currentData != null && !currentData.isEmpty()) {
                                String anonName = ProtocolLibHook.this.plugin.getAnonymousName();
                                String skinVal = ProtocolLibHook.this.plugin.getCachedSkinValue();
                                String skinSig = ProtocolLibHook.this.plugin.getCachedSkinSignature();
                                boolean receiverBypassed = ProtocolLibHook.this.plugin.isBypassed(receiver);
                                List<PlayerInfoData> newDataList = new ArrayList<>(currentData.size());

                                for (PlayerInfoData data : currentData) {
                                    java.util.UUID targetUuid = data.getProfileId();
                                    WrappedGameProfile origProfile = data.getProfile();
                                    boolean isRevealed = receiverBypassed
                                            || ProtocolLibHook.this.plugin.isBypassed(targetUuid)
                                            || (ProtocolLibHook.this.plugin.getTrackerManager() != null && ProtocolLibHook.this.plugin.getTrackerManager().isTrackerRevealed(receiver, targetUuid));

                                    WrappedGameProfile newProfile;
                                    WrappedChatComponent newDisplayName;

                                    if (isRevealed) {
                                        String realName = ProtocolLibHook.this.plugin.getRealName(targetUuid);
                                        if (realName.equals("Unknown") || realName.equalsIgnoreCase(anonName) || realName.equalsIgnoreCase("Stranger")) {
                                            Player targetPlayer = Bukkit.getPlayer(targetUuid);
                                            if (targetPlayer != null && !targetPlayer.getName().equalsIgnoreCase(anonName) && !targetPlayer.getName().equalsIgnoreCase("Stranger")) {
                                                realName = targetPlayer.getName();
                                            }
                                        }

                                        if (origProfile != null && !realName.equals("Unknown")) {
                                            newProfile = origProfile.withName(realName);
                                        } else if (!realName.equals("Unknown")) {
                                            newProfile = new WrappedGameProfile(targetUuid, realName);
                                        } else {
                                            newProfile = origProfile;
                                        }

                                        StrangersDatabase.CachedSkin realSkin = ProtocolLibHook.this.plugin.getOriginalSkin(targetUuid);
                                        if (realSkin != null && realSkin.value != null && realSkin.signature != null && newProfile != null) {
                                            newProfile.getProperties().clear();
                                            newProfile.getProperties().put("textures",
                                                    new WrappedSignedProperty("textures", realSkin.value, realSkin.signature));
                                        }

                                        Component origTab = ProtocolLibHook.this.plugin.getOriginalListName(targetUuid);
                                        if (origTab != null) {
                                            newDisplayName = WrappedChatComponent.fromJson(net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().serialize(origTab));
                                        } else if (!realName.equals("Unknown")) {
                                            newDisplayName = WrappedChatComponent.fromText(realName);
                                        } else {
                                            newDisplayName = data.getDisplayName();
                                        }
                                    } else {
                                        if (origProfile != null) {
                                            newProfile = origProfile.withName(anonName);
                                        } else {
                                            newProfile = new WrappedGameProfile(targetUuid, anonName);
                                        }

                                        if (skinVal != null && skinSig != null) {
                                            newProfile.getProperties().clear();
                                            newProfile.getProperties().put("textures",
                                                    new WrappedSignedProperty("textures", skinVal, skinSig));
                                        }

                                        newDisplayName = WrappedChatComponent.fromText(anonName);
                                    }

                                    PlayerInfoData newData;
                                    try {
                                        if (data.getRemoteChatSessionData() != null) {
                                            newData = new PlayerInfoData(data.getProfileId(), data.getLatency(),
                                                    data.isListed(), data.getGameMode(), newProfile, newDisplayName,
                                                    data.getRemoteChatSessionData());
                                        } else {
                                            newData = new PlayerInfoData(data.getProfileId(), data.getLatency(),
                                                    data.isListed(), data.getGameMode(), newProfile, newDisplayName);
                                        }
                                    } catch (Throwable t) {
                                        newData = new PlayerInfoData(newProfile, data.getLatency(), data.getGameMode(),
                                                newDisplayName);
                                    }
                                    newDataList.add(newData);
                                }
                                event.getPacket().getPlayerInfoDataLists().write(0, newDataList);
                            }
                        } catch (Throwable ignored) {
                        }
                        return;
                    }

                    // For chat and tab-complete packets: bypassed players receive raw packets
                    if (ProtocolLibHook.this.plugin.isBypassed(receiver)) {
                        return;
                    }

                    // 2. Intercept System Chat & Disguised Chat to mask real player names from plugins/vanilla
                    if (type == PacketType.Play.Server.SYSTEM_CHAT || type == PacketType.Play.Server.DISGUISED_CHAT) {
                        try {
                            if (ProtocolLibHook.this.plugin.isBypassed(receiver)) {
                                return;
                            }
                            WrappedChatComponent comp = event.getPacket().getChatComponents().readSafely(0);
                            if (comp != null) {
                                String json = comp.getJson();
                                if (json != null) {
                                    if (json.contains("lost a life") || json.contains("elvesztett egy életet")
                                            || json.contains("eliminated") || json.contains("kiesett")
                                            || json.contains("resurrected") || json.contains("feltámasztva")
                                            || json.contains("TRACKER") || json.contains("KÖVETŐ")
                                            || json.contains("Tracking") || json.contains("követés") || json.contains("Követés")
                                            || json.contains("has died!") || json.contains("meghalt!")
                                            || json.contains("has logged off") || json.contains("kilépett")
                                            || json.contains("has reconnected") || json.contains("újra csatlakozott")
                                            || json.contains("Identity Tracker") || json.contains("Személyazonosság Követő")) {
                                        return;
                                    }
                                    String anonName = ProtocolLibHook.this.plugin.getAnonymousName();
                                    String modifiedJson = json;
                                    boolean modified = false;
                                    for (Player online : ProtocolLibHook.this.plugin.getServer().getOnlinePlayers()) {
                                        if (ProtocolLibHook.this.plugin.getLifeManager() != null
                                                && ProtocolLibHook.this.plugin.getLifeManager().isEliminated(online.getUniqueId())) {
                                            continue;
                                        }
                                        if (ProtocolLibHook.this.plugin.getTrackerManager() != null
                                                && ProtocolLibHook.this.plugin.getTrackerManager().isTrackerRevealed(receiver, online.getUniqueId())) {
                                            continue;
                                        }
                                        String name = online.getName();
                                        if (modifiedJson.contains(name)) {
                                            modifiedJson = modifiedJson.replaceAll("(?i)\\b"
                                                    + java.util.regex.Pattern.quote(name) + "\\b", anonName);
                                            modified = true;
                                        }
                                    }
                                    if (modified) {
                                        event.getPacket().getChatComponents().write(0,
                                                WrappedChatComponent.fromJson(modifiedJson));
                                    }
                                }
                            } else {
                                String raw = event.getPacket().getStrings().readSafely(0);
                                if (raw != null) {
                                    if (raw.contains("lost a life") || raw.contains("elvesztett egy életet")
                                            || raw.contains("eliminated") || raw.contains("kiesett")
                                            || raw.contains("resurrected") || raw.contains("feltámasztva")
                                            || raw.contains("TRACKER") || raw.contains("KÖVETŐ")
                                            || raw.contains("Tracking") || raw.contains("követés") || raw.contains("Követés")
                                            || raw.contains("has died!") || raw.contains("meghalt!")
                                            || raw.contains("has logged off") || raw.contains("kilépett")
                                            || raw.contains("has reconnected") || raw.contains("újra csatlakozott")
                                            || raw.contains("Identity Tracker") || raw.contains("Személyazonosság Követő")) {
                                        return;
                                    }
                                    String anonName = ProtocolLibHook.this.plugin.getAnonymousName();
                                    String modifiedRaw = raw;
                                    boolean modified = false;
                                    for (Player online : ProtocolLibHook.this.plugin.getServer().getOnlinePlayers()) {
                                        if (ProtocolLibHook.this.plugin.getLifeManager() != null
                                                && ProtocolLibHook.this.plugin.getLifeManager().isEliminated(online.getUniqueId())) {
                                            continue;
                                        }
                                        if (ProtocolLibHook.this.plugin.getTrackerManager() != null
                                                && ProtocolLibHook.this.plugin.getTrackerManager().isTrackerRevealed(receiver, online.getUniqueId())) {
                                            continue;
                                        }
                                        String name = online.getName();
                                        if (modifiedRaw.contains(name)) {
                                            modifiedRaw = modifiedRaw.replaceAll("(?i)\\b"
                                                    + java.util.regex.Pattern.quote(name) + "\\b", anonName);
                                            modified = true;
                                        }
                                    }
                                    if (modified) {
                                        event.getPacket().getStrings().write(0, modifiedRaw);
                                    }
                                }
                            }
                        } catch (Throwable ignored) {
                        }
                        return;
                    }

                    // 3. Tab Complete Packet Filtering
                    if (type == PacketType.Play.Server.TAB_COMPLETE) {
                        try {
                            String[] currentArray = event.getPacket().getStringArrays().readSafely(0);
                            if (currentArray != null) {
                                List<String> completions = new ArrayList<>(Arrays.asList(currentArray));
                                boolean modified = false;
                                for (Player online : ProtocolLibHook.this.plugin.getServer().getOnlinePlayers()) {
                                    String realName = online.getName();
                                    if (completions.removeIf(c -> c.equalsIgnoreCase(realName))) {
                                        modified = true;
                                    }
                                }
                                if (modified) {
                                    event.getPacket().getStringArrays().write(0, completions.toArray(new String[0]));
                                }
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }
            };

            protocolManager.addPacketListener(packetListener);
            plugin.getLogger().info("Successfully hooked into ProtocolLib for packet-level anonymization!");
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to initialize ProtocolLib packet listener: " + t.getMessage());
        }
    }

    public void shutdown() {
        if (protocolManager != null && packetListener != null) {
            try {
                protocolManager.removePacketListener(packetListener);
            } catch (Throwable ignored) {
            }
            packetListener = null;
        }
    }
}

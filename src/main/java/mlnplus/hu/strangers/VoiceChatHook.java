package mlnplus.hu.strangers;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.packets.MicrophonePacket;
import org.bukkit.entity.Player;

import java.util.UUID;

public class VoiceChatHook implements VoicechatPlugin {

    private final Strangers plugin;
    private VoiceProcessorManager processorManager;

    public VoiceChatHook(Strangers plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getPluginId() {
        return "strangers";
    }

    private VoicechatApi api;

    @Override
    public void initialize(VoicechatApi api) {
        this.api = api;
        plugin.getLogger().info("Hooking into Simple Voice Chat API...");
        reloadSettings();
    }

    public void reloadSettings() {
        boolean enabled = plugin.getConfig().getBoolean("voice-changer.enabled", true);
        double pitchRatio = plugin.getConfig().getDouble("voice-changer.pitch-ratio", 0.65);
        int windowMs = plugin.getConfig().getInt("voice-changer.window-ms", 30);

        if (enabled) {
            if (this.processorManager != null) {
                this.processorManager.updateSettings(pitchRatio, windowMs);
            } else if (this.api != null) {
                this.processorManager = new VoiceProcessorManager(this.api, pitchRatio, windowMs);
            }
            plugin.getLogger().info("Voice Changer reloaded: pitch-ratio=" + pitchRatio + ", window-ms=" + windowMs);
        } else {
            if (this.processorManager != null) {
                this.processorManager.clear();
                this.processorManager = null;
            }
            plugin.getLogger().info("Voice Changer is disabled in config.yml.");
        }
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicPacket);
    }

    private void onMicPacket(MicrophonePacketEvent event) {
        if (processorManager == null || !plugin.isPluginEnabled()) {
            return;
        }

        VoicechatConnection conn = event.getSenderConnection();
        if (conn == null || conn.getPlayer() == null) {
            return;
        }

        Player player;
        Object playerObj = conn.getPlayer().getPlayer();
        if (playerObj instanceof Player p) {
            player = p;
        } else {
            return;
        }

        if (plugin.isBypassed(player)) {
            return;
        }

        try {
            MicrophonePacket packet = event.getPacket();
            byte[] rawOpus = packet.getOpusEncodedData();
            if (rawOpus == null || rawOpus.length == 0) {
                return;
            }

            byte[] processed = processorManager.process(player.getUniqueId(), rawOpus);
            packet.setOpusEncodedData(processed);
        } catch (Exception e) {
            plugin.getLogger().severe("Error processing voice packet for " + player.getName() + ": " + e.getMessage());
        }
    }

    public void handlePlayerQuit(UUID playerId) {
        if (processorManager != null) {
            processorManager.removePlayer(playerId);
        }
    }

    public void shutdown() {
        if (processorManager != null) {
            processorManager.clear();
        }
    }
}

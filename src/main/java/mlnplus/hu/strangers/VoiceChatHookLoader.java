package mlnplus.hu.strangers;

import org.bukkit.plugin.RegisteredServiceProvider;
import java.util.UUID;

@SuppressWarnings("null")
public class VoiceChatHookLoader {
    
    private static VoiceChatHook hook;

    public static void register(Strangers plugin) {
        try {
            RegisteredServiceProvider<de.maxhenkel.voicechat.api.BukkitVoicechatService> provider = plugin.getServer()
                    .getServicesManager().getRegistration(de.maxhenkel.voicechat.api.BukkitVoicechatService.class);
            if (provider != null) {
                hook = new VoiceChatHook(plugin);
                provider.getProvider().registerPlugin(hook);
                plugin.getLogger().info("Successfully registered Simple Voice Chat API hook!");
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to hook into Simple Voice Chat API: " + t.getMessage());
        }
    }

    public static void handlePlayerQuit(UUID playerId) {
        if (hook != null) {
            hook.handlePlayerQuit(playerId);
        }
    }

    public static void reloadSettings() {
        if (hook != null) {
            hook.reloadSettings();
        }
    }

    public static void shutdown() {
        if (hook != null) {
            hook.shutdown();
            hook = null;
        }
    }
}

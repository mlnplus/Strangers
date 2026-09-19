package mlnplus.hu.strangers;

public class PacketEventsHookLoader {

    private static PacketEventsHook hook;

    public static void register(Strangers plugin) {
        if (plugin.getServer().getPluginManager().isPluginEnabled("packetevents")) {
            try {
                hook = new PacketEventsHook(plugin);
                hook.register();
            } catch (Throwable t) {
                plugin.getLogger().warning("Failed to load PacketEvents hook: " + t.getMessage());
            }
        } else {
            plugin.getLogger().info("PacketEvents plugin not found. Packet-level anonymization disabled.");
        }
    }

    public static void shutdown() {
        if (hook != null) {
            hook.shutdown();
            hook = null;
        }
    }

    public static boolean isAvailable() {
        return hook != null && hook.isRegistered();
    }
}

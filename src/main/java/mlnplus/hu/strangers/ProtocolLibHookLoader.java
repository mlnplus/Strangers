package mlnplus.hu.strangers;

public class ProtocolLibHookLoader {

    private static ProtocolLibHook hook;

    public static void register(Strangers plugin) {
        if (plugin.getServer().getPluginManager().isPluginEnabled("ProtocolLib")) {
            try {
                hook = new ProtocolLibHook(plugin);
                hook.register();
            } catch (Throwable t) {
                plugin.getLogger().warning("Failed to load ProtocolLib hook: " + t.getMessage());
            }
        } else {
            plugin.getLogger().info("ProtocolLib plugin not found. Packet-level anonymization disabled.");
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

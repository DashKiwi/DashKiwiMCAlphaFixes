import java.util.logging.Logger;

/**
 * ServerFixes - two independent fixes bundled as one hMod plugin:
 *
 *  1) Creeper spawn protection (see SpawnProtection) - creeper explosions within a
 *     configurable radius of world spawn no longer damage the world.
 *  2) Lighting auto-fix (see LightingFix) - automates the "re-set a block nearby"
 *     trick that already fixes the Alpha-era lighting glitch by hand, so it happens
 *     on its own as players explore instead of needing someone to notice and fix it.
 *
 * See README.md for the full explanation of how each fix works and the hMod API
 * limitations that shaped the design.
 */
public class ServerFixes extends Plugin {
    private static final String NAME = "ServerFixes";
    private static final Logger log = Logger.getLogger("Minecraft");

    private FixesListener listener;

    @Override
    public void enable() {
        setName(NAME);

        listener = new FixesListener();

        etc.getInstance().getLoader().addListener(
                PluginLoader.Hook.EXPLODE, listener, this, PluginListener.Priority.MEDIUM);
        etc.getInstance().getLoader().addListener(
                PluginLoader.Hook.IGNITE, listener, this, PluginListener.Priority.MEDIUM);
        etc.getInstance().getLoader().addListener(
                PluginLoader.Hook.DISCONNECT, listener, this, PluginListener.Priority.MEDIUM);
        etc.getInstance().getLoader().addListener(
                PluginLoader.Hook.BLOCK_BROKEN, listener, this, PluginListener.Priority.MEDIUM);
        etc.getInstance().getLoader().addListener(
                PluginLoader.Hook.BLOCK_PLACE, listener, this, PluginListener.Priority.MEDIUM);
        etc.getInstance().getLoader().addListener(
                PluginLoader.Hook.PLAYER_MOVE, listener, this, PluginListener.Priority.MEDIUM);
        etc.getInstance().getLoader().addListener(
                PluginLoader.Hook.LOGIN, listener, this, PluginListener.Priority.MEDIUM);
        etc.getInstance().getLoader().addListener(
                PluginLoader.Hook.COMMAND, listener, this, PluginListener.Priority.MEDIUM);
        etc.getInstance().getLoader().addListener(
                PluginLoader.Hook.SERVERCOMMAND, listener, this, PluginListener.Priority.MEDIUM);

        etc.getInstance().addCommand("/fixes", "- Show ServerFixes status");
        etc.getInstance().addCommand("/spawnradius", "<blocks> - Set the spawn-protection radius");
        etc.getInstance().addCommand("/firespread", "<on|off> - Toggle fire spread protection");
        etc.getInstance().addCommand("/lightingfix", "<on|off> - Toggle the lighting auto-fix (ops only)");
        etc.getInstance().addCommand("/sleep", "- Vote to sleep through the night, or <on|off> to toggle it (ops only)");
        etc.getInstance().addCommand("/worlddownload", "<host> <port> - Zip and send the world to a remote host:port");

        log.info("[ServerFixes] Enabled. " + LightingFix.getInstance().chunksNudged()
                + " chunk(s) already nudged for lighting.");
    }

    @Override
    public void disable() {
        etc.getInstance().removeCommand("/fixes");
        etc.getInstance().removeCommand("/spawnradius");
        etc.getInstance().removeCommand("/firespread");
        etc.getInstance().removeCommand("/lightingfix");
        etc.getInstance().removeCommand("/sleep");
        etc.getInstance().removeCommand("/worlddownload");

        LightingFix.getInstance().save();

        log.info("[ServerFixes] Disabled.");
    }
}
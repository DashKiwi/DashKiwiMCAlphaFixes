import java.util.logging.Logger;

public class FireSpreadFix {
    private static final Logger log = Logger.getLogger("Minecraft");
    private static final String CONFIG_FILE = "serverfixes-firespread.properties";

    private static FireSpreadFix instance;

    private boolean enabled = true;

    private FireSpreadFix() {
        load();
    }

    public static synchronized FireSpreadFix getInstance() {
        if (instance == null) {
            instance = new FireSpreadFix();
        }
        return instance;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        save();
    }

    public boolean shouldCancelIgnite(Block block, Player player) {
        if (!enabled) return false;
        return player == null;
    }

    // persistence

    private void save() {
        SettingsStore.getInstance().set("firespread.enabled", enabled);
        SettingsStore.getInstance().save();
    }

    private void load() {
        enabled = SettingsStore.getInstance().getBoolean("firespread.enabled", true);
    }
}
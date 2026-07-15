import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;
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

    public boolean shouldCancelFlow(Block blockFrom, Block blockTo) {
        if (!enabled) return false;
        return Block.Type.fromId(blockFrom.getType()) == Block.Type.Fire;
    }

    // persistence

    private void load() {
        File file = new File(CONFIG_FILE);
        if (!file.exists()) {
            save();
            return;
        }

        Properties props = new Properties();
        FileReader reader = null;
        try {
            reader = new FileReader(file);
            props.load(reader);
            enabled = Boolean.parseBoolean(props.getProperty("fire-spread-protection-enabled", "true"));
        } catch (Exception e) {
            log.warning("[ServerFixes] Failed to load " + CONFIG_FILE + ", using defaults: " + e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void save() {
        Properties props = new Properties();
        props.setProperty("fire-spread-protection-enabled", Boolean.toString(enabled));

        FileWriter writer = null;
        try {
            writer = new FileWriter(CONFIG_FILE);
            props.store(writer, "Fire spread settings");
        } catch (IOException e) {
            log.warning("[ServerFixes] Failed to save " + CONFIG_FILE + ": " + e);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
}
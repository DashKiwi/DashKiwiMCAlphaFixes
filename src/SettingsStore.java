import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Logger;

public class SettingsStore {
    private static final Logger log = Logger.getLogger("Minecraft");
    private static final String FILE = "serverfixes-settings.properties";

    private static SettingsStore instance;

    private final Properties props = new Properties();

    private SettingsStore() {
        load();
    }

    public static synchronized SettingsStore getInstance() {
        if (instance == null) {
            instance = new SettingsStore();
        }
        return instance;
    }

    public synchronized String get(String key, String def) {
        return props.getProperty(key, def);
    }

    public synchronized boolean getBoolean(String key, boolean def) {
        return Boolean.parseBoolean(props.getProperty(key, Boolean.toString(def)));
    }

    public synchronized int getInt(String key, int def) {
        try {
            return Integer.parseInt(props.getProperty(key, Integer.toString(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public synchronized void set(String key, String value) {
        props.setProperty(key, value);
    }

    public synchronized void set(String key, boolean value) {
        set(key, Boolean.toString(value));
    }

    public synchronized void set(String key, int value) {
        set(key, Integer.toString(value));
    }

    private void load() {
        File file = new File(FILE);
        if (!file.exists()) return;

        FileReader reader = null;
        try {
            reader = new FileReader(file);
            props.load(reader);
        } catch (IOException e) {
            log.warning("[ServerFixes] Failed to load " + FILE + ": " + e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    public synchronized void save() {
        FileWriter writer = null;
        try {
            writer = new FileWriter(FILE);
            props.store(writer, "ServerFixes settings");
        } catch (IOException e) {
            log.warning("[ServerFixes] Failed to save " + FILE + ": " + e);
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
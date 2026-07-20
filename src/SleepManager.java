import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;


public class SleepManager {
    private static final Logger log = Logger.getLogger("Minecraft");

    // Minecraft's day/night cycle is 24000 ticks long. This window (roughly
    // matching the vanilla "can sleep in a bed" window introduced later) is
    // used as the definition of "night" for this command.
    private static final long DAY_LENGTH = 24000L;
    private static final long NIGHT_START = 12500L;
    private static final long NIGHT_END = 23500L;

    private static SleepManager instance;

    private boolean enabled = true;

    // Player names (lowercase) currently "asleep" for tonight.
    private final Set<String> sleeping = Collections.synchronizedSet(new HashSet<String>());

    private SleepManager() {
        load();
    }

    public static synchronized SleepManager getInstance() {
        if (instance == null) {
            instance = new SleepManager();
        }
        return instance;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            sleeping.clear();
        }
        SettingsStore.getInstance().set("sleep.enabled", enabled);
        SettingsStore.getInstance().save();
    }

    private void load() {
        enabled = SettingsStore.getInstance().getBoolean("sleep.enabled", true);
    }

    public void handleSleep(Player player) {
        if (player == null) {
            return; // console can't sleep
        }

        if (!enabled) {
            player.sendMessage("/sleep is currently disabled on this server.");
            return;
        }

        long time = etc.getServer().getTime() % DAY_LENGTH;

        if (!isNight(time)) {
            // New day (or never voted) make sure stale votes don't linger.
            sleeping.clear();
            player.sendMessage("You can only sleep at night.");
            return;
        }

        String key = player.getName().toLowerCase();
        if (!sleeping.add(key)) {
            player.sendMessage("You're already sleeping.");
            return;
        }

        int online = onlineCount();
        int asleep = sleeping.size();

        if (asleep >= online) {
            skipNight();
        } else {
            etc.getServer().messageAll(player.getName() + " is now sleeping (" + asleep + "/" + online + ").");
        }
    }

    public void onDisconnect(Player player) {
        if (player == null) return;

        boolean removed = sleeping.remove(player.getName().toLowerCase());
        if (!removed) return;

        // That player leaving might have been the only one left to vote.
        int online = onlineCount();
        int asleep = sleeping.size();
        if (online > 0 && asleep >= online) {
            skipNight();
        }
    }

    private void skipNight() {
        long time = etc.getServer().getTime();
        long nextMorning = ((time / DAY_LENGTH) + 1) * DAY_LENGTH;
        etc.getServer().setTime(nextMorning);
        sleeping.clear();
        etc.getServer().messageAll("Everyone is asleep... skipping to morning!");
        log.info("[ServerFixes] Night skipped via /sleep vote.");
    }

    private int onlineCount() {
        List<Player> players = etc.getServer().getPlayerList();
        return players == null ? 0 : players.size();
    }

    private static boolean isNight(long timeOfDay) {
        return timeOfDay >= NIGHT_START && timeOfDay <= NIGHT_END;
    }
}

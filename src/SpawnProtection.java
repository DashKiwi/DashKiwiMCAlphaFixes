import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Protects the area around world spawn two ways:
 *
 *  1) Cancels creeper explosions that go off within radius+buffer of spawn (see
 *     shouldCancelExplosion) - no block damage, but see the class-level caveat below
 *     about why that also blocks splash damage/knockback with hMod's API.
 *  2) Cancels ordinary player block breaking/placing within radius of spawn (see
 *     shouldBlockBreak / shouldBlockPlace), the same way vanilla spawn protection
 *     works in later Minecraft versions. Players with the bypass permission (or
 *     ops) are exempt, so staff can still build at spawn.
 *
 * Both checks are always measured against the server's actual current spawn point
 * (etc.getServer().getSpawnLocation()), not a hardcoded coordinate - if spawn is
 * ever moved with a vanilla command, the protected zone moves with it.
 *
 * A note on the explosion side specifically: hMod's EXPLODE hook only fires once per
 * explosion, at its origin block, and only lets a listener allow or fully cancel the
 * whole thing (see OExplosion.java in hMod's own source: it calls the hook before any
 * block or entity damage happens, and a "true" return skips both the block-destruction
 * pass and the player/mob damage-and-knockback pass). There's no per-block block list
 * to filter the way Bukkit's EntityExplodeEvent gives you. Block.getStatus() tells us
 * what caused it: 1 = TNT, 2 = creeper. So cancelling a creeper blast near spawn also
 * prevents its splash damage/knockback to players in the zone, not just block damage -
 * there's no way around that with this API; see README.md for more on this tradeoff.
 * The block break/place side doesn't have this problem - onBlockBreak/onBlockPlace
 * cancel exactly the one thing they're named for.
 */
public class SpawnProtection {
    private static final Logger log = Logger.getLogger("Minecraft");
    private static final String CONFIG_FILE = "serverfixes-spawn.properties";
    private static final int CREEPER_STATUS = 2;
    private static final int TNT_STATUS = 1;
    private static final String BYPASS_PERMISSION = "/spawnbypass";
    private static final long WARNING_COOLDOWN_MS = 3000; // don't spam chat on repeated digging

    private static SpawnProtection instance;

    private boolean explosionProtectionEnabled = true;
    private boolean blockProtectionEnabled = true;
    private int radius = 16;  // blocks, horizontal, from world spawn
    private int buffer = 6;   // extra margin so blasts starting just outside are still caught

    // player name (lowercase) -> last time we warned them about spawn protection,
    // so repeated digging against a protected block doesn't spam their chat.
    private final ConcurrentHashMap<String, Long> lastWarned = new ConcurrentHashMap<String, Long>();

    private SpawnProtection() {
        load();
    }

    public static synchronized SpawnProtection getInstance() {
        if (instance == null) {
            instance = new SpawnProtection();
        }
        return instance;
    }

    public boolean isExplosionProtectionEnabled() {
        return explosionProtectionEnabled;
    }

    public boolean isBlockProtectionEnabled() {
        return blockProtectionEnabled;
    }

    public int getRadius() {
        return radius;
    }

    public int getBuffer() {
        return buffer;
    }

    public void setRadius(int radius) {
        this.radius = Math.max(0, radius);
        save();
    }

    public void setBuffer(int buffer){
        this.buffer = Math.max(0, buffer);
        save();
    }

    public boolean shouldCancelExplosion(Block block) {
        if (!explosionProtectionEnabled) return false;
        int status = block.getStatus();
        if (status != CREEPER_STATUS && status != TNT_STATUS) return false;

        double distSq = horizontalDistanceSqFromSpawn(block.getX(), block.getZ());
        double maxDist = radius + buffer;

        return distSq <= maxDist * maxDist;
    }

    public boolean shouldBlockBreak(Player player, Block block) {
        return shouldBlockAction(player, block.getX(), block.getZ());
    }

    public boolean shouldBlockPlace(Player player, Block blockPlaced) {
        return shouldBlockAction(player, blockPlaced.getX(), blockPlaced.getZ());
    }

    private boolean shouldBlockAction(Player player, int x, int z) {
        if (!blockProtectionEnabled) return false;
        if (canBypass(player)) return false;

        double distSq = horizontalDistanceSqFromSpawn(x, z);
        if (distSq > (double) radius * radius) return false;

        warn(player);
        return true;
    }

    private double horizontalDistanceSqFromSpawn(int x, int z) {
        Location spawn = etc.getServer().getSpawnLocation();
        double dx = (x + 0.5) - spawn.x;
        double dz = (z + 0.5) - spawn.z;
        return dx * dx + dz * dz;
    }

    private static boolean canBypass(Player player) {
        return player.isAdmin() || player.canUseCommand(BYPASS_PERMISSION);
    }

    private void warn(Player player) {
        String key = player.getName().toLowerCase();
        long now = System.currentTimeMillis();
        Long last = lastWarned.get(key);

        if (last != null && now - last < WARNING_COOLDOWN_MS) return;

        lastWarned.put(key, now);
        player.sendMessage("This area is spawn-protected.");
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
            explosionProtectionEnabled = Boolean.parseBoolean(props.getProperty("explosion-protection-enabled", "true"));
            blockProtectionEnabled = Boolean.parseBoolean(props.getProperty("block-protection-enabled", "true"));
            radius = Integer.parseInt(props.getProperty("radius", "16"));
            buffer = Integer.parseInt(props.getProperty("buffer", "6"));
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
        props.setProperty("explosion-protection-enabled", Boolean.toString(explosionProtectionEnabled));
        props.setProperty("block-protection-enabled", Boolean.toString(blockProtectionEnabled));
        props.setProperty("radius", Integer.toString(radius));
        props.setProperty("buffer", Integer.toString(buffer));

        FileWriter writer = null;
        try {
            writer = new FileWriter(CONFIG_FILE);
            props.store(writer, "Spawn-protection settings");
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
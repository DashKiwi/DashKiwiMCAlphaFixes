import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;


public class LightingFix {
    private static final Logger log = Logger.getLogger("Minecraft");
    private static final String CHUNKS_FILE = "serverfixes-relit-chunks.dat";
    // Sample offsets within a chunk (0-15 on each axis). This must include 15,
    // not just multiples of the old step size - a chunk is only ever nudged
    // once, so if the far edge (offset 13-15) is never sampled here, that
    // strip along the edge of every single chunk never gets relit at all.
    private static final int[] SAMPLE_OFFSETS = {0, 4, 8, 12, 15};

    private static LightingFix instance;

    private boolean enabled = true;

    // Chunk coordinates already nudged, encoded as (chunkX << 32) ^ chunkZ.
    private final Set<Long> relitChunks = Collections.synchronizedSet(new HashSet<Long>());

    private LightingFix() {
        load();
    }

    public static synchronized LightingFix getInstance() {
        if (instance == null) {
            instance = new LightingFix();
        }
        return instance;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        SettingsStore.getInstance().set("lighting.enabled", enabled);
        SettingsStore.getInstance().save();
    }

    public int chunksNudged() {
        return relitChunks.size();
    }

    public void nudgeAround(Player player) {
        if (!enabled) return;

        int pcx = ((int) Math.floor(player.getX())) >> 4;
        int pcz = ((int) Math.floor(player.getZ())) >> 4;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                nudgeChunk(pcx + dx, pcz + dz);
            }
        }
    }

    // implementation

    private void nudgeChunk(int chunkX, int chunkZ) {
        long key = key(chunkX, chunkZ);
        if (relitChunks.contains(key)) return;

        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;

        if (!etc.getServer().isChunkLoaded(baseX, 64, baseZ)) return; // try again once it's loaded

        relitChunks.add(key);

        for (int ox : SAMPLE_OFFSETS) {
            for (int oz : SAMPLE_OFFSETS) {
                int wx = baseX + ox;
                int wz = baseZ + oz;
                int topY = etc.getServer().getHighestBlockY(wx, wz);
                if (topY <= 0) continue;

                Block b = etc.getServer().getBlockAt(wx, topY, wz);
                b.update(); // re-set the block in place -> forces a real relight of this column
            }
        }
    }

    private static long key(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
    }

    // persistence

    private void load() {
        enabled = SettingsStore.getInstance().getBoolean("lighting.enabled", true);

        File file = new File(CHUNKS_FILE);
        if (!file.exists()) return;

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split(",");
                if (parts.length != 2) continue;

                int cx = Integer.parseInt(parts[0]);
                int cz = Integer.parseInt(parts[1]);
                relitChunks.add(key(cx, cz));
            }
        } catch (Exception e) {
            log.warning("[ServerFixes] Failed to load " + CHUNKS_FILE + ": " + e);
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
        PrintWriter writer = null;
        try {
            writer = new PrintWriter(new FileWriter(CHUNKS_FILE));
            synchronized (relitChunks) {
                for (long key : relitChunks) {
                    int cx = (int) (key >> 32);
                    int cz = (int) key;
                    writer.println(cx + "," + cz);
                }
            }
        } catch (IOException e) {
            log.warning("[ServerFixes] Failed to save " + CHUNKS_FILE + ": " + e);
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }
}

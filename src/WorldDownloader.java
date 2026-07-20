import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;


public class WorldDownloader {
    private static final Logger log = Logger.getLogger("Minecraft");
    private static final String DEFAULT_WORLD_FOLDER = "world";
    private static final int CONNECT_TIMEOUT_MS = 15000;

    private static WorldDownloader instance;

    private final AtomicBoolean transferInProgress = new AtomicBoolean(false);

    private WorldDownloader() {
    }

    public static synchronized WorldDownloader getInstance() {
        if (instance == null) {
            instance = new WorldDownloader();
        }
        return instance;
    }

    public void beginTransfer(final Player sender, final String hostArg, final int port) {
        if (!transferInProgress.compareAndSet(false, true)) {
            reply(sender, "A world download is already in progress, wait for it to finish first.");
            return;
        }

        final String host = stripBrackets(hostArg); // allow "[::1]" style IPv6 input
        final File worldDir = new File(resolveWorldFolderName());

        if (!worldDir.isDirectory()) {
            transferInProgress.set(false);
            reply(sender, "Can't find the world folder ('" + worldDir.getPath() + "'), aborting.");
            return;
        }

        reply(sender, "Connecting to " + host + ":" + port + " ...");

        Thread thread = new Thread("WorldDownloader") {
            @Override
            public void run() {
                try {
                    runTransfer(sender, host, port, worldDir);
                } finally {
                    transferInProgress.set(false);
                }
            }
        };
        thread.setDaemon(true);
        thread.start();
    }

    private void runTransfer(Player sender, String host, int port, File worldDir) {
        Socket socket = null;
        long startedAt = System.currentTimeMillis();
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);

            OutputStream rawOut = new BufferedOutputStream(socket.getOutputStream(), 64 * 1024);
            ZipOutputStream zipOut = new ZipOutputStream(rawOut);
            zipOut.setLevel(java.util.zip.Deflater.DEFAULT_COMPRESSION);

            reply(sender, "Connected. Zipping and sending '" + worldDir.getName() + "' ...");

            long[] totals = new long[]{0L, 0L}; // {filesSent, bytesSent}
            addDirectoryToZip(worldDir, worldDir.getName(), zipOut, totals);

            zipOut.finish();
            zipOut.flush();

            double seconds = (System.currentTimeMillis() - startedAt) / 1000.0;
            reply(sender, "Done. Sent " + totals[0] + " file(s), "
                    + humanReadableBytes(totals[1]) + " in " + String.format("%.1f", seconds) + "s.");
            log.info("[ServerFixes] World download to " + host + ":" + port + " finished: "
                    + totals[0] + " files, " + totals[1] + " bytes.");
        } catch (IOException e) {
            reply(sender, "World download failed: " + e.getMessage());
            log.warning("[ServerFixes] World download to " + host + ":" + port + " failed: " + e);
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void addDirectoryToZip(File dir, String zipPath, ZipOutputStream zipOut, long[] totals) throws IOException {
        File[] children = dir.listFiles();
        if (children == null) return;

        byte[] buffer = new byte[64 * 1024];

        for (File child : children) {
            String childZipPath = zipPath + "/" + child.getName();

            if (child.isDirectory()) {
                addDirectoryToZip(child, childZipPath, zipOut, totals);
                continue;
            }

            zipOut.putNextEntry(new ZipEntry(childZipPath));

            InputStream in = null;
            try {
                in = new FileInputStream(child);
                int read;
                while ((read = in.read(buffer)) != -1) {
                    zipOut.write(buffer, 0, read);
                    totals[1] += read;
                }
            } finally {
                if (in != null) {
                    in.close();
                }
            }

            zipOut.closeEntry();
            totals[0]++;
        }
    }

    private String resolveWorldFolderName() {
        File propsFile = new File("server.properties");
        if (!propsFile.isFile()) return DEFAULT_WORLD_FOLDER;

        Properties props = new Properties();
        FileReader reader = null;
        try {
            reader = new FileReader(propsFile);
            props.load(reader);
            return props.getProperty("level-name", DEFAULT_WORLD_FOLDER);
        } catch (IOException e) {
            return DEFAULT_WORLD_FOLDER;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static String stripBrackets(String host) {
        if (host.length() >= 2 && host.startsWith("[") && host.endsWith("]")) {
            return host.substring(1, host.length() - 1);
        }
        return host;
    }

    private static String humanReadableBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format("%.1f MB", mb);
        return String.format("%.1f GB", mb / 1024.0);
    }

    private static void reply(Player sender, String message) {
        if (sender == null) {
            Logger.getLogger("Minecraft").info("[ServerFixes] " + message);
        } else {
            sender.sendMessage(message);
        }
    }
}

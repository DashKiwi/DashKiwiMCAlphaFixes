public class FixesListener extends PluginListener {

    // Explosion / block-protection / lighting hooks

    @Override
    public boolean onExplode(Block block) {
        return SpawnProtection.getInstance().shouldCancelExplosion(block);
    }

    @Override
    public boolean onIgnite(Block block, Player player) {
        return FireSpreadFix.getInstance().shouldCancelIgnite(block, player);
    }

    @Override
    public boolean onBlockBreak(Player player, Block block) {
        return SpawnProtection.getInstance().shouldBlockBreak(player, block);
    }

    @Override
    public boolean onBlockPlace(Player player, Block blockPlaced, Block blockClicked, Item itemInHand) {
        return SpawnProtection.getInstance().shouldBlockPlace(player, blockPlaced);
    }

    @Override
    public void onLogin(Player player) {
        LightingFix.getInstance().nudgeAround(player);
    }

    @Override
    public void onDisconnect(Player player) {
        SleepManager.getInstance().onDisconnect(player);
    }

    @Override
    public void onPlayerMove(Player player, Location from, Location to) {
        int fromCX = ((int) Math.floor(from.x)) >> 4;
        int fromCZ = ((int) Math.floor(from.z)) >> 4;
        int toCX = ((int) Math.floor(to.x)) >> 4;
        int toCZ = ((int) Math.floor(to.z)) >> 4;

        if (fromCX == toCX && fromCZ == toCZ) return; // still in the same chunk

        LightingFix.getInstance().nudgeAround(player);
    }

    //
    // Commands
    //

    @Override
    public boolean onCommand(Player player, String[] split) {
        String cmd = split[0];

        if (cmd.equalsIgnoreCase("/fixes")) {
            handleFixes(player, split);
            return true;
        }
        if (cmd.equalsIgnoreCase("/spawnradius")) {
            handleSpawnRadius(player, split);
            return true;
        }
        if (cmd.equalsIgnoreCase("/firespread")) {
            handleFireSpread(player, split);
            return true;
        }
        if (cmd.equalsIgnoreCase("/sleep")) {
            SleepManager.getInstance().handleSleep(player);
            return true;
        }
        if (cmd.equalsIgnoreCase("/worlddownload")) {
            handleWorldDownload(player, split);
            return true;
        }
        return false;
    }

    @Override
    public boolean onConsoleCommand(String[] split) {
        if (split.length == 0) return false;
        String cmd = split[0];

        if (cmd.equalsIgnoreCase("/fixes")) {
            handleFixes(null, split);
            return true;
        }
        if (cmd.equalsIgnoreCase("/spawnradius")) {
            handleSpawnRadius(null, split);
            return true;
        }
        if (cmd.equalsIgnoreCase("/firespread")) {
            handleFireSpread(null, split);
            return true;
        }
        if (cmd.equalsIgnoreCase("/worlddownload")) {
            handleWorldDownload(null, split);
            return true;
        }
        return false;
    }

    //
    // Implementation
    //

    private void handleFixes(Player sender, String[] split) {
        SpawnProtection spawn = SpawnProtection.getInstance();
        LightingFix lighting = LightingFix.getInstance();

        reply(sender, "ServerFixes status:");
        reply(sender, " creeper explosion protection: " + (spawn.isExplosionProtectionEnabled() ? "on" : "off")
                + " (radius " + spawn.getRadius() + " +" + spawn.getBuffer() + " buffer)");
        reply(sender, " block break/place protection: " + (spawn.isBlockProtectionEnabled() ? "on" : "off")
                + " (radius " + spawn.getRadius() + ")");
        reply(sender, " lighting auto-fix: " + (lighting.isEnabled() ? "on" : "off")
                + " (" + lighting.chunksNudged() + " chunks nudged so far)");
        reply(sender, " fire spread protection: " + (FireSpreadFix.getInstance().isEnabled() ? "on" : "off"));
    }

    private void handleSpawnRadius(Player sender, String[] split) {
        if (!canUse(sender, "/spawnradius")) {
            deny(sender);
            return;
        }

        if (split.length < 2) {
            reply(sender, "Usage: /spawnradius <blocks>");
            return;
        }

        try {
            SpawnProtection spawn = SpawnProtection.getInstance();
            spawn.setRadius(Integer.parseInt(split[1]));
            reply(sender, "Creeper spawn-protection radius set to " + spawn.getRadius()
                    + " (+" + spawn.getBuffer() + " buffer).");
        } catch (NumberFormatException e) {
            reply(sender, "'" + split[1] + "' isn't a number.");
        }
    }

    private void handleFireSpread(Player sender, String[] split) {
        if (!canUse(sender, "/firespread")) {
            deny(sender);
            return;
        }

        if (split.length < 2) {
            reply(sender, "Usage: /firespread <on|off>");
            return;
        }

        FireSpreadFix fire = FireSpreadFix.getInstance();
        if (split[1].equalsIgnoreCase("on")) {
            fire.setEnabled(true);
            reply(sender, "Fire spread protection enabled.");
        } else if (split[1].equalsIgnoreCase("off")) {
            fire.setEnabled(false);
            reply(sender, "Fire spread protection disabled.");
        } else {
            reply(sender, "Usage: /firespread <on|off>");
        }
    }

    private void handleWorldDownload(Player sender, String[] split) {
        if (!canUse(sender, "/worlddownload")) {
            deny(sender);
            return;
        }

        if (split.length < 3) {
            reply(sender, "Usage: /worlddownload <host> <port>");
            reply(sender, "  host can be an IPv4/IPv6 address or hostname of the machine");
            reply(sender, "  that's listening for the incoming connection (e.g. running");
            reply(sender, "  'nc -l <port> > world.zip' or similar) on the other network.");
            return;
        }

        String host = split[1];
        int port;
        try {
            port = Integer.parseInt(split[2]);
        } catch (NumberFormatException e) {
            reply(sender, "'" + split[2] + "' isn't a valid port number.");
            return;
        }
        if (port < 1 || port > 65535) {
            reply(sender, "Port must be between 1 and 65535.");
            return;
        }

        WorldDownloader.getInstance().beginTransfer(sender, host, port);
    }

    private static boolean canUse(Player sender, String command) {
        // Console (sender == null) always allowed.
        return sender == null || sender.canUseCommand(command);
    }

    private static void deny(Player sender) {
        reply(sender, "You do not have permission to use this command.");
    }

    private static void reply(Player sender, String message) {
        if (sender == null) {
            java.util.logging.Logger.getLogger("Minecraft").info("[ServerFixes] " + message);
        } else {
            sender.sendMessage(message);
        }
    }
}
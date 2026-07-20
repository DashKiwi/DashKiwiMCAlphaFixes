# ServerFixes

An hMod plugin for old Minecraft servers (built/tested against Alpha 1.2.6) that
bundles several unrelated fixes and utilities:

1. **Spawn protection** — creeper explosions *and* ordinary player block
   breaking/placing near world spawn no longer affect the world.
2. **Automatic lighting fix** — automates the "place/break a block nearby" trick
   that already fixes the lighting glitch you're seeing, so it happens on its own
   as players explore instead of needing to be manually triggered.
3. **Fire spread protection** — stops fire from spreading on its own
   (block-to-block, or lava setting things alight), server-wide. Deliberate
   player ignitions (flint & steel, etc.) still work.
4. **`/sleep` vote** — a chat-command stand-in for beds (which don't exist yet in
   Alpha 1.2.6). When everyone online has typed `/sleep` during the night, time
   skips straight to morning.
5. **`/worlddownload`** — zips the world save folder and pushes it out over a
   plain TCP socket to a host/port you give it, so you can grab a copy of the
   world onto another machine on a different network without needing SSH/SCP
   access to the server itself.

## Why this exists / how it works

Both fixes were built by reading hMod's actual source
(`traitor/Minecraft-Server-Mod` on GitHub, the codebase CanaryMod.jar's plugin
API is compiled from), because hMod is a lot less permissive than Bukkit here and
it matters for the design below.

**Spawn protection** is measured against the server's actual current spawn point
(`etc.getServer().getSpawnLocation()`), not a hardcoded coordinate. if spawn is
ever moved, the protected zone moves with it. It works two ways:

- *Creepers.* hMod exposes exactly one hook for explosions,
  `onExplode(Block block)`, fired once per explosion with the block at the
  explosion's *origin*. hMod's own explosion code (`OExplosion.java`) calls this
  before any damage happens, and a `true` return cancels the **entire**
  explosion, both the block destruction and the player/mob
  damage-and-knockback pass. `block.getStatus()` tells creeper (`2`) apart from
  TNT (`1`). Unlike Bukkit's `EntityExplodeEvent`, there's no per-block block
  list to filter, the only lever is "let this explosion happen" or "cancel it
  completely." So cancelling a creeper blast within the protected radius also
  blocks its splash damage/knockback to players caught in the zone, not just
  block damage, there's no way around that with this API. If you want players
  to still take damage but only spare blocks, that needs a source-level patch
  to hMod itself, not a plugin.
- *Players.* This one doesn't have that limitation, `onBlockBreak(Player,
  Block)` and `onBlockPlace(Player, Block, Block, Item)` fire per-action and
  cancel exactly that break or placement (hMod's `OItemBlock.java` shows a
  cancelled placement is undone and the item refunded). Players with the
  `/spawnbypass` permission (or ops) are exempt, so staff can still build at
  spawn. A player who's denied gets a one-line reminder, throttled to once
  every 3 seconds so repeated digging doesn't spam their chat.

**Lighting fix.** hMod has no "recalculate lighting" call and no "chunk
generated" hook at all, that's below what the plugin API exposes. What it does
expose is `Block.update()`, which runs the same `setBlock` code path that fires
whenever a player places or breaks a block, exactly the trick you already
found. This plugin automates it: whenever a player walks into a new chunk, it
re-sets a sparse grid of surface blocks in it and its neighbors (each one
relights a full vertical column, so it doesn't need to touch every block), once
per chunk, ever. It's a workaround riding on the same mechanism as the manual
fix, not a guaranteed root-cause fix, see the caveats below.

**Fire spread protection.** Fire spreading uses a hook,
`Hook.IGNITE`, fired as `onIgnite(Block block, Player player)` whenever any
block is about to catch fire, whether that's fire spreading to a neighboring
flammable block, lava igniting something nearby, or a player using flint &
steel. `player` is only non-null when a player directly caused it. This plugin
now hooks `IGNITE` and cancels every ignition where `player == null` while
enabled, that's the "spreading on its own" case, without blocking players who
deliberately light something with flint & steel.

**`/sleep`.** Alpha 1.2.6 has no beds (added later, in Beta 1.7), so there's no
vanilla sleep event to hook. This is a plain chat-command vote instead: typing
`/sleep` only works while `etc.getServer().getTime()` is in the game's night
window; it adds you to a "sleeping" set and tells everyone how many are in so
far. Once everyone currently online is in the set, time is fast-forwarded to
the next morning (`setTime` to the next multiple of the 24000-tick day length)
and the set is cleared. Nothing skips the night unless *everybody* online has
voted. Once it's day, `/sleep` naturally goes back to telling you to wait for
night, no separate "already skipped tonight" flag needed. A player
disconnecting is dropped from the vote and the remaining players are
re-checked immediately, in case that was the last holdout.

**`/worlddownload`.** Zips the world save folder (its name is read from
`level-name` in `server.properties`, defaulting to `world` if that's missing)
and streams it straight into a TCP socket connected out to the host/port you
give the command, no external libraries, no listening port opened on the
server side, no encryption or authentication either (see the command section
below for what that means in practice).

## Install

1. Drop `ServerFixes.jar` into your server's plugin folder.
2. Add it to your plugin list in `server.properties`, e.g.:
   ```
   plugins=OnlineModeFix,DbanMod,ServerFixes
   ```
3. Restart the server.

A couple files are created next to the server jar the first time it runs:

- `serverfixes-settings.properties` — serverfixes settings file.
- `serverfixes-relit-chunks.dat` — chunks already nudged for lighting, so it's
  never redone.

`/sleep` votes and in-progress `/worlddownload` transfers are purely in-memory
and don't persist across a restart.

To let a group build inside the spawn-protected area, give it the
`/spawnbypass` permission via your usual hMod groups setup (ops always bypass
regardless).

## Commands

- `/fixes` — shows current status of spawn protection, the lighting fix,
  fire-spread protection, and whether `/sleep` voting is enabled (also usable
  from console).
- `/spawnradius <blocks>` — sets the spawn-protection radius, used by both the
  explosion check and the block break/place check (needs the `/spawnradius`
  permission, or console).
- `/firespread <on|off>` — toggles fire-spread protection (needs the
  `/firespread` permission, or console). Default is on.
- `/lightingfix <on|off>` — toggles the automatic lighting fix (needs the
  `/lightingfix` permission, or console). Default is on. Turn it off if you
  want to confirm whether it's actually the source of a lighting problem
  you're seeing.
- `/sleep` — votes to sleep through the night. Any player, no special
  permission needed. Only works at night; needs every player currently online
  to type it before time skips to morning.
- `/sleep <on|off>` — toggles whether the `/sleep` vote command is usable at
  all (needs the `/sleeptoggle` permission, or console). Default is on. When
  off, players typing `/sleep` are told voting is disabled.
- `/worlddownload <host> <port>` — zips the world and sends it to
  `host:port` over a plain TCP socket. **Ops only** (needs the
  `/worlddownload` permission, or console), since it hands over a full copy of
  the world. `host` can be a hostname, IPv4 address, or IPv6 address (with or
  without `[brackets]`).

  You need something listening on the other end *before* running the command,
  since the server connects out to you, e.g. on the receiving machine:
  ```
  nc -l 4444 > world.zip        # or: nc -l -p 4444 > world.zip, depending on your nc
  ```
  then in-game or console: `/worlddownload your.ipv6.address 4444`.

  **Security note:** this transfer is plain TCP, not encrypted or
  authenticated. Anyone who can see that network traffic in flight, or who
  connects to that port before you do, could intercept or receive the world
  instead of you. Treat the host/port as a one-time secret: stand up the
  listener right before you run the command, use it once, and don't leave it
  sitting open. If you need this over an untrusted network, run it through an
  SSH tunnel (`ssh -R` from the server, or `-L`/`-D` from your end) rather than
  exposing the raw port directly, that's genuinely more secure than anything
  a plugin can do on its own without pulling in a TLS library.

## Notes / things worth knowing

- **Cancelling a creeper blast near spawn blocks player damage too**, not just
  block damage, see the explanation above. If that's not what you want, say so
  and we can look at a hMod source patch instead of a plugin.
- **Block break/place protection doesn't have that problem**, it only ever
  stops the one break or placement it fires for, nothing else.
- **The lighting fix only touches chunks a player has actually walked near.**
  Chunks nobody visits won't get nudged, which is fine since nobody will see the
  glitch there either.
- **The lighting fix is a workaround, not a guaranteed fix.** It rides on the
  same mechanism as the manual "update a block nearby" trick, so it should catch
  the same class of glitches you're already seeing get fixed by hand. If you're
  hitting a different lighting issue that doesn't respond to a manual block
  update, this won't help with that particular case.
- The explosion buffer (default 6 blocks, `buffer` in
  `serverfixes-spawn.properties`) only applies to the creeper-explosion check,
  it exists because that hook only fires for the blast's center point, and a
  creeper that pops just outside the radius can still fling blocks or knock a
  player further in. The block break/place check doesn't need a buffer since
  it's checked against the exact block being touched.

## Building from source

```
mvn package
```

You'll need `lib/CanaryMod.jar` (the hMod-compatible API jar), grab it from the
[OnlineModeFix](https://github.com/craftycodie/OnlineModeFix) repo, since it's
the same API surface this mod compiles against.


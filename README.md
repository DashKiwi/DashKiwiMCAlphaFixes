# ServerFixes

An hMod plugin for old Minecraft servers (built/tested against Alpha 1.2.6) that
bundles two unrelated fixes:

1. **Spawn protection** — creeper explosions *and* ordinary player block
   breaking/placing near world spawn no longer affect the world.
2. **Automatic lighting fix** — automates the "place/break a block nearby" trick
   that already fixes the lighting glitch you're seeing, so it happens on its own
   as players explore instead of needing to be manually triggered.

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

## Install

1. Drop `ServerFixes.jar` into your server's plugin folder.
2. Add it to your plugin list in `server.properties`, e.g.:
   ```
   plugins=OnlineModeFix,DbanMod,ServerFixes
   ```
3. Restart the server.

Two files are created next to the server jar the first time it runs:

- `serverfixes-spawn.properties` — spawn-protection settings.
- `serverfixes-relit-chunks.dat` — chunks already nudged for lighting, so it's
  never redone.

To let a group build inside the spawn-protected area, give it the
`/spawnbypass` permission via your usual hMod groups setup (ops always bypass
regardless).

## Commands

- `/fixes` — shows current status of all three protections (also usable from
  console).
- `/spawnradius <blocks>` — sets the spawn-protection radius, used by both the
  explosion check and the block break/place check (needs the `/spawnradius`
  permission, or console).

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


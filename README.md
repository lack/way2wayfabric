# way2wayfabric

[![Modrinth Version](https://img.shields.io/modrinth/v/lO0vzQUy?color=informational&label=Modrinth&logo=modrinth)](https://modrinth.com/mod/way2wayfabric)

Waystone -> Xaero's Minimap Waypoint sync for fabric

Since Xaero's Minimap doesn't have an official API,
it's unlikely that the Waystones mod will support
automatic integration; so how do we get automatic
waypoints in Xaero's Minimap? Via a bridge mod like
this one. There are others for Forge (w2w and
waymaker), but none for Fabric, which is why I threw
this together.

## Usage

No configuration needed! Once the mod is installed, it
will automatically create waypoints in your minimap
for all discovered waystones.

If you accidentally delete one or more auto-generated
waypoints, they will all reappear in the current
waypoint set when you disconnect and reconnect.

For those who use multiple waypoint sets:

- Each newly discovered waystone is added to the
  currently active waypoint set.
- If you use multiple waypoint sets, ensure the right
  one is active before you activate a waystone.
- These waypoints can be moved to other waypoint sets
  after discovery, using the regular waypoint editing
  gui.

## Requirements

Required mods:

- [Xaero's Minimap](https://modrinth.com/mod/xaeros-minimap) (tested with 23.3.2)
  or [Xaero's Minimap fair-play](https://modrinth.com/mod/xaeros-minimap-fair)
- [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin)

Supported waystone mods:

- [Waystones](https://modrinth.com/mod/waystones) (tested with 11.3.1)
- [Fabric Waystones](https://modrinth.com/mod/fwaystones) (tested with 3.0.5)

Supports both waystone mods simultaneously (each mod's
waypoints will have a unique minimap symbol)

---

Inspired by:

- [Waystones2Waypoints (curseforge)](https://www.curseforge.com/minecraft/mc-mods/waystones2waypoints) (Forge 1.16)
- [Waystone Waypoint Maker](https://modrinth.com/mod/waymaker) (Forge 1.17+)


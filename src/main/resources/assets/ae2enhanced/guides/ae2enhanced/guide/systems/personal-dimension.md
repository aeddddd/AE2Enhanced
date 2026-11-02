---
navigation:
  title: Personal Dimension
  parent: systems.md
  position: 10
  icon: personal_dimension
item_ids: [personal_dimension, yellow_stripes_block_b]
---

# Personal Dimension

Every player can own a private, flat, single-biome dimension. The <ItemLink id="personal_dimension" /> **Personal Dimension Core** is the key to it.

## Entering and Leaving

- **Right-click a block**: teleport. Outside your dimension it records a return point and teleports you in (creating the dimension on first use); inside, it teleports you back to the return point.
- **Shift + right-click a block**: bind the entry point (only inside your own dimension).
- **Right-click air**: open the rules GUI.
- You cannot set spawn in a personal dimension; dying returns you to the overworld.

## Rules (per dimension)

- Mob Spawning: deny natural mob spawns (default off).
- Lock Weather: clears rain and thunder every tick, blocks snow/ice/lightning.
- Lock Time / Daylight Cycle: freeze world time at the configured value (default 6000).
- Flight: allow flying inside the dimension (creative always can).
- Movement Speed: walk/fly speed, clamped between 0.05 and 2.0 (default 0.1).
- No Flight Inertia: stop drifting when not moving while flying.

## Floor Preset

The floor generates at y=64 (config `personalDimension.floorY`) from the compatible JSON `default` preset. New chunks have no bedrock substrate. Open the management GUI and choose the **Preset** tab to preview the preset, edit road, road-line and platform colors, then apply and repaint with live remaining-chunk progress. The `/ae2e pd floor style`, `color` and `apply` commands remain available. Old bedrock is removed only in the two layers below the target floor. Region scanning is asynchronous; world edits, lighting and chunk packets are main-thread batches. Unloaded chunks resume on load, and task progress persists across restart.

## Team and Personal Modes

`/ae2e pd mode <auto|team|personal>` selects the default; `/ae2e pd enter [mode]` enters it. AUTO uses an FTB PLAYER team when present, otherwise your personal world. TEAM without a team gives an explicit message. Both worlds remain intact when switching.

FTB teams own team membership and MOD/OWNER management. The old invite/kick/setperm UI is removed; legacy whitelist data remains readable. Forge/FTB ranks control enter, build, interact, manage and bypass nodes. Interactions and return points are checked server-side. Personal owners or team MOD/OWNER can edit rules and floors. See [Commands and Config](systems/commands-config.md).

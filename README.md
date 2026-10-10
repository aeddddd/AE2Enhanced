# AE2Enhanced

AE2Enhanced is a late-game addon for **AE2 Unofficial Extended Life (AE2-UEL)** that introduces multiple massive multiblock structures, a powerful central automation interface, and a series of utility items for the endgame.

*Current version: 1.7.9.0*

[中文版本 (Chinese)](/README_zh.md)

## Core Items

### Omni Wireless Terminal

A universal wireless terminal that combines item viewing, crafting, and pattern encoding in one interface.

> **Note: The Omni Terminal is currently undergoing a complete rewrite.** Details are intentionally omitted here as they are subject to change.

### Central ME Interface

An advanced ME Interface that can remotely bind to target block entities, enabling true physical parallelism and native automation for external crafting devices.

The Central ME Interface is **extremely powerful**, allowing:
- **Unlimited, CPU-free** one-to-many parallel dispatch (just like pattern P2P).
- Default forced primary product return mode.
- **Automatic collection** of materials (*the Central ME Interface will attempt to collect all products at once!*).
- **Remote binding** — can link to target block entities at any distance.
- **Unlimited** number of binding targets.
- **Physical parallelism** — bind multiple identical devices to expand throughput.

In addition, there is another very powerful feature:

The Central ME Interface can **automatically adapt** to different bound block entities (Tiles) and complete complex crafting, just like packaged crafting, but without complex packagers/unpackagers. You only need to bind the target block entity, encode the recipe into a pattern, and automation is done easily!

(**Note: For recipes with primary + auxiliary materials, the primary material must be placed in the first slot of the pattern.**)

**Supported Mods and Devices:**

| Mod | Supported Devices |
|-----|-------------------|
| **Blood Magic** | Alchemy Table, Soul Forge, Blood Altar |
| **Thaumcraft 6** | Infusion Matrix, Crucible |
| **Astral Sorcery** | All tiers of Starlight Altars |
| **Actually Additions** | Empowerer (with Display Stands) |
| **Extended Crafting** | Combination Crafting Core, all tiers of Crafting Tables, Quantum Compressor, Ender Crafter |
| **Avaritia** | Extreme Crafting Table |
| **Botania** | Petal Apothecary, Mana Pool, Alfheim Portal, Runic Altar, Terrestrial Agglomeration Plate |
| **Bewitchment** | Witches' Cauldron, Spinning Wheel, Distillery |
| **Draconic Evolution** | Fusion Crafting Core (with injectors) |
| **Ender IO** | All machines (namespace-wide adapter) |
| **Thermal Expansion** | All machines (namespace-wide adapter) |
| **NuclearCraft** | All machines (Legacy & Overhauled, namespace-wide adapter) |
| **Tech Reborn** | 19 machine types (Alloy Smelter, Industrial Blast Furnace, Industrial Grinder, ...) |
| **Generic fallback** | Any block exposing `IItemHandler` on any side |

(All adaptations do not require manual player initiation; crafting will start automatically, including for the Infusion Matrix and Astral Sorcery. If crafting leaves behind returned items such as empty buckets, don't worry — the Central ME Interface will automatically collect them.)

Use the Universal Memory Card for binding.

**Virtual Parallel Cards (Tier 1–8)**: installing one into the Central ME Interface switches supported targets to pure virtual batch crafting, with parallelism from 8 up to `Long.MAX_VALUE`. Targets with no physical interaction space (e.g. crafting-table-like devices) run virtually even without a card.

### Smart Pattern Interface

Converts JEI recipes of a bound machine into **Smart Patterns** in bulk — one Smart Pattern holds many recipes, each individually toggleable/lockable, with JEI `[+]` filling, input/output rotation, and batch replacement. It is a pure manual configuration terminal and does not connect to the ME network.

---

## Multiblock Structures

- **Supercausal Assembly Hub** — a 344-block crafting array similar to LazyAE2's Large Molecular Assembler, allowing extreme parallelism (up to `Long.MAX_VALUE`) and automatic pattern uploading. Six upgrade modules available: Temporal Fold (parallel), Spacetime Dilation (speed), Energy Optimization (efficiency), Dimensional Fold (capacity), Auto-Upload, and Timeline Divergence.
- **Hyperdimensional Storage Nexus** — an unlimited-capacity storage structure (21 blocks). Item counts are tracked with `HugeCount` (long fast-path + BigInteger overflow), supporting items, fluids, Mekanism gases, and Thaumcraft essentia. Data is persisted to external files (base snapshot + write-ahead log + idle checkpoints), preventing save bloat; corruption falls back to a read-only safe mode.
- **Supercausal Computation Core** — a super crafting CPU structure (~860 blocks) hosting a managed pool of native AE2 `CraftingCPUCluster` instances: one persistent cluster plus dynamic clusters for concurrent orders, with idle clusters automatically reclaimed.

---

## Crafting Systems

### Black Hole Crafting

A unique crafting system that allows players to create a **Micro Singularity** and throw items into its event horizon to transmute them into desired materials.

**Warning**: The Micro Singularity only lasts for `300 seconds`. Right-click it to activate crafting. **Do not stand too close!!!**

Full **CraftTweaker** support:
```zenscript
import mods.ae2enhanced.BlackHole;
BlackHole.addRecipe(IItemStack output, IItemStack[] inputs);
// Example
BlackHole.addRecipe(<minecraft:obsidian>, [<minecraft:stone> * 8, <minecraft:diamond>]);
// Recipes can be removed by id (built-in or CT-added):
BlackHole.removeRecipe("stable_spacetime_manifold");
```

9 built-in recipes (Stable Spacetime Manifold, Differential Form Stabilizer, Conformal Invariant Charge, Smart Blank Pattern, and the Virtual Parallel Card tier-1 base plus tier-2~6 upgrade chain).

Also included: the **Micro Singularity Ritual** (throw 64 AE2 singularities + 4 Nether Stars, then right-click an ME Controller with a Nether Star to obtain a constrained Micro Singularity) and **Singularity Fuel** (Stable Spacetime Manifold / Differential Form Stabilizer extend singularity lifetime; Conformal Invariant Charge makes it permanent).

### Singularity Processing Chamber

A late-game single-block, high-parallelism processing machine. It aggregates recipes from Black Hole Crafting, the AE2 Inscriber (including merged multi-step chains), crystal charging/fluix aggregation/seed growth, and the AE2 Grinder — plus custom recipes via CraftTweaker:

```zenscript
import mods.ae2enhanced.SingularityChamber;
SingularityChamber.addRecipe(<minecraft:obsidian> * 2, [<minecraft:stone> * 64], 100);
SingularityChamber.removeRecipe("ct_...");
```

Inputs go into long-scale buffer slots (pipe or GUI); Virtual Parallel Cards set the parallel channel count and same-recipe batches merge into one job; speed cards shorten processing time. Output is injected into the ME network first, with an internal output buffer as fallback. Three redstone modes (ignore / high / low) are available in the GUI.

---

## Personal Dimension

The **Personal Dimension Core** gives every player their own pocket dimension:
- Right-click air to open the config GUI; right-click a block to teleport; Shift+right-click a block to bind an entry point (inside your own dimension only).
- Configurable rules: mob spawning, weather/time lock, daylight cycle, flight, movement speed, flight inertia.
- Floor presets via `config/ae2enhanced/personal_dimension_floor.json`.
- Invite-based sharing: invited players get full enter/build/interact access.

Commands (no OP required, except deleting others' dimensions):
```
/ae2e pd list
/ae2e pd info <player>
/ae2e pd tp <player>
/ae2e pd invite <player>
/ae2e pd kick <player>
/ae2e pd delete [player]
```

---

## Energy & Resource Devices

- **Network Access Node** — merged RF / Mana / Starlight access node: automatically performs the corresponding IO based on adjacent blocks, with input/output mode switching.
- **Chunk Power Node / Compressed Chunk Power Node** — powers all FE devices in the chunk (or 3×3 chunks) from the ME network's RF storage channel; costs 1 channel.
- **Chunk Mana Node / Compressed Chunk Mana Node** — supplies Mana from the ME network to Botania mana consumers in the chunk (or 3×3 chunks).
- **Energy Storage Bus** — works like a storage bus for RF: exposes adjacent RF containers (Forge Energy / Draconic Evolution extended RF) to the ME network, with priority/access-mode/whitelist support.
- **EMC Interface** (requires ProjectE) — exposes the bound player's EMC balance (BigInteger-level) as an item source for the ME network; a creative mode is unlocked when EMC reaches the cap.

---

## Additional Utility Items

- **Universal Import Bus / Export Bus** — all types of IO (items, fluids, gases, essentia) through a single bus.
- **Stocking Bus** — maintains a specified item count in the target inventory.
- **Wireless Channel Transmitter** — a block broadcasting 32 dense channels; pair it with a blank **Channel Receiver Card** (via its GUI) and insert the card into any AE2 device to grant it a remote wireless connection (unlimited distance, cross-dimension, configurable).
- **Universal Memory Card** — works like a standard memory card, plus copying machine configurations and upgrade cards (cross-mod: Mekanism, Ender IO, Thermal Expansion, ...), bulk editing, and Central ME Interface binding.
- **Advanced ME Omni Tool** — one tool with four modes (Universal / Placement / Rotate / Travel), silk touch & fortune options, drop routing (normal / inventory / ME network), blink travel, and upgrade support (Chaos Core, Bedrock Breaker, ...). Press C to open its config GUI.
- **ME Placement Tool** — places blocks / AE2 parts / facades / cables directly from the ME network; radial menu on G, batch placement with direction locking.
- **Advanced ME Collector** — injects dropped items into the ME network before they spawn as entities, with an internal buffer, 63-slot filter inventory, and a configurable collection area.
- **ME Network Recycler** — manages remote/cross-dimension recycling targets and writes products directly into the Hyperdimensional Storage Nexus.
- **Display Panel (+ Display Frames)** — free-form multiblock display wall (2~16 × 2~9) showing ME network statistics with 5 chart types (Line / Area / Delta / Rate / Stacked).
- **AE2Enhanced Guide** — a built-in Markdown-based guide book (28 pages, EN/ZH, 4 themes). Hold **G** while hovering a related item to open the relevant page.

---

## LP Crafting Planner

AE2Enhanced replaces AE2's crafting calculation with an **LP-based planner** (SCC condensation layering + three-phase lexicographic simplex: minimize missing items → maximize delivery → minimize execution count), which correctly handles cyclic/looped recipes that the native calculator cannot. Plans containing cyclic patterns are marked specially and must be executed by the Supercausal Computation Core.

The original calculator remains as fallback. Configure via `Crafting.dagPlannerMode` (`OFF` / `FALLBACK` / `DEFAULT`), or hot-switch at runtime:
```
/ae2e specialcrafting enable|disable|status
```

---

## Modifications to AE2-UEL

- **Long-order support**: Allows crafting orders exceeding `Int.MAX_VALUE` items.
- **Crafting plan optimization**: Brings back the "missing items on top" feature from modern AE2 versions to `1.12.2`.
- **Creative cell buff**: Changes creative storage cell capacity to `Long.MAX_VALUE`.
- **F-key JEI search**: Search all AE2 terminals from JEI/HEI (including bookmark overlay), key configurable.
- For fluid/gas/essentia terminal support (modern unified terminal display), please use **[AE2FluidCraft-Rework-Unofficial](https://github.com/Circulate233/AE2FluidCraft-Rework-Unofficial)** — AE2Enhanced's own fake-item based display is deprecated in its favor.
- For additional QoL improvements (e.g., crafting pinning), consider using [RandomComplement](https://github.com/Circulate233/RandomComplement), which provides many modern AE2 features.

---

## Performance

### Assembly Hub
Uses a hybrid virtual + real crafting mechanism. Even for extremely large orders, `mspt` will not be significantly affected. Nearly all AE2-craftable orders are fully supported.

> **Note on CraftTweaker:** When using `.reuse()` in CRT scripts, AE2 may still request the full amount during ordering since it does not recognize the item as non-consumed.

### Hyperdimensional Storage Nexus
Uses an asynchronous + incremental refresh model with external file storage (snapshot + WAL), fundamentally solving NBT overflow and tick lag issues while supporting extremely high storage capacity. Periodic full checks ensure correctness (configurable; default every 200 ticks).

### LP Crafting Planner
Planning runs in a budgeted, resumable time-sliced job; condensation layering keeps the LP model small even for large networks. Typical plans complete well within the default budgets (configurable).

Built-in diagnostics: `/ae2e perf` (TPS & node timing), `/ae2e diag check` (storage / personal dimension integrity), `/ae2e plan stats|running`.

---

## Requirements

- **Minecraft**: 1.12.2
- **Forge**: 14.23.5.2768+

**Hard Dependencies**
- **AE2-UEL**: v0.56.7+
- **MixinBooter**: 10.6+

**Recommended**
- **[AE2FluidCraft-Rework-Unofficial](https://github.com/Circulate233/AE2FluidCraft-Rework-Unofficial)** — fluid/gas/essentia terminal support (AE2Enhanced's own fake-item display is deprecated; use this instead)

**Optional**
- **CTM** — connected textures
- **Mekanism + MekanismEnergistics** — gas storage support
- **Thaumcraft + Thaumic Energistics** — essentia storage support
- **ProjectE** — EMC Interface
- **Botania** — Mana channel & chunk mana nodes
- **CraftTweaker** — recipe modification methods
- **JEI/HEI** — recipe transfer, ghost ingredients, F-key search
- Central ME Interface device adapters: Thaumcraft 6, Astral Sorcery, Blood Magic, Botania, Bewitchment, Actually Additions, Extended Crafting, Avaritia, Draconic Evolution, Ender IO, Thermal Expansion, NuclearCraft, Tech Reborn

---

## Download

[CurseForge](https://www.curseforge.com/minecraft/mc-mods/ae2enhanced)  
[Releases](https://github.com/aeddddd/AE2Enhanced/releases)

---

## Compatibility

Compatible with **CleanroomMC**. Maintains good compatibility with other AE2 addons. Tested successfully in the large modpack **Divine Journey 2** (requires updating AE2-UEL to the latest version).

---

## License

**GPL-3.0** (see [LICENSE](/LICENSE)).

Attribution:
- The dockable workspace layout engine is ported from **MEST (ME Spliced Terminal)**, used under the MIT License (see `licenses/MEST-LICENSE.txt`).
- The unified terminal implementation referenced parts of [AE2FluidCraft-Rework-Unofficial](https://github.com/Circulate233/AE2FluidCraft-Rework-Unofficial), with compatibility maintained.
- The GUI artwork of the spliced terminal (window frames, slot grids, buttons, tabs, scrollbar handles, crafting arrow, pattern-encoding blocks, text field and amount dialog) is taken from **Applied Energistics 2**, version 19.2.17. Those textures are
  © 2020 Ridanisaurus Rid, © 2013-2020 AlgorithmX2 et al, licensed under **CC BY-NC-SA 3.0**
  (https://creativecommons.org/licenses/by-nc-sa/3.0/). This project is non-commercial and grants no paid
  access, which keeps the NonCommercial term satisfied; derivative artwork of those files must stay under the
  same licence. The exact file list and the full notice are in `licenses/AE2-ASSETS.txt`.

# AE2Enhanced

> **Fold immense crafting timelines into a single tick.**

[中文版本 (Chinese)](/README_zh.md)

AE2Enhanced is a late-game addon for **AE2 Unofficial Extended Life (AE2-UEL)** that introduces multiple massive multiblock structures for the endgame:

- **Supercausal Assembly Hub** — a 344-block crafting array similar to LazyAE's Large Molecular Assembler, allowing extreme parallelism and speed for crafting patterns.
- **Hyperdimensional Storage Nexus** — an unlimited-capacity storage structure that bypasses the `long` limit, supporting items, fluids, Mekanism gases, and Thaumcraft essentia, with data persisted to external files to prevent save bloat.
- More multiblock structures planned for future updates.

---

## Supercausal Assembly Hub

Base operation parameters: **64 parallel, 1 second per operation** — equivalent to LazyAE's Large Molecular Assembler at its strongest stock configuration.

**Importantly, the Assembly Hub's parallelism does NOT depend on CPU co-processors!**

With all 5 Parallel Extension Modules installed, parallelism reaches `Long.MAX_VALUE` — effectively infinite for virtually all modpacks (this even exceeds the AE2 terminal display limit in 1.12.2!).

Speed Modules reduce the cooldown from the default `20 ticks` down to `1 tick`, so crafting is no longer a bottleneck for factory expansion.

Additionally, the hub provides an **Auto-Upload Module** similar to AE2's Pattern Provider — encoded crafting patterns are automatically uploaded to the hub with duplicate detection. Duplicate patterns are silently ignored.

---

## Hyperdimensional Storage Nexus

Designed to solve the late-game AE2 network storage capacity bottleneck.

- **BigInteger-level capacity**: Single-structure capacity is theoretically unlimited, completely bypassing `int`/`long` restrictions.
- **Multi-type compatibility**: Native support for items and fluids; optional support for Mekanism gases and Thaumcraft essentia (requires corresponding mods).
- **External file persistence**: Data is stored in `<world>/ae2enhanced/storage/<uuid>.dat`, avoiding NBT overflow and tick lag caused by massive storage. Save file size is independent of stored capacity.
- **Safe mode**: Automatically enters read-only state when file exceptions occur, preventing data corruption. Supports safe mod version updates — items will not be lost when updating the mod.
- **Third-party extension**: Provides the `registerExternalAdapter()` API, allowing other mods to plug in custom storage types.

---

## Performance

### Assembly Hub
Uses a **hybrid virtual + real crafting mechanism**. Even for extremely large orders, **MSPT impact is negligible**. Nearly all AE2-craftable orders are fully supported.

> **Note on CraftTweaker:** When using `.reuse()` in CRT scripts, AE2 may still request the full amount during ordering since it does not recognize the item as non-consumed. This does not affect actual batch execution.

### Hyperdimensional Storage Nexus
Uses an **asynchronous + incremental refresh model** with external file storage, fundamentally solving NBT overflow and tick lag issues while supporting extremely high storage capacity.

> Built-in support for Mekanism gases and Thaumcraft essentia is automatically enabled when the corresponding mods are installed. Support for other storage types can be requested in `issues`, and an API is provided for easy extension.

---

## Configuration

Located at `config/ae2enhanced.cfg`, the following parameters are adjustable:

| Category | Parameter | Description |
|---|---|---|
| Storage | `flushIntervalSeconds` | Auto-flush interval for the storage file (seconds, default: 5) |
| Render | `enableHyperdimensionalRenderer` | Enable effect rendering (default: true) |
| Render | `renderDistance` | Maximum render distance in blocks (default: 64) |
| BlackHole | `damageMode` | Black hole damage mode: ALL / NON_CREATIVE / NONE (default: ALL) |

---

## Requirements

- **Minecraft**: 1.12.2
- **Forge**: 14.23.5.2768+
- **AE2-UEL**: v0.56.7+
- **MixinBooter**: 8.9+ (automatically loaded as a dependency)

Optional dependencies:
- **Mekanism + MekanismEnergistics** — enables gas storage support
- **Thaumcraft + Thaumic Energistics** — enables essentia storage support

---

## Download

[CurseForge](https://www.curseforge.com/minecraft/mc-mods/ae2enhanced)  
[Releases](https://github.com/aeddddd/AE2Enhanced/releases)

---

## Compatibility

Compatible with **CleanroomMC**. Maintains good compatibility with other AE2 addons. Tested successfully in the large modpack **Divine Journey 2** (requires updating AE2-UEL to the latest version).

---

### 🌀 Black Hole Crafting

A unique crafting system that allows players to create a **Micro Singularity** and throw items into its event horizon to transmute them into desired materials.

**Warning**: The Micro Singularity only lasts for `300 seconds`. Right-click it to activate crafting. **Do not stand too close!!!**

Full **CraftTweaker** support:
```zenscript
import mods.ae2enhanced.BlackHole;
BlackHole.addRecipe(IItemStack output, IItemStack[] inputs);
// Example
BlackHole.addRecipe(<minecraft:obsidian>, [<minecraft:stone> * 8, <minecraft:diamond>]);
BlackHole.removeRecipe("test_obsidian");
```

Built-in recipe names:
```
id: "test_obsidian"
id: "stable_spacetime_manifold"
id: "differential_form_stabilizer"
id: "conformal_invariant_charge"
```

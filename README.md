# AE2Enhanced

AE2Enhanced is a late-game addon for **AE2 Unofficial Extended Life (AE2-UEL)** that introduces multiple massive multiblock structures and a series of utility items for the endgame:

*Current version: 1.5.2*
> New features and utility items are still under active development. A Release will be published once development is complete.

[中文版本 (Chinese)](/README_zh.md)

## Core Items

### Omni Terminal
A modern, universal, multi-functional terminal.

- Supports encoding patterns with **81 inputs** and **27 outputs**.
- Perform pattern encoding and item crafting in the same interface.

### Central ME Interface *(New in 1.5.x!)*

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
| **Thaumcraft 6** | Infusion Altar |
| **Astral Sorcery** | All tiers of Starlight Altars |
| **Actually Additions** | Empowerer |
| **Extended Crafting** | Combination Crafting, all Crafting Tables, Quantum Compressor |
| **Botania** | Petal Apothecary, Mana Pool, Alfheim Portal, Rune Altar, Terrestrial Agglomeration Plate |
| **Bewitchment** | Witches' Cauldron, Spinning Wheel, Distillery |
| **Draconic Evolution** | Fusion Crafting Injector |

(All adaptations do not require manual player initiation; crafting will start automatically, including for the Infusion Altar and Astral Sorcery. If crafting leaves behind returned items such as empty buckets, don't worry — the Central ME Interface will automatically collect them.)

Use the Universal Memory Card for binding.

## Multiblock Structures

- **Supercausal Assembly Hub** — a 344-block crafting array similar to LazyAE's Large Molecular Assembler, allowing extreme parallelism (up to `Long.MAX_VALUE`) and automatic pattern uploading.
- **Hyperdimensional Storage Nexus** — an unlimited-capacity storage structure using `BigInteger`, supporting items, fluids, Mekanism gases, and Thaumcraft essentia, with data persisted to external files to prevent save bloat.
- **Supercausal Computation Core** — a super crafting CPU with `Long.MAX_VALUE` storage and 16384 parallel accelerator capacity, supporting multi-order concurrency via dynamic virtual CPU cluster pools.

---

## Additional Utility Items

- **Universal Import Bus / Export Bus** — allows all types of IO through a single bus.
- **Stock Bus** — maintains a specified item count in the target inventory.
- **Wireless Channel Emitter** — broadcasts its channels wirelessly.
- **Channel Receiver Card** — receives channels from the Wireless Channel Emitter (unlimited distance/dimensions, configurable).
- **ME Interface Mirror** — mirrors an ME Interface.
- **Universal Memory Card** — works like a standard memory card, plus copying machine configurations and upgrade cards, bulk editing, and linking ME Interface Mirrors.
- **ME Sender Node** — wirelessly sends items in bulk (similar to an export bus), range set via the Universal Memory Card.
- **ME Receiver Node** — counterpart to the ME Sender Node.
- **Concurrency Card** — increases parallelism of AE2 IO devices.
- **ME Collector Node** — collects items from the world into the ME network.

---

## Modifications to AE2-UEL

- **Long-order support**: Allows crafting orders exceeding `Int.MAX_VALUE` items.
- **Crafting plan optimization**: Brings back the "missing items on top" feature from modern AE2 versions to `1.12.2`.
- **Creative cell buff**: Changes creative storage cell capacity to `Long.MAX_VALUE`.
- **Modern ME Terminal**: You no longer need separate fluid, gas, or essentia terminals. Everything is displayed in a single unified terminal, just like in modern AE2 versions. *(Thanks to [AE2fc-rework-unofficial](https://github.com/Circulate233/AE2FluidCraft-Rework-Unofficial/tree/main) for partial implementation reference, with compatibility maintained.)*
- For additional QoL improvements (e.g., crafting pinning), consider using [RandomComplement](https://github.com/Circulate233/RandomComplement), which provides many modern AE2 features.

---

## Performance

### Assembly Hub
Uses a hybrid virtual + real crafting mechanism. Even for extremely large orders, `mspt` will not be significantly affected. Nearly all AE2-craftable orders are fully supported.

> **Note on CraftTweaker:** When using `.reuse()` in CRT scripts, AE2 may still request the full amount during ordering since it does not recognize the item as non-consumed.

### Hyperdimensional Storage Nexus
Uses an asynchronous + incremental refresh model with external file storage, fundamentally solving NBT overflow and tick lag issues while supporting extremely high storage capacity.

Uses a full-incremental refresh mechanism different from AE2-UEL itself, and performs periodic full checks to ensure correctness. Also supports a configuration file, allowing players to change the frequency of scheduled checks (*default is every 200 ticks*).

---

## Requirements

- **Minecraft**: 1.12.2
- **Forge**: 14.23.5.2768+

**Hard Dependencies**
- **AE2-UEL**: v0.56.7+
- **MixinBooter**: 8.9+

**Optional**
- **CTM** — provides connected textures
- **Mekanism + MekanismEnergistics** — enables gas storage support
- **Thaumcraft + Thaumic Energistics** — enables essentia storage support
- **CraftTweaker** — provides recipe modification methods
- **JEI/HEI** — related support
- Special adaptations for many mods: (Thaumcraft 6, Astral Sorcery, Blood Magic, Botania, Bewitchment, Actually Additions, Extended Crafting, Mekanism, Thermal Series, Ender IO, ...)

**Compatibility**
- **AE2fc-rework-unofficial** — does not re-register fake fluids (compatible)

---

## Download

[CurseForge](https://www.curseforge.com/minecraft/mc-mods/ae2enhanced)  
[Releases](https://github.com/aeddddd/AE2Enhanced/releases)

---

## Compatibility

Compatible with **CleanroomMC**. Maintains good compatibility with other AE2 addons. Tested successfully in the large modpack **Divine Journey 2** (requires updating AE2-UEL to the latest version).

---

### Black Hole Crafting

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

> Additional note for the Assembly Hub:
> When using CraftTweaker to modify crafting recipes, a simple `.reuse()` does not tell AE2 that the item is non-consumed; the full amount will still be requested during ordering.

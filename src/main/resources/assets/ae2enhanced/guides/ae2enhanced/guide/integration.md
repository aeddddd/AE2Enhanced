---
navigation:
  title: Integration
  parent: index.md
  position: 60
  icon: emc_interface
item_ids: [emc_interface]
---

# Integration

All integration is optional: the corresponding content only loads when the mod is installed.

## Thaumcraft

- Essentia drops, essentia support in universal buses, and essentia channels in the [Hyperdimensional Storage Nexus](machines/storage-nexus.md) (requires Thaumcraft + Thaumic Energistics).
- Central ME Interface handlers for the infusion matrix and the crucible (crucible essentia is cleared after each craft by default).

## Mekanism

- Gas drops and gas support in universal buses and the storage nexus (requires Mekanism + MekanismEnergistics, and ae2fc not installed).

## Botania

- Network Mana storage channel, Mana drops, [Chunk Mana Nodes](devices/chunk-nodes.md), the Mana bridge of the [Network Access Node](devices/network-access-node.md), and Central ME Interface handlers for mana pool, alfheim portal, terra plate, rune altar and apothecary.
- If Botania Applied provides a Mana channel, it is reused instead of registering a new one.

## Astral Sorcery

- Network Starlight storage channel, Starlight drops, the starlight bridge of the Network Access Node, and the altar handler of the Central ME Interface.

## ProjectE

- The <ItemLink id="emc_interface" /> **EMC Interface** exposes the bound player's transmutation knowledge as an item source of the ME network (one-way: network items are generated from the EMC balance, nothing is injected back).
- Binds to the placing player; **Shift + right-click** the GUI title to rebind (owner or level-2 operator). Extraction also works while the owner is offline (EMC is deducted from the saved player data).
- A 2040-slot whitelist (20 pages x 102) limits which items are exposed; empty whitelist exposes nothing.
- Idle power 5 AE; requires config `EMCInterface.enabled` (default on).

## Draconic Evolution

- Fusion crafting core handler for the Central ME Interface, energy core support for the [Energy Storage Bus](buses/energy-storage-bus.md), creative RF source boost for the Network Access Node, and the Chaos Core upgrade for the [ME Omni Tool](tools/omni-tool.md).

## Others

- **AE2 Fluid Crafting (ae2fc)**: when installed, AE2E's own fluid/gas fake items are disabled; `/ae2e migratefluids` migrates existing drops.
- **Flux Applied**: its energy channel is reused when present.
- **Storage Drawers**: drawer networks can be wrapped as network monitors.
- **JEI**: black hole recipe category, Omni Terminal recipe transfer, terminal search sync (press **F**), ghost ingredients for the Smart Pattern Interface.
- **CraftTweaker**: black hole recipes, singularity rituals/fuels, chamber alternatives and assembly hub upgrade APIs (see [Black Hole Crafting](systems/black-hole.md)).

## CraftTweaker Guide Pages

`mods.ae2enhanced.Guide` can add pages for any mod. `Guide.load("mymod/page.md")` and `Guide.loadDirectory("mymod")` read UTF-8 Markdown only below `config/ae2enhanced/guides/`; no separate page manifest is needed. `Guide.addPage(...)` creates a page with navigation metadata, `Guide.addTranslation(...)` adds a localized body, and `Guide.removePage(...)` removes a script page.

Script pages are sent to clients when they log in and override built-in pages with the same ID. Use `_zh_cn/` directories for external translations. Paths escaping the configured root, invalid UTF-8, oversized content and excessive item indexes are rejected. Restart after changing scripts or files; there is no hot reload.

## Read-only Java API

Third-party mods can use `MicroSingularityApi` to read remaining ticks, permanence, crafting/suction ranges, nearby inputs, executable black-hole recipes, rituals, fuels and constrained-item lifetime. `ChamberApi` exposes machine energy, channels, inventories, active jobs and recipe queries. World/position queries are server-main-thread only; ticks are game time and freeze while unloaded. Missing targets return empty optionals, unfinished catalogs return empty lists, and queries never finalize recipes early. Snapshots and lists are defensive copies; execution remains outside this read-only API.

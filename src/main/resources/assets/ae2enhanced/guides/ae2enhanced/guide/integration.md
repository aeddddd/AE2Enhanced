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
- **CraftTweaker**: black hole recipes, singularity rituals/fuels and assembly hub upgrade APIs (see [Black Hole Crafting](systems/black-hole.md)).

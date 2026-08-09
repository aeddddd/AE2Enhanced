---
navigation:
  title: Central ME Interface
  parent: devices.md
  position: 10
  icon: central_me_interface
item_ids: [central_me_interface, virtual_parallel_card]
---

# Central ME Interface

The <ItemLink id="central_me_interface" /> **Central ME Interface** runs encoded patterns on remote machines instead of adjacent inventories. It is bound to targets with the [Universal Memory Card](tools/memory-card.md).

## Slots

- 36 pattern slots: 9 by default, +9 per Dimensional Fold Module (pattern expansion), up to 36.
- 9 config slots and 9 storage slots (512 per slot).
- 4 upgrade slots. Inserting a bound Channel Receiver Card turns the interface into a wireless channel endpoint (dense smart cable).

## Remote Handlers

Remote execution is dispatched per mod, loaded only when the mod is present:

- Botania: mana pool, alfheim portal, terra plate, rune altar, petal apothecary.
- Blood Magic: alchemy table, soul forge, altar.
- Bewitchment: spinning wheel, distillery, witches' cauldron.
- Astral Sorcery: altar. Actually Additions: empowerer.
- Extended Crafting: crafting core, tables, compressor, ender crafter.
- Avaritia: extreme crafting table.
- Draconic Evolution: fusion crafting core.
- Thaumcraft: infusion matrix; crucible (clears essentia after each craft by default).
- Ender IO, Thermal Expansion, NuclearCraft: direct machine inventory access, bypassing side config.
- Anything unmatched falls back to a single-batch default handler.

## Virtual Parallel Card

<ItemLink id="virtual_parallel_card" /> **Virtual Parallel Cards** (Tier 1-8, tier stored in NBT) installed in the upgrade slots switch supported remote targets to virtual crafting: no physical ingredients are moved, each virtual craft costs energy.

- Parallel counts by tier: 8 / 32 / 128 / 512 / 32,768 / 2,097,152 / 134,217,728 / unlimited. Multiple cards take the highest tier.
- Energy cost per virtual craft = actual parallel x `virtualParallelEnergyCost` (config, default 16.0 AE). Insufficient energy reduces the parallel count accordingly.
- Obtained through [Black Hole Crafting](systems/black-hole.md): Tier 1 from 1024 acceleration cards + 1024 capacity cards + 64 singularities; Tier 2-6 from 16 cards of the previous tier each.

Related config: processing timeout 600 ticks, virtual cooldown 20 ticks per target and 20 ticks global.

---
navigation:
  title: Black Hole Crafting
  parent: systems.md
  position: 20
  icon: constrained_micro_singularity
item_ids: [constrained_micro_singularity, singularity_constrictor, stable_spacetime_manifold, differential_form_stabilizer, conformal_invariant_charge]
---

# Black Hole Crafting

A singularity-based end-game crafting system: recipes are executed by throwing ingredients into an event horizon.

## The Ritual

1. Drop **64 AE2 singularities** and **4 nether stars** on the ground near an ME controller.
2. Hold **1 nether star** and right-click the ME controller.
3. A <ItemLink id="constrained_micro_singularity" /> **Micro Singularity** appears with a lifetime of 6000 ticks (300 seconds). The ritual consumes the dropped items within a 5x5x5 area.

Custom rituals can be added with CraftTweaker (`mods.ae2enhanced.SingularityRitual`).

## Micro Singularity

- Unbreakable, emits light, drags entities in its 3x3x3 event horizon into "spacetime" damage (mode set by `blackHole.damageMode`: ALL / NON_CREATIVE / NONE).
- **Right-click** to run black hole recipes: matching item entities in the 3x3x3 area are consumed and results pop out above (non-matching items are not destroyed when activated by hand).
- Feeding extends the lifetime:
  - <ItemLink id="stable_spacetime_manifold" /> Stable Spacetime Manifold: +12000 ticks (600 s).
  - <ItemLink id="differential_form_stabilizer" /> Differential Form Stabilizer: +48000 ticks (2400 s).
  - <ItemLink id="conformal_invariant_charge" /> Conformal Invariant Charge: makes it permanent.
- When the lifetime ends it collapses and disappears.
- Right-clicking with a <ItemLink id="singularity_constrictor" /> **Singularity Constrictor** constrains the singularity into item form (its countdown slows to 1/30 speed). Throw the item and let it rest on the ground to restore the block form, returning the empty constrictor.

## Built-in Recipes (thrown into the event horizon)

- 16x 16384k spatial component + 64x singularity = Stable Spacetime Manifold.
- 128x singularity + 16x nether star = Differential Form Stabilizer.
- 16x Stable Spacetime Manifold + 16x Differential Form Stabilizer = Conformal Invariant Charge.
- 64x blank pattern = [Smart Blank Pattern](devices/smart-pattern.md).
- 1024x acceleration card + 1024x capacity card + 64x singularity = [Virtual Parallel Card](devices/central-interface.md) Tier 1.
- 16x Virtual Parallel Card of one tier = the next tier (up to Tier 6).

## CraftTweaker

- `mods.ae2enhanced.BlackHole.addRecipe(output, inputs)` / `removeRecipe(id)`.
- `mods.ae2enhanced.SingularityRitual.addRecipe(id, droppedInputs, heldItem, targetBlock, lifetimeTicks)`.
- `mods.ae2enhanced.SingularityFuel.addFuel(id, item, ticks)` / `addPermanentFuel(id, item)`.
- `mods.ae2enhanced.AssemblyHub.registerParallelUpgrade(card, maxStack, values)` / `registerSpeedUpgrade(...)`.

## Matter Cannon Ammo

The Conformal Invariant Charge is registered as matter cannon ammo with weight 1E8 (about 5,000,000 damage). Kills with it trigger an explosion of particles, absolute void damage, heavy knockback and ignition.

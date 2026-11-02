---
navigation:
  title: ME Omni Tool
  parent: tools.md
  position: 20
  icon: me_omni_tool
item_ids: [me_omni_tool]
---

# Advanced ME Omni Tool

The <ItemLink id="me_omni_tool" /> **Advanced ME Omni Tool** is a network-bound multi-tool. Bind it to your network in an ME security terminal before use.

## Modes

Four modes, cycled with **N**:

- **Universal**: general mining and combat.
- **Placement**: places blocks from the network; middle-click picks the targeted block as preset, **G** opens the radial preset menu, Shift+wheel toggles single/batch placement.
- **Rotate**: rotates blocks.
- **Travel**: blink/teleport movement, up to 256 blocks (config `omniTool.maxBlinkDistance`); wall phasing is enabled by default.

## Keys

- **N**: cycle mode. **Shift+N**: toggle silk touch (with an "advanced silk" option that preserves block NBT).
- **Ctrl+N**: cycle drop mode: normal drops, straight to inventory, or straight to the ME network (requires binding by Shift+right-clicking a Wireless Channel Transmitter).
- **C**: open the config GUI.

## Combat

- Base attack damage 6.0 as true damage (bypasses armor, config `omniTool.baseAttackDamage`).
- Entities hit are marked with an anti-heal flag that blocks healing.

## Upgrades

- **Chaos Core** (requires Draconic Evolution): enables chaos damage and force-kill.
- **Bedrock Breaker**: allows breaking bedrock.
- **Fortune** levels, and other enchantments can be imported from enchanted books.
- **Travel Staff**: prerequisite of Travel mode; can bind a travel anchor.
- **Conformal Invariant Charge**: unlocks its dedicated handler. See [Black Hole Crafting](systems/black-hole.md).

## Global ring module

Install the legacy Network Link Credential into the tool with the ring-module conversion recipe. Its tier, ascension, energy, network key and feature switches are preserved in a nested module NBT. Ring settings appear in the tool's paginated config GUI. The old ring and standalone ring GUI are deprecated, but existing items and abilities remain compatible.

Break cooldown caps at 20 ticks; unbreakable blocks can be blacklisted in config.

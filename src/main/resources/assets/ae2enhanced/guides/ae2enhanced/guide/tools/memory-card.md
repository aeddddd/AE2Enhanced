---
navigation:
  title: Universal Memory Card
  parent: tools.md
  position: 30
  icon: universal_memory_card
item_ids: [universal_memory_card]
---

# Universal Memory Card

The <ItemLink id="universal_memory_card" /> **Universal Memory Card** copies machine configurations between devices and binds remote targets for the Central ME Interface and the ME Network Recycler.

## Operations

- **Shift + right-click** a machine: copy its configuration and upgrades.
- **Right-click** a machine: paste the copied configuration. Missing upgrades are reported; missing items can be requested from network autocrafting.
- **Ctrl + right-click**: select or deselect targets according to the current select mode, then paste to all selected at once.
- **Alt + right-click**: clear bindings (Central ME Interface / ME Network Recycler).
- **Right-click air**: open the management GUI.

## Modes & Paste Options

Managed in the GUI (right-click air):

- **Paste content mode**: Full (config + upgrades) / Config Only / Upgrades Only. The card always stores the full copy; the mode filters what is applied on paste.
- **Select mode** (Ctrl + right-click):
  - *Single*: select one block at a time.
  - *Chain*: flood-fill up to 64 connected blocks of the same type. Already-selected blocks are skipped, so re-clicking a network only adds newly discovered blocks.
  - *Area*: click two opposite corners to select every supported machine in the box (up to 128).
- **Paste options**: independently toggle whether paste applies upgrades, facing, side configuration and redstone control.

## Special Targets

- Right-click a [Central ME Interface](devices/central-interface.md): set it as the binding source for remote targets.
- Right-click a [ME Network Recycler](devices/collector-recycler.md): bind all selected machines to it in batch.
- Right-click a [Smart Pattern Interface](devices/smart-pattern.md): query JEI recipes of the selected targets and bind them.

## Supported Devices

- AE2 parts and block devices (always).
- Conditionally: Mekanism, Ender IO (machines and conduits), Thermal Expansion (machines, dynamos, devices and storage blocks), NuclearCraft, TechReborn, Industrial Foregoing, Extra Utilities 2 and Quantum Things machines, AE2 Stuff and Lazy AE2 devices, and the RFTools Crafter.
- Vanilla containers (chests, hoppers, furnaces, etc.): copying records the container contents as a snapshot; pasting fills missing stacks from your inventory or the bound network. Slots already occupied by other items are left untouched. Can be disabled in the config (`memoryCard.vanillaContainerCopy`).

## Mod Notes

- **AE2 parts**: besides settings, priority, filter slots and upgrade cards, the card also covers P2P tunnel frequency and input/output mode (registered through the P2P cache, same tunnel type required), storage/conversion monitor locked item or fluid and lock state, the ore dictionary storage bus filter expression, pattern terminal crafting/substitution mode, and the wrench-rotated panel orientation.

- **Thermal Expansion**: copies facing, side configuration, redstone control, augments, filters and security settings; clients are re-synced after paste.
- **Extra Utilities 2**: supports machines (receiver/provider), the Mechanical User, trash cans, the Resonator and the player chest; copies redstone mode, upgrades, machine type and filter.
- **Quantum Things**: the igniter's mode is copied and re-triggered on paste; filters (e.g. Item Collector) are copied and applied.
- **AE2 Stuff**: the Advanced Inscriber's plate locks and the wireless connectors' name/color are copied; wireless pairing coordinates are intentionally not copied.
- **Lazy AE2**: processor machines copy facing, side configuration, auto-export and speed cards; the ME Level Maintainer's stock requests (item, target count, batch size) are copied and the stack watcher is rebuilt on paste.

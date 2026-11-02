---
navigation:
  title: Omni Terminal
  parent: tools.md
  position: 10
  icon: omni_wireless_terminal_ii
item_ids: [omni_wireless_terminal, omni_wireless_terminal_ii, omni_upgrade_card]
---

# Omni Terminal II

The <ItemLink id="omni_wireless_terminal_ii" /> **Omni Wireless Terminal II** is a powered wireless terminal with a spliced panel interface. Link it in an ME security terminal and keep it charged before use. It can also be worn in a Baubles trinket slot.

## Panels and layout

- Item repository, crafting, crafting management, interface terminal, pattern encoding, storage, upgrades, player inventory and View Cell are independent panels.
- Drag, dock, float, close and reopen panels to build your own workspace. The repository column count follows panel width. Drag the bottom-right grip to resize; hold Shift while dragging to preserve the aspect ratio.
- The layout editor provides undo, reset, three presets and layout import/export/share.
- Layouts are stored in the terminal item's `omni_layout` NBT and validated by the server.
- Local backups and presets are isolated by terminal `storageId`; a new terminal does not inherit another terminal's layout or active preset.

## Crafting and encoding

- Extract items from the network and use the 3x3 crafting grid.
- The Interface Terminal uses AE2's native provider list, pattern synchronization and slot actions rather than a parallel protocol.
- Encode crafting and processing patterns, with JEI/HEI recipe transfer support.
- Search and repository pages are synchronized with the server; separate clients have independent sessions.

## Legacy terminals

The original <ItemLink id="omni_wireless_terminal" /> is deprecated but remains usable. Its normal crafting recipe now produces Terminal II. Use the legacy-to-II conversion recipe to preserve network binding, storage ID, inventories, upgrades and layout NBT.

## Upgrades and search

The terminal retains upgrade slots and View Cell filtering. Press **Shift+E** to open it. The search box can synchronize with JEI/HEI; hover an ingredient and press **F** to copy its display name.

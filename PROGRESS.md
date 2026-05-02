# AE2Enhanced Development Progress

> Current branch: `master`  
> Version: `1.2.1-dev`  
> Last updated: 2026-04-29

---

## Phase 3: Supercausal Computation Core

### Design
- **Role**: Super Crafting CPU. No pattern storage; actual crafting delegated to network ICraftingMediums (assemblers, ME interfaces).
- **Parallel**: Fixed at **16384** via `AE2EnhancedConfig.crafting.maxParallel`.
- **Storage**: `Long.MAX_VALUE` bytes crafting storage capacity.
- **Integration**: Mixin into `CraftingGridCache` to register as a valid CPU node.

### Completed

| Item | Status | Commit |
|---|---|---|
| 5 block registrations + textures / models / localization | ✅ | pre-`762b85c` |
| 854-block structure validation + 4-direction rotation | ✅ | pre-`762b85c` |
| Event system (NeighborNotify / Break / ChunkLoad / WorldTick) | ✅ | pre-`762b85c` |
| Safe one-click placement (suffocation fix, skip controller) | ✅ | pre-`762b85c` |
| Dual GUI framework (unformed / formed) + full localization | ✅ | pre-`762b85c` |
| Dyson-sphere TESR (solid core + tech grid + panels + energy streams + rings) | ✅ | pre-`762b85c` |
| Big number formatting (Z/Y units + scientific notation + Shift toggle) | ✅ | `a3d95aa` |
| Essentia/Gas storage discovery fix | ✅ | `762b85c` |
| Core engine skeletons: `CraftingOrder`, `OrderScheduler`, `ParallelAllocator`, `BatchManager` | ✅ | `32142c5` |
| `TileComputationCore` role correction (removed `ICraftingProvider` / `ICraftingMedium`) | ✅ | `32142c5` |
| `MixinCraftingGridCache` (register / unregister / enumerate CPUs) | ✅ | `3efecdc` |
| `ComputationCoreCPU` (`ICraftingCPU` proxy, `Long.MAX_VALUE` storage, `ItemList` inventory) | ✅ | `3efecdc` |
| `TileComputationCore` implements `IActionHost`, triggers `MENetworkCraftingCpuChange` | ✅ | `3efecdc` |

### In Progress / Next Steps

| Priority | Task | Blocker |
|---|---|---|
| **P1** | Mixin `CraftingGridCache.injectItems` / `extractItems` to forward into `ComputationCoreCPU` | None |
| **P1** | `submitJob` real implementation: parse `ICraftingJob`, enqueue `CraftingOrder` | Needs inject/extract mixin first |
| **P1** | `OrderScheduler` + `ParallelAllocator` full order drive loop | Needs submitJob |
| **P2** | Terminal auto-selection priority (Computation Core > native CPU) | Needs submitJob |
| **P2** | GUI: order list, parallel visualization, failure retry | Needs engine |
| **P3** | Recipe data (5 blocks + structure assembly recipe) | None |
| **P3** | Extreme stress test (>Long.MAX_VALUE orders, 16384 parallel) | Needs full engine |

### Known Issues

| Issue | Status |
|---|---|
| Terminal display突破 `long` | Frozen (AE2-UEL `IAEItemStack` API unchangeable) |
| `ru_ru` localization incomplete | On hold (waiting for external PR) |

---

## Phase 1 & 2 (Archived)

- **Supercausal Assembly Hub** — Complete, maintenance mode only.
- **Hyperdimensional Storage Nexus** — Complete, maintenance mode only.

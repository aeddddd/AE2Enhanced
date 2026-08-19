package com.github.aeddddd.ae2enhanced.centralinterface.handler.botania;

import com.github.aeddddd.ae2enhanced.centralinterface.TargetSession;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.centralinterface.IRemoteHandler;
import com.github.aeddddd.ae2enhanced.centralinterface.IVirtualBatchCraftingHandler;
import com.github.aeddddd.ae2enhanced.storage.mana.AEManaStack;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraft.util.EnumParticleTypes;

/**
 * Botania 远程处理器.
 *
 * 支持设备：
 * <ul>
 *   <li>魔力池 (botania:pool) — 通过 collideEntityItem 注入物品进行魔力转换</li>
 *   <li>精灵门 (botania:alfportal) — 丢入 EntityItem 进行精灵交易</li>
 *   <li>泰拉凝聚板 (botania:terraplate) — 丢入 3 个 manaResource 合成泰拉钢锭</li>
 *   <li>符文祭坛 (botania:runealtar) — addItem 放入物品 + 活石催化 + 魔杖启动合成</li>
 *   <li>花药台 (botania:altar) — 放入材料后手动触发配方合成</li>
 * </ul>
 *
 * 所有 Botania 类均通过 {@link BotaniaReflectionHelper} 访问(方法经 Mixin Invoker,字段经反射),
 * 本类不存在对 vazkii.botania 的编译期硬引用.
 */
public class BotaniaHandler implements IRemoteHandler, IVirtualBatchCraftingHandler {

    /** 中枢推送的输入物品标记,用于 revertMaterials 区分未消耗输入与产物 */
    private static final String TAG_INPUT_FLAG = "_ae2eInput";
    /** 推料保护期：防止推料后机器尚未开始处理就被判定为 idle */
    private static final int PUSH_IDLE_GRACE_TICKS = 4;
    /** Alf Portal 需要更长时间完成精灵交易 */
    private static final int ALF_PORTAL_GRACE_TICKS = 20;

    @Override
    public boolean canHandle(String blockId) {
        return "botania:pool".equals(blockId)
                || "botania:alfheimportal".equals(blockId)
                || "botania:terraplate".equals(blockId)
                || "botania:runealtar".equals(blockId)
                || "botania:altar".equals(blockId);
    }

    @Override
    public boolean isValidTarget(World world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        return BotaniaReflectionHelper.isInstance(BotaniaReflectionHelper.CLASS_TILE_POOL, te)
                || BotaniaReflectionHelper.isInstance(BotaniaReflectionHelper.CLASS_TILE_ALF_PORTAL, te)
                || BotaniaReflectionHelper.isInstance(BotaniaReflectionHelper.CLASS_TILE_TERRA_PLATE, te)
                || BotaniaReflectionHelper.isInstance(BotaniaReflectionHelper.CLASS_TILE_RUNE_ALTAR, te)
                || BotaniaReflectionHelper.isInstance(BotaniaReflectionHelper.CLASS_TILE_ALTAR, te);
    }

    @Override
    public boolean canStart(World world, BlockPos pos, InventoryCrafting ingredients, TargetSession session) {
        TileEntity te = world.getTileEntity(pos);
        if (BotaniaReflectionHelper.isInstance(BotaniaReflectionHelper.CLASS_TILE_POOL, te)) {
            return canStartPool(world, pos, te, ingredients);
        } else if (BotaniaReflectionHelper.isInstance(BotaniaReflectionHelper.CLASS_TILE_ALF_PORTAL, te)) {
            return canStartAlfPortal(world, pos, te, ingredients);
        } else if (BotaniaReflectionHelper.isInstance(BotaniaReflectionHelper.CLASS_TILE_TERRA_PLATE, te)) {
            return canStartTerraPlate(world, pos, te, ingredients);
        } else if (BotaniaReflectionHelper.isInstance(BotaniaReflectionHelper.CLASS_TILE_RUNE_ALTAR, te)) {
            return canStartRuneAltar(world, pos, te, ingredients);
        } else if (BotaniaReflectionHelper.isInstance(BotaniaReflectionHelper.CLASS_TILE_ALTAR, te)) {
            return canStartAltar(world, pos, te, ingredients);
        }
        return false;
    }

    @Override
    public boolean pushMaterials(World world, BlockPos pos, InventoryCrafting ingredients, IActionSource source, TargetSession session) {
        TileEntity te = world.getTileEntity(pos);
        boolean success = false;
        if (BotaniaReflectionHelper.isInstance(BotaniaReflectionHelper.CLASS_TILE_POOL, te)) {
            success = pushMaterialsPool(world, pos, te, ingredients);
        } else if (BotaniaReflectionHelper.isInstance(BotaniaReflectionHelper.CLASS_TILE_ALF_PORTAL, te)) {
            success = pushMaterialsAlfPortal(world, pos, te, ingredients);
        } else if (BotaniaReflectionHelper.isInstance(BotaniaReflectionHelper.CLASS_TILE_TERRA_PLATE, te)) {
            success = pushMaterialsTerraPlate(world, pos, te, ingredients);
        } else if (BotaniaReflectionHelper.isInstance(BotaniaReflectionHelper.CLASS_TILE_RUNE_ALTAR, te)) {
            success = pushMaterialsRuneAltar(world, pos, te, ingredients);
        } else if (BotaniaReflectionHelper.isInstance(BotaniaReflectionHelper.CLASS_TILE_ALTAR, te)) {
            success = pushMaterialsAltar(world, pos, te, ingredients);
        }
        if (success && session != null) {
            session.setPushTick(world.getTotalWorldTime());
        }
        return success;
    }

    @Override
    public boolean startProcess(World world, BlockPos pos, IActionSource source, TargetSession session) {
        TileEntity te = world.getTileEntity(pos);
        if (BotaniaReflectionHelper.isInstance(BotaniaReflectionHelper.CLASS_TILE_RUNE_ALTAR, te)) {
            return startProcessRuneAltar(world, pos, te);
        } else if (BotaniaReflectionHelper.isInstance(BotaniaReflectionHelper.CLASS_TILE_ALTAR, te)) {
            return startProcessAltar(world, pos, te);
        }
        return true;
    }

    @Override
    public List<ItemStack> collectProducts(World world, BlockPos pos, IAEItemStack[] expectedOutputs, List<ItemStack> inputs, IActionSource source, TargetSession session) {
        TileEntity te = world.getTileEntity(pos);
        if (BotaniaReflectionHelper.isInstance(BotaniaReflectionHelper.CLASS_TILE_RUNE_ALTAR, te)) {
            return collectProductsRuneAltar(world, pos, expectedOutputs);
        } else if (BotaniaReflectionHelper.isInstance(BotaniaReflectionHelper.CLASS_TILE_ALTAR, te)) {
            return collectProductsAltar(world, pos);
        } else {
            return collectMatchingEntityItems(world, pos, expectedOutputs);
        }
    }

    @Override
    public List<ItemStack> revertMaterials(World world, BlockPos pos, IActionSource source, TargetSession session) {
        TileEntity te = world.getTileEntity(pos);
//         AE2Enhanced.LOGGER.debug("[AE2E-Botania] revertMaterials at {} te={}", pos, te != null ? te.getClass().getSimpleName() : "null");
        if (BotaniaReflectionHelper.isInstance(BotaniaReflectionHelper.CLASS_TILE_RUNE_ALTAR, te)) {
            List<ItemStack> result = new ArrayList<>();
            IItemHandler handler = BotaniaReflectionHelper.runeAltarGetItemHandler(te);
            if (handler != null) {
                for (int i = 0; i < handler.getSlots(); i++) {
                    ItemStack stack = handler.extractItem(i, 64, false);
                    if (!stack.isEmpty()) result.add(stack);
                }
            }
//             AE2Enhanced.LOGGER.debug("[AE2E-Botania] revertMaterials RuneAltar: {} items at {}", result.size(), pos);
            return result;
        } else if (BotaniaReflectionHelper.isInstance(BotaniaReflectionHelper.CLASS_TILE_ALTAR, te)) {
            List<ItemStack> result = new ArrayList<>();
            IItemHandler handler = BotaniaReflectionHelper.altarGetItemHandler(te);
            if (handler != null) {
                for (int i = 0; i < handler.getSlots(); i++) {
                    ItemStack stack = handler.extractItem(i, 64, false);
                    if (!stack.isEmpty()) result.add(stack);
                }
            }
            // 兜底：回收 AABB 内可能残留的输入 EntityItem(正常不应存在)
            for (EntityItem item : getEntityItemsInAABB(world, pos)) {
                if (!item.isDead && !item.getItem().isEmpty() && item.getEntityData().getBoolean(TAG_INPUT_FLAG)) {
                    result.add(item.getItem().copy());
                    item.setDead();
                }
            }
//             AE2Enhanced.LOGGER.debug("[AE2E-Botania] revertMaterials Altar: {} items at {}", result.size(), pos);
            return result;
        }
        // 对于 Pool / AlfPortal / TerraPlate 等通过 EntityItem 推料的设备,
        // 只回收带 TAG_INPUT_FLAG 标记的未消耗输入,不回收产物
        List<ItemStack> fallback = new ArrayList<>();
        for (EntityItem item : getEntityItemsInAABB(world, pos)) {
            boolean isInput = item.getEntityData().getBoolean(TAG_INPUT_FLAG);
//             AE2Enhanced.LOGGER.debug("[AE2E-Botania] revertMaterials scan at {}: {} inputFlag={} dead={}",
//                     pos, item.getItem(), isInput, item.isDead);
            if (!item.isDead && !item.getItem().isEmpty() && isInput) {
                fallback.add(item.getItem().copy());
                item.setDead();
            }
        }
//         AE2Enhanced.LOGGER.debug("[AE2E-Botania] revertMaterials Pool/Portal/Terra: {} items at {}", fallback.size(), pos);
        return fallback;
    }

    @Override
    public boolean isIdle(World world, BlockPos pos, List<ItemStack> inputs, TargetSession session) {
        TileEntity te = world.getTileEntity(pos);
        if (BotaniaReflectionHelper.isInstance(BotaniaReflectionHelper.CLASS_TILE_POOL, te)) {
            return isIdlePool(world, pos, session);
        } else if (BotaniaReflectionHelper.isInstance(BotaniaReflectionHelper.CLASS_TILE_ALF_PORTAL, te)) {
            return isIdleAlfPortal(world, pos, session);
        } else if (BotaniaReflectionHelper.isInstance(BotaniaReflectionHelper.CLASS_TILE_TERRA_PLATE, te)) {
            return isIdleTerraPlate(world, pos, session);
        } else if (BotaniaReflectionHelper.isInstance(BotaniaReflectionHelper.CLASS_TILE_RUNE_ALTAR, te)) {
            return isIdleRuneAltar(world, pos, te, session);
        } else if (BotaniaReflectionHelper.isInstance(BotaniaReflectionHelper.CLASS_TILE_ALTAR, te)) {
            return isIdleAltar(world, pos, te, session);
        }
        return true;
    }

    private boolean isGraceElapsed(TargetSession session, long currentWorldTime, int graceTicks) {
        return session == null || session.isPushGraceElapsed(currentWorldTime, graceTicks);
    }

    // ---- IVirtualCraftingHandler / IVirtualBatchCraftingHandler ----

    @Override
    public boolean canCraftVirtually(World world, BlockPos pos, InventoryCrafting ingredients, IAEItemStack[] outputs) {
        TileEntity te = world.getTileEntity(pos);
        if (BotaniaReflectionHelper.isInstance(BotaniaReflectionHelper.CLASS_TILE_POOL, te)) {
            return canCraftVirtuallyPool(world, pos, ingredients, outputs);
        } else if (BotaniaReflectionHelper.isInstance(BotaniaReflectionHelper.CLASS_TILE_ALF_PORTAL, te)) {
            return canCraftVirtuallyAlfPortal(world, pos, ingredients, outputs);
        } else if (BotaniaReflectionHelper.isInstance(BotaniaReflectionHelper.CLASS_TILE_TERRA_PLATE, te)) {
            return canCraftVirtuallyTerraPlate(world, pos, ingredients, outputs);
        } else if (BotaniaReflectionHelper.isInstance(BotaniaReflectionHelper.CLASS_TILE_RUNE_ALTAR, te)) {
            return canCraftVirtuallyRuneAltar(world, pos, ingredients, outputs);
        } else if (BotaniaReflectionHelper.isInstance(BotaniaReflectionHelper.CLASS_TILE_ALTAR, te)) {
            return canCraftVirtuallyAltar(world, pos, ingredients, outputs);
        }
        return false;
    }

    public List<ItemStack> virtualCraft(World world, BlockPos pos, InventoryCrafting ingredients, IAEItemStack[] outputs, IActionSource source) {
        return virtualCraftBatch(world, pos, ingredients, outputs, 1, source);
    }

    @Override
    public List<EnumParticleTypes> getVirtualCraftingParticles(World world, BlockPos pos) {
        return Arrays.asList(
                EnumParticleTypes.SPELL_WITCH,
                EnumParticleTypes.PORTAL,
                EnumParticleTypes.ENCHANTMENT_TABLE,
                EnumParticleTypes.END_ROD
        );
    }

    @Override
    public List<IAEStack> getVirtualCost(World world, BlockPos pos, InventoryCrafting ingredients, IAEItemStack[] outputs, long count) {
        TileEntity te = world.getTileEntity(pos);
        if (BotaniaReflectionHelper.isInstance(BotaniaReflectionHelper.CLASS_TILE_POOL, te)) {
            return getVirtualCostPool(world, pos, ingredients, outputs, count);
        } else if (BotaniaReflectionHelper.isInstance(BotaniaReflectionHelper.CLASS_TILE_ALF_PORTAL, te)) {
            return getVirtualCostAlfPortal(world, pos, ingredients, outputs, count);
        } else if (BotaniaReflectionHelper.isInstance(BotaniaReflectionHelper.CLASS_TILE_TERRA_PLATE, te)) {
            return getVirtualCostTerraPlate(world, pos, ingredients, outputs, count);
        } else if (BotaniaReflectionHelper.isInstance(BotaniaReflectionHelper.CLASS_TILE_RUNE_ALTAR, te)) {
            return getVirtualCostRuneAltar(world, pos, ingredients, outputs, count);
        } else if (BotaniaReflectionHelper.isInstance(BotaniaReflectionHelper.CLASS_TILE_ALTAR, te)) {
            return getVirtualCostAltar(world, pos, ingredients, outputs, count);
        }
        return new ArrayList<>();
    }

    @Override
    public List<ItemStack> virtualCraftBatch(World world, BlockPos pos, InventoryCrafting ingredients, IAEItemStack[] outputs, long count, IActionSource source) {
        List<ItemStack> products = new ArrayList<>();
        if (!canCraftVirtually(world, pos, ingredients, outputs)) return products;
        return scaleOutputsByCount(outputs, count);
    }

    // ==================== Pool ====================

    private boolean canStartPool(World world, BlockPos pos, Object pool, InventoryCrafting ingredients) {
        int totalManaNeeded = 0;
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            Object recipe = BotaniaReflectionHelper.getPoolMatchingRecipe(stack, world.getBlockState(pos.down()));
            if (recipe == null) {
//                 AE2Enhanced.LOGGER.debug("[AE2E-Botania] canStartPool failed: no recipe for {} at {}", stack, pos);
                return false;
            }
            totalManaNeeded += BotaniaReflectionHelper.manaInfusionGetManaToConsume(recipe) * stack.getCount();
        }
        boolean manaOk = BotaniaReflectionHelper.getPoolCurrentMana(pool) >= totalManaNeeded;
        if (!manaOk) {
//             AE2Enhanced.LOGGER.debug("[AE2E-Botania] canStartPool failed: need {} mana, pool has {} at {}",
//                     totalManaNeeded, pool.getCurrentMana(), pos);
        } else {
//             AE2Enhanced.LOGGER.debug("[AE2E-Botania] canStartPool ok: need {} mana, pool has {} at {}",
//                     totalManaNeeded, pool.getCurrentMana(), pos);
        }
        return manaOk;
    }

    private boolean pushMaterialsPool(World world, BlockPos pos, Object pool, InventoryCrafting ingredients) {
//         AE2Enhanced.LOGGER.debug("[AE2E-Botania] pushMaterialsPool start at {}", pos);

        // 清理 AABB 中已有的中枢输入标记实体,防止实体堆叠干扰 collideEntityItem
        for (EntityItem existing : getEntityItemsInAABB(world, pos)) {
            if (!existing.isDead && existing.getEntityData().getBoolean(TAG_INPUT_FLAG)) {
                existing.setDead();
//                 AE2Enhanced.LOGGER.debug("[AE2E-Botania] pushMaterialsPool cleaned leftover input entity: {} at {}",
//                         existing.getItem(), pos);
            }
        }

        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
//             AE2Enhanced.LOGGER.debug("[AE2E-Botania] pushMaterialsPool slot {}: stack=[count={}, item={}, class={}, hash={}, empty={}]",
//                     i, stack.getCount(), stack.getItem(), stack.getClass().getName(), System.identityHashCode(stack), stack.isEmpty());
            if (stack.isEmpty()) continue;

            // 每次只推送 1 个,collideEntityItem 每次消耗 1 个
            ItemStack remaining = stack.copy();
//             AE2Enhanced.LOGGER.debug("[AE2E-Botania] pushMaterialsPool slot {}: remaining copy=[count={}, item={}, hash={}, empty={}]",
//                     i, remaining.getCount(), remaining.getItem(), System.identityHashCode(remaining), remaining.isEmpty());
            while (!remaining.isEmpty()) {
                int countBefore = remaining.getCount();
                ItemStack single = remaining.splitStack(1);
//                 AE2Enhanced.LOGGER.debug("[AE2E-Botania] pushMaterialsPool slot {} split: beforeCount={}, afterCount={}, single=[count={}, item={}, hash={}, empty={}]",
//                         i, countBefore, remaining.getCount(), single.getCount(), single.getItem(), System.identityHashCode(single), single.isEmpty());
                if (single.isEmpty()) {
                    AE2Enhanced.LOGGER.warn("[AE2E-Botania] pushMaterialsPool: splitStack returned empty! Aborting slot. remainingCount={}, remainingItem={}",
                            remaining.getCount(), remaining.getItem());
                    break;
                }
                EntityItem entityItem = new EntityItem(world, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, single);
                // 标记为中枢输入,使 revertMaterials 能区分未消耗输入与产物
                entityItem.getEntityData().setBoolean(TAG_INPUT_FLAG, true);
                // 注意：Botania TilePool.collideEntityItem 会拒绝 100 < pickupDelay < 130 的物品
                // (视为玩家刚投掷的物品).EntityItem 默认 pickupDelay=10,不在拒绝区间内.
                entityItem.motionX = 0;
                entityItem.motionY = 0;
                entityItem.motionZ = 0;
                world.spawnEntity(entityItem);  // 必须加入世界,否则产物不会出现在世界中
//                 AE2Enhanced.LOGGER.debug("[AE2E-Botania] pushMaterialsPool spawned entityItem: item={} hash={}",
//                         entityItem.getItem(), System.identityHashCode(entityItem));

                boolean consumed = BotaniaReflectionHelper.poolCollideEntityItem(pool, entityItem);
                // WORKAROUND: 某些整合包中 collideEntityItem 执行了成功路径(shrink+spawn产物)
                // 但返回值被某个未知模组篡改为 false.以 entityItem.item 是否被 shrink 空作为成功判据.
                boolean actuallyConsumed = consumed || entityItem.getItem().isEmpty();
                if (!actuallyConsumed) {
                    int actualPickupDelay = 10;
                    try {
                        actualPickupDelay = (Integer) net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(
                                EntityItem.class, entityItem, "field_145804_b", "pickupDelay");
                    } catch (Exception ignored) {}
//                     AE2Enhanced.LOGGER.debug("[AE2E-Botania] pushMaterialsPool failed: collideEntityItem refused {} (item={} dead={} pickupDelay={}) at {} (mana={})",
//                             single, entityItem.getItem(), entityItem.isDead, actualPickupDelay, pos, pool.getCurrentMana());
                    // 清理已 spawn 的实体,避免空实体堆积
                    if (!entityItem.isDead) {
                        entityItem.setDead();
                    }
                    return false;
                }
                // collideEntityItem 成功后：
                // Botania 标准行为：原 EntityItem 的 item 被 shrink(1) 清空,产物 spawn 到新 EntityItem
                // 因此原 entityItem.getItem() 必然为空.直接 setDead 清理即可.
                if (!entityItem.getItem().isEmpty()) {
                    // 防御性处理：如果某个魔改版本把产物塞回了原 EntityItem
                    ItemStack product = entityItem.getItem();
//                     AE2Enhanced.LOGGER.debug("[AE2E-Botania] pushMaterialsPool product kept in original entity: {} -> {} at {}",
//                             single, product, pos);
                    entityItem.getEntityData().removeTag(TAG_INPUT_FLAG);
                } else {
                    entityItem.setDead();
                }
            }
        }
//         AE2Enhanced.LOGGER.debug("[AE2E-Botania] pushMaterialsPool success at {}", pos);
        return true;
    }

    private boolean isIdlePool(World world, BlockPos pos, TargetSession session) {
        if (!isGraceElapsed(session, world.getTotalWorldTime(), PUSH_IDLE_GRACE_TICKS)) return false;
        // 空闲判定：没有带 TAG_INPUT_FLAG 的未处理输入即可。
        List<EntityItem> items = getEntityItemsInAABB(world, pos);
        for (EntityItem item : items) {
            if (item.isDead) continue;
            if (item.getEntityData().getBoolean(TAG_INPUT_FLAG)) {
                return false;
            }
        }
        return true;
    }

    // ==================== Alf Portal ====================

    private boolean canStartAlfPortal(World world, BlockPos pos, Object portal, InventoryCrafting ingredients) {
        Object state = BotaniaReflectionHelper.getAlfPortalState(world.getBlockState(pos));
        if (state == BotaniaReflectionHelper.ALFPORTAL_STATE_OFF) return false;

        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (!isValidElvenTradeInput(stack)) return false;
        }
        return true;
    }

    private boolean pushMaterialsAlfPortal(World world, BlockPos pos, Object portal, InventoryCrafting ingredients) {
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            EntityItem entityItem = new EntityItem(world, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, stack.copy());
            entityItem.setNoPickupDelay();
            entityItem.motionX = 0;
            entityItem.motionY = 0;
            entityItem.motionZ = 0;
            entityItem.getEntityData().setBoolean(TAG_INPUT_FLAG, true);
            world.spawnEntity(entityItem);
        }
        return true;
    }

    private boolean isIdleAlfPortal(World world, BlockPos pos, TargetSession session) {
        // 推料后至少等待 grace，让精灵门有时间把输入传走并返回产物
        if (!isGraceElapsed(session, world.getTotalWorldTime(), ALF_PORTAL_GRACE_TICKS)) return false;
        // 只要有非输入标记的实体（产物）就视为可收集；AABB 为空时 grace 已过后也视为 idle
        List<EntityItem> items = getEntityItemsInAABB(world, pos);
        for (EntityItem item : items) {
            if (item.isDead) continue;
            if (!item.getEntityData().getBoolean(TAG_INPUT_FLAG)) {
                return true;
            }
        }
        return true;
    }

    // ==================== Terra Plate ====================

    private boolean canStartTerraPlate(World world, BlockPos pos, Object plate, InventoryCrafting ingredients) {
        // 检查平台结构
        if (BotaniaReflectionHelper.METHOD_HAS_VALID_PLATFORM != null) {
            try {
                Boolean valid = (Boolean) BotaniaReflectionHelper.METHOD_HAS_VALID_PLATFORM.invoke(plate);
                if (!valid) return false;
            } catch (Exception e) {
                return false;
            }
        }
        // 检查板上是否已有物品
        if (BotaniaReflectionHelper.METHOD_GET_ITEMS != null) {
            try {
                List<?> items = (List<?>) BotaniaReflectionHelper.METHOD_GET_ITEMS.invoke(plate);
                if (!items.isEmpty()) return false;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    private boolean pushMaterialsTerraPlate(World world, BlockPos pos, Object plate, InventoryCrafting ingredients) {
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            EntityItem entityItem = new EntityItem(world, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, stack.copy());
            entityItem.setNoPickupDelay();
            entityItem.motionX = 0;
            entityItem.motionY = 0;
            entityItem.motionZ = 0;
            entityItem.getEntityData().setBoolean(TAG_INPUT_FLAG, true);
            world.spawnEntity(entityItem);
        }
        return true;
    }

    private boolean isIdleTerraPlate(World world, BlockPos pos, TargetSession session) {
        if (!isGraceElapsed(session, world.getTotalWorldTime(), PUSH_IDLE_GRACE_TICKS)) return false;
        List<EntityItem> items = getEntityItemsInAABB(world, pos);
        for (EntityItem item : items) {
            if (item.isDead) continue;
            if (item.getEntityData().getBoolean(TAG_INPUT_FLAG)) {
                return false;
            }
        }
        return true;
    }

    // ==================== Rune Altar ====================

    private boolean canStartRuneAltar(World world, BlockPos pos, Object altar, InventoryCrafting ingredients) {
        int cooldown = BotaniaReflectionHelper.getRuneAltarCooldown(altar);
        if (cooldown > 0) return false;
        if (!BotaniaReflectionHelper.runeAltarIsEmpty(altar)) return false;
        return true;
    }

    private boolean pushMaterialsRuneAltar(World world, BlockPos pos, Object altar, InventoryCrafting ingredients) {
        ItemStack livingrock = ItemStack.EMPTY;

        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            // 活石作为催化剂,不放入祭坛物品栏,而是丢在旁边
            if (stack.getItem() == Item.getItemFromBlock(BotaniaReflectionHelper.BLOCK_LIVINGROCK) && stack.getMetadata() == 0) {
                if (livingrock.isEmpty()) {
                    livingrock = stack.copy();
                } else {
                    livingrock.grow(stack.getCount());
                }
                continue;
            }

            // 其他物品逐个放入祭坛
            int count = stack.getCount();
            for (int c = 0; c < count; c++) {
                ItemStack single = stack.copy();
                single.setCount(1);
                boolean added = BotaniaReflectionHelper.runeAltarAddItem(altar, single);
                if (!added) return false;
            }
        }

        // 丢活石在旁边
        if (!livingrock.isEmpty()) {
            EntityItem entityItem = new EntityItem(world, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, livingrock);
            entityItem.setNoPickupDelay();
            entityItem.motionX = 0;
            entityItem.motionY = 0;
            entityItem.motionZ = 0;
            world.spawnEntity(entityItem);
        }

        return true;
    }

    private boolean startProcessRuneAltar(World world, BlockPos pos, Object altar) {
        int cooldown = BotaniaReflectionHelper.getRuneAltarCooldown(altar);
        if (cooldown > 0) return true;

        int mana = BotaniaReflectionHelper.getRuneAltarMana(altar);
        int manaToGet = BotaniaReflectionHelper.runeAltarGetManaToGet(altar);

        if (manaToGet <= 0 || mana < manaToGet) {
            return true; // 魔力不足,等待充能
        }

        // 查找匹配的配方
        Object recipe = null;
        Object currentRecipe = BotaniaReflectionHelper.getRuneAltarCurrentRecipe(altar);
        if (BotaniaReflectionHelper.isInstance(BotaniaReflectionHelper.CLASS_RECIPE_RUNE_ALTAR, currentRecipe)) {
            recipe = currentRecipe;
        } else {
            for (Object r : BotaniaReflectionHelper.RUNE_ALTAR_RECIPES) {
                if (BotaniaReflectionHelper.petalsMatches(r, BotaniaReflectionHelper.runeAltarGetItemHandler(altar))) {
                    recipe = r;
                    break;
                }
            }
        }

        if (recipe == null) return false;

        // 扫描活石
        List<EntityItem> items = getEntityItemsInAABB(world, pos);
        EntityItem livingrockItem = null;
        for (EntityItem item : items) {
            if (item.isDead) continue;
            ItemStack stack = item.getItem();
            if (stack.getItem() == Item.getItemFromBlock(BotaniaReflectionHelper.BLOCK_LIVINGROCK) && stack.getMetadata() == 0) {
                livingrockItem = item;
                break;
            }
        }

        if (livingrockItem == null) {
            return true; // 活石还没到,等待
        }

        // 执行合成核心逻辑(反射绕过 player 检查)
        try {
            int manaCost = BotaniaReflectionHelper.runeAltarRecipeGetManaUsage(recipe);
            BotaniaReflectionHelper.runeAltarRecieveMana(altar, -manaCost);

            ItemStack output = BotaniaReflectionHelper.petalsGetOutput(recipe).copy();
            EntityItem outputItem = new EntityItem(world, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, output);
            world.spawnEntity(outputItem);

            BotaniaReflectionHelper.setRuneAltarCurrentRecipe(altar, null);
            world.addBlockEvent(pos, BotaniaReflectionHelper.BLOCK_RUNE_ALTAR, 1, 60);
            world.addBlockEvent(pos, BotaniaReflectionHelper.BLOCK_RUNE_ALTAR, 2, 0);
            BotaniaReflectionHelper.runeAltarSaveLastRecipe(altar);

            // 清空物品栏
            IItemHandlerModifiable handler = BotaniaReflectionHelper.runeAltarGetItemHandler(altar);
            for (int i = 0; i < BotaniaReflectionHelper.runeAltarGetSizeInventory(altar); i++) {
                ItemStack stack = handler.getStackInSlot(i);
                if (stack.isEmpty()) continue;
                // 符文回收：非创造模式时符文会掉落
                if (stack.getItem() == BotaniaReflectionHelper.ITEM_RUNE) {
                    EntityItem runeItem = new EntityItem(world, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, stack.copy());
                    world.spawnEntity(runeItem);
                }
                handler.setStackInSlot(i, ItemStack.EMPTY);
            }

            // 消耗活石
            livingrockItem.getItem().shrink(1);
            if (livingrockItem.getItem().isEmpty()) {
                livingrockItem.setDead();
            }

            return true;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] RuneAltar crafting failed", e);
            return false;
        }
    }

    private boolean isIdleRuneAltar(World world, BlockPos pos, Object altar, TargetSession session) {
        if (!isGraceElapsed(session, world.getTotalWorldTime(), PUSH_IDLE_GRACE_TICKS)) return false;
        int cooldown = BotaniaReflectionHelper.getRuneAltarCooldown(altar);
        // cooldown > 0 表示合成刚完成,正在冷却
        if (cooldown > 0) return true;

        // 祭坛为空说明可以接收下一次发配
        if (BotaniaReflectionHelper.runeAltarIsEmpty(altar)) {
            return true;
        }

        // 祭坛内有物品且 manaToGet > 0,说明正在充能/合成,不 idle
        if (BotaniaReflectionHelper.runeAltarGetManaToGet(altar) > 0) {
            return false;
        }

        // 有物品但魔力已充满,startProcess 尚未完成,继续等待
        return false;
    }

    // ==================== Petal Apothecary (Altar) ====================

    /** Botania 内部用于识别种子的正则,与 TileAltar.SEED_PATTERN 一致 */
    private static final java.util.regex.Pattern SEED_PATTERN = java.util.regex.Pattern.compile(
            "(?:(?:(?:[A-Z-_.:]|^)seed)|(?:(?:[a-z-_.:]|^)Seed))(?:[sA-Z-_.:]|$)"
    );

    private boolean isSeed(ItemStack stack) {
        if (stack.isEmpty()) return false;
        // 与 TileAltar.collideEntityItem 一致,使用 ItemStack.getTranslationKey() (func_77977_a)
        String unlocalizedName = stack.getTranslationKey();
        return SEED_PATTERN.matcher(unlocalizedName).find();
    }

    private boolean canStartAltar(World world, BlockPos pos, Object altar, InventoryCrafting ingredients) {
        // 类似符文祭坛的严格前置检查
        if (!BotaniaReflectionHelper.altarIsEmpty(altar)) return false;
        if (BotaniaReflectionHelper.altarHasLava(altar)) return false;
        return findMatchingRecipe(ingredients) != null;
    }

    private boolean pushMaterialsAltar(World world, BlockPos pos, Object altar, InventoryCrafting ingredients) {
        // 自动填水：花药台合成必须有水,handler 自动保证
        if (!BotaniaReflectionHelper.altarHasWater(altar)) {
            BotaniaReflectionHelper.altarSetWater(altar, true);
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
        }

        // 收集并分类材料：种子 vs 非种子
        List<ItemStack> nonSeeds = new ArrayList<>();
        ItemStack seedStack = ItemStack.EMPTY;
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (isSeed(stack)) {
                if (seedStack.isEmpty()) {
                    seedStack = stack.copy();
                } else {
                    seedStack.grow(stack.getCount());
                }
            } else {
                nonSeeds.add(stack.copy());
            }
        }

        // 先推非种子材料(花瓣等),放入 itemHandler
        for (ItemStack stack : nonSeeds) {
            if (!pushItemToAltar(world, pos, altar, stack)) {
                return false;
            }
        }

        // 最后推种子,触发 collideEntityItem 中的配方匹配与合成
        // Botania 只需要 1 个种子作为触发剂
        if (!seedStack.isEmpty()) {
            ItemStack singleSeed = seedStack.copy();
            singleSeed.setCount(1);
            if (!pushItemToAltar(world, pos, altar, singleSeed)) {
                return false;
            }
        }

        return true;
    }

    /**
     * 将物品作为 EntityItem 直接送入花药台的 collideEntityItem.
     * 这样可精确控制处理顺序(非种子先、种子后),避免 AABB 扫描的不确定性.
     */
    private boolean pushItemToAltar(World world, BlockPos pos, Object altar, ItemStack stack) {
        while (!stack.isEmpty()) {
            ItemStack single = stack.splitStack(1);
            EntityItem entityItem = new EntityItem(world, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, single);
            entityItem.setNoPickupDelay();
            entityItem.motionX = 0;
            entityItem.motionY = 0;
            entityItem.motionZ = 0;
            entityItem.getEntityData().setBoolean(TAG_INPUT_FLAG, true);

            boolean consumed = BotaniaReflectionHelper.altarCollideEntityItem(altar, entityItem);
            if (!consumed) {
                entityItem.setDead();
                return false;
            }
            // collideEntityItem 成功：非种子已放入 itemHandler,或种子已触发合成
            // 若 stack 仍有残留(理论上不应发生),也视为异常
            if (!entityItem.getItem().isEmpty()) {
                entityItem.setDead();
                return false;
            }
            entityItem.setDead();
        }
        return true;
    }

    private boolean startProcessAltar(World world, BlockPos pos, Object altar) {
        // 花药台通过 collideEntityItem 自动触发合成,无需外部手动激活.
        // pushMaterialsAltar 中已按顺序调用 collideEntityItem,合成已即时完成.
        return true;
    }

    private boolean isIdleAltar(World world, BlockPos pos, Object altar, TargetSession session) {
        if (!isGraceElapsed(session, world.getTotalWorldTime(), PUSH_IDLE_GRACE_TICKS)) return false;
        // pushMaterialsAltar 已直接调用 collideEntityItem 并将输入 EntityItem setDead,
        // 因此 AABB 中只会留下 Botania 生成的产物 EntityItem.
        List<EntityItem> items = getEntityItemsInAABB(world, pos);
        for (EntityItem item : items) {
            if (item.isDead) continue;
            // 跳过尚未处理的输入(兜底,理论上不应存在)
            if (item.getEntityData().getBoolean(TAG_INPUT_FLAG)) continue;
            return true;
        }
        return false;
    }

    private Object findMatchingRecipe(InventoryCrafting ingredients) {
        // 提取非空物品到临时 IItemHandler
        ItemStackHandler temp = new ItemStackHandler(16);
        int slot = 0;
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (!stack.isEmpty()) {
                temp.setStackInSlot(slot++, stack.copy());
            }
        }
        if (slot == 0) return null;
        return findMatchingRecipe(temp);
    }

    private Object findMatchingRecipe(net.minecraftforge.items.IItemHandler handler) {
        for (Object recipe : BotaniaReflectionHelper.PETAL_RECIPES) {
            if (BotaniaReflectionHelper.petalsMatches(recipe, handler)) {
                return recipe;
            }
        }
        return null;
    }

    // ---- 批量虚拟合成辅助方法 ----

    // -------------------- Pool --------------------

    private boolean canCraftVirtuallyPool(World world, BlockPos pos, InventoryCrafting ingredients, IAEItemStack[] outputs) {
        if (outputs == null || outputs.length == 0 || outputs[0] == null) return false;
        Object recipe = findManaInfusionRecipeByOutput(outputs[0].createItemStack());
        if (recipe == null) return false;
        Object inputObj = BotaniaReflectionHelper.manaInfusionGetInput(recipe);
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (!stack.isEmpty() && matchesRecipeObject(inputObj, stack)) return true;
        }
        return false;
    }

    private List<IAEStack> getVirtualCostPool(World world, BlockPos pos, InventoryCrafting ingredients, IAEItemStack[] outputs, long count) {
        List<IAEStack> costs = new ArrayList<>();
        if (outputs == null || outputs.length == 0 || outputs[0] == null) return costs;
        Object recipe = findManaInfusionRecipeByOutput(outputs[0].createItemStack());
        if (recipe == null) return costs;
        Object inputObj = BotaniaReflectionHelper.manaInfusionGetInput(recipe);
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (!stack.isEmpty() && matchesRecipeObject(inputObj, stack)) {
                IAEItemStack cost = AEItemStack.fromItemStack(stack.copy());
                cost.setStackSize(count);
                costs.add(cost);
                break;
            }
        }
        costs.add(AEManaStack.create((long) BotaniaReflectionHelper.manaInfusionGetManaToConsume(recipe) * count));
        return costs;
    }

    private Object findManaInfusionRecipeByOutput(ItemStack output) {
        if (output.isEmpty()) return null;
        for (Object recipe : BotaniaReflectionHelper.MANA_INFUSION_RECIPES) {
            ItemStack recipeOutput = BotaniaReflectionHelper.manaInfusionGetOutput(recipe);
            if (!recipeOutput.isEmpty()
                    && recipeOutput.getItem() == output.getItem()
                    && recipeOutput.getMetadata() == output.getMetadata()) {
                return recipe;
            }
        }
        return null;
    }

    // -------------------- Alf Portal --------------------

    private boolean canCraftVirtuallyAlfPortal(World world, BlockPos pos, InventoryCrafting ingredients, IAEItemStack[] outputs) {
        if (outputs == null || outputs.length == 0 || outputs[0] == null) return false;
        Object recipe = findElvenTradeRecipeByOutput(outputs[0].createItemStack());
        if (recipe == null) return false;
        return matchRecipeInputs(BotaniaReflectionHelper.elvenTradeGetInputs(recipe), collectNonEmpty(ingredients));
    }

    private List<IAEStack> getVirtualCostAlfPortal(World world, BlockPos pos, InventoryCrafting ingredients, IAEItemStack[] outputs, long count) {
        List<IAEStack> costs = new ArrayList<>();
        if (outputs == null || outputs.length == 0 || outputs[0] == null) return costs;
        Object recipe = findElvenTradeRecipeByOutput(outputs[0].createItemStack());
        if (recipe == null) return costs;
        List<ItemStack> available = collectNonEmpty(ingredients);
        for (Object req : BotaniaReflectionHelper.elvenTradeGetInputs(recipe)) {
            ItemStack consumed = consumeFromAvailable(available, req);
            if (consumed.isEmpty()) continue;
            IAEItemStack cost = AEItemStack.fromItemStack(consumed);
            cost.setStackSize((long) consumed.getCount() * count);
            costs.add(cost);
        }
        return costs;
    }

    private Object findElvenTradeRecipeByOutput(ItemStack output) {
        if (output.isEmpty()) return null;
        for (Object recipe : BotaniaReflectionHelper.ELVEN_TRADE_RECIPES) {
            for (Object outputObj : BotaniaReflectionHelper.elvenTradeGetOutputs(recipe)) {
                ItemStack recipeOutput = (ItemStack) outputObj;
                if (!recipeOutput.isEmpty()
                        && recipeOutput.getItem() == output.getItem()
                        && recipeOutput.getMetadata() == output.getMetadata()) {
                    return recipe;
                }
            }
        }
        return null;
    }

    // -------------------- Terra Plate --------------------

    private static final long TERRA_PLATE_MANA_COST = 500000L;

    private boolean canCraftVirtuallyTerraPlate(World world, BlockPos pos, InventoryCrafting ingredients, IAEItemStack[] outputs) {
        if (outputs == null || outputs.length == 0 || outputs[0] == null) return false;
        ItemStack expected = outputs[0].createItemStack();
        if (expected.getItem() != BotaniaReflectionHelper.ITEM_MANA_RESOURCE || expected.getMetadata() != 4) return false;
        boolean hasSteel = false, hasPearl = false, hasDiamond = false;
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (stack.isEmpty() || stack.getItem() != BotaniaReflectionHelper.ITEM_MANA_RESOURCE) continue;
            int meta = stack.getMetadata();
            if (meta == 0) hasSteel = true;
            else if (meta == 1) hasPearl = true;
            else if (meta == 2) hasDiamond = true;
        }
        return hasSteel && hasPearl && hasDiamond;
    }

    private List<IAEStack> getVirtualCostTerraPlate(World world, BlockPos pos, InventoryCrafting ingredients, IAEItemStack[] outputs, long count) {
        List<IAEStack> costs = new ArrayList<>();
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (stack.isEmpty() || stack.getItem() != BotaniaReflectionHelper.ITEM_MANA_RESOURCE) continue;
            int meta = stack.getMetadata();
            if (meta == 0 || meta == 1 || meta == 2) {
                IAEItemStack cost = AEItemStack.fromItemStack(stack.copy());
                cost.setStackSize(count);
                costs.add(cost);
            }
        }
        costs.add(AEManaStack.create(TERRA_PLATE_MANA_COST * count));
        return costs;
    }

    // -------------------- Rune Altar --------------------

    private boolean canCraftVirtuallyRuneAltar(World world, BlockPos pos, InventoryCrafting ingredients, IAEItemStack[] outputs) {
        if (outputs == null || outputs.length == 0 || outputs[0] == null) return false;
        Object recipe = findRuneAltarRecipeByOutput(outputs[0].createItemStack());
        if (recipe == null) return false;
        List<ItemStack> available = collectNonEmpty(ingredients);
        ItemStack livingrockTemplate = new ItemStack(Item.getItemFromBlock(BotaniaReflectionHelper.BLOCK_LIVINGROCK), 1, 0);
        boolean hasLivingrock = !consumeFromAvailable(available, livingrockTemplate).isEmpty();
        return hasLivingrock && matchRecipeInputs(BotaniaReflectionHelper.petalsGetInputs(recipe), available);
    }

    private List<IAEStack> getVirtualCostRuneAltar(World world, BlockPos pos, InventoryCrafting ingredients, IAEItemStack[] outputs, long count) {
        List<IAEStack> costs = new ArrayList<>();
        if (outputs == null || outputs.length == 0 || outputs[0] == null) return costs;
        Object recipe = findRuneAltarRecipeByOutput(outputs[0].createItemStack());
        if (recipe == null) return costs;

        List<ItemStack> available = collectNonEmpty(ingredients);
        // 先扣活石
        ItemStack livingrockTemplate = new ItemStack(Item.getItemFromBlock(BotaniaReflectionHelper.BLOCK_LIVINGROCK), 1, 0);
        ItemStack livingrock = consumeFromAvailable(available, livingrockTemplate);
        if (!livingrock.isEmpty()) {
            IAEItemStack cost = AEItemStack.fromItemStack(livingrock);
            cost.setStackSize((long) livingrock.getCount() * count);
            costs.add(cost);
        }

        // 再扣配方输入
        for (Object req : BotaniaReflectionHelper.petalsGetInputs(recipe)) {
            ItemStack consumed = consumeFromAvailable(available, req);
            if (consumed.isEmpty()) continue;
            IAEItemStack cost = AEItemStack.fromItemStack(consumed);
            cost.setStackSize((long) consumed.getCount() * count);
            costs.add(cost);
        }

        costs.add(AEManaStack.create((long) BotaniaReflectionHelper.runeAltarRecipeGetManaUsage(recipe) * count));
        return costs;
    }

    private Object findRuneAltarRecipeByOutput(ItemStack output) {
        if (output.isEmpty()) return null;
        for (Object recipe : BotaniaReflectionHelper.RUNE_ALTAR_RECIPES) {
            ItemStack recipeOutput = BotaniaReflectionHelper.petalsGetOutput(recipe);
            if (!recipeOutput.isEmpty()
                    && recipeOutput.getItem() == output.getItem()
                    && recipeOutput.getMetadata() == output.getMetadata()) {
                return recipe;
            }
        }
        return null;
    }

    // -------------------- Petal Apothecary --------------------

    private boolean canCraftVirtuallyAltar(World world, BlockPos pos, InventoryCrafting ingredients, IAEItemStack[] outputs) {
        if (outputs == null || outputs.length == 0 || outputs[0] == null) return false;
        Object recipe = findPetalRecipeByOutput(outputs[0].createItemStack());
        if (recipe == null) return false;
        List<ItemStack> available = new ArrayList<>();
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (!stack.isEmpty() && !isSeed(stack)) available.add(stack.copy());
        }
        return matchRecipeInputs(BotaniaReflectionHelper.petalsGetInputs(recipe), available);
    }

    private List<IAEStack> getVirtualCostAltar(World world, BlockPos pos, InventoryCrafting ingredients, IAEItemStack[] outputs, long count) {
        List<IAEStack> costs = new ArrayList<>();
        if (outputs == null || outputs.length == 0 || outputs[0] == null) return costs;
        Object recipe = findPetalRecipeByOutput(outputs[0].createItemStack());
        if (recipe == null) return costs;
        List<ItemStack> available = new ArrayList<>();
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (!stack.isEmpty() && !isSeed(stack)) available.add(stack.copy());
        }
        for (Object req : BotaniaReflectionHelper.petalsGetInputs(recipe)) {
            ItemStack consumed = consumeFromAvailable(available, req);
            if (consumed.isEmpty()) continue;
            IAEItemStack cost = AEItemStack.fromItemStack(consumed);
            cost.setStackSize((long) consumed.getCount() * count);
            costs.add(cost);
        }
        return costs;
    }

    private Object findPetalRecipeByOutput(ItemStack output) {
        if (output.isEmpty()) return null;
        for (Object recipe : BotaniaReflectionHelper.PETAL_RECIPES) {
            ItemStack recipeOutput = BotaniaReflectionHelper.petalsGetOutput(recipe);
            if (!recipeOutput.isEmpty()
                    && recipeOutput.getItem() == output.getItem()
                    && recipeOutput.getMetadata() == output.getMetadata()) {
                return recipe;
            }
        }
        return null;
    }

    // -------------------- Common virtual helpers --------------------

    private List<ItemStack> collectNonEmpty(InventoryCrafting ingredients) {
        List<ItemStack> list = new ArrayList<>();
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (!stack.isEmpty()) list.add(stack.copy());
        }
        return list;
    }

    private boolean matchRecipeInputs(List<?> required, List<ItemStack> available) {
        for (Object req : required) {
            if (consumeFromAvailable(available, req).isEmpty()) return false;
        }
        return available.isEmpty();
    }

    /**
     * 从 available 中按配方输入 req 消耗一份物品.
     *
     * <p>按需求量逐个缩减而非整栈移除,支持同种物品堆叠在同一槽位
     * (例如配方需要 2 个相同花瓣而样板将其堆叠在单个槽位).</p>
     *
     * @param available 可用物品列表(会被就地修改)
     * @param req       配方输入(ItemStack 或 oreDict 字符串)
     * @return 实际消耗的物品(count 为每份消耗量);无匹配或数量不足返回 {@link ItemStack#EMPTY}
     */
    private ItemStack consumeFromAvailable(List<ItemStack> available, Object req) {
        int needed = 1;
        if (req instanceof ItemStack) {
            needed = Math.max(1, ((ItemStack) req).getCount());
        }
        for (int i = 0; i < available.size(); i++) {
            ItemStack stack = available.get(i);
            if (!matchesRecipeObject(req, stack) || stack.getCount() < needed) continue;
            ItemStack consumed = stack.copy();
            consumed.setCount(needed);
            stack.shrink(needed);
            if (stack.isEmpty()) available.remove(i);
            return consumed;
        }
        return ItemStack.EMPTY;
    }

    private boolean matchesRecipeObject(Object obj, ItemStack stack) {
        if (obj instanceof String) {
            for (ItemStack ore : OreDictionary.getOres((String) obj)) {
                if (OreDictionary.itemMatches(ore, stack, false)) return true;
            }
            return false;
        } else if (obj instanceof ItemStack) {
            ItemStack target = (ItemStack) obj;
            return stack.getItem() == target.getItem() && stack.getMetadata() == target.getMetadata();
        }
        return false;
    }

    // ==================== Common Helpers ====================

    private List<EntityItem> getEntityItemsInAABB(World world, BlockPos pos) {
        return world.getEntitiesWithinAABB(EntityItem.class,
                new AxisAlignedBB(pos, pos.add(1, 2, 1)));
    }

    /**
     * 收集花药台 AABB 内的所有产物 EntityItem.
     * pushMaterialsAltar 已直接调用 collideEntityItem 并清理输入 EntityItem,
     * 因此 AABB 中遗留的 EntityItem 均可视为产物.
     */
    private List<ItemStack> collectProductsAltar(World world, BlockPos pos) {
        List<EntityItem> items = getEntityItemsInAABB(world, pos);
        List<ItemStack> collected = new ArrayList<>();
        for (EntityItem entityItem : new ArrayList<>(items)) {
            if (entityItem.isDead) continue;
            if (entityItem.getEntityData().getBoolean(TAG_INPUT_FLAG)) continue;
            ItemStack stack = entityItem.getItem();
            if (stack.isEmpty()) continue;
            collected.add(stack.copy());
            entityItem.setDead();
        }
        return collected;
    }

    private List<ItemStack> collectMatchingEntityItems(World world, BlockPos pos, IAEItemStack[] expectedOutputs) {
        List<EntityItem> items = getEntityItemsInAABB(world, pos);
        List<ItemStack> collected = new ArrayList<>();
        if (expectedOutputs == null || expectedOutputs.length == 0) {
//             AE2Enhanced.LOGGER.debug("[AE2E-Botania] collectMatchingEntityItems: no expectedOutputs at {}", pos);
            return collected;
        }

        for (EntityItem entityItem : new ArrayList<>(items)) {
            if (entityItem.isDead) continue;
            // 跳过未消耗的输入,只收集产物
            if (entityItem.getEntityData().getBoolean(TAG_INPUT_FLAG)) continue;
            ItemStack stack = entityItem.getItem();
            if (stack.isEmpty()) continue;

            boolean matched = false;
            for (IAEItemStack expected : expectedOutputs) {
                if (expected == null) continue;
                ItemStack expectedStack = expected.createItemStack();
                if (ItemStack.areItemsEqual(stack, expectedStack)) {
                    int toCollect = Math.min(expectedStack.getCount(), stack.getCount());
                    if (toCollect >= stack.getCount()) {
                        collected.add(stack.copy());
                        entityItem.setDead();
                    } else {
                        ItemStack taken = stack.splitStack(toCollect);
                        collected.add(taken);
                    }
                    matched = true;
//                     AE2Enhanced.LOGGER.debug("[AE2E-Botania] collectMatchingEntityItems matched: {} -> collect {} at {}",
//                             stack, toCollect, pos);
                    break;
                }
            }
            if (!matched) {
//                 AE2Enhanced.LOGGER.debug("[AE2E-Botania] collectMatchingEntityItems unmatched: {} at {}", stack, pos);
            }
        }
//         AE2Enhanced.LOGGER.debug("[AE2E-Botania] collectMatchingEntityItems result: {} items at {}", collected.size(), pos);
        return collected;
    }

    private List<ItemStack> collectProductsRuneAltar(World world, BlockPos pos, IAEItemStack[] expectedOutputs) {
        List<EntityItem> items = getEntityItemsInAABB(world, pos);
        List<ItemStack> collected = new ArrayList<>();

        for (EntityItem entityItem : new ArrayList<>(items)) {
            if (entityItem.isDead) continue;
            ItemStack stack = entityItem.getItem();
            if (stack.isEmpty()) continue;

            boolean collectedThis = false;

            // 1. 收集预期产物
            if (expectedOutputs != null) {
                for (IAEItemStack expected : expectedOutputs) {
                    if (expected == null) continue;
                    ItemStack expectedStack = expected.createItemStack();
                    if (ItemStack.areItemsEqual(stack, expectedStack)) {
                        collected.add(stack.copy());
                        entityItem.setDead();
                        collectedThis = true;
                        break;
                    }
                }
            }

            // 2. 收集返还物品(整合包可能有非符文返还；活石是催化剂不应收集)
            if (!collectedThis && !(stack.getItem() == Item.getItemFromBlock(BotaniaReflectionHelper.BLOCK_LIVINGROCK) && stack.getMetadata() == 0)) {
                collected.add(stack.copy());
                entityItem.setDead();
            }
        }
        return collected;
    }

    private boolean isValidElvenTradeInput(ItemStack stack) {
        if (stack.getItem() == BotaniaReflectionHelper.ITEM_LEXICON) return true;
        for (Object recipe : BotaniaReflectionHelper.ELVEN_TRADE_RECIPES) {
            for (Object o : BotaniaReflectionHelper.elvenTradeGetInputs(recipe)) {
                if (o instanceof String) {
                    for (ItemStack target : OreDictionary.getOres((String) o)) {
                        if (OreDictionary.itemMatches(target, stack, false)) return true;
                    }
                } else if (o instanceof ItemStack) {
                    ItemStack target = (ItemStack) o;
                    if (stack.getItem() == target.getItem() && stack.getMetadata() == target.getMetadata()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}

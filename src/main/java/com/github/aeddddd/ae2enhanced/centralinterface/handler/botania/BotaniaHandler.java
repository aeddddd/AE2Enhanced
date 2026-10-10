package com.github.aeddddd.ae2enhanced.centralinterface.handler.botania;

import com.github.aeddddd.ae2enhanced.centralinterface.TargetSession;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.centralinterface.HandlerUtils;
import com.github.aeddddd.ae2enhanced.centralinterface.IRemoteHandler;
import com.github.aeddddd.ae2enhanced.centralinterface.IVirtualBatchCraftingHandler;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.EntityScanThrottle;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
 *
 * <p><b>配方校验取舍</b>：物理路径不为“配方是否存在”做全表遍历。{@code canStart} 只检查
 * 设备自身的就绪状态(精灵门是否开启、泰拉凝聚板结构是否完整、符文祭坛是否空闲且未冷却、
 * 花药台是否为空且无岩浆)；配方能否成立改由设备自己在推料时决定。失败方式因此变为：</p>
 * <ul>
 *   <li>花药台在种子触发配方匹配失败时不产出，材料留在台内，会话保持 PROCESSING 直到
 *       {@code processingTimeoutTicks} 超时，再由 {@link #revertMaterials} 清空物品栏取回；</li>
 *   <li>魔力池在魔力不足时由 {@code collideEntityItem} 拒绝消耗，{@code pushMaterialsPool}
 *       当场返回 false：AE2 的 CPU 会把未被接受的整份材料原样注入回网络，因此被拒绝的那个
 *       输入实体必须销毁（否则 revertMaterials 会再取一份回网络，同一份材料变两份）；
 *       本批此前已成功转化的产物留在池面上、不带标记，由后续批次的"物品身份"兜底逐份回收。</li>
 * </ul>
 *
 * <h3>掉落物类设备的两个共同约定（魔力池 / 精灵门 / 泰拉凝聚板 / 符文祭坛的活石）</h3>
 * <ol>
 *   <li><b>输入实体一律 setInfinitePickupDelay()</b>。1.12.2 的
 *       {@code EntityItem#combineItems} 唯一的合并豁免条件是 {@code pickupDelay == Short.MAX_VALUE}：
 *       用 0 或默认 10 都会参与同型掉落物自动合并，本批输入被并入他自己人的堆叠后
 *       {@link #TAG_INPUT_FLAG} 会随之丢失（回退找不到、玩家可能直接捡走本批材料）。
 *       各设备的消费逻辑都不看 pickupDelay，{@code TilePool.collideEntityItem} 只拒绝
 *       {@code 100 < pickupDelay < 130}（玩家刚投掷的物品），32767 可以正常被消费。</li>
 *   <li><b>魔力池的产物必须由中枢自己打标记</b>。Botania 只给精灵门产物打
 *       {@code _elvenPortal}，池产物是无标记普通掉落物；中枢在推料同一 tick 给池心新出现的
 *       实体打 {@link #TAG_PRODUCT_FLAG}，收集(宽范围) 与"是否收完"({@link #hasFinished})
 *       都以它为准，避免用"物品身份"放宽范围时把玩家落在池边的同型物品收走。</li>
 * </ol>
 *
 * <p><b>精灵门的例外</b>：精灵门开启时，Botania 自身会吞掉 AABB 内所有未带
 * {@code _elvenPortal} 标记的物品，并且只有在 {@code validateItemUsage} 通过
 * (合成书或合法精灵交易输入)时才把它加入待交易列表，否则该物品实体直接被销毁。
 * {@code canStartAlfPortal} 不预校验“输入是否为合法精灵交易输入”，
 * 推入非法输入时物品会被 Botania 销毁，而不是由超时回退取回。</p>
 */
public class BotaniaHandler implements IRemoteHandler, IVirtualBatchCraftingHandler {

    /** 中枢推送的输入物品标记,用于 revertMaterials 区分未消耗输入与产物（包级可见：同包单测引用，避免写死字面量） */
    static final String TAG_INPUT_FLAG = "_ae2eInput";
    /**
     * 中枢在魔力池中生成的本批产物标记.
     *
     * <p>Botania 只给精灵门的产物打 {@code _elvenPortal} 标记,魔力池产物是完全无标记的普通掉落物,
     * 仅靠"物品身份"识别产物时无法区分"本批产物"与"玩家落在池面上的同型物品"。本标记由中枢在
     * 整批转化成功后的同一 tick 内打在池产物生成点上新出现的实体上(见 {@link #tagNewBatchProducts}),
     * 收集与"是否已收完"判定都以它为首要判据。</p>
     */
    static final String TAG_PRODUCT_FLAG = "_ae2eProduct";
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
            // 魔力池不预检配方与魔力：配方存在性与魔力是否够用改由 collideEntityItem 在推料时判定
            return true;
        } else if (BotaniaReflectionHelper.isInstance(BotaniaReflectionHelper.CLASS_TILE_ALF_PORTAL, te)) {
            return canStartAlfPortal(world, pos);
        } else if (BotaniaReflectionHelper.isInstance(BotaniaReflectionHelper.CLASS_TILE_TERRA_PLATE, te)) {
            return canStartTerraPlate(world, pos, te);
        } else if (BotaniaReflectionHelper.isInstance(BotaniaReflectionHelper.CLASS_TILE_RUNE_ALTAR, te)) {
            return canStartRuneAltar(world, pos, te);
        } else if (BotaniaReflectionHelper.isInstance(BotaniaReflectionHelper.CLASS_TILE_ALTAR, te)) {
            return canStartAltar(world, pos, te);
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
        } else if (BotaniaReflectionHelper.isInstance(BotaniaReflectionHelper.CLASS_TILE_POOL, te)) {
            // 魔力池：产物是无标记的普通掉落物，按"本机产物标记（宽范围）+ 物品身份（设备本列）"回收
            return collectProductsPool(world, pos, expectedOutputs);
        } else {
            return collectMatchingEntityItems(world, pos, expectedOutputs);
        }
    }

    /**
     * 魔力池的"本次发配是否已结束"判定.
     *
     * <p>默认实现（{@code isIdle}）对本设备是不完整的：魔力池的输入在推料时就被同步消耗，
     * {@link #isIdlePool} 在推料保护期结束后必然为 true，此时若有产物实体没被收走
     * （落点被水流/活塞/玩家等外力带出收集范围、或合并堆叠未取尽），会话会当场结束——
     * 而 Botania 设备没有 {@code clearOutputs} 兜底，残留产物从此再无人收集。</p>
     *
     * <p>因此这里在 {@code isIdle} 之外追加"池面上没有未收回的本批产物实体"：
     * 只要还有本批应产出的产物实体存在，会话就继续停留在 COLLECTING（每 tick 再收一次），
     * 直到扫不到为止；真的收不到时由 {@code processingTimeoutTicks} 超时回退兜底
     * （代价是这一批延迟到超时才结束，但不会静默丢产物）。</p>
     *
     * <p><b>身份兜底范围的取舍</b>：设备本列内"未带标记但物品身份匹配预期产物"的实体
     * （历史残留、或本批产物与旧残留合并后标记随被合并方消失）也计入未收完，
     * 让它们能跨 tick 被逐份收干净。代价是：这些实体里也可能有玩家自己放在池子这一格里的
     * 同型物品，此时会话会持续收集（每 tick 每实体最多 1 份）直到取尽、或 600 tick 超时回退；
     * 只有"池子这一格里堆了 600 份以上预期产物"才会走到超时。</p>
     */
    @Override
    public boolean hasFinished(World world, BlockPos pos, List<ItemStack> inputs, TargetSession session) {
        TileEntity te = world.getTileEntity(pos);
        if (!BotaniaReflectionHelper.isInstance(BotaniaReflectionHelper.CLASS_TILE_POOL, te)) {
            // 其余设备沿用 IRemoteHandler 默认实现（hasFinished == isIdle）
            return isIdle(world, pos, inputs, session);
        }
        IAEItemStack[] expected = session != null ? session.getExpectedOutputs() : null;
        return isIdlePool(world, pos, session)
                && !hasPendingProducts(getEntityItemsInBox(world, productScanBox(pos)),
                        getEntityItemsInAABB(world, pos), expected);
    }

    @Override
    public List<ItemStack> revertMaterials(World world, BlockPos pos, IActionSource source, TargetSession session) {
        TileEntity te = world.getTileEntity(pos);
        if (BotaniaReflectionHelper.isInstance(BotaniaReflectionHelper.CLASS_TILE_RUNE_ALTAR, te)) {
            List<ItemStack> result = new ArrayList<>();
            IItemHandler handler = BotaniaReflectionHelper.runeAltarGetItemHandler(te);
            if (handler != null) {
                for (int i = 0; i < handler.getSlots(); i++) {
                    ItemStack stack = handler.extractItem(i, 64, false);
                    if (!stack.isEmpty()) result.add(stack);
                }
            }
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
            return result;
        }
        // 对于 Pool / AlfPortal / TerraPlate 等通过 EntityItem 推料的设备,
        // 只回收带 TAG_INPUT_FLAG 标记的未消耗输入,不回收产物
        List<ItemStack> fallback = new ArrayList<>();
        for (EntityItem item : getEntityItemsInAABB(world, pos)) {
            boolean isInput = item.getEntityData().getBoolean(TAG_INPUT_FLAG);
            if (!item.isDead && !item.getItem().isEmpty() && isInput) {
                fallback.add(item.getItem().copy());
                item.setDead();
            }
        }
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

    private boolean pushMaterialsPool(World world, BlockPos pos, Object pool, InventoryCrafting ingredients) {
        // 清理 AABB 中已有的中枢输入标记实体,防止实体堆叠干扰 collideEntityItem
        for (EntityItem existing : getEntityItemsInAABB(world, pos)) {
            if (!existing.isDead && existing.getEntityData().getBoolean(TAG_INPUT_FLAG)) {
                existing.setDead();
            }
        }

        // 推料前记下产物生成点已有的实体 id：本 tick 推料后在该点新出现的实体就是
        // collideEntityItem 生成的池产物（本方法整段在同一个服务端 tick 内执行完毕）。
        Set<Integer> preexisting = snapshotProductAreaEntityIds(world, pos);

        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            // 每次只推送 1 个,collideEntityItem 每次消耗 1 个
            ItemStack remaining = stack.copy();
            while (!remaining.isEmpty()) {
                ItemStack single = remaining.splitStack(1);
                if (single.isEmpty()) {
                    AE2Enhanced.LOGGER.warn("[AE2E-Botania] pushMaterialsPool: splitStack returned empty! Aborting slot. remainingCount={}, remainingItem={}",
                            remaining.getCount(), remaining.getItem());
                    break;
                }
                EntityItem entityItem = new EntityItem(world, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, single);
                // 标记为中枢输入,使 revertMaterials 能区分未消耗输入与产物
                entityItem.getEntityData().setBoolean(TAG_INPUT_FLAG, true);
                // 无限拾取延迟（32767）：
                // 1) 1.12.2 的 EntityItem.combineItems 唯一的合并豁免条件是
                //    pickupDelay == Short.MAX_VALUE，设 0 或保持默认 10 都会参与同型掉落物自动合并；
                //    一旦本批输入被并入他人的堆叠（或反之），本批标记会随被合并方一起消失，
                //    回退找不到它、玩家还可能把本批材料直接捡走。
                // 2) Botania TilePool.collideEntityItem 只拒绝 100 < pickupDelay < 130 的物品
                //    （视为玩家刚投掷的物品），32767 不在该区间内，池仍会正常消费。
                entityItem.setInfinitePickupDelay();
                entityItem.motionX = 0;
                entityItem.motionY = 0;
                entityItem.motionZ = 0;
                world.spawnEntity(entityItem);  // 必须加入世界,否则产物不会出现在世界中

                boolean consumed = BotaniaReflectionHelper.poolCollideEntityItem(pool, entityItem);
                // WORKAROUND: 某些整合包中 collideEntityItem 执行了成功路径(shrink+spawn产物)
                // 但返回值被某个未知模组篡改为 false.以 entityItem.item 是否被 shrink 空作为成功判据.
                boolean actuallyConsumed = consumed || entityItem.getItem().isEmpty();
                if (!actuallyConsumed) {
                    // 池拒绝转化（魔力不足 / 无匹配配方）：必须销毁这个尚未消耗的输入实体。
                    // 推送失败时 AE2 的 CPU 会把整份材料原样注入回网络（CraftingCPUCluster 对
                    // 未被任何 medium 接受的 craftingInv 做回灌），若这里把实体留在世界里，
                    // revertMaterials 会再把它取一份回网络 —— 同一份材料变成两份。
                    if (!entityItem.isDead) {
                        entityItem.setDead();
                    }
                    return false;
                }
                // collideEntityItem 成功后：
                // Botania 标准行为：原 EntityItem 的 item 被 shrink(1) 清空,产物 spawn 到新 EntityItem
                // 因此原 entityItem.getItem() 必然为空.直接 setDead 清理即可.
                if (!entityItem.getItem().isEmpty()) {
                    // 防御性处理：如果某个魔改版本把产物塞回了原 EntityItem,移除输入标记
                    entityItem.getEntityData().removeTag(TAG_INPUT_FLAG);
                } else {
                    entityItem.setDead();
                }
            }
        }

        // 整批转化成功后才标记产物：本批产物是"已提交批次"的产物，收集与完成判定都以它为准。
        // （推送中途失败时不标记：那一批已被 dispatcher 回退，其残余产物仍按"物品身份"
        //   口径由后续批次逐份回收，避免与 AE2 的材料回灌叠加成多份物品。）
        tagNewBatchProducts(world, pos, preexisting);
        return true;
    }

    /**
     * 魔力池的输入是否已被池取走（可以开始收集产物）.
     *
     * <p>判据是"没有带 {@link #TAG_INPUT_FLAG} 的未消耗输入"：魔力池的转化在
     * {@code pushMaterialsPool} 里同步完成，因此推料保护期一过这里必然为 true，本判定退化为
     * "推料后已过 grace"。这与 {@link #isIdleAlfPortal} 是同型判据（都是"设备是否已取走本批输入"），
     * 区别是精灵门的输入异步离开，所以那边必须真的扫一遍。</p>
     *
     * <p><b>产物不参与 isIdle</b>：{@code isIdle} 是"进入收集阶段"的闸门，把产物算进来会导致
     * "有产物→不空闲→不收集→产物永远收不走"的死锁。产物的判据放在
     * {@link #hasFinished}（"输入已取走 且 产物已收完"），与框架层
     * {@code AbstractMachineHandler#hasFinished} 的算法一致。</p>
     */
    private boolean isIdlePool(World world, BlockPos pos, TargetSession session) {
        if (!isGraceElapsed(session, world.getTotalWorldTime(), PUSH_IDLE_GRACE_TICKS)) return false;
        for (EntityItem item : getIdleScanEntityItems(world, pos)) {
            if (item.isDead) continue;
            if (item.getEntityData().getBoolean(TAG_INPUT_FLAG)) {
                return false;
            }
        }
        return true;
    }

    // ==================== Alf Portal ====================

    private boolean canStartAlfPortal(World world, BlockPos pos) {
        Object state = BotaniaReflectionHelper.getAlfPortalState(world.getBlockState(pos));
        return state != BotaniaReflectionHelper.ALFPORTAL_STATE_OFF;
    }

    private boolean pushMaterialsAlfPortal(World world, BlockPos pos, Object portal, InventoryCrafting ingredients) {
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            EntityItem entityItem = new EntityItem(world, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, stack.copy());
            entityItem.setInfinitePickupDelay();
            entityItem.motionX = 0;
            entityItem.motionY = 0;
            entityItem.motionZ = 0;
            entityItem.getEntityData().setBoolean(TAG_INPUT_FLAG, true);
            world.spawnEntity(entityItem);
        }
        return true;
    }

    /**
     * 精灵门的输入是否已被门取走（可以开始收集产物）.
     *
     * <p>与 {@link #isIdlePool} 同型：grace 只是下限，真正的判据是"本批推入的输入实体已被
     * Botania 取走"。只等 grace 时，门尚未取走输入（例如门刚打开、{@code ticksOpen}
     * 还没超过 60 时门根本不吃物品）就会结束会话，残留输入和之后才出现的产物都成了孤儿；
     * 因此这里扫 {@link #alfPortalScanBox} 内的 {@link #TAG_INPUT_FLAG} 实体，
     * 只要还有输入没被取走就继续等（真取不走时由 processingTimeoutTicks 超时回退取回）。</p>
     *
     * <p>判据只认<b>我们自己的输入标记</b>，不使用"门内存在无 {@code _elvenPortal} 标记的实体"
     * 这种写法：门附近玩家自己丢的物品既没有该标记、也不会被门取走，
     * 那样判定会让会话永远无法结束。</p>
     */
    private boolean isIdleAlfPortal(World world, BlockPos pos, TargetSession session) {
        if (!isGraceElapsed(session, world.getTotalWorldTime(), ALF_PORTAL_GRACE_TICKS)) return false;
        for (EntityItem item : getIdleScanEntityItems(world, pos, alfPortalScanBox(pos))) {
            if (item.isDead) continue;
            if (item.getEntityData().getBoolean(TAG_INPUT_FLAG)) {
                return false;
            }
        }
        return true;
    }

    // ==================== Terra Plate ====================

    private boolean canStartTerraPlate(World world, BlockPos pos, Object plate) {
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
            entityItem.setInfinitePickupDelay();
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
        List<EntityItem> items = getIdleScanEntityItems(world, pos);
        for (EntityItem item : items) {
            if (item.isDead) continue;
            if (item.getEntityData().getBoolean(TAG_INPUT_FLAG)) {
                return false;
            }
        }
        return true;
    }

    // ==================== Rune Altar ====================

    private boolean canStartRuneAltar(World world, BlockPos pos, Object altar) {
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
            entityItem.setInfinitePickupDelay();
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

    private boolean canStartAltar(World world, BlockPos pos, Object altar) {
        // 花药台的就绪条件：台面为空、无岩浆冲突。
        // 是否为合法配方由 pushMaterialsAltar 的 collideEntityItem 决定（无匹配材料会留在台内，
        // 由超时回退取回）。
        return BotaniaReflectionHelper.altarIsEmpty(altar) && !BotaniaReflectionHelper.altarHasLava(altar);
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
            entityItem.setInfinitePickupDelay();
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
        List<EntityItem> items = getIdleScanEntityItems(world, pos);
        for (EntityItem item : items) {
            if (item.isDead) continue;
            // 跳过尚未处理的输入(兜底,理论上不应存在)
            if (item.getEntityData().getBoolean(TAG_INPUT_FLAG)) continue;
            return true;
        }
        return false;
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
        return matchRecipeInputs(BotaniaReflectionHelper.elvenTradeGetInputs(recipe), HandlerUtils.collectNonEmpty(ingredients));
    }

    private List<IAEStack> getVirtualCostAlfPortal(World world, BlockPos pos, InventoryCrafting ingredients, IAEItemStack[] outputs, long count) {
        List<IAEStack> costs = new ArrayList<>();
        if (outputs == null || outputs.length == 0 || outputs[0] == null) return costs;
        Object recipe = findElvenTradeRecipeByOutput(outputs[0].createItemStack());
        if (recipe == null) return costs;
        List<ItemStack> available = HandlerUtils.collectNonEmpty(ingredients);
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
        List<ItemStack> available = HandlerUtils.collectNonEmpty(ingredients);
        ItemStack livingrockTemplate = new ItemStack(Item.getItemFromBlock(BotaniaReflectionHelper.BLOCK_LIVINGROCK), 1, 0);
        boolean hasLivingrock = !consumeFromAvailable(available, livingrockTemplate).isEmpty();
        return hasLivingrock && matchRecipeInputs(BotaniaReflectionHelper.petalsGetInputs(recipe), available);
    }

    private List<IAEStack> getVirtualCostRuneAltar(World world, BlockPos pos, InventoryCrafting ingredients, IAEItemStack[] outputs, long count) {
        List<IAEStack> costs = new ArrayList<>();
        if (outputs == null || outputs.length == 0 || outputs[0] == null) return costs;
        Object recipe = findRuneAltarRecipeByOutput(outputs[0].createItemStack());
        if (recipe == null) return costs;

        List<ItemStack> available = HandlerUtils.collectNonEmpty(ingredients);
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

    /**
     * 实时扫描设备上方 AABB 内的 EntityItem（推料 / 收集产物 / 回退材料等改变物品归属的路径必须走这里）.
     */
    private List<EntityItem> getEntityItemsInAABB(World world, BlockPos pos) {
        return world.getEntitiesWithinAABB(EntityItem.class, entityScanBox(pos));
    }

    /**
     * 按给定范围实时扫描 EntityItem（收集产物的路径必须用实时扫描，不能走节流缓存）.
     */
    private List<EntityItem> getEntityItemsInBox(World world, AxisAlignedBB box) {
        return world.getEntitiesWithinAABB(EntityItem.class, box);
    }

    /**
     * isIdle 路径专用的<b>节流</b>扫描.
     *
     * <p>{@code isIdle} 每个 tick 都会被 {@code PhysicalDispatcher} 调用，而 AABB 内实体列表
     * 的变化速度远低于 tick 速度，因此复用 {@link EntityScanThrottle} 的缓存
     * （默认每 {@link EntityScanThrottle#DEFAULT_INTERVAL_TICKS} tick 才真扫一次）。</p>
     *
     * <p><b>语义影响</b>：空闲判定最多延迟 {@code intervalTicks - 1} 个 tick（默认 3 tick），
     * 即"机器已处理完本批材料"最多晚 3 tick 被识别，产物收集相应延后。</p>
     */
    private List<EntityItem> getIdleScanEntityItems(World world, BlockPos pos) {
        return EntityScanThrottle.scan(world, pos, EntityItem.class, entityScanBox(pos));
    }

    /** 设备本列（与 Botania 自身的 AABB 口径一致）的节流扫描：x/z ∈ [pos, pos+1]，y ∈ [pos, pos+2]. */
    private List<EntityItem> getIdleScanEntityItems(World world, BlockPos pos, AxisAlignedBB box) {
        return EntityScanThrottle.scan(world, pos, EntityItem.class, box);
    }

    /**
     * 输入/回退扫描范围：设备本列 —— x/z ∈ [pos, pos+1]、y ∈ [pos, pos+2].
     *
     * <p>与 Botania 自身（{@code TilePool}/{@code TileTerraPlate}/{@code TileRuneAltar} 的
     * {@code new AxisAlignedBB(pos, pos.add(1,1,1))}）同口径：中枢推入的输入实体一定在这一列内，
     * 这里的窄范围也保证 {@code revertMaterials} 不会去动设备旁玩家自己的物品。</p>
     */
    static AxisAlignedBB entityScanBox(BlockPos pos) {
        return new AxisAlignedBB(pos, pos.add(1, 2, 1));
    }

    /**
     * 产物扫描范围：设备本列向四周外扩 1 格（x/z ∈ [pos-1, pos+2]，y ∈ [pos-1, pos+2]）.
     *
     * <p>魔力池的产物是会被外力带走的普通掉落物：水流/活塞推到相邻方块上方、玩家走过踢开、
     * 掉落物堆叠被挤出池壁（池的碰撞盒只有 0.5 高的一圈围墙）都会让落点离开设备本列。
     * 只按设备本列收集时，落在邻格的产物永远收不回来（且 Botania 没有 {@code clearOutputs} 兜底）。</p>
     *
     * <p>本范围<b>只用于"带本机产物标记"的实体</b>（见 {@link #isBatchProduct}），
     * 因此放宽不会把设备旁玩家自己的同型物品收走。</p>
     */
    static AxisAlignedBB productScanBox(BlockPos pos) {
        return new AxisAlignedBB(pos).grow(1, 1, 1);
    }

    /**
     * 池产物生成点的标记范围（Botania 把产物 spawn 在 {@code (x+0.5, y+1.5, z+0.5)}）.
     *
     * <p>范围紧贴生成点、且竖向从 y+0.5 起：既保证同 tick 内生成的产物一定落在里面，
     * 又排除了沉在池底（y+0.06 ~ y+0.31）的玩家物品。配合"推料前快照 id、只标新实体"，
     * 误标面只剩下"同一 tick 内被别的模组 spawn 在池心"这种可以忽略的情况。</p>
     */
    static AxisAlignedBB productTagBox(BlockPos pos) {
        return new AxisAlignedBB(pos.getX() + 0.25, pos.getY() + 0.5, pos.getZ() + 0.25,
                pos.getX() + 0.75, pos.getY() + 2.0, pos.getZ() + 0.75);
    }

    /**
     * 精灵门工作范围（覆盖 ON_X / ON_Z 两种朝向）.
     *
     * <p>Botania 的 {@code TileAlfPortal#getPortalAABB} 在 x 或 z 方向各展开 1 格、向上 4 格
     * （ON_X 为 x ∈ [pos-1, pos+2]、ON_Z 为 z ∈ [pos-1, pos+2]，y 均为 [pos+1, pos+4]），
     * 这里取其并集，避免为了朝向判定去反射私有方法。</p>
     */
    static AxisAlignedBB alfPortalScanBox(BlockPos pos) {
        return new AxisAlignedBB(pos.getX() - 1, pos.getY() + 1, pos.getZ() - 1,
                pos.getX() + 2, pos.getY() + 4, pos.getZ() + 2);
    }

    /**
     * 该实体是否为本机在魔力池中生成的产物实体（带 {@link #TAG_PRODUCT_FLAG}、存活且非空）.
     */
    static boolean isBatchProduct(EntityItem item) {
        return item != null && !item.isDead && !item.getItem().isEmpty()
                && item.getEntityData().getBoolean(TAG_PRODUCT_FLAG);
    }

    /**
     * 该实体是否为"本次推料新增的池产物"：推料前快照里没有它、存活、非空、且不是本批输入.
     *
     * <p>不按物品身份判定：池产物的 NBT/meta 由配方决定，可能与本模组样板声明的产物不完全一致，
     * 按身份判会漏标（漏标即漏收）；"本 tick 在池心新出现"是更可靠的判据。</p>
     */
    static boolean isNewBatchProduct(EntityItem item, Set<Integer> preexistingIds) {
        return item != null && !item.isDead && !item.getItem().isEmpty()
                && !item.getEntityData().getBoolean(TAG_INPUT_FLAG)
                && !preexistingIds.contains(item.getEntityId());
    }

    /**
     * 按预期产物口径，本趟最多可从该堆叠取走的数量；0 表示它不是预期产物.
     *
     * <p>上限是"样板声明的单次产物数量"：物理发配一次只推一份材料（CPU 聚合发配不适用于本设备，
     * 中枢接口不是 {@code DualityInterface}/{@code IInterfaceHost}），因此设备本列内身份匹配到的
     * 未标记实体保守地只取这一份。</p>
     */
    static int expectedCollectCount(ItemStack stack, IAEItemStack[] expectedOutputs) {
        if (stack == null || stack.isEmpty() || expectedOutputs == null) {
            return 0;
        }
        for (IAEItemStack expected : expectedOutputs) {
            if (expected == null || expected.getStackSize() <= 0) continue;
            ItemStack expectedStack = expected.createItemStack();
            if (!expectedStack.isEmpty() && ItemStack.areItemsEqual(stack, expectedStack)) {
                return Math.min(expectedStack.getCount(), stack.getCount());
            }
        }
        return 0;
    }

    /**
     * 收集带本机产物标记的实体（整栈取走）.
     *
     * <p>必须整栈：Botania 给池产物设的 pickupDelay 是 105（不在我们的控制范围内），
     * 同型产物仍会互相合并成一个堆叠；按"每实体只取预期数量"取会让合并堆叠里的其余产物
     * 永久留在池面上。</p>
     */
    static List<ItemStack> collectTaggedProducts(List<EntityItem> items) {
        List<ItemStack> collected = new ArrayList<>();
        if (items == null) {
            return collected;
        }
        for (EntityItem item : items) {
            if (!isBatchProduct(item)) continue;
            collected.add(item.getItem().copy());
            item.setDead();
        }
        return collected;
    }

    /**
     * 收集"未带标记但物品身份匹配预期产物"的实体（兼容标记丢失/历史残留）.
     *
     * <p>每实体每趟最多取 {@link #expectedCollectCount}；数量上限之外的部分
     * 由 {@link #hasPendingProducts} 判定为"还没收完"，会话继续停留收集阶段逐 tick 取，
     * 直到取尽或超时回退。</p>
     */
    static List<ItemStack> collectMatchedProducts(List<EntityItem> items, IAEItemStack[] expectedOutputs) {
        List<ItemStack> collected = new ArrayList<>();
        if (items == null || expectedOutputs == null || expectedOutputs.length == 0) {
            return collected;
        }
        for (EntityItem entityItem : items) {
            if (entityItem.isDead) continue;
            // 跳过未消耗的输入,只收集产物
            if (entityItem.getEntityData().getBoolean(TAG_INPUT_FLAG)) continue;
            ItemStack stack = entityItem.getItem();
            if (stack.isEmpty()) continue;

            int toCollect = expectedCollectCount(stack, expectedOutputs);
            if (toCollect <= 0) continue;
            if (toCollect >= stack.getCount()) {
                collected.add(stack.copy());
                entityItem.setDead();
            } else {
                collected.add(stack.splitStack(toCollect));
            }
        }
        return collected;
    }

    /**
     * 池面上是否还有"本批应产出但尚未收回"的产物实体.
     *
     * @param taggedScope     宽范围（{@link #productScanBox}）扫描结果：只认本机产物标记
     * @param identityScope   窄范围（{@link #entityScanBox}）扫描结果：未带标记但物品身份匹配预期产物也算
     * @param expectedOutputs 本批预期产物（可为 null）
     */
    static boolean hasPendingProducts(List<EntityItem> taggedScope, List<EntityItem> identityScope,
                                      IAEItemStack[] expectedOutputs) {
        if (taggedScope != null) {
            for (EntityItem item : taggedScope) {
                if (isBatchProduct(item)) {
                    return true;
                }
            }
        }
        if (identityScope == null || expectedOutputs == null || expectedOutputs.length == 0) {
            return false;
        }
        for (EntityItem item : identityScope) {
            if (item.isDead) continue;
            if (item.getEntityData().getBoolean(TAG_INPUT_FLAG)) continue;
            if (expectedCollectCount(item.getItem(), expectedOutputs) > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 推料前快照产物生成点内的实体 id（用于识别本次推料新增的产物实体）.
     */
    private Set<Integer> snapshotProductAreaEntityIds(World world, BlockPos pos) {
        Set<Integer> ids = new HashSet<>();
        for (EntityItem item : getEntityItemsInBox(world, productTagBox(pos))) {
            ids.add(item.getEntityId());
        }
        return ids;
    }

    /**
     * 给整批转化成功后、在产物生成点新出现的实体打上 {@link #TAG_PRODUCT_FLAG}.
     *
     * <p>推料与此方法在同一个 tick 内执行，此时产物尚未 {@code onUpdate}（不会移动、不会合并），
     * 因此一定还在 {@link #productTagBox} 内。</p>
     */
    private void tagNewBatchProducts(World world, BlockPos pos, Set<Integer> preexistingIds) {
        for (EntityItem item : getEntityItemsInBox(world, productTagBox(pos))) {
            if (isNewBatchProduct(item, preexistingIds)) {
                item.getEntityData().setBoolean(TAG_PRODUCT_FLAG, true);
            }
        }
    }

    /**
     * 收集魔力池产物：宽范围内按本机产物标记整栈回收，设备本列内按物品身份兜底回收.
     */
    private List<ItemStack> collectProductsPool(World world, BlockPos pos, IAEItemStack[] expectedOutputs) {
        List<ItemStack> collected = collectTaggedProducts(getEntityItemsInBox(world, productScanBox(pos)));
        collected.addAll(collectMatchedProducts(getEntityItemsInAABB(world, pos), expectedOutputs));
        return collected;
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
        return collectMatchedProducts(getEntityItemsInAABB(world, pos), expectedOutputs);
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
}

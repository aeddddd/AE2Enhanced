package com.github.aeddddd.ae2enhanced.centralinterface.handler.thaumcraft;

import com.github.aeddddd.ae2enhanced.centralinterface.TargetSession;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.centralinterface.IRemoteHandler;
import com.github.aeddddd.ae2enhanced.centralinterface.IVirtualBatchCraftingHandler;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.EntityScanThrottle;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.mojang.authlib.GameProfile;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraft.util.EnumParticleTypes;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Thaumcraft 6 坩埚远程处理器.
 *
 * <p>支持中枢 ME 接口对坩埚的自动化：
 * <ul>
 *   <li>智能发配顺序：按样板声明的主产物定位 CrucibleRecipe 取得催化剂,确保催化剂最后投入</li>
 *   <li>催化剂自然投掷：源质来源直接分解,催化剂以 EntityItem 形式从坩埚上方自然落下</li>
 *   <li>研究绕过：使用 FakePlayer 临时授予所有坩埚配方研究</li>
 *   <li>源质残留管理：成功收集产物后才调用 spillRemnants 清空(可配置开关)</li>
 * </ul>
 *
 * <p><b>配方策略</b>：{@code canStart} 只检查是否还有未落下的催化剂；{@code pushMaterials}
 * 以样板声明的主产物 {@code expectedOutputs[0]} 定位一次坩埚配方，用其中的 catalyst 字段
 * 决定“哪个物品最后投”。
 * 定位失败时退化为全部物品按源质来源处理(不投催化剂)。</p>
 */
public class ThaumcraftCrucibleHandler implements IRemoteHandler, IVirtualBatchCraftingHandler {

    private static final String BLOCK_ID = "thaumcraft:crucible";
    private static final GameProfile CRUCIBLE_PROFILE = new GameProfile(
            UUID.nameUUIDFromBytes("ae2e-crucible".getBytes()), "[AE2E]");
    private static boolean researchGrantAttempted = false;
    private static final String TAG_CATALYST = "ae2eCatalyst";
    /** 催化剂/产物的水平识别半径（格），用于排除相邻坩埚的实体。 */
    private static final double CATALYST_RANGE = 0.8;

    private static Class<?> CLASS_TILE_CRUCIBLE;
    private static Class<?> CLASS_ENTITY_SPECIAL_ITEM;
    private static Method METHOD_ATTEMPT_SMELT;
    private static Method METHOD_SPILL_REMNANTS;
    private static Method METHOD_GET_ITEM;
    private static boolean reflectionReady = false;

    private static void initReflection() {
        if (reflectionReady) return;
        try {
            CLASS_TILE_CRUCIBLE = Class.forName("thaumcraft.common.tiles.crafting.TileCrucible");
            CLASS_ENTITY_SPECIAL_ITEM = Class.forName("thaumcraft.common.entities.EntitySpecialItem");

            METHOD_ATTEMPT_SMELT = CLASS_TILE_CRUCIBLE.getMethod("attemptSmelt", ItemStack.class, String.class);
            METHOD_SPILL_REMNANTS = CLASS_TILE_CRUCIBLE.getMethod("spillRemnants");
            // EntityItem.getItem() 运行时 SRG 名为 func_92059_d
            METHOD_GET_ITEM = CLASS_ENTITY_SPECIAL_ITEM.getMethod("func_92059_d");

            reflectionReady = true;
        } catch (Exception e) {
            throw new RuntimeException("[AE2E] ThaumcraftCrucibleHandler reflection init failed", e);
        }
    }

    @Override
    public boolean canHandle(String blockId) {
        return BLOCK_ID.equals(blockId);
    }

    @Override
    public boolean isValidTarget(World world, BlockPos pos) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        return CLASS_TILE_CRUCIBLE.isInstance(te);
    }

    @Override
    public boolean canStart(World world, BlockPos pos, InventoryCrafting ingredients, TargetSession session) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_TILE_CRUCIBLE.isInstance(te)) return false;
        if (hasPendingCatalyst(world, pos)) return false;

        boolean hasItems = false;
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            if (!ingredients.getStackInSlot(i).isEmpty()) {
                hasItems = true;
                break;
            }
        }
        return hasItems;
    }

    @Override
    public boolean pushMaterials(World world, BlockPos pos, InventoryCrafting ingredients, IActionSource source, TargetSession session) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_TILE_CRUCIBLE.isInstance(te)) return false;

        try {
            // 收集物品
            List<ItemStack> items = new ArrayList<>();
            for (int i = 0; i < ingredients.getSizeInventory(); i++) {
                ItemStack stack = ingredients.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    items.add(stack.copy());
                }
            }
            if (items.isEmpty()) return false;

            // 识别催化剂(以样板声明的主产物定位坩埚配方，取配方 catalyst)
            ItemStack identifiedCatalyst = identifyCatalystFromOutput(items, session);
            ItemStack catalyst = ItemStack.EMPTY;
            List<ItemStack> sources = new ArrayList<>();
            if (!identifiedCatalyst.isEmpty()) {
                boolean found = false;
                for (ItemStack stack : items) {
                    if (!found && ItemStack.areItemsEqual(stack, identifiedCatalyst)
                            && ItemStack.areItemStackTagsEqual(stack, identifiedCatalyst)) {
                        catalyst = stack;
                        found = true;
                    } else {
                        sources.add(stack);
                    }
                }
            } else {
                sources.addAll(items);
            }

            // FakePlayer 研究绕过
            String username = ensureResearch(world);

            // 投入源质来源(直接 attemptSmelt)
            for (ItemStack stack : sources) {
                // attemptSmelt 返回未消耗的余量,由调用方负责处理;中枢此处丢弃该返回值
                METHOD_ATTEMPT_SMELT.invoke(te, stack, username);
            }

            // 催化剂以 EntityItem 形式从坩埚上方自然落下
            if (!catalyst.isEmpty()) {
                EntityItem entityItem = new EntityItem(world,
                        pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5,
                        catalyst.copy());
                // 必须设置 thrower,否则坩埚 attemptSmelt(EntityItem) 会传空字符串,
                // 导致 getPlayerEntityByName 返回 null,配方匹配失败,催化剂被分解
                entityItem.getEntityData().setString("thrower", username);
                entityItem.getEntityData().setBoolean(TAG_CATALYST, true);
                entityItem.motionX = 0;
                entityItem.motionY = 0;
                entityItem.motionZ = 0;
                world.spawnEntity(entityItem);
            }

            return true;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] ThaumcraftCrucibleHandler.pushMaterials failed at {}", pos, e);
            return false;
        }
    }

    @Override
    public boolean startProcess(World world, BlockPos pos, IActionSource source, TargetSession session) {
        return true;
    }

    @Override
    public boolean isIdle(World world, BlockPos pos, List<ItemStack> inputs, TargetSession session) {
        initReflection();
        // isIdle 每 tick 调用一次，扫描走节流缓存；判定最多延迟 intervalTicks-1 tick（默认 3 tick）。
        // 真正决定"是否收集"的 collectProducts 仍使用实时扫描，催化剂未落地时不会提前收集。
        return !hasPendingCatalystThrottled(world, pos);
    }

    // ---- IVirtualCraftingHandler / IVirtualBatchCraftingHandler ----

    @Override
    public boolean canCraftVirtually(World world, BlockPos pos, InventoryCrafting ingredients, IAEItemStack[] outputs) {
        if (outputs == null || outputs.length == 0 || outputs[0] == null) return false;
        Object recipe = ThaumcraftReflectionHelper.getCrucibleRecipe(outputs[0].createItemStack());
        if (recipe == null) return false;
        Ingredient catalyst = ThaumcraftReflectionHelper.crucibleGetCatalyst(recipe);
        if (catalyst == null) return false;
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            if (catalyst.apply(ingredients.getStackInSlot(i))) return true;
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
        List<IAEStack> costs = new ArrayList<>();
        if (outputs == null || outputs.length == 0 || outputs[0] == null) return costs;
        Object recipe = ThaumcraftReflectionHelper.getCrucibleRecipe(outputs[0].createItemStack());
        if (recipe == null) return costs;

        Ingredient catalyst = ThaumcraftReflectionHelper.crucibleGetCatalyst(recipe);
        if (catalyst != null) {
            for (int i = 0; i < ingredients.getSizeInventory(); i++) {
                ItemStack stack = ingredients.getStackInSlot(i);
                if (!stack.isEmpty() && catalyst.apply(stack)) {
                    IAEItemStack cost = AEItemStack.fromItemStack(stack.copy());
                    cost.setStackSize(count);
                    costs.add(cost);
                    break;
                }
            }
        }

        Object aspects = ThaumcraftReflectionHelper.crucibleGetAspects(recipe);
        if (aspects != null && ThaumcraftReflectionHelper.aspectListSize(aspects) > 0) {
            List<IAEStack> essentia = ThaumcraftReflectionHelper.createEssentiaCosts(aspects, count);
            if (essentia == null) {
                return null;
            }
            costs.addAll(essentia);
        }

        return costs;
    }

    @Override
    public List<ItemStack> virtualCraftBatch(World world, BlockPos pos, InventoryCrafting ingredients, IAEItemStack[] outputs, long count, IActionSource source) {
        List<ItemStack> products = new ArrayList<>();
        if (!canCraftVirtually(world, pos, ingredients, outputs)) return products;
        return scaleOutputsByCount(outputs, count);
    }

    @Override
    public List<ItemStack> collectProducts(World world, BlockPos pos, IAEItemStack[] expectedOutputs,
            List<ItemStack> inputs, IActionSource source, TargetSession session) {
        initReflection();
        // 如果还有未落下的催化剂,不能收集产物
        if (hasPendingCatalyst(world, pos)) {
            return new ArrayList<>();
        }

        List<ItemStack> result = collectSpecialItems(world, pos);

        // 只在成功收集到产物后才清空坩埚,避免失败/revert时误清空
        if (!result.isEmpty() && AE2EnhancedConfig.thaumcraft.clearAfterCraft) {
            try {
                TileEntity te = world.getTileEntity(pos);
                if (CLASS_TILE_CRUCIBLE.isInstance(te)) {
                    METHOD_SPILL_REMNANTS.invoke(te);
                }
            } catch (Exception e) {
                AE2Enhanced.LOGGER.error("[AE2E] spillRemnants failed at {}", pos, e);
            }
        }

        return result;
    }

    @Override
    public List<ItemStack> revertMaterials(World world, BlockPos pos, IActionSource source, TargetSession session) {
        // 杀死未落下的催化剂 EntityItem,收集已弹出的产物,不清空坩埚
        initReflection();
        killPendingCatalysts(world, pos);
        return collectSpecialItems(world, pos);
    }

    // ---- 内部方法 ----

    private static boolean hasPendingCatalyst(World world, BlockPos pos) {
        return hasPendingCatalystIn(pos, world.getEntitiesWithinAABB(EntityItem.class, catalystScanBox(pos)));
    }

    /**
     * isIdle 路径专用的<b>节流</b>扫描版本.
     *
     * <p>催化剂扫描范围较大（坩埚上方 6 格高的柱体），而 {@code isIdle} 每 tick 都会被调用，
     * 因此复用 {@link EntityScanThrottle} 的缓存（默认每
     * {@link EntityScanThrottle#DEFAULT_INTERVAL_TICKS} tick 才真扫一次）。</p>
     *
     * <p><b>语义影响</b>：空闲判定最多延迟 3 tick（催化剂已消失但缓存尚未刷新），
     * 产物收集相应延后最多 3 tick；{@link #collectProducts} 仍用实时扫描，
     * 催化剂真的还在半空时会返回空产物并等待下一 tick。</p>
     */
    private static boolean hasPendingCatalystThrottled(World world, BlockPos pos) {
        return hasPendingCatalystIn(pos,
                EntityScanThrottle.scan(world, pos, EntityItem.class, catalystScanBox(pos)));
    }

    private static boolean hasPendingCatalystIn(BlockPos pos, List<EntityItem> entities) {
        for (EntityItem entity : entities) {
            if (entity.isDead) continue;
            if (!isWithinHorizontalRange(entity, pos, CATALYST_RANGE)) continue;
            if (entity.getEntityData().getBoolean(TAG_CATALYST)) {
                return true;
            }
        }
        return false;
    }

    /** 坩埚上方用于识别"未落下的催化剂"的扫描范围（水平 ±0.5，垂直 -0.5 ~ +5.0）。 */
    private static AxisAlignedBB catalystScanBox(BlockPos pos) {
        return new AxisAlignedBB(
                pos.getX() - 0.5, pos.getY() - 0.5, pos.getZ() - 0.5,
                pos.getX() + 1.5, pos.getY() + 5.0, pos.getZ() + 1.5
        );
    }

    private static void killPendingCatalysts(World world, BlockPos pos) {
        for (EntityItem entity : world.getEntitiesWithinAABB(EntityItem.class, catalystScanBox(pos))) {
            if (!isWithinHorizontalRange(entity, pos, CATALYST_RANGE)) continue;
            if (entity.getEntityData().getBoolean(TAG_CATALYST)) {
                entity.setDead();
            }
        }
    }

    private List<ItemStack> collectSpecialItems(World world, BlockPos pos) {
        List<ItemStack> result = new ArrayList<>();
        AxisAlignedBB aabb = new AxisAlignedBB(
                pos.getX() - 0.5, pos.getY() + 0.5, pos.getZ() - 0.5,
                pos.getX() + 1.5, pos.getY() + 3.0, pos.getZ() + 1.5
        );
        try {
            @SuppressWarnings("unchecked")
            Class<? extends Entity> clazz = (Class<? extends Entity>) CLASS_ENTITY_SPECIAL_ITEM;
            for (Entity entity : world.getEntitiesWithinAABB(clazz, aabb)) {
                if (!CLASS_ENTITY_SPECIAL_ITEM.isInstance(entity)) continue;
                if (!isWithinHorizontalRange(entity, pos, CATALYST_RANGE)) continue;

                ItemStack item = (ItemStack) METHOD_GET_ITEM.invoke(entity);
                if (item != null && !item.isEmpty()) {
                    result.add(item.copy());
                    entity.setDead();
                }
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] collectSpecialItems failed at {}", pos, e);
        }
        return result;
    }

    /**
     * 判断实体是否在当前坩埚的水平范围内，避免相邻坩埚的催化剂/产物被误识别。
     */
    private static boolean isWithinHorizontalRange(Entity entity, BlockPos pos, double range) {
        double dx = entity.posX - (pos.getX() + 0.5);
        double dz = entity.posZ - (pos.getZ() + 0.5);
        return dx * dx + dz * dz <= range * range;
    }

    /**
     * 用样板声明的主产物定位坩埚配方，并取出本批物品中匹配该配方催化剂的物品.
     *
     * <p>催化剂必须最后投入(源质来源先分解出源质，催化剂落下时配方才能成立)，
     * 这一顺序知识来自配方的 catalyst 字段，因此这里按主产物
     * {@code expectedOutputs[0]} 定位配方。
     * 定位到的配方缓存到 {@link TargetSession}，随 session reset 清空。</p>
     *
     * @return 匹配到的催化剂物品；无声明产物、查不到配方或无匹配时返回 {@link ItemStack#EMPTY}
     *         (此时全部物品按源质来源处理)
     */
    private static ItemStack identifyCatalystFromOutput(List<ItemStack> items, TargetSession session) {
        try {
            Object recipe = session != null ? session.getRecipeCache() : null;
            if (recipe == null) {
                IAEItemStack[] expected = session != null ? session.getExpectedOutputs() : null;
                if (expected == null || expected.length == 0 || expected[0] == null) return ItemStack.EMPTY;
                ItemStack mainOutput = expected[0].createItemStack();
                if (mainOutput.isEmpty()) return ItemStack.EMPTY;
                recipe = ThaumcraftReflectionHelper.getCrucibleRecipe(mainOutput);
                if (recipe == null) return ItemStack.EMPTY;
                session.setRecipeCache(recipe);
            }

            Ingredient catalyst = ThaumcraftReflectionHelper.crucibleGetCatalyst(recipe);
            if (catalyst == null) return ItemStack.EMPTY;
            for (ItemStack item : items) {
                if (catalyst.apply(item)) {
                    return item;
                }
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] crucible catalyst lookup failed", e);
        }
        return ItemStack.EMPTY;
    }

    private static String ensureResearch(World world) {
        if (!(world instanceof WorldServer)) return "[AE2E]";

        FakePlayer fakePlayer = FakePlayerFactory.get((WorldServer) world, CRUCIBLE_PROFILE);
        Object knowledge = ThaumcraftReflectionHelper.getKnowledge(fakePlayer);
        if (knowledge != null && !researchGrantAttempted) {
            researchGrantAttempted = true;
            int granted = 0;
            for (Object recipe : ThaumcraftReflectionHelper.getCraftingRecipes().values()) {
                if (ThaumcraftReflectionHelper.CLASS_CRUCIBLE_RECIPE.isInstance(recipe)) {
                    String research = ThaumcraftReflectionHelper.crucibleGetResearch(recipe);
                    if (research != null && !research.isEmpty()) {
                        // addResearch 只把研究标记为“已知”,默认 stage=0,
                        // 但坩埚配方检查用的是 isResearchComplete,需要 stage > stages.length.
                        // 因此这里额外 setResearchStage 到一个足够大的值.
                        ThaumcraftReflectionHelper.knowledgeAddResearch(knowledge, research);
                        ThaumcraftReflectionHelper.knowledgeSetResearchStage(knowledge, research, 100);
                        granted++;
                    }
                }
            }
            // 同步给 FakePlayer,确保后续 attemptSmelt 中 knowsResearchStrict 能命中
            ThaumcraftReflectionHelper.knowledgeSync(knowledge, fakePlayer);
            AE2Enhanced.LOGGER.info("[AE2E] Granted {} crucible researches to FakePlayer [{}]",
                    granted, fakePlayer.getName());
        }
        return fakePlayer.getName();
    }
}

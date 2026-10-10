package com.github.aeddddd.ae2enhanced.centralinterface.handler.botania;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;

import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;

/**
 * {@link BotaniaHandler} 魔力池产物回收相关纯逻辑测试.
 *
 * <p><b>为什么不驱动接口方法</b>：魔力池分支的入口（{@code canStart}/{@code pushMaterials}/
 * {@code collectProducts}/{@code isIdle}/{@code hasFinished}）都要先走到真实的 Botania
 * {@code TilePool}（构造它要完整的 Botania 运行期世界，测试里不做），且在无头环境造不出可用的
 * {@code World}。因此这里只驱动被抽成包内静态方法的纯逻辑：扫描范围、产物标记判据、
 * 收集取值口径、以及"是否还有未收回产物"的判据——实体用 Mockito 假造。</p>
 *
 * <p>不覆盖：{@code EntityItem#combineItems} 的实际合并、（Botania 侧的）pickupDelay 消费判定、
 * 以及真机上的落点漂移；这些只能实机验证（见交付说明）。</p>
 */
public class BotaniaHandlerTest {

    private static final BlockPos POS = new BlockPos(4, 64, 4);
    /** Botania 魔力池产物的固定生成点：方块中心上方 1.5 格. */
    private static final double PRODUCT_X = 4.5;
    private static final double PRODUCT_Y = 65.5;
    private static final double PRODUCT_Z = 4.5;
    /** EntityItem 的碰撞箱半宽/半高. */
    private static final double ITEM_HALF = 0.125;

    @BeforeAll
    public static void boot() {
        // 用例中构造 ItemStack / NBT / AEItemStack，需无头引导
        AE2TestBootstrap.boot();
    }

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    private static IAEItemStack[] expected(ItemStack... stacks) {
        IAEItemStack[] result = new IAEItemStack[stacks.length];
        for (int i = 0; i < stacks.length; i++) {
            result[i] = AEItemStack.fromItemStack(stacks[i]);
        }
        return result;
    }

    /** 造一个 EntityItem 替身；ForgeData 用真实 NBTTagCompound（标记读写走真实数据）. */
    private static EntityItem item(ItemStack stack, int entityId) {
        EntityItem entity = mock(EntityItem.class);
        when(entity.getItem()).thenReturn(stack);
        when(entity.getEntityData()).thenReturn(new NBTTagCompound());
        when(entity.getEntityId()).thenReturn(entityId);
        return entity;
    }

    private static EntityItem product(ItemStack stack, int entityId) {
        EntityItem entity = item(stack, entityId);
        entity.getEntityData().setBoolean(BotaniaHandler.TAG_PRODUCT_FLAG, true);
        return entity;
    }

    private static EntityItem input(ItemStack stack, int entityId) {
        EntityItem entity = item(stack, entityId);
        entity.getEntityData().setBoolean(BotaniaHandler.TAG_INPUT_FLAG, true);
        return entity;
    }

    /** {@code Entity.isDead} 是字段不是方法，Mockito 拦不住，只能直接写字段. */
    private static void markDead(EntityItem entity) {
        try {
            Field field = Entity.class.getDeclaredField("isDead");
            field.setAccessible(true);
            field.setBoolean(entity, true);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("无法设置 Entity.isDead", e);
        }
    }

    // ------------------------------------------------------------------
    // 扫描范围
    // ------------------------------------------------------------------

    /** 输入/回退范围必须严格等于设备本列（与 Botania 自身 AABB 同口径）. */
    @Test
    public void testInputScanBoxIsExactlyDeviceColumn() {
        AxisAlignedBB box = BotaniaHandler.entityScanBox(POS);

        assertThat(box.minX).isEqualTo(4.0);
        assertThat(box.maxX).isEqualTo(5.0);
        assertThat(box.minY).isEqualTo(64.0);
        assertThat(box.maxY).isEqualTo(66.0);
        assertThat(box.minZ).isEqualTo(4.0);
        assertThat(box.maxZ).isEqualTo(5.0);
    }

    /** 产物范围必须外扩 1 格：落在邻格的产物（被水流/活塞/玩家带出池子）也要能收到. */
    @Test
    public void testProductScanBoxCoversNeighbouringBlocks() {
        AxisAlignedBB box = BotaniaHandler.productScanBox(POS);

        assertThat(box.minX).isEqualTo(3.0);
        assertThat(box.maxX).isEqualTo(6.0);
        assertThat(box.minY).isEqualTo(63.0);
        assertThat(box.maxY).isEqualTo(66.0);
        assertThat(box.minZ).isEqualTo(3.0);
        assertThat(box.maxZ).isEqualTo(6.0);
        // 邻格地板(y=64.0)、池下一层(y=63.0)都必须在竖直范围内
        assertThat(box.minY).isLessThanOrEqualTo(64.0);
    }

    /** 池产物的生成点（含实体碰撞箱）必须完整落在标记范围内，否则产物打不上标记. */
    @Test
    public void testProductTagBoxCoversSpawnPoint() {
        AxisAlignedBB box = BotaniaHandler.productTagBox(POS);

        assertThat(box.minX).isLessThanOrEqualTo(PRODUCT_X - ITEM_HALF);
        assertThat(box.maxX).isGreaterThanOrEqualTo(PRODUCT_X + ITEM_HALF);
        assertThat(box.minZ).isLessThanOrEqualTo(PRODUCT_Z - ITEM_HALF);
        assertThat(box.maxZ).isGreaterThanOrEqualTo(PRODUCT_Z + ITEM_HALF);
        assertThat(box.minY).isLessThanOrEqualTo(PRODUCT_Y - ITEM_HALF);
        assertThat(box.maxY).isGreaterThanOrEqualTo(PRODUCT_Y + ITEM_HALF);
        // 同一 tick 内可能已经被别处触发过一次 onUpdate（下落 0.04），仍要在范围内
        assertThat(box.minY).isLessThanOrEqualTo(PRODUCT_Y - ITEM_HALF - 0.04);
    }

    /**
     * 标记范围必须排除"沉在池底"的物品：池底(y+0.0625)上静止的物品碰撞箱上沿是 y+0.3125，
     * 玩家自己丢在池面上等待转化的同型物品正是躺在这个高度上，不能被误标成本批产物.
     */
    @Test
    public void testProductTagBoxExcludesItemsRestingOnPoolBottom() {
        AxisAlignedBB box = BotaniaHandler.productTagBox(POS);

        assertThat(box.minY).isGreaterThan(64.0 + 0.3125);
    }

    /** 精灵门扫描范围必须覆盖 Botania 的两种朝向 AABB（ON_X: x∈[pos-1,pos+2]、ON_Z: z∈[pos-1,pos+2]）. */
    @Test
    public void testAlfPortalScanBoxCoversBothOrientations() {
        AxisAlignedBB box = BotaniaHandler.alfPortalScanBox(POS);

        // ON_X: [3,6] x [65,68] x [4,5]
        // ON_Z: [4,5] x [65,68] x [3,6]
        assertThat(box.minX).isLessThanOrEqualTo(3.0);
        assertThat(box.maxX).isGreaterThanOrEqualTo(6.0);
        assertThat(box.minZ).isLessThanOrEqualTo(3.0);
        assertThat(box.maxZ).isGreaterThanOrEqualTo(6.0);
        assertThat(box.minY).isLessThanOrEqualTo(65.0);
        assertThat(box.maxY).isGreaterThanOrEqualTo(68.0);
    }

    // ------------------------------------------------------------------
    // 产物标记判据
    // ------------------------------------------------------------------

    /** 推料后新出现在池心的实体才算本批产物（身份无关，避免池产物 NBT/meta 与样板不一致时漏标）. */
    @Test
    public void testNewEntityAtSpawnPointIsBatchProduct() {
        Set<Integer> preexisting = new HashSet<>(Arrays.asList(1, 2));

        assertThat(BotaniaHandler.isNewBatchProduct(item(new ItemStack(Items.APPLE, 1), 3), preexisting)).isTrue();
    }

    /** 推料前就在池心的实体（玩家丢的物品 / 上一批残留）不能被标成本批产物. */
    @Test
    public void testPreexistingEntityIsNotBatchProduct() {
        Set<Integer> preexisting = new HashSet<>(Collections.singletonList(7));

        assertThat(BotaniaHandler.isNewBatchProduct(item(new ItemStack(Items.APPLE, 1), 7), preexisting)).isFalse();
    }

    /** 本批自己的输入实体（仍带输入标记、尚未被取走）不能被误标成产物. */
    @Test
    public void testInputEntityIsNotBatchProduct() {
        Set<Integer> preexisting = Collections.emptySet();

        assertThat(BotaniaHandler.isNewBatchProduct(input(new ItemStack(Items.APPLE, 1), 9), preexisting)).isFalse();
    }

    /** 已死实体、空栈实体一律不算产物（调用方 setDead 后同 tick 再扫会看到它们）. */
    @Test
    public void testDeadOrEmptyEntityIsNotBatchProduct() {
        Set<Integer> preexisting = Collections.emptySet();

        EntityItem dead = item(new ItemStack(Items.APPLE, 1), 11);
        markDead(dead);
        assertThat(BotaniaHandler.isNewBatchProduct(dead, preexisting)).isFalse();

        assertThat(BotaniaHandler.isNewBatchProduct(item(ItemStack.EMPTY, 12), preexisting)).isFalse();
        assertThat(BotaniaHandler.isBatchProduct(dead)).isFalse();
    }

    // ------------------------------------------------------------------
    // 收集
    // ------------------------------------------------------------------

    /**
     * 合并堆叠（Botania 给池产物设的 pickupDelay=105 不阻止合并）必须整栈取回：
     * 只按"每实体取预期数量"取会把堆叠里其余产物永久留在池面上.
     */
    @Test
    public void testTaggedProductIsCollectedAsWholeMergedStack() {
        ItemStack merged = new ItemStack(Items.DIAMOND, 64);
        EntityItem entity = product(merged, 21);

        List<ItemStack> collected = BotaniaHandler.collectTaggedProducts(Collections.singletonList(entity));

        assertThat(collected).hasSize(1);
        assertThat(collected.get(0).getCount()).isEqualTo(64);
        verify(entity).setDead();
    }

    /** 标记产物在宽范围里被收集；未带标记、带输入标记、空栈的实体都不动. */
    @Test
    public void testCollectTaggedProductsIgnoresOthers() {
        EntityItem foreign = item(new ItemStack(Items.DIAMOND, 3), 31);
        EntityItem ourInput = input(new ItemStack(Items.DIAMOND, 3), 32);
        EntityItem empty = product(ItemStack.EMPTY, 33);

        List<ItemStack> collected = BotaniaHandler.collectTaggedProducts(Arrays.asList(foreign, ourInput, empty));

        assertThat(collected).isEmpty();
        verify(foreign, never()).setDead();
        verify(ourInput, never()).setDead();
        verify(empty, never()).setDead();
    }

    /** 身份兜底收集：堆叠大于预期数量时最多取预期数量，其余留给后续 tick（不一次性误收整栈）. */
    @Test
    public void testIdentityFallbackCapsAtExpectedCount() {
        ItemStack merged = new ItemStack(Items.DIAMOND, 64);
        EntityItem entity = item(merged, 41);
        IAEItemStack[] expected = expected(new ItemStack(Items.DIAMOND, 1));

        List<ItemStack> collected = BotaniaHandler.collectMatchedProducts(
                Collections.singletonList(entity), expected);

        assertThat(collected).hasSize(1);
        assertThat(collected.get(0).getCount()).isEqualTo(1);
        assertThat(merged.getCount()).isEqualTo(63);
        verify(entity, never()).setDead();
    }

    /** 身份兜底收集：堆叠不超过预期数量时整栈取走并 setDead. */
    @Test
    public void testIdentityFallbackTakesWholeStackWhenWithinExpected() {
        ItemStack stack = new ItemStack(Items.DIAMOND, 4);
        EntityItem entity = item(stack, 42);
        IAEItemStack[] expected = expected(new ItemStack(Items.DIAMOND, 8));

        List<ItemStack> collected = BotaniaHandler.collectMatchedProducts(
                Collections.singletonList(entity), expected);

        assertThat(collected).hasSize(1);
        assertThat(collected.get(0).getCount()).isEqualTo(4);
        verify(entity).setDead();
    }

    /** 身份兜底收集：物品不匹配、带输入标记、预期为空时一律不收. */
    @Test
    public void testIdentityFallbackIgnoresNonProducts() {
        EntityItem other = item(new ItemStack(Items.APPLE, 1), 51);
        EntityItem ourInput = input(new ItemStack(Items.DIAMOND, 1), 52);
        IAEItemStack[] expected = expected(new ItemStack(Items.DIAMOND, 1));

        assertThat(BotaniaHandler.collectMatchedProducts(Arrays.asList(other, ourInput), expected)).isEmpty();
        assertThat(BotaniaHandler.collectMatchedProducts(
                Collections.singletonList(item(new ItemStack(Items.DIAMOND, 1), 53)), null)).isEmpty();
        assertThat(BotaniaHandler.collectMatchedProducts(
                Collections.singletonList(item(new ItemStack(Items.DIAMOND, 1), 54)), new IAEItemStack[0])).isEmpty();
        verify(other, never()).setDead();
        verify(ourInput, never()).setDead();
    }

    /** 预期数量为 0 的样板条目不应产出空堆叠（重构前 splitStack(0) 会把空栈加进结果）. */
    @Test
    public void testExpectedCollectCountZeroSizedExpected() {
        IAEItemStack zero = AEItemStack.fromItemStack(new ItemStack(Items.DIAMOND, 1));
        zero.setStackSize(0);
        IAEItemStack[] expected = { zero };

        assertThat(BotaniaHandler.expectedCollectCount(new ItemStack(Items.DIAMOND, 1), expected)).isZero();
        assertThat(BotaniaHandler.collectMatchedProducts(
                Collections.singletonList(item(new ItemStack(Items.DIAMOND, 1), 55)), expected)).isEmpty();
    }

    /** 身份口径只认 item+metadata（与重构前一致），不因 NBT 不同而漏收. */
    @Test
    public void testExpectedCollectCountMatchesIdentityOnly() {
        ItemStack withTag = new ItemStack(Items.DIAMOND, 2);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("k", 1);
        withTag.setTagCompound(tag);
        IAEItemStack[] expected = expected(new ItemStack(Items.DIAMOND, 1));

        assertThat(BotaniaHandler.expectedCollectCount(withTag, expected)).isEqualTo(1);
        assertThat(BotaniaHandler.expectedCollectCount(new ItemStack(Items.DIAMOND, 1, 1), expected)).isZero();
        assertThat(BotaniaHandler.expectedCollectCount(new ItemStack(Items.APPLE, 1), expected)).isZero();
        assertThat(BotaniaHandler.expectedCollectCount(ItemStack.EMPTY, expected)).isZero();
    }

    // ------------------------------------------------------------------
    // "是否还有未收回产物"判据（hasFinished 用）
    // ------------------------------------------------------------------

    /** 全空 = 收完了，会话可以结束. */
    @Test
    public void testNoPendingProductsWhenNothingLeft() {
        IAEItemStack[] expected = expected(new ItemStack(Items.DIAMOND, 1));

        assertThat(BotaniaHandler.hasPendingProducts(Collections.emptyList(), Collections.emptyList(), expected))
                .isFalse();
    }

    /** 宽范围里还有带标记的产物（哪怕它已经跑到邻格）就不能结束会话. */
    @Test
    public void testPendingProductsWhenTaggedProductRemains() {
        IAEItemStack[] expected = expected(new ItemStack(Items.DIAMOND, 1));
        EntityItem stray = product(new ItemStack(Items.DIAMOND, 1), 61);

        assertThat(BotaniaHandler.hasPendingProducts(Collections.singletonList(stray),
                Collections.emptyList(), expected)).isTrue();
    }

    /** 已死（本 tick 刚被收走）的标记产物不算残留. */
    @Test
    public void testPendingProductsIgnoresDeadProduct() {
        IAEItemStack[] expected = expected(new ItemStack(Items.DIAMOND, 1));
        EntityItem collected = product(new ItemStack(Items.DIAMOND, 1), 62);
        markDead(collected);

        assertThat(BotaniaHandler.hasPendingProducts(Collections.singletonList(collected),
                Collections.emptyList(), expected)).isFalse();
    }

    /** 设备本列内未带标记但身份匹配的实体（历史残留/标记丢失）算残留，会话继续收集. */
    @Test
    public void testPendingProductsWhenUntaggedIdentityMatchRemains() {
        IAEItemStack[] expected = expected(new ItemStack(Items.DIAMOND, 1));
        EntityItem leftover = item(new ItemStack(Items.DIAMOND, 5), 63);

        assertThat(BotaniaHandler.hasPendingProducts(Collections.emptyList(),
                Collections.singletonList(leftover), expected)).isTrue();
    }

    /** 设备本列内的未消耗输入、非预期产物不算残留（否则会话永不结束）. */
    @Test
    public void testPendingProductsIgnoresInputsAndForeignItems() {
        IAEItemStack[] expected = expected(new ItemStack(Items.DIAMOND, 1));

        assertThat(BotaniaHandler.hasPendingProducts(Collections.emptyList(),
                Collections.singletonList(input(new ItemStack(Items.DIAMOND, 1), 64)), expected)).isFalse();
        assertThat(BotaniaHandler.hasPendingProducts(Collections.emptyList(),
                Collections.singletonList(item(new ItemStack(Items.APPLE, 1), 65)), expected)).isFalse();
        // 未带标记的实体落在"宽范围"里不算数：宽范围只认标记，避免把玩家落在邻格的同型物品当残留
        assertThat(BotaniaHandler.hasPendingProducts(
                Collections.singletonList(item(new ItemStack(Items.DIAMOND, 1), 66)),
                Collections.emptyList(), expected)).isFalse();
    }

    /** 预期产物为空时不靠身份判定（避免把设备旁的任何物品都当成待收产物）. */
    @Test
    public void testPendingProductsWithNullExpected() {
        EntityItem leftover = item(new ItemStack(Items.DIAMOND, 5), 67);

        assertThat(BotaniaHandler.hasPendingProducts(Collections.emptyList(),
                Collections.singletonList(leftover), null)).isFalse();
    }
}

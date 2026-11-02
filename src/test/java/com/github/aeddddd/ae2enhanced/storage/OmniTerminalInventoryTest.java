package com.github.aeddddd.ae2enhanced.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;

/**
 * {@link OmniTerminalInventory} 大堆叠物品栏测试。
 *
 * <p>覆盖 Integer.MAX_VALUE 槽位上限、insertItem 合并语义（绕过 64 上限、
 * 不匹配物品拒绝、simulate 无副作用、空栈短路）、内容变更回调、
 * 自定义 NBT 序列化（Count 以 Integer 存储，含 NBT 标签往返、大数量往返）、
 * 以及反序列化的防御分支（未知物品 id 跳过、越界槽位忽略、旧内容重置）。</p>
 */
public class OmniTerminalInventoryTest {

    private OmniTerminalInventory inv;

    @BeforeAll
    public static void boot() {
        // 用例中构造 ItemStack，需无头引导初始化物品注册表
        AE2TestBootstrap.boot();
    }

    @BeforeEach
    public void setUp() {
        this.inv = new OmniTerminalInventory(9);
    }

    /** 手工构造一条物品 NBT 条目。 */
    private static NBTTagCompound itemEntry(int slot, String id, int count) {
        NBTTagCompound entry = new NBTTagCompound();
        entry.setInteger("Slot", slot);
        entry.setString("id", id);
        entry.setInteger("Count", count);
        entry.setShort("Damage", (short) 0);
        return entry;
    }

    /** 槽位上限为 Integer.MAX_VALUE。 */
    @Test
    public void testSlotLimitIsMaxValue() {
        assertThat(inv.getSlotLimit(0)).isEqualTo(Integer.MAX_VALUE);
        assertThat(inv.getSlotLimit(8)).isEqualTo(Integer.MAX_VALUE);
    }

    /** insertItem 合并时忽略 getMaxStackSize：同种物品可堆叠超过 64。 */
    @Test
    public void testInsertMergesBeyondVanillaStackLimit() {
        ItemStack remainder1 = inv.insertItem(0, new ItemStack(Items.APPLE, 64), false);
        assertThat(remainder1.isEmpty()).isTrue();

        // 继续插入 100 个：合并到同一槽位，总数 164 > 原版 64 上限
        ItemStack remainder2 = inv.insertItem(0, new ItemStack(Items.APPLE, 100), false);
        assertThat(remainder2.isEmpty()).isTrue();

        ItemStack slot = inv.getStackInSlot(0);
        assertThat(slot.getItem()).isEqualTo(Items.APPLE);
        assertThat(slot.getCount()).isEqualTo(164);
    }

    /** 槽位被其它物品占据时，insertItem 原样返回输入栈且不改动槽位。 */
    @Test
    public void testInsertRejectsMismatchedItem() {
        inv.setStackInSlot(0, new ItemStack(Items.APPLE, 10));

        ItemStack input = new ItemStack(Items.WHEAT, 5);
        ItemStack result = inv.insertItem(0, input, false);

        assertThat(result).isSameAs(input);
        assertThat(inv.getStackInSlot(0).getItem()).isEqualTo(Items.APPLE);
        assertThat(inv.getStackInSlot(0).getCount()).isEqualTo(10);
    }

    /** NBT 不匹配的物品同样拒绝合并。 */
    @Test
    public void testInsertRejectsMismatchedNbt() {
        ItemStack withTag = new ItemStack(Items.APPLE, 10);
        withTag.setTagCompound(new NBTTagCompound());
        inv.setStackInSlot(0, withTag);

        ItemStack input = new ItemStack(Items.APPLE, 5); // 无 NBT
        ItemStack result = inv.insertItem(0, input, false);

        assertThat(result).isSameAs(input);
        assertThat(inv.getStackInSlot(0).getCount()).isEqualTo(10);
    }

    /** simulate=true 不修改槽位内容，也不触发回调。 */
    @Test
    public void testInsertSimulateDoesNotModify() {
        AtomicInteger callbackCount = new AtomicInteger();
        inv.setOnContentsChangedCallback(callbackCount::incrementAndGet);

        ItemStack result = inv.insertItem(0, new ItemStack(Items.APPLE, 64), true);

        assertThat(result.isEmpty()).isTrue(); // 模拟结果：可全部插入
        assertThat(inv.getStackInSlot(0).isEmpty()).isTrue(); // 实际未变
        assertThat(callbackCount.get()).isEqualTo(0);
    }

    /** 插入空栈直接返回空栈，不触发回调。 */
    @Test
    public void testInsertEmptyStackReturnsEmpty() {
        AtomicInteger callbackCount = new AtomicInteger();
        inv.setOnContentsChangedCallback(callbackCount::incrementAndGet);

        assertThat(inv.insertItem(0, ItemStack.EMPTY, false).isEmpty()).isTrue();
        assertThat(callbackCount.get()).isEqualTo(0);
    }

    /** 内容变更回调：非模拟插入触发一次。 */
    @Test
    public void testCallbackInvokedOnContentChange() {
        AtomicInteger callbackCount = new AtomicInteger();
        inv.setOnContentsChangedCallback(callbackCount::incrementAndGet);

        inv.insertItem(0, new ItemStack(Items.APPLE, 1), false);
        assertThat(callbackCount.get()).isEqualTo(1);
    }

    /**
     * 序列化往返保留大数量：Count 以 Integer 标签存储（而非原版 byte），
     * 超过 127 的数量不溢出。
     */
    @Test
    public void testSerializeRoundTripLargeCount() {
        inv.setStackInSlot(2, new ItemStack(Items.APPLE, 200));

        NBTTagCompound nbt = inv.serializeNBT();

        // 格式断言：Count 为 Integer 标签、Size 记录槽位数
        NBTTagCompound entry = nbt.getTagList("Items", Constants.NBT.TAG_COMPOUND).getCompoundTagAt(0);
        assertThat(entry.hasKey("Count", Constants.NBT.TAG_INT)).isTrue();
        assertThat(nbt.getInteger("Size")).isEqualTo(9);

        OmniTerminalInventory restored = new OmniTerminalInventory(9);
        restored.deserializeNBT(nbt);

        ItemStack slot = restored.getStackInSlot(2);
        assertThat(slot.getItem()).isEqualTo(Items.APPLE);
        assertThat(slot.getCount()).isEqualTo(200);
    }

    /** 序列化往返保留物品的 NBT 标签与 meta。 */
    @Test
    public void testSerializeRoundTripPreservesNbtTag() {
        ItemStack stack = new ItemStack(Items.DYE, 16, 4);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("k", "v");
        stack.setTagCompound(tag);
        inv.setStackInSlot(0, stack);

        OmniTerminalInventory restored = new OmniTerminalInventory(9);
        restored.deserializeNBT(inv.serializeNBT());

        ItemStack slot = restored.getStackInSlot(0);
        assertThat(slot.getItem()).isEqualTo(Items.DYE);
        assertThat(slot.getMetadata()).isEqualTo(4);
        assertThat(slot.getCount()).isEqualTo(16);
        assertThat(slot.getTagCompound()).isEqualTo(tag);
    }

    /** 未知物品不进入当前库存视图，但原始条目保留；主动删除不得复活。 */
    @Test
    public void testDeserializePreservesUnknownItemIdUntilExplicitRemoval() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setInteger("Size", 9);
        NBTTagList list = new NBTTagList();
        list.appendTag(itemEntry(0, "minecraft:no_such_item_zzz", 1));
        list.appendTag(itemEntry(2, "minecraft:apple", 5));
        nbt.setTag("Items", list);

        inv.deserializeNBT(nbt);

        assertThat(inv.getStackInSlot(0).isEmpty()).isTrue();
        assertThat(inv.getStackInSlot(2).getItem()).isEqualTo(Items.APPLE);
        assertThat(inv.getStackInSlot(2).getCount()).isEqualTo(5);
        assertThat(inv.serializeNBT().toString()).contains("minecraft:no_such_item_zzz");
        inv.setStackInSlot(0, ItemStack.EMPTY);
        assertThat(inv.serializeNBT().toString()).doesNotContain("minecraft:no_such_item_zzz");
    }

    /** 反序列化防御分支：越界槽位索引被忽略，不抛异常。 */
    @Test
    public void testDeserializeIgnoresOutOfRangeSlot() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setInteger("Size", 9);
        NBTTagList list = new NBTTagList();
        list.appendTag(itemEntry(99, "minecraft:apple", 1)); // 越界
        list.appendTag(itemEntry(-1, "minecraft:wheat", 1)); // 负索引
        list.appendTag(itemEntry(1, "minecraft:apple", 3));
        nbt.setTag("Items", list);

        inv.deserializeNBT(nbt);

        assertThat(inv.getStackInSlot(1).getItem()).isEqualTo(Items.APPLE);
        assertThat(inv.getStackInSlot(1).getCount()).isEqualTo(3);
    }

    /** 反序列化通过 setSize 重建槽位列表：旧内容被完全重置。 */
    @Test
    public void testDeserializeResetsExistingContent() {
        inv.setStackInSlot(1, new ItemStack(Items.WHEAT, 7));

        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setInteger("Size", 9);
        NBTTagList list = new NBTTagList();
        list.appendTag(itemEntry(0, "minecraft:apple", 2));
        nbt.setTag("Items", list);

        inv.deserializeNBT(nbt);

        assertThat(inv.getStackInSlot(0).getItem()).isEqualTo(Items.APPLE);
        // 旧的槽位 1 内容被清除
        assertThat(inv.getStackInSlot(1).isEmpty()).isTrue();
    }

    @Test
    public void testMultipleListenersDoNotOverwriteAndUnregisterIndependently() {
        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();
        Runnable firstListener = first::incrementAndGet;
        Runnable secondListener = second::incrementAndGet;
        inv.addOnContentsChangedListener(firstListener);
        inv.addOnContentsChangedListener(secondListener);

        inv.setStackInSlot(0, new ItemStack(Items.APPLE, 1));
        assertThat(first).hasValue(1);
        assertThat(second).hasValue(1);

        inv.removeOnContentsChangedListener(firstListener);
        inv.setStackInSlot(0, new ItemStack(Items.WHEAT, 1));
        assertThat(first).hasValue(1);
        assertThat(second).hasValue(2);

        inv.removeOnContentsChangedListener(secondListener);
        inv.setStackInSlot(0, ItemStack.EMPTY);
        assertThat(first).hasValue(1);
        assertThat(second).hasValue(2);
    }
}

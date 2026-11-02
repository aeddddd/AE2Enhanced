package com.github.aeddddd.ae2enhanced.centralinterface;

import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PendingProductQueue} 虚拟合成产物暂存队列测试。
 *
 * <p>覆盖 addAll/drainAll/isEmpty 基本语义、drainAll 返回副本且清空队列、
 * writeToNBT/readFromNBT 多条目往返、空队列写 NBT 时 removeTag 的边界行为、
 * 以及读写两侧对空栈的跳过逻辑。</p>
 */
public class PendingProductQueueTest {

    private PendingProductQueue queue;

    @BeforeAll
    public static void boot() {
        // 用例中构造 ItemStack，需无头引导初始化物品注册表
        AE2TestBootstrap.boot();
    }

    @BeforeEach
    public void setUp() {
        this.queue = new PendingProductQueue();
    }

    /** 断言两个 ItemStack 的 item/count/meta/NBT 一致。 */
    private static void assertStackEquals(ItemStack actual, ItemStack expected) {
        assertThat(actual.getItem()).isEqualTo(expected.getItem());
        assertThat(actual.getCount()).isEqualTo(expected.getCount());
        assertThat(actual.getMetadata()).isEqualTo(expected.getMetadata());
        assertThat(actual.getTagCompound()).isEqualTo(expected.getTagCompound());
    }

    /** 新队列为空；空队列 drainAll 返回空列表。 */
    @Test
    public void testInitiallyEmpty() {
        assertThat(queue.isEmpty()).isTrue();
        assertThat(queue.drainAll()).isEmpty();
        assertThat(queue.isEmpty()).isTrue();
    }

    /** addAll 后 drainAll 返回全部条目并清空队列；再次 drain 为空。 */
    @Test
    public void testAddAllAndDrainAll() {
        ItemStack apple = new ItemStack(Items.APPLE, 3);
        ItemStack wheat = new ItemStack(Items.WHEAT, 7);
        queue.addAll(Arrays.asList(apple, wheat));

        assertThat(queue.isEmpty()).isFalse();

        List<ItemStack> drained = queue.drainAll();
        assertThat(drained).hasSize(2);
        assertStackEquals(drained.get(0), apple);
        assertStackEquals(drained.get(1), wheat);

        // 队列已清空
        assertThat(queue.isEmpty()).isTrue();
        assertThat(queue.drainAll()).isEmpty();
    }

    /** drainAll 返回独立副本：修改返回值不影响队列后续行为。 */
    @Test
    public void testDrainAllReturnsIndependentCopy() {
        queue.addAll(Arrays.asList(new ItemStack(Items.APPLE, 1)));

        List<ItemStack> drained = queue.drainAll();
        // 修改返回的列表
        drained.add(new ItemStack(Items.STICK, 99));
        drained.clear();

        // 队列后续行为不受上述修改影响
        queue.addAll(Arrays.asList(new ItemStack(Items.WHEAT, 2)));
        List<ItemStack> second = queue.drainAll();
        assertThat(second).hasSize(1);
        assertStackEquals(second.get(0), new ItemStack(Items.WHEAT, 2));
    }

    /** writeToNBT/readFromNBT 多条目往返（含带 NBT 的物品）。 */
    @Test
    public void testNbtRoundTripMultipleEntries() {
        ItemStack apple = new ItemStack(Items.APPLE, 3);
        ItemStack dye = new ItemStack(Items.DYE, 16, 4);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("k", "v");
        dye.setTagCompound(tag);
        ItemStack wheat = new ItemStack(Items.WHEAT, 1);
        queue.addAll(Arrays.asList(apple, dye, wheat));

        NBTTagCompound data = new NBTTagCompound();
        queue.writeToNBT(data);
        assertThat(data.hasKey("pendingVirtualProducts")).isTrue();

        PendingProductQueue restored = new PendingProductQueue();
        restored.readFromNBT(data);

        List<ItemStack> drained = restored.drainAll();
        assertThat(drained).hasSize(3);
        assertStackEquals(drained.get(0), apple);
        assertStackEquals(drained.get(1), dye);
        assertStackEquals(drained.get(2), wheat);
    }

    /** 边界：队列 drain 为空后再次 writeToNBT，应 removeTag 而非写入空列表。 */
    @Test
    public void testWriteToNbtEmptyQueueRemovesKey() {
        queue.addAll(Arrays.asList(new ItemStack(Items.APPLE, 1)));

        NBTTagCompound data = new NBTTagCompound();
        queue.writeToNBT(data);
        assertThat(data.hasKey("pendingVirtualProducts")).isTrue();

        // 清空后重写：键被移除
        queue.drainAll();
        queue.writeToNBT(data);
        assertThat(data.hasKey("pendingVirtualProducts")).isFalse();
    }

    /** 边界：写入时跳过空栈——队列仅含空栈时等同于空队列（removeTag）。 */
    @Test
    public void testWriteToNbtSkipsEmptyStacks() {
        queue.addAll(Arrays.asList(ItemStack.EMPTY, ItemStack.EMPTY));

        NBTTagCompound data = new NBTTagCompound();
        queue.writeToNBT(data);

        assertThat(data.hasKey("pendingVirtualProducts")).isFalse();
    }

    /** 读取时跳过反序列化为空的栈（如 air 条目），只保留有效项。 */
    @Test
    public void testReadFromNbtSkipsEmptyStacks() {
        NBTTagCompound data = new NBTTagCompound();
        NBTTagList list = new NBTTagList();
        // air 序列化结果在 new ItemStack(nbt) 后为空栈，应被跳过
        list.appendTag(ItemStack.EMPTY.serializeNBT());
        list.appendTag(new ItemStack(Items.APPLE, 2).serializeNBT());
        data.setTag("pendingVirtualProducts", list);

        queue.readFromNBT(data);

        List<ItemStack> drained = queue.drainAll();
        assertThat(drained).hasSize(1);
        assertStackEquals(drained.get(0), new ItemStack(Items.APPLE, 2));
    }

    @Test
    public void testUnknownItemEntryIsPreservedWithoutJoiningTheLiveQueue() {
        NBTTagCompound unknown = new NBTTagCompound();
        unknown.setString("id", "missing_mod:item");
        unknown.setInteger("Count", 2);
        NBTTagList list = new NBTTagList();
        list.appendTag(unknown);
        NBTTagCompound data = new NBTTagCompound();
        data.setTag("pendingVirtualProducts", list);

        queue.readFromNBT(data);
        NBTTagCompound rewritten = new NBTTagCompound();
        queue.writeToNBT(rewritten);

        assertThat(queue.isEmpty()).isTrue();
        assertThat(rewritten.getTagList("pendingVirtualProducts", 10).getCompoundTagAt(0))
                .isEqualTo(unknown);
    }

    /** 读取缺失键的 NBT 时清空队列（clear 语义先于键检查）。 */
    @Test
    public void testReadFromNbtWithoutKeyClearsQueue() {
        queue.addAll(Arrays.asList(new ItemStack(Items.APPLE, 1)));
        assertThat(queue.isEmpty()).isFalse();

        queue.readFromNBT(new NBTTagCompound());

        assertThat(queue.isEmpty()).isTrue();
    }
}

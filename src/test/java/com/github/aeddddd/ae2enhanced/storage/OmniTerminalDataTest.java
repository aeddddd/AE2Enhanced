package com.github.aeddddd.ae2enhanced.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;

/**
 * {@link OmniTerminalData} 持久化数据测试（WorldSavedData 子类直接 new 测试）。
 *
 * <p>覆盖 readFromNBT/writeToNBT 多 UUID 条目往返、getOrCreate 的脏标记语义
 * （新建标脏、命中不标脏）、remove 幂等性，以及 readFromNBT 清除旧条目的语义。
 * 静态方法 {@code get(World)} 依赖服务器环境，不在本测试范围内。</p>
 */
public class OmniTerminalDataTest {

    private OmniTerminalData data;

    @BeforeAll
    public static void boot() {
        // 用例中构造 ItemStack，且 OmniTerminalData 静态字段触发主 mod 类加载，需无头引导
        AE2TestBootstrap.boot();
    }

    @BeforeEach
    public void setUp() {
        this.data = new OmniTerminalData();
    }

    /** 多 UUID 条目的 writeToNBT/readFromNBT 往返，条目内容（合成栏物品）正确还原。 */
    @Test
    public void testNbtRoundTripMultipleUuids() {
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();

        // 条目 A：合成栏放入物品；条目 B：空存储
        data.getOrCreate(idA).getCraftingInventory().setStackInSlot(0, new ItemStack(Items.APPLE, 3));
        data.getOrCreate(idB);

        NBTTagCompound nbt = data.writeToNBT(new NBTTagCompound());

        OmniTerminalData restored = new OmniTerminalData();
        restored.readFromNBT(nbt);
        assertThat(restored.writeToNBT(new NBTTagCompound()).getTagList("storages", 10))
                .anySatisfy(entry -> assertThat(((NBTTagCompound) entry).getUniqueId("id")).isEqualTo(idA))
                .anySatisfy(entry -> assertThat(((NBTTagCompound) entry).getUniqueId("id")).isEqualTo(idB));

        // getOrCreate 命中已读入的条目不标脏，借此可观测"确实读回来了"
        restored.setDirty(false);
        OmniTerminalStorage storageA = restored.getOrCreate(idA);
        assertThat(restored.isDirty()).isFalse();
        ItemStack slot0 = storageA.getCraftingInventory().getStackInSlot(0);
        assertThat(slot0.getItem()).isEqualTo(Items.APPLE);
        assertThat(slot0.getCount()).isEqualTo(3);

        restored.setDirty(false);
        assertThat(restored.getOrCreate(idB)).isNotNull();
        assertThat(restored.isDirty()).isFalse();

        // 未写入过的 UUID 走新建分支并标脏
        restored.setDirty(false);
        restored.getOrCreate(UUID.randomUUID());
        assertThat(restored.isDirty()).isTrue();
    }

    /** getOrCreate：新建时 markDirty 并返回同一实例；已存在时不标脏。 */
    @Test
    public void testGetOrCreateMarksDirtyOnlyWhenCreating() {
        UUID id = UUID.randomUUID();
        assertThat(data.isDirty()).isFalse();

        OmniTerminalStorage created = data.getOrCreate(id);
        assertThat(created).isNotNull();
        assertThat(data.isDirty()).isTrue();

        // 已存在：返回同一实例，不标脏
        data.setDirty(false);
        OmniTerminalStorage again = data.getOrCreate(id);
        assertThat(again).isSameAs(created);
        assertThat(data.isDirty()).isFalse();
    }

    /** remove 幂等：命中删除标脏；重复删除/删除不存在的 id 不标脏。 */
    @Test
    public void testRemoveIsIdempotent() {
        UUID id = UUID.randomUUID();
        data.getOrCreate(id);

        data.setDirty(false);
        data.remove(id);
        assertThat(data.isDirty()).isTrue();

        // 第二次删除同一 id：无效果、不标脏
        data.setDirty(false);
        data.remove(id);
        assertThat(data.isDirty()).isFalse();

        // 删除从未存在的 id：不标脏
        data.remove(UUID.randomUUID());
        assertThat(data.isDirty()).isFalse();

        // 删除后 getOrCreate 重新走新建分支
        data.setDirty(false);
        data.getOrCreate(id);
        assertThat(data.isDirty()).isTrue();
    }

    /** readFromNBT 先清空既有条目：读入空 NBT 后原 UUID 不再存在。 */
    @Test
    public void testReadFromNbtClearsPreviousEntries() {
        UUID id = UUID.randomUUID();
        data.getOrCreate(id);

        data.readFromNBT(new NBTTagCompound());

        // 原条目已被清除，getOrCreate 重新走新建分支（标脏可观测）
        data.setDirty(false);
        data.getOrCreate(id);
        assertThat(data.isDirty()).isTrue();
    }

    @Test
    public void inventoryChangesMarkOwningDataDirtyWithoutCloseAndWriteRootVersion() {
        UUID id = UUID.randomUUID();
        OmniTerminalStorage storage = data.getOrCreate(id);
        data.setDirty(false);

        storage.getRightStorageInventory().setStackInSlot(0, new ItemStack(Items.APPLE, 4));
        assertThat(data.isDirty()).isTrue();

        NBTTagCompound written = data.writeToNBT(new NBTTagCompound());
        assertThat(written.getInteger("rootVersion")).isEqualTo(1);

        OmniTerminalData restored = new OmniTerminalData();
        restored.readFromNBT(written);
        restored.setDirty(false);
        restored.getOrCreate(id).getCraftingInventory().extractItem(0, 1, false);
        restored.getOrCreate(id).getRightStorageInventory().extractItem(0, 1, false);
        assertThat(restored.isDirty()).isTrue();
    }
}
